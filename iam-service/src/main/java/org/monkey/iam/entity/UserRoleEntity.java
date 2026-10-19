package org.monkey.iam.entity;

import com.baomidou.mybatisplus.annotation.*;

/**
 * 用户角色实体 —— 映射 t_user_role 表
 *
 * @author monkey
 */
@TableName("t_user_role")
public class UserRoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String role;

    @TableField("create_by")
    private Long createBy;

    public UserRoleEntity() {}
    public UserRoleEntity(Long userId, String role) { this.userId = userId; this.role = role; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
}
