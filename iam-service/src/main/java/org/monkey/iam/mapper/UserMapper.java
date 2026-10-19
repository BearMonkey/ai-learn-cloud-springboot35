package org.monkey.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.iam.entity.UserEntity;

/**
 * 用户 Mapper
 *
 * @author monkey
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
