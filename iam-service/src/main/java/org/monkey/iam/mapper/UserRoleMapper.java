package org.monkey.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.iam.entity.UserRoleEntity;

/**
 * 用户角色 Mapper
 *
 * @author monkey
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
}
