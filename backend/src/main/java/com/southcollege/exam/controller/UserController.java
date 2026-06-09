package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.request.UserSaveRequest;
import com.southcollege.exam.dto.request.UserUpdateRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.dto.response.UserResponse;
import com.southcollege.exam.entity.User;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 用户管理控制器
 * 提供系统用户的增删改查功能，仅限管理员访问
 */
@Tag(name = "用户管理", description = "用户增删改查（仅管理员）")
@RestController
@RequestMapping("/api/users")
@RequireRole(RoleEnum.ADMIN)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "获取所有用户", description = "获取系统中所有用户列表")
    @GetMapping
    public Result<List<UserResponse>> list() {
        return Result.success(userService.convertToResponses(userService.list()));
    }

    @Operation(summary = "分页查询用户", description = "支持关键字搜索和角色筛选")
    @GetMapping("/page")
    public Result<PageResult<UserResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色筛选") @RequestParam(required = false) String role) {
        return Result.success(userService.page(pageRequest, keyword, role));
    }

    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @GetMapping("/{id}")
    public Result<UserResponse> getById(@Parameter(description = "用户ID") @PathVariable Long id) {
        return Result.success(userService.convertToResponse(userService.getById(id)));
    }

    @Operation(summary = "创建用户", description = "新增系统用户，密码将自动加密存储")
    @PostMapping
    public Result<Boolean> save(@Valid @RequestBody UserSaveRequest userSaveRequest) {
        User user = new User();
        user.setUsername(userSaveRequest.getUsername());
        user.setPassword(userSaveRequest.getPassword());
        user.setNickname(userSaveRequest.getNickname());
        user.setRole(userSaveRequest.getRole());
        return Result.success(userService.createUser(user));
    }

    @Operation(summary = "更新用户", description = "更新用户昵称、角色、状态，不允许修改密码和用户名")
    @PutMapping("/{id}")
    public Result<Boolean> update(@Parameter(description = "用户ID") @PathVariable Long id,
                                  @Valid @RequestBody UserUpdateRequest request) {
        User user = new User();
        user.setId(id);
        user.setNickname(request.getNickname());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus() != null ?
                com.southcollege.exam.enums.UserStatusEnum.valueOf(request.getStatus()) : null);
        return Result.success(userService.updateUser(user));
    }

    @Operation(summary = "删除用户", description = "根据ID删除用户")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@Parameter(description = "用户ID") @PathVariable Long id) {
        userService.checkCanDelete(id);
        userService.deleteUser(id);
        return Result.success(true);
    }

    @Operation(summary = "批量删除用户", description = "根据ID列表批量删除用户")
    @DeleteMapping("/batch")
    public Result<Boolean> deleteBatch(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success(true);
        }
        Set<Long> blockedUserIds = userService.checkCanDeleteBatch(ids);
        if (!blockedUserIds.isEmpty()) {
            throw new com.southcollege.exam.exception.BusinessException(
                    "以下用户有进行中的考试，无法删除：" + blockedUserIds);
        }
        userService.deleteUsers(ids);
        return Result.success(true);
    }
}