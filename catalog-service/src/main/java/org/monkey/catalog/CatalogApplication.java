package org.monkey.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 产品目录服务启动类
 *
 * <p>核心职责：
 * <ul>
 *   <li>卖家端：商品上架/下架、修改价格/描述/图片</li>
 *   <li>买家端：商品列表查询、商品详情查询</li>
 *   <li>商品详情页的库存信息通过 Feign 调用 {@code inventory-service} 获取</li>
 * </ul>
 *
 * <p>DDD 架构：
 * <ul>
 *   <li>{@code application} —— 应用层：Controller、DTO、ApplicationService</li>
 *   <li>{@code domain} —— 领域层：Product 聚合根、领域服务、Repository 接口</li>
 *   <li>{@code infrastructure} —— 基础设施层：Repository 实现、Feign Client 实现</li>
 *   <li>{@code interfaces} —— 接口层：Feign Client 接口定义</li>
 * </ul>
 *
 * @author monkey
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "org.monkey")
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
