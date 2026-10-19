package org.monkey.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类
 *
 * <p>核心职责：
 * <ul>
 *   <li>买家端：创建订单、查看我的订单列表/详情、取消订单</li>
 *   <li>卖家端：查看店铺订单列表、发货（订单状态流转）</li>
 *   <li>下单流程（最终一致性）：
 *     <ol>
 *       <li>创建订单（状态：待支付）</li>
 *       <li>调用 inventory-service.lock() 锁定库存</li>
 *       <li>锁定成功 → 订单状态变更为"待发货"</li>
 *       <li>锁定失败 → 订单状态变更为"已取消"</li>
 *     </ol>
 *   </li>
 *   <li>订单状态机：待支付 → 待发货 → 配送中 → 已完成 / 已取消</li>
 * </ul>
 *
 * <p>DDD 架构：
 * <ul>
 *   <li>{@code application} —— 应用层：Controller、DTO、ApplicationService</li>
 *   <li>{@code domain} —— 领域层：Order 聚合根、领域服务、领域事件、Repository 接口</li>
 *   <li>{@code infrastructure} —— 基础设施层：Repository 实现、Feign Client 实现、消息队列适配器</li>
 *   <li>{@code interfaces} —— 接口层：Feign Client 接口定义</li>
 * </ul>
 *
 * @author monkey
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "org.monkey")
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
