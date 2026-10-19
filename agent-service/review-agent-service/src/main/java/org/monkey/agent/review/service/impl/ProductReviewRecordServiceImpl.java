package org.monkey.agent.review.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.mapper.ProductReviewRecordMapper;
import org.monkey.agent.review.model.ProductReviewRecord;
import org.monkey.agent.review.service.ProductReviewRecordService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProductReviewRecordServiceImpl extends ServiceImpl<ProductReviewRecordMapper, ProductReviewRecord> implements ProductReviewRecordService {
}
