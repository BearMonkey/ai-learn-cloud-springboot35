package org.monkey.agent.review.service;

import org.monkey.common.mq.dto.ProductEventMessage;

public interface ReviewService {

    /**
     * 商品自动审核
     * @param msg ProductEventMessage
     */
    void handleProductAudit(ProductEventMessage msg);
}
