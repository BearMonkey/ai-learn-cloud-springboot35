package org.monkey.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 库存服务启动类
 *
 * <p>核心职责：
 * <ul>
 *   <li>卖家端：管理仓库点位（创建/修改/删除）、商品入库/出库、调整安全库存</li>
 *   <li>下单流程：提供 {@code lock(skuId, quantity)} 接口供 order-service 调用</li>
 *   <li>核心业务不变性：{@code availableStock - lockedStock >= 0}</li>
 *   <li>库存扣减成功后发送领域事件通知 order-service</li>
 * </ul>
 *
 * <p>DDD 架构：
 * <ul>
 *   <li>{@code application} —— 应用层：Controller、DTO、ApplicationService</li>
 *   <li>{@code domain} —— 领域层：InventoryItem 聚合根、领域服务、领域事件、Repository 接口</li>
 *   <li>{@code infrastructure} —— 基础设施层：Repository 实现、消息队列适配器</li>
 *   <li>{@code interfaces} —— 接口层：Feign Client 接口定义、事件发布接口</li>
 * </ul>
 *
 * @author monkey
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "org.monkey")
@EnableScheduling
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
