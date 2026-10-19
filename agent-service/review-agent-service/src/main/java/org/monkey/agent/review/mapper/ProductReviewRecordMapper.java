package org.monkey.agent.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.agent.review.model.ProductReviewRecord;

@Mapper
public interface ProductReviewRecordMapper extends BaseMapper<ProductReviewRecord> {
}
