# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建命令

```bash
# 编译全部模块
mvn compile

# 运行全部测试
mvn test

# 运行单个测试类
mvn test -pl <module> -Dtest=<TestClassName>

# 跳过测试编译
mvn compile -DskipTests

# 打包全部服务
mvn package -DskipTests

# 打包单个模块
mvn package -pl <module> -DskipTests

# 启动单个服务（开发环境，需先启动基础设施）
mvn spring-boot:run -pl <module>
```

## 基础设施（开发环境）

启动依赖服务（MySQL + Redis + Nacos）：

```bash
docker-compose up -d
```

关键地址：
- Nacos 控制台: http://localhost:8848/nacos (nacos/nacos)
- MySQL: localhost:3306 (root/root)
- Redis: localhost:6379
- Gateway: http://localhost:8080

停止并清理数据：`docker-compose down -v`

MySQL 初始化脚本：`docker/mysql/init.sql` 创建 4 个独立数据库。

## 项目架构

商城微服务系统，Spring Boot 3.4.1 + Spring Cloud 2024.0.0 + Spring Cloud Alibaba 2023.0.3.2, Java 17。

### 模块依赖关系

```
gateway-service          (Spring Cloud Gateway，端口 8080)
    │
    └──路由转发──→ iam-service (认证/用户)
                   catalog-service (商品)
                   inventory-service (库存)
                   order-service (订单)

order-service ──Feign──→ catalog-service (SKU 查询)
order-service ──Feign──→ inventory-service (锁定/扣减/释放库存)
catalog-service ──Feign──→ inventory-service (库存查询)
```

### 模块说明

| 模块 | 类型 | 职责 |
|------|------|------|
| `gateway-service` | 网关 (WebFlux) | JWT 鉴权、路由转发、traceId 注入 |
| `iam-service` | 业务服务 | 注册、登录、JWT 签发、角色管理 |
| `iam-service-api` | API 契约 | `IamFeignClient` + `UserDTO` |
| `catalog-service` | 业务服务 | 商品和 SKU 管理（买家/卖家） |
| `catalog-service-api` | API 契约 | `CatalogFeignClient` + `ProductDTO`/`SkuDTO` |
| `inventory-service` | 业务服务 | 仓库管理、库存锁定/扣减/释放 |
| `inventory-service-api` | API 契约 | `InventoryFeignClient` + `InventoryDTO` |
| `order-service` | 业务服务 | 订单创建、支付、发货、取消 |
| `order-service-api` | API 契约 | `OrderFeignClient` + `OrderDTO` |
| `common-core` | 共享库 | `Result`、`UserContextHolder`、`TraceIdFilter`、`FeignTraceIdInterceptor` |

每个 `*-api` 模块定义 `@FeignClient` 接口和 DTO，服务间通过 Maven 依赖引用。

### 端口分布

| 模块                     | 端口    |
|------------------------|-------|
| `gateway-service`      | 8080  |
| `iam-service`          | 8090  |
| `catalog-service`      | 8100  |
| `inventory-service`    | 8110  |
| `order-service`        | 8120  | 
| `mcp-server`           | 8130  |
| `review-agent-service` | 8140  |
| `test-service`         | 10000 |

### 认证机制

- **没有使用 Spring Security**，只用了 `spring-security-crypto` 的 BCrypt
- JWT 使用 jjwt 库，HMAC-SHA256 对称签名，密钥在 `gateway-service` 和 `iam-service` 共享
- Gateway 的 `AuthGlobalFilter`（order = -1）拦截所有非公开路径，校验 Bearer JWT，检查 Redis 黑名单
- 校验通过后注入 `X-User-Id` 和 `X-User-Roles` 头到下游
- `common-core` 的 `TraceIdFilter` 在 Servlet 服务中从 Header 提取用户信息到 `UserContextHolder` ThreadLocal
- JWT 黑名单存储在 Redis，key 格式 `blacklist:token:<token>`，TTL = JWT 剩余有效期
- 公开路径在 `GatewayAuthProperties` 配置：`/iam/auth/register`、`/iam/auth/login`、`/swagger-ui` 等

### 分布式链路追踪

- Gateway 的 `TraceIdFilter`（order = -2）为每个请求生成 UUID 作为 traceId
- 通过 `X-Trace-Id` Header 透传到下游
- `common-core` 注入到 MDC，日志模式使用 `%X{traceId}`
- Feign 调用通过 `FeignTraceIdInterceptor` 自动传播 traceId
- 响应体中 `Result.traceId` 字段返回 traceId

### DDD 分层结构

catalog/inventory/order 三个服务采用 DDD 分层：

```
controller/     REST 控制器，DTO 转换
service/        领域服务接口
service/impl/   领域服务实现（编排）
model/          聚合根、领域对象（富模型，封装业务逻辑）
entity/         MyBatis-Plus 实体类
mapper/         MyBatis-Plus Mapper 接口
dto/            请求/响应 DTO
config/         配置类（异常处理、MyBatis-Plus、Swagger 等）
```

仓储模式：`*Repository` 接口定义持久化契约 → `*RepositoryImpl` 在 MyBatis-Plus 上实现 → `*EntityMapper` 负责实体与领域对象的相互转换。

### 订单状态机

```
PENDING_PAYMENT → PENDING_DELIVERY → IN_DELIVERY → COMPLETED
       ↓                ↓
   CANCELLED  ←─────────┘
```

### Nacos 配置

所有业务服务使用 Nacos 作为注册中心和配置中心：
- 命名空间: `08f5b907-570f-4347-9a73-3b182ad669ae`
- 分组: `MALL_GROUP`
- Gateway 只使用 Nacos 服务发现，不使用 Nacos 配置
- 配置导入: `optional:nacos:${spring.application.name}-${spring.profiles.active}.yaml`

## 编码规范

- 所有方法、变量、常量、接口、类、枚举必须有 JavaDoc 注释
- 所有逻辑代码段必须写代码注释
- 使用单行注释 `//`，禁止使用块注释 `/**/`（JavaDoc 除外）
- 提交信息使用简体中文，简短一句话
- 禁止直接 commit 或 push 代码
