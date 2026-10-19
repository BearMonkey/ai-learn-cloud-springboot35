package org.monkey.mcp.server.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.order.api.dto.OrderDTO;
import org.monkey.order.api.enums.OrderStatus;
import org.monkey.order.api.feign.OrderFeignClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTools {

    private final OrderFeignClient orderFeignClient;

    /**
     * 查询订单详情
     */
    @Tool(name = "query_order", description = "查询订单详情——传入订单ID，返回订单号、金额、状态、收货地址")
    public String queryOrder(
            ToolContext toolContext,
            @ToolParam(description = "订单ID（数据库主键，纯数字，不是 ORD 开头的订单号）") Long orderId) {

        Optional<McpSyncServerExchange> mcpExchange = McpToolUtils.getMcpExchange(toolContext);
        McpSyncServerExchange exchange = mcpExchange.get();
        log.info("exchange: {}", exchange);
        McpSchema.ClientCapabilities clientCapabilities = exchange.getClientCapabilities();
        log.info("ClientCapabilities: {}", clientCapabilities);
        Long userId = UserContextHolder.getUserId();
        log.info("AI 查询订单 userId={}, orderId={}", userId, orderId);
        Result<OrderDTO> orderRet = orderFeignClient.getOrderById(orderId, userId);
        // Feign 返回 null 说明订单不存在
        if (Objects.isNull(orderRet) || Objects.isNull(orderRet.getData())) {
            return "未找到订单，ID=" + orderId;
        }
        OrderDTO order = orderRet.getData();
        OrderStatus orderStatus = null;
        try {
            orderStatus = OrderStatus.fromValue(order.getStatus());
        } catch (Exception e) {
            return e.getMessage();
        }
        // 拼成 LLM 易读的自然语言文本返回
        return String.format("订单号：%s，金额：%s 元，状态：%s，收货地址：%s",
                order.getOrderNo(),
                order.getTotalAmount(),
                orderStatus,
                order.getAddress());
    }

    @Tool(name = "cancel_order", description = "根据传入订单ID取消订单——传入订单ID，返回取消结果：成功/失败")
    public String cancelOrder(@ToolParam(description = "订单ID（数据库主键，纯数字，不是 ORD 开头的订单号）") Long orderId) {
        Long userId = UserContextHolder.getUserId();
        log.info("AI 取消订单 userId={}, orderId={}", userId, orderId);
        Result<Boolean> cancelResult = orderFeignClient.cancelOrder(orderId);
        if (Objects.isNull(cancelResult) || Objects.isNull(cancelResult.getData())) {
            return "取消结果未知，查询重试，orderId=" + orderId;
        }
        Boolean result = cancelResult.getData();
        if (result) {
            return "取消成功";
        }
        return "取消失败";
    }
}
