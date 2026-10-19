package org.monkey.iam.controller;

import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.iam.api.dto.UserDTO;
import org.monkey.iam.dto.UserInfoResponse;
import org.monkey.iam.service.UserService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 *
 * @author monkey
 */
@RestController
@RequestMapping("/iam/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser() {
        Long userId = UserContextHolder.getUserId();
        UserInfoResponse info = userService.getCurrentUser(userId);
        return Result.success(toApiDto(info));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getUserById(@PathVariable Long id) {
        UserInfoResponse info = userService.getUserById(id);
        return Result.success(toApiDto(info));
    }

    private UserDTO toApiDto(UserInfoResponse info) {
        return UserDTO.builder()
                .id(info.getId()).username(info.getUsername()).nickname(info.getNickname())
                .realName(info.getRealName()).email(info.getEmail()).phone(info.getPhone())
                .avatar(info.getAvatar()).status(info.getStatus()).roles(info.getRoles())
                .createTime(info.getCreateTime()).updateTime(info.getUpdateTime())
                .build();
    }
}
