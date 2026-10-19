# review-agent-service 可靠性改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为审核 Agent 补齐可靠性能力：审计落库 + eventId 幂等 + 消费简化 + LLM 调用容错 + 定时补偿，零新组件。

**Architecture:** 决策先行落库（审计表 = 幂等键 + 补偿数据源 + 观测数据源）；MQ 重投只负责技术失败的投递契约，业务失败（回调 catalog 失败）置 FAILED 后正常 ack，恢复全权交给每分钟的定时补偿任务；LLM 调用统一走 LlmInvoker（错误分类 → 瞬时错误指数退避重试 → 耗尽降级转人工）。

**Tech Stack:** Spring Boot 3.4.1 / MyBatis-Plus 3.5.9 / RocketMQ / LangChain4j / JUnit 5 + Mockito + AssertJ

**设计文档:** `docs/superpowers/specs/2026-09-17-review-agent-enterprise-hardening-design.md` 第 4 章

**注意:** 本项目不是 git 仓库且规范禁止自动提交——计划中的"检查点"以编译/测试通过为准，不执行任何 commit/push。

---

### Task 1: 持久化依赖与配置（pom + 数据源 + 启动类注解）

**Files:**
- Modify: `agent-service/review-agent-service/pom.xml`
- Modify: `agent-service/review-agent-service/src/main/resources/application-dev.yml`
- Modify: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/ReviewAgentApp.java`

- [ ] **Step 1: pom 增加 MyBatis-Plus 与 MySQL 驱动**

在 `review-agent-service/pom.xml` 的 RocketMQ starter 依赖之后追加：

```xml
        <!-- MyBatis-Plus：审计记录持久化（版本由根 pom 统一管理） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>

        <!-- MySQL 驱动（仅运行时需要） -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: application-dev.yml 增加数据源与 MyBatis-Plus 配置**

在 `application-dev.yml` 末尾追加：

```yaml
# ========== MySQL 数据源（review_agent_db：审计记录独立库） ==========
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/review_agent_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

# ========== MyBatis-Plus（与其他服务同款配置） ==========
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

- [ ] **Step 3: 启动类加 @MapperScan 与 @EnableScheduling**

将 `ReviewAgentApp.java` 改为：

```java
package org.monkey.agent.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 审核 Agent 服务启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "org.monkey")
@MapperScan("org.monkey.agent.review.mapper")   // 审计记录 Mapper 扫描
@EnableScheduling                               // 定时补偿任务
public class ReviewAgentApp {
    public static void main(String[] args) {
        SpringApplication.run(ReviewAgentApp.class, args);
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl agent-service/review-agent-service`
Expected: BUILD SUCCESS

---

### Task 2: 数据库 DDL（review_agent_db + 审计表）

**Files:**
- Modify: `docker/mysql/init.sql`

- [ ] **Step 1: init.sql 末尾追加审核库与审计表**

在 `docker/mysql/init.sql` 文件末尾追加：

```sql
-- ================================================================
-- review_agent_db —— 审核 Agent 独立库（审计记录）
-- ================================================================
CREATE DATABASE IF NOT EXISTS review_agent_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE review_agent_db;

-- 商品审核审计记录表
CREATE TABLE `t_product_review_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id`        VARCHAR(64)  NOT NULL                COMMENT '审核事件ID（幂等键，唯一）',
    `product_id`      BIGINT       NOT NULL                COMMENT '商品ID',
    `seller_id`       BIGINT       NOT NULL                COMMENT '卖家ID',
    `trace_id`        VARCHAR(64)  DEFAULT NULL            COMMENT '链路追踪ID',
    `decision`        VARCHAR(16)  NOT NULL                COMMENT '裁决：APPROVED/REJECTED/ESCALATED',
    `decision_reason` TEXT         DEFAULT NULL            COMMENT '裁决理由（含各节点驳回/转人工原因）',
    `node_results`    JSON         DEFAULT NULL            COMMENT '四路节点结论（verdict/reason）',
    `llm_usage`       JSON         DEFAULT NULL            COMMENT 'LLM 用量（token 计量后续接入，当前置空）',
    `graph_cost_ms`   BIGINT       DEFAULT NULL            COMMENT '整图执行耗时',
    `callback_status` SMALLINT     NOT NULL DEFAULT 0      COMMENT '回调状态：0=待回调,1=成功,2=失败,3=放弃',
    `callback_retry_count` INT     NOT NULL DEFAULT 0      COMMENT '回调重试次数（补偿扫描用）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品审核审计记录';
```

- [ ] **Step 2: 本地已有数据卷时手动执行 DDL**

`init.sql` 只在 MySQL 容器首次初始化时执行。本地若已有数据卷（容器 `mall-mysql` 已跑过），任选其一：

方式 A（保留数据）：把上面两段 SQL 手动执行到 MySQL（`docker exec -i mall-mysql mysql -uroot -proot` 后粘贴执行）
方式 B（清空重建）：`docker-compose down -v && docker-compose up -d`（会清掉全部数据，仅学习环境可用）

- [ ] **Step 3: 验证建表结果**

Run: `docker exec -i mall-mysql mysql -uroot -proot -e "SHOW CREATE TABLE review_agent_db.t_product_review_record\G"`
Expected: 输出含 `UNIQUE KEY uk_event_id (event_id)` 和 `callback_status` 字段

---

### Task 3: 审计实体与 Mapper

**Files:**
- Create: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/entity/ReviewRecordEntity.java`
- Create: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/mapper/ReviewRecordMapper.java`

- [ ] **Step 1: 创建实体类 ReviewRecordEntity**

```java
package org.monkey.agent.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品审核审计记录实体 —— 映射 t_product_review_record 表
 *
 * <p>职责：审核裁决的持久化凭证——幂等键（event_id 唯一索引）、回调补偿数据源、观测统计数据源
 *
 * @author monkey
 */
@TableName("t_product_review_record")
@Data
public class ReviewRecordEntity {

    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 审核事件ID（幂等键，uk_event_id 唯一索引）
    private String eventId;

    // 商品ID
    private Long productId;

    // 卖家ID
    private Long sellerId;

    // 链路追踪ID
    private String traceId;

    // 裁决：APPROVED/REJECTED/ESCALATED
    private String decision;

    // 裁决理由（含各节点驳回/转人工原因）
    private String decisionReason;

    // 四路节点结论 JSON（verdict/reason）
    private String nodeResults;

    // LLM 用量 JSON（token 计量后续接入，当前置空）
    private String llmUsage;

    // 整图执行耗时（毫秒）
    private Long graphCostMs;

    // 回调状态：0=待回调,1=成功,2=失败,3=放弃
    private Integer callbackStatus;

    // 回调重试次数（补偿扫描用）
    private Integer callbackRetryCount;

    // 创建时间
    private LocalDateTime createdAt;

    // 更新时间
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 Mapper 接口**

```java
package org.monkey.agent.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.monkey.agent.review.entity.ReviewRecordEntity;

/**
 * 审核审计记录 Mapper —— MyBatis-Plus BaseMapper 提供单表 CRUD
 *
 * @author monkey
 */
public interface ReviewRecordMapper extends BaseMapper<ReviewRecordEntity> {
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl agent-service/review-agent-service`
Expected: BUILD SUCCESS

---

### Task 4: LlmErrorClassifier（TDD）

**Files:**
- Create: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/component/llm/LlmErrorClassifier.java`
- Test: `agent-service/review-agent-service/src/test/java/org/monkey/agent/review/service/component/llm/LlmErrorClassifierTest.java`

- [ ] **Step 1: 写失败测试**

```java
package org.monkey.agent.review.service.component.llm;

import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmErrorClassifier 单测：可重试（瞬时）与不可重试（永久）两类错误的分类
 */
class LlmErrorClassifierTest {

    private final LlmErrorClassifier classifier = new LlmErrorClassifier();

    @Test
    void shouldRetryTimeout() {
        assertThat(classifier.isRetryable(new SocketTimeoutException("read timed out"))).isTrue();
    }

    @Test
    void shouldRetryConnectFailure() {
        assertThat(classifier.isRetryable(new ConnectException("connection refused"))).isTrue();
    }

    @Test
    void shouldRetryHttp429() {
        // 429 限流：退避后重试
        assertThat(classifier.isRetryable(new HttpException(429, "rate limited"))).isTrue();
    }

    @Test
    void shouldRetryHttp5xx() {
        // 服务器错误：瞬时故障
        assertThat(classifier.isRetryable(new HttpException(503, "service unavailable"))).isTrue();
    }

    @Test
    void shouldNotRetryHttp401() {
        // 鉴权错误：重试无意义
        assertThat(classifier.isRetryable(new HttpException(401, "unauthorized"))).isFalse();
    }

    @Test
    void shouldNotRetryHttp400() {
        // 参数错误：重试无意义还烧钱
        assertThat(classifier.isRetryable(new HttpException(400, "bad request"))).isFalse();
    }

    @Test
    void shouldNotRetryUnknownError() {
        // 未知异常（含 LLM 输出解析失败）：宁可转人工
        assertThat(classifier.isRetryable(new IllegalArgumentException("输出解析失败"))).isFalse();
    }

    @Test
    void shouldFindRetryableInCauseChain() {
        // cause 链深处埋超时：包装层再多也能识别
        assertThat(classifier.isRetryable(new RuntimeException(new IOException(new SocketTimeoutException())))).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=LlmErrorClassifierTest`
Expected: 编译失败（LlmErrorClassifier 不存在）

- [ ] **Step 3: 实现分类器**

```java
package org.monkey.agent.review.service.component.llm;

import dev.langchain4j.exception.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * LLM 调用错误分类器：区分瞬时错误（可重试）与永久错误（重试无意义）
 *
 * <p>遍历 cause 链识别：超时/连接失败/429/5xx 可重试；其余（4xx/参数错/非法输出）不重试
 *
 * @author monkey
 */
public class LlmErrorClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmErrorClassifier.class);

    // HTTP 429：供应商限流，退避后可重试
    private static final int TOO_MANY_REQUESTS = 429;

    /**
     * 判断异常是否可重试
     *
     * @param throwable 调用抛出的异常
     * @return true=可重试（瞬时错误），false=永久错误
     */
    public boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // 超时与连接失败：网络抖动，重试有效
            if (current instanceof SocketTimeoutException || current instanceof ConnectException) {
                return true;
            }
            // 其余 IO 异常：连接被重置等瞬时网络问题
            if (current instanceof IOException) {
                return true;
            }
            // langchain4j HttpException：按状态码分类
            if (current instanceof HttpException httpException) {
                int status = httpException.statusCode();
                if (status == TOO_MANY_REQUESTS || status >= 500) {
                    // 限流与服务器错误：退避后重试
                    return true;
                }
                // 4xx（除 429）：请求本身有问题，重试无意义还烧钱
                return false;
            }
            current = current.getCause();
        }
        // 未知异常：按永久错误处理，宁可转人工
        return false;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=LlmErrorClassifierTest`
Expected: 8 个测试全部 PASS

---

### Task 5: LlmInvoker + LlmInvokeException（TDD）

**Files:**
- Create: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/component/llm/LlmInvokeException.java`
- Create: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/component/llm/LlmInvoker.java`
- Test: `agent-service/review-agent-service/src/test/java/org/monkey/agent/review/service/component/llm/LlmInvokerTest.java`

- [ ] **Step 1: 写失败测试**

```java
package org.monkey.agent.review.service.component.llm;

import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmInvoker 单测：成功直返 / 瞬时错误重试后成功 / 永久错误不重试 / 重试耗尽抛异常
 */
class LlmInvokerTest {

    private final LlmInvoker invoker = new LlmInvoker();

    @Test
    void shouldReturnResultOnFirstTry() {
        String result = invoker.invoke("test", () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRetryThenSucceed() {
        AtomicInteger calls = new AtomicInteger();
        String result = invoker.invoke("test", () -> {
            // 前两次抛超时（瞬时），第三次成功
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException(new SocketTimeoutException("timeout"));
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3); // 1 次正常 + 2 次重试
    }

    @Test
    void shouldNotRetryPermanentError() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> invoker.invoke("test", () -> {
            calls.incrementAndGet();
            throw new HttpException(401, "unauthorized");
        })).isInstanceOf(LlmInvokeException.class);
        assertThat(calls.get()).isEqualTo(1); // 永久错误：立即失败不重试
    }

    @Test
    void shouldExhaustAndThrow() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> invoker.invoke("test", () -> {
            calls.incrementAndGet();
            throw new RuntimeException(new SocketTimeoutException("timeout"));
        })).isInstanceOf(LlmInvokeException.class);
        assertThat(calls.get()).isEqualTo(3); // 耗尽：3 次尝试后抛异常
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=LlmInvokerTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 创建 LlmInvokeException**

```java
package org.monkey.agent.review.service.component.llm;

/**
 * LLM 调用失败异常：重试耗尽或永久错误时抛出——节点捕获后按 ESCALATED 降级转人工
 *
 * @author monkey
 */
public class LlmInvokeException extends RuntimeException {

    public LlmInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: 实现 LlmInvoker**

```java
package org.monkey.agent.review.service.component.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * LLM 调用统一封装：错误分类 → 瞬时错误指数退避重试 → 耗尽抛 {@link LlmInvokeException}
 *
 * <p>降级语义与审核领域绑定：耗尽后由调用方节点按 ESCALATED 转人工，而非自动通过/驳回
 *
 * @author monkey
 */
@Component
@Slf4j
public class LlmInvoker {

    // 最大尝试次数：1 次正常 + 2 次重试
    private static final int MAX_ATTEMPTS = 3;

    // 基础退避时间（毫秒）：第 n 次重试等待 基础 × 2^(n-1)
    private static final long BASE_BACKOFF_MS = 500;

    // 抖动上限（毫秒）：避免批量任务同步重试打爆供应商
    private static final long MAX_JITTER_MS = 200;

    // 错误分类器：单一职责，独立可测
    private final LlmErrorClassifier classifier = new LlmErrorClassifier();

    /**
     * 带容错执行一次 LLM 调用
     *
     * @param opName 调用名（如 text-judge），用于日志定位
     * @param call   实际调用
     * @return 调用结果
     * @throws LlmInvokeException 永久错误或重试耗尽
     */
    public <T> T invoke(String opName, Supplier<T> call) {
        int attempt = 0;
        while (true) {
            long startMs = System.currentTimeMillis();
            try {
                T result = call.get();
                log.info("[LlmInvoker] {} 调用成功 attempt={} costMs={}", opName, attempt + 1, System.currentTimeMillis() - startMs);
                return result;
            } catch (Exception e) {
                boolean retryable = classifier.isRetryable(e);
                attempt++;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    // 永久错误或重试耗尽：抛业务异常，节点捕获后降级转人工
                    log.error("[LlmInvoker] {} 调用失败 attempt={} retryable={}", opName, attempt, retryable, e);
                    throw new LlmInvokeException(opName + " 调用失败（attempt=" + attempt + "）", e);
                }
                // 指数退避 + 抖动
                long backoffMs = BASE_BACKOFF_MS * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(MAX_JITTER_MS);
                log.warn("[LlmInvoker] {} 瞬时错误，{}ms 后重试（第 {} 次）", opName, backoffMs, attempt, e);
                sleep(backoffMs);
            }
        }
    }

    /**
     * 退避等待：被中断时恢复中断标记，让上层能感知取消
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=LlmInvokerTest`
Expected: 4 个测试全部 PASS（耗时约 2 秒，含退避等待）

---

### Task 6: 三个节点的 LLM 调用点接入 LlmInvoker

**Files:**
- Modify: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/component/node/TextReviewNode.java`
- Modify: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/component/node/CategoryCheckNode.java`
- Modify: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/component/node/ParseInputNode.java`

- [ ] **Step 1: TextReviewNode 接入（降级转人工）**

改动 1——类头部增加字段注入（在 `textJudgeService` 字段后追加）：

```java
    // LLM 调用统一封装：错误分类重试，耗尽降级转人工
    @Autowired
    private LlmInvoker llmInvoker;
```

改动 2——import 区追加：

```java
import org.monkey.agent.review.service.component.llm.LlmInvokeException;
import org.monkey.agent.review.service.component.llm.LlmInvoker;
```

改动 3——替换第二层 LLM 调用（原 `TextJudgeResult judge = textJudgeService.judge(...)` 一句）：

```java
        // ========== 第二层：LLM 变体识别（LlmInvoker 容错：瞬时错误重试，耗尽降级转人工） ==========
        TextJudgeResult judge;
        try {
            judge = llmInvoker.invoke("text-judge",
                    () -> textJudgeService.judge(title, description, textRuleScreener.bannedWordsRef()));
        } catch (LlmInvokeException e) {
            // 降级：LLM 服务不可用 → 该路转人工（聚合节点按 ESCALATED 兜底）
            log.error("[Node][TextReviewNode] LLM 服务不可用，降级转人工", e);
            return Map.of(ReviewState.TEXT_RESULT,
                    new TextReviewResult(Verdict.ESCALATED, List.of(), List.of(), "LLM 服务不可用：" + e.getMessage()));
        }
        log.info("[Node][TextReviewNode] judge: {}", judge);
```

- [ ] **Step 2: CategoryCheckNode 接入（降级转人工）**

改动 1——类头部增加字段注入（在 `categoryJudgeService` 字段后追加）：

```java
    // LLM 调用统一封装：错误分类重试，耗尽降级转人工
    @Autowired
    private LlmInvoker llmInvoker;
```

改动 2——import 区追加：

```java
import org.monkey.agent.review.service.component.llm.LlmInvokeException;
import org.monkey.agent.review.service.component.llm.LlmInvoker;
```

改动 3——替换第三层 LLM 精判调用（原 `CategoryJudgeResult judge = categoryJudgeService.judge(...)` 一段）：

```java
        // ========== 第三层：LLM 精判（LlmInvoker 容错：瞬时错误重试，耗尽降级转人工） ==========
        String selectedCategoryText = profile.toLlmJudgeText();
        long startTime = System.currentTimeMillis();
        CategoryJudgeResult judge;
        try {
            judge = llmInvoker.invoke("category-judge",
                    () -> categoryJudgeService.judge(state.title(), state.description(), selectedCategoryText, JSONObject.toJSONString(search)));
        } catch (LlmInvokeException e) {
            // 降级：LLM 服务不可用 → 该路转人工
            log.error("[Node][CategoryCheckNode] LLM 服务不可用，降级转人工", e);
            return Map.of(ReviewState.CATEGORY_RESULT, CategoryCheckResult.escalate("LLM 服务不可用：" + e.getMessage()));
        }
        log.info("[Node][CategoryCheckNode] category judge, result={}, cost={}",
                JSONObject.toJSONString(judge), System.currentTimeMillis() - startTime);
```

- [ ] **Step 3: ParseInputNode 接入（保留空列表兜底）**

改动 1——类头部增加字段注入（在 `keywordExtractor` 字段后追加）：

```java
    // LLM 调用统一封装：错误分类重试，耗尽降级为空列表
    @Autowired
    private LlmInvoker llmInvoker;
```

改动 2——import 区追加：

```java
import org.monkey.agent.review.service.component.llm.LlmInvoker;
```

改动 3——替换 `parseKeyword` 方法体中的调用（保留原有 catch 兜底，只把调用包进 LlmInvoker）：

```java
    private List<String> parseKeyword(ReviewState state) {
        List<String> keywords;
        try {
            // LlmInvoker：瞬时错误重试，耗尽抛 LlmInvokeException → 走下方空列表兜底
            keywords result = llmInvoker.invoke("parse-input",
                    () -> keywordExtractor.extract(state.title(), state.description()));
            keywords = result.getKeywords();
        } catch (Exception e) {
            // 兜底：LLM 调用失败不中断图，降级为空列表（后续节点有 orElse 兜底）
            log.warn("[Node][ParseInputNode][parseKeyword] LLM 提词失败，降级为空列表，", e);
            keywords = List.of();
        }
        log.info("[Node][ParseInputNode][parseKeyword] 提取关键词: {}", keywords);
        return keywords;
    }
```

- [ ] **Step 4: 编译 + 既有测试回归**

Run: `mvn test -pl agent-service/review-agent-service`
Expected: BUILD SUCCESS，全部既有测试（TextRuleRepositoryTest / TextRuleScreenerTest）保持 PASS

---

### Task 7: ReviewServiceImpl 改造（TDD）

**Files:**
- Modify: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/service/impl/ReviewServiceImpl.java`（整体重写）
- Test: `agent-service/review-agent-service/src/test/java/org/monkey/agent/review/service/impl/ReviewServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

```java
package org.monkey.agent.review.service.impl;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.monkey.agent.review.entity.ReviewRecordEntity;
import org.monkey.agent.review.mapper.ReviewRecordMapper;
import org.monkey.agent.review.service.component.ReviewGraphRunner;
import org.monkey.agent.review.service.component.model.ReviewDecision;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.catalog.api.dto.ProductDTO;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.monkey.common.core.dto.Result;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ReviewServiceImpl 单测：重入短路 / 状态守卫 / 决策先行落库 / 回调分类（业务失败不抛）
 */
class ReviewServiceImplTest {

    private ReviewRecordMapper recordMapper;
    private ReviewGraphRunner graphRunner;
    private CatalogFeignClient catalogFeignClient;
    private ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        recordMapper = mock(ReviewRecordMapper.class);
        graphRunner = mock(ReviewGraphRunner.class);
        catalogFeignClient = mock(CatalogFeignClient.class);
        service = new ReviewServiceImpl();
        // 字段注入的 Bean 在单测里手动装配
        ReflectionTestUtils.setField(service, "reviewRecordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "reviewGraphRunner", graphRunner);
        ReflectionTestUtils.setField(service, "catalogFeignClient", catalogFeignClient);
    }

    @Test
    void shouldSkipWhenRecordExists() {
        // 审计表已存在（MQ 重投）→ 不跑图不回调
        when(recordMapper.selectCount(any())).thenReturn(1L);
        service.handleProductAudit(msg("e1", 100L, 1L));
        verify(graphRunner, never()).run(any());
        verify(catalogFeignClient, never()).productAudit(anyLong(), anyLong(), any());
    }

    @Test
    void shouldSkipWhenProductNotOnReview() {
        // 状态守卫：非 ON_REVIEW = 生产者重复事件，跳过
        when(recordMapper.selectCount(any())).thenReturn(0L);
        ProductDTO product = product(ProductStatus.REJECTED.getValue());
        when(catalogFeignClient.getProductById(100L)).thenReturn(Result.success(product));
        service.handleProductAudit(msg("e1", 100L, 1L));
        verify(graphRunner, never()).run(any());
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void shouldInsertBeforeCallbackAndMarkSuccess() {
        // 正常流：图出裁决 → 先落库（PENDING）→ 回调 → 回写 SUCCESS
        when(recordMapper.selectCount(any())).thenReturn(0L);
        ProductDTO product = product(ProductStatus.ON_REVIEW.getValue());
        when(catalogFeignClient.getProductById(100L)).thenReturn(Result.success(product));
        when(graphRunner.run(product)).thenReturn(Optional.of(state(ReviewDecision.reject("违禁词命中"))));

        service.handleProductAudit(msg("e1", 100L, 1L));

        // 决策先行：insert 必须先于回调
        InOrder inOrder = inOrder(recordMapper, catalogFeignClient);
        ArgumentCaptor<ReviewRecordEntity> captor = ArgumentCaptor.forClass(ReviewRecordEntity.class);
        inOrder.verify(recordMapper).insert(captor.capture());
        inOrder.verify(catalogFeignClient).productAudit(1L, 100L, ProductStatus.REJECTED);
        assertThat(captor.getValue().getCallbackStatus()).isEqualTo(0); // PENDING
        assertThat(captor.getValue().getDecision()).isEqualTo("REJECTED");
        // 回调成功 → SUCCESS 回写
        verify(recordMapper).update(any(), any());
    }

    @Test
    void shouldMarkFailedButNotThrowOnCallbackFailure() {
        // 业务失败：回调抛 FeignException → 置 FAILED，不向上抛（正常 ack）
        when(recordMapper.selectCount(any())).thenReturn(0L);
        ProductDTO product = product(ProductStatus.ON_REVIEW.getValue());
        when(catalogFeignClient.getProductById(100L)).thenReturn(Result.success(product));
        when(graphRunner.run(product)).thenReturn(Optional.of(state(ReviewDecision.approve("通过", 1.0))));
        when(recordMapper.insert(any())).thenReturn(1);
        doThrow(FeignException.ServiceUnavailable.class).when(catalogFeignClient)
                .productAudit(anyLong(), anyLong(), any());

        assertThatCode(() -> service.handleProductAudit(msg("e1", 100L, 1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipWhenDuplicateKeyOnInsert() {
        // 并发同消息：INSERT 唯一键冲突 → 跳过，不回调
        when(recordMapper.selectCount(any())).thenReturn(0L);
        ProductDTO product = product(ProductStatus.ON_REVIEW.getValue());
        when(catalogFeignClient.getProductById(100L)).thenReturn(Result.success(product));
        when(graphRunner.run(product)).thenReturn(Optional.of(state(ReviewDecision.approve("通过", 1.0))));
        when(recordMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_event_id 冲突"));

        service.handleProductAudit(msg("e1", 100L, 1L));
        verify(catalogFeignClient, never()).productAudit(anyLong(), anyLong(), any());
    }

    // ========== 测试辅助 ==========

    private ProductEventMessage msg(String eventId, Long productId, Long sellerId) {
        ProductEventMessage msg = new ProductEventMessage();
        msg.setEventId(eventId);
        msg.setProductId(productId);
        msg.setSellerId(sellerId);
        msg.setTraceId("trace-1");
        return msg;
    }

    private ProductDTO product(int status) {
        return ProductDTO.builder().id(100L).sellerId(1L).title("测试商品").status(status).build();
    }

    private ReviewState state(ReviewDecision decision) {
        return new ReviewState(Map.of(ReviewState.FINAL_DECISION, decision));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=ReviewServiceImplTest`
Expected: 编译失败（ReviewServiceImpl 尚无 recordMapper 字段）

- [ ] **Step 3: 整体重写 ReviewServiceImpl**

```java
package org.monkey.agent.review.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.entity.ReviewRecordEntity;
import org.monkey.agent.review.mapper.ReviewRecordMapper;
import org.monkey.agent.review.service.ReviewService;
import org.monkey.agent.review.service.component.ReviewGraphRunner;
import org.monkey.agent.review.service.component.model.ReviewDecision;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.ReviewResult;
import org.monkey.catalog.api.dto.ProductDTO;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.monkey.common.core.dto.Result;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 商品审核服务实现：消费简化后的主流程
 *
 * <p>流程：重入检查（eventId）→ 查商品 → 状态守卫（ON_REVIEW）→ 跑图 → 决策先行落库 → 回调 catalog
 *
 * <p>异常分类：技术失败（查商品异常/DB 不可用/图异常）自然上抛 → MQ 重投；
 * 业务失败（回调失败）置 FAILED 后正常返回，恢复全权交给定时补偿任务
 */
@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    // 回调状态常量：与 t_product_review_record.callback_status 数字编码一致
    private static final int CALLBACK_PENDING = 0;
    private static final int CALLBACK_SUCCESS = 1;
    private static final int CALLBACK_FAILED = 2;

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    @Autowired
    private ReviewGraphRunner reviewGraphRunner;

    @Autowired
    private CatalogFeignClient catalogFeignClient;

    @Override
    public void handleProductAudit(ProductEventMessage msg) {
        log.info("处理商品审核事件: eventId={}", msg.getEventId());
        String eventId = msg.getEventId();
        Long productId = msg.getProductId();

        // 1. 重入检查：同一事件已处理过 → 直接返回 ack，恢复由定时补偿负责
        if (existsByEventId(eventId)) {
            log.info("审计记录已存在（MQ 重投），跳过: eventId={}", eventId);
            return;
        }

        // 2. 查询商品信息（Feign 失败是技术失败，异常自然上抛 → MQ 重投）
        Result<ProductDTO> productById = catalogFeignClient.getProductById(productId);
        if (productById == null || productById.getData() == null) {
            log.info("商品不存在，跳过: productId={}", productId);
            return;
        }
        ProductDTO product = productById.getData();

        // 3. 状态守卫：非 ON_REVIEW = 生产者重复事件，跳过
        if (product.getStatus() == null || product.getStatus() != ProductStatus.ON_REVIEW.getValue()) {
            log.info("商品状态非审核中（生产者重复事件），跳过: productId={}, status={}", productId, product.getStatus());
            return;
        }

        // 4. 跑审核图（图只做决策，是幂等纯计算）
        long startMs = System.currentTimeMillis();
        Optional<ReviewState> result = reviewGraphRunner.run(product);
        ReviewDecision decision = result.map(ReviewState::finalDecision).orElse(null);
        if (decision == null) {
            // 聚合节点兜底保证非空，理论不可达
            log.error("审核图未产出裁决: sellerId={}, productId={}", msg.getSellerId(), productId);
            return;
        }
        long graphCostMs = System.currentTimeMillis() - startMs;

        // 5. 决策先行落库（幂等键 uk_event_id；冲突 = 并发同消息，跳过）
        try {
            reviewRecordMapper.insert(buildRecord(msg, product, decision, result.get(), graphCostMs));
        } catch (DuplicateKeyException e) {
            log.info("并发同消息，审计记录已存在，跳过: eventId={}", eventId);
            return;
        }

        // 6. 回调 catalog（业务失败：置 FAILED 后正常返回，不抛异常）
        try {
            catalogFeignClient.productAudit(product.getSellerId(), productId, toProductStatus(decision));
            updateCallbackStatus(eventId, CALLBACK_SUCCESS);
        } catch (FeignException e) {
            log.error("回调 catalog 失败（业务失败，交定时补偿）: eventId={}", eventId, e);
            updateCallbackStatus(eventId, CALLBACK_FAILED);
        }

        // 中性收尾日志：带裁决状态
        log.info("商品审核处理完成: eventId={}, productId={}, decision={}", eventId, productId, decision.getStatus());
    }

    /**
     * 按事件ID查审计记录是否存在（幂等第一道防线）
     */
    private boolean existsByEventId(String eventId) {
        Long count = reviewRecordMapper.selectCount(new LambdaQueryWrapper<ReviewRecordEntity>()
                .eq(ReviewRecordEntity::getEventId, eventId));
        return count != null && count > 0;
    }

    /**
     * 组装审计记录：决策先行落库，回调结果由本类/补偿任务后续回写
     */
    private ReviewRecordEntity buildRecord(ProductEventMessage msg, ProductDTO product,
                                           ReviewDecision decision, ReviewState state, long graphCostMs) {
        ReviewRecordEntity record = new ReviewRecordEntity();
        record.setEventId(msg.getEventId());
        record.setProductId(product.getId());
        record.setSellerId(product.getSellerId());
        record.setTraceId(msg.getTraceId());
        record.setDecision(decision.getStatus().name());
        record.setDecisionReason(decision.getReason());
        record.setNodeResults(buildNodeResults(state));
        record.setGraphCostMs(graphCostMs);
        record.setCallbackStatus(CALLBACK_PENDING);
        record.setCallbackRetryCount(0);
        return record;
    }

    /**
     * 四路节点结论拼 JSON：审计追溯与评测归因用
     */
    private String buildNodeResults(ReviewState state) {
        JSONObject nodeResults = new JSONObject();
        putNode(nodeResults, "image", state.imageResult());
        putNode(nodeResults, "text", state.textResult());
        putNode(nodeResults, "category", state.categoryResult());
        putNode(nodeResults, "priceQual", state.priceQualResult());
        return nodeResults.toJSONString();
    }

    /**
     * 单路节点结论写入：null 表示节点异常未产出，按无结论记录
     */
    private void putNode(JSONObject target, String key, ReviewResult nodeResult) {
        JSONObject item = new JSONObject();
        if (nodeResult == null) {
            item.put("verdict", "NO_RESULT");
            item.put("reason", "节点无结果");
        } else {
            item.put("verdict", nodeResult.getVerdict().name());
            item.put("reason", nodeResult.getReason());
        }
        target.put(key, item);
    }

    /**
     * 裁决 → catalog 商品状态：APPROVED→在售，REJECTED→驳回，ESCALATED→人工审核
     */
    private ProductStatus toProductStatus(ReviewDecision decision) {
        return switch (decision.getStatus()) {
            case APPROVED -> ProductStatus.ON_SALE;
            case REJECTED -> ProductStatus.REJECTED;
            case ESCALATED -> ProductStatus.HUMAN_REVIEW;
        };
    }

    /**
     * 回写回调状态（按事件ID定位）
     */
    private void updateCallbackStatus(String eventId, int status) {
        ReviewRecordEntity update = new ReviewRecordEntity();
        update.setCallbackStatus(status);
        reviewRecordMapper.update(update, new LambdaQueryWrapper<ReviewRecordEntity>()
                .eq(ReviewRecordEntity::getEventId, eventId));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=ReviewServiceImplTest`
Expected: 5 个测试全部 PASS

---

### Task 8: ProductEventConsumer 改造（移除 Redis 幂等）

**Files:**
- Modify: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/mq/ProductEventConsumer.java`（整体重写）

- [ ] **Step 1: 整体重写消费者**

```java
package org.monkey.agent.review.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.monkey.agent.review.service.ReviewService;
import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商品事件消费者 —— 消费 PRODUCT_EVENT Topic 的 PRODUCT_AUDIT 标签
 *
 * <p>异常分类：技术失败（DB 不可用/查商品失败/图异常）异常自然上抛 → RocketMQ RECONSUME_LATER 重投；
 * 业务失败（回调 catalog 失败）在 Service 层置 FAILED 后正常返回 ack，恢复交定时补偿
 *
 * <p>幂等说明：Redis 幂等键已移除——eventId 唯一索引（审计表）+ 商品状态守卫两层防线替代
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.PRODUCT_EVENT,
        selectorType = SelectorType.TAG,
        consumerGroup = "product-consumer_${rocketmq.env}",
        selectorExpression = EventType.PRODUCT_AUDIT
)
public class ProductEventConsumer implements RocketMQListener<ProductEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    @Autowired
    private ReviewService reviewService;

    @Override
    public void onMessage(ProductEventMessage msg) {
        String traceId = msg.getTraceId();
        MDC.put("traceId", traceId);
        log.info("收到消息: {}", msg);
        try {
            // 技术失败异常上抛 → MQ 重投；业务失败在 Service 层消化（正常返回 ack）
            reviewService.handleProductAudit(msg);
            log.info("商品审核事件处理结束: eventId={}, productId={}", msg.getEventId(), msg.getProductId());
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl agent-service/review-agent-service`
Expected: BUILD SUCCESS（Redis 相关 import 全部移除）

---

### Task 9: 回调补偿任务（TDD）

**Files:**
- Create: `agent-service/review-agent-service/src/main/java/org/monkey/agent/review/task/CallbackCompensationTask.java`
- Test: `agent-service/review-agent-service/src/test/java/org/monkey/agent/review/task/CallbackCompensationTaskTest.java`

- [ ] **Step 1: 写失败测试**

```java
package org.monkey.agent.review.task;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.monkey.agent.review.entity.ReviewRecordEntity;
import org.monkey.agent.review.mapper.ReviewRecordMapper;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CallbackCompensationTask 单测：无记录空转 / 重放成功置 SUCCESS / 失败计数 / 满 5 次置 ABANDONED
 */
class CallbackCompensationTaskTest {

    private ReviewRecordMapper recordMapper;
    private CatalogFeignClient catalogFeignClient;
    private CallbackCompensationTask task;

    @BeforeEach
    void setUp() {
        recordMapper = mock(ReviewRecordMapper.class);
        catalogFeignClient = mock(CatalogFeignClient.class);
        task = new CallbackCompensationTask();
        // 字段注入的 Bean 在单测里手动装配
        ReflectionTestUtils.setField(task, "reviewRecordMapper", recordMapper);
        ReflectionTestUtils.setField(task, "catalogFeignClient", catalogFeignClient);
    }

    @Test
    void shouldDoNothingWhenNoRecords() {
        when(recordMapper.selectList(any())).thenReturn(List.of());
        task.compensate();
        verify(catalogFeignClient, never()).productAudit(anyLong(), anyLong(), any());
    }

    @Test
    void shouldMarkSuccessOnReplay() {
        // 重放成功 → SUCCESS(1)，计数清零
        ReviewRecordEntity record = record(2, 0, "REJECTED");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        task.compensate();
        verify(catalogFeignClient).productAudit(1L, 100L, ProductStatus.REJECTED);
        ArgumentCaptor<ReviewRecordEntity> captor = ArgumentCaptor.forClass(ReviewRecordEntity.class);
        verify(recordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCallbackStatus()).isEqualTo(1);
        assertThat(captor.getValue().getCallbackRetryCount()).isEqualTo(0);
    }

    @Test
    void shouldIncrementRetryCountOnFailure() {
        // 已失败 3 次，第 4 次仍失败：保持 FAILED(2)，计数 4
        ReviewRecordEntity record = record(2, 3, "APPROVED");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        doThrow(FeignException.ServiceUnavailable.class).when(catalogFeignClient)
                .productAudit(anyLong(), anyLong(), any());
        task.compensate();
        ArgumentCaptor<ReviewRecordEntity> captor = ArgumentCaptor.forClass(ReviewRecordEntity.class);
        verify(recordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCallbackStatus()).isEqualTo(2);
        assertThat(captor.getValue().getCallbackRetryCount()).isEqualTo(4);
    }

    @Test
    void shouldAbandonAfterMaxRetries() {
        // 已失败 4 次，第 5 次失败：置 ABANDONED(3)，转人工
        ReviewRecordEntity record = record(2, 4, "APPROVED");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        doThrow(FeignException.ServiceUnavailable.class).when(catalogFeignClient)
                .productAudit(anyLong(), anyLong(), any());
        task.compensate();
        ArgumentCaptor<ReviewRecordEntity> captor = ArgumentCaptor.forClass(ReviewRecordEntity.class);
        verify(recordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCallbackStatus()).isEqualTo(3);
        assertThat(captor.getValue().getCallbackRetryCount()).isEqualTo(5);
    }

    // ========== 测试辅助 ==========

    private ReviewRecordEntity record(int status, int retryCount, String decision) {
        ReviewRecordEntity record = new ReviewRecordEntity();
        record.setId(1L);
        record.setSellerId(1L);
        record.setProductId(100L);
        record.setDecision(decision);
        record.setCallbackStatus(status);
        record.setCallbackRetryCount(retryCount);
        record.setUpdatedAt(LocalDateTime.now().minusMinutes(10)); // 超过重放窗口，可被扫描
        return record;
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=CallbackCompensationTaskTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现补偿任务**

```java
package org.monkey.agent.review.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.entity.ReviewRecordEntity;
import org.monkey.agent.review.mapper.ReviewRecordMapper;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回调补偿任务：回调恢复的唯一机制
 *
 * <p>每分钟扫描待回调（PENDING）与回调失败（FAILED）且未超重试上限、超过重放窗口的记录，
 * 重放回调：成功置 SUCCESS；失败计数 +1，满 5 次置 ABANDONED 转人工
 *
 * <p>恢复延迟上界约 6 分钟（扫描周期 1 分钟 + 重放窗口 5 分钟），审核业务分钟级容忍度内
 *
 * @author monkey
 */
@Component
@Slf4j
public class CallbackCompensationTask {

    // 回调状态常量：与 t_product_review_record.callback_status 数字编码一致
    private static final int CALLBACK_PENDING = 0;
    private static final int CALLBACK_SUCCESS = 1;
    private static final int CALLBACK_FAILED = 2;
    private static final int CALLBACK_ABANDONED = 3;

    // 最大自动重试次数：满 5 次放弃自动补偿，转人工介入
    private static final int MAX_RETRY_COUNT = 5;

    // 重放窗口（分钟）：刚写入的记录可能回调在途，扫早了会抢跑
    private static final long REPLAY_WINDOW_MINUTES = 5;

    // 单轮扫描上限：避免一次补偿风暴打垮 catalog
    private static final int BATCH_SIZE = 50;

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    @Autowired
    private CatalogFeignClient catalogFeignClient;

    /**
     * 每分钟执行：扫描待补偿记录并重放回调
     */
    @Scheduled(fixedDelayString = "${review.compensate.interval-ms:60000}")
    public void compensate() {
        // 重放窗口：updated_at 早于 5 分钟，避开正在途的回调
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(REPLAY_WINDOW_MINUTES);
        List<ReviewRecordEntity> records = reviewRecordMapper.selectList(new LambdaQueryWrapper<ReviewRecordEntity>()
                .in(ReviewRecordEntity::getCallbackStatus, CALLBACK_PENDING, CALLBACK_FAILED)
                .lt(ReviewRecordEntity::getCallbackRetryCount, MAX_RETRY_COUNT)
                .lt(ReviewRecordEntity::getUpdatedAt, cutoff)
                .last("limit " + BATCH_SIZE));
        if (records.isEmpty()) {
            return;
        }
        log.info("[Compensate] 待补偿记录数: {}", records.size());
        for (ReviewRecordEntity record : records) {
            replayCallback(record);
        }
    }

    /**
     * 重放单条记录的回调：记录自包含（productId/sellerId/decision），直接重放无需判断"该补什么"
     */
    private void replayCallback(ReviewRecordEntity record) {
        try {
            catalogFeignClient.productAudit(record.getSellerId(), record.getProductId(), toProductStatus(record.getDecision()));
            updateRecord(record.getId(), CALLBACK_SUCCESS, 0);
            log.info("[Compensate] 重放成功: recordId={}, productId={}", record.getId(), record.getProductId());
        } catch (FeignException e) {
            int newCount = record.getCallbackRetryCount() + 1;
            if (newCount >= MAX_RETRY_COUNT) {
                // 放弃自动补偿：置 ABANDONED，error 日志转人工
                updateRecord(record.getId(), CALLBACK_ABANDONED, newCount);
                log.error("[Compensate] 重试 {} 次仍失败，放弃自动补偿转人工: recordId={}, productId={}",
                        MAX_RETRY_COUNT, record.getId(), record.getProductId(), e);
            } else {
                // 保持 FAILED，下轮继续扫描
                updateRecord(record.getId(), CALLBACK_FAILED, newCount);
                log.warn("[Compensate] 重放失败（第 {} 次）: recordId={}", newCount, record.getId(), e);
            }
        }
    }

    /**
     * 裁决字符串 → catalog 商品状态：未知值宁可转人工
     */
    private ProductStatus toProductStatus(String decision) {
        return switch (decision) {
            case "APPROVED" -> ProductStatus.ON_SALE;
            case "REJECTED" -> ProductStatus.REJECTED;
            default -> ProductStatus.HUMAN_REVIEW;
        };
    }

    /**
     * 回写回调状态与重试计数
     */
    private void updateRecord(Long id, int status, int retryCount) {
        ReviewRecordEntity update = new ReviewRecordEntity();
        update.setId(id);
        update.setCallbackStatus(status);
        update.setCallbackRetryCount(retryCount);
        reviewRecordMapper.updateById(update);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl agent-service/review-agent-service -Dtest=CallbackCompensationTaskTest`
Expected: 4 个测试全部 PASS

---

### Task 10: 全量验证与端到端清单

**Files:** 无（验证任务）

- [ ] **Step 1: 全量单元测试**

Run: `mvn test -pl agent-service/review-agent-service`
Expected: BUILD SUCCESS，新增 17 个测试 + 既有 2 个测试类全部 PASS

- [ ] **Step 2: 启动基础依赖并启动服务**

Run: `docker-compose up -d`（若已启动跳过）
Run: `mvn spring-boot:run -pl agent-service/review-agent-service`
Expected: 服务启动无异常，日志含 MyBatis-Plus SQL 输出（说明数据源连通）

- [ ] **Step 3: 端到端验证清单（逐项手工执行）**

1. 正常审核：创建商品 → 审核通过 → `t_product_review_record` 新增一条 `callback_status=1`、`decision=APPROVED`，catalog 商品状态变在售
2. 重复消息：用 RocketMQ 控制台重发同一条消息（eventId 不变）→ 日志出现"审计记录已存在（MQ 重投），跳过"，不重跑图
3. 业务失败：停掉 catalog-service → 创建商品 → 审核完成但回调失败 → 记录 `callback_status=2`，商品卡 ON_REVIEW → 重启 catalog → **6 分钟内**补偿任务自动重放 → 记录变 `callback_status=1`，商品状态同步
4. 放弃路径：停 catalog 超 6 分钟（补偿重试 5 次）→ 记录变 `callback_status=3`（ABANDONED），日志出现 error "放弃自动补偿转人工"
5. 技术失败：停止 MySQL → 消费消息 → 日志报 DB 异常 → 消息重投（RocketMQ 控制台可见重试）→ 恢复 MySQL 后消息被重新消费成功

- [ ] **Step 4: 完成标准**

以上 5 项端到端验证全部符合预期，且 Step 1 全量测试保持绿色，可靠性部分完成。

---

## 计划自审记录

- **设计文档覆盖**：4.1 审计落库（Task 2/3/7）✓；4.2 幂等改造与消费简化（Task 7/8）✓；4.3 LlmInvoker（Task 4/5/6）✓；4.4 回调补偿（Task 9）✓；测试策略第 8 章（各任务 TDD）✓
- **范围边界（如实声明）**：llm_usage 字段的 token 计量不在本计划——留给可观测性计划（统计任务）接入；catalog `productAudit` 幂等性验证在端到端清单第 3 项自然覆盖（重复重放无害即幂等）
- **类型一致性**：`callback_status` 数字编码 0/1/2/3 在 Entity 注释、ServiceImpl 常量、CompensationTask 常量三处一致；`ReviewRecordMapper.update(update, wrapper)` 与 `updateById` 均为 MyBatis-Plus 3.5.9 标准 API
