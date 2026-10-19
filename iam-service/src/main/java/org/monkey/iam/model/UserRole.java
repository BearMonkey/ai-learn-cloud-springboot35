package org.monkey.iam.model;

import java.util.Objects;

/**
 * 用户角色值对象
 *
 * @author monkey
 */
public class UserRole {

    private final Role role;

    public UserRole(Role role) {
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRole)) return false;
        UserRole userRole = (UserRole) o;
        return role == userRole.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(role);
    }
}
