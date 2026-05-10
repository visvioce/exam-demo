package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.LoginResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.UserResponse;
import com.southcollege.exam.entity.User;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.UserMapper;
import com.southcollege.exam.enums.UserStatusEnum;
import com.southcollege.exam.entity.ExamSession;
import com.southcollege.exam.utils.JwtUtil;
import com.southcollege.exam.utils.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务
 * 管理用户的注册登录、信息管理、分页查询和删除校验
 */
@Slf4j
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    private final JwtUtil jwtUtil;
    private final ExamSessionService examSessionService;

    public UserService(JwtUtil jwtUtil, @Lazy ExamSessionService examSessionService) {
        this.jwtUtil = jwtUtil;
        this.examSessionService = examSessionService;
    }

    /**
     * 根据用户名查询用户
     */
    public User getByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }

    /**
     * 批量获取用户的显示名称映射
     * <p>
     * 优先使用昵称，其次使用用户名
     *
     * @param userIds 用户ID列表
     * @return 用户ID -> 显示名称的映射
     */
    public Map<Long, String> getDisplayNameMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return listByIds(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        this::getDisplayName,
                        (a, _b) -> a
                ));
    }

    /**
     * 获取用户的显示名称：昵称优先，否则使用用户名
     */
    public String getDisplayName(User user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername() == null ? "" : user.getUsername();
    }

    /**
     * 用户登录：验证用户名密码、检查状态、生成JWT令牌
     *
     * @param username 用户名
     * @param password 密码（明文）
     * @return 登录响应，包含令牌和用户信息
     */
    public LoginResponse login(String username, String password) {
        User user = getByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        if (user.getStatus() != UserStatusEnum.ACTIVE) {
            throw new BusinessException("用户已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        log.info("用户登录成功: username={}, userId={}", username, user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(convertToResponse(user));
        return response;
    }

    /**
     * 用户注册：校验用户名唯一性、加密密码后保存
     *
     * @param username 用户名
     * @param password 密码（明文）
     * @param nickname 昵称
     * @param role     角色
     * @return 注册成功的用户信息
     */
    @Transactional
    public UserResponse register(String username, String password, String nickname, String role) {
        if (getByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encrypt(password));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(UserStatusEnum.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());

        save(user);
        log.info("用户注册成功: username={}, userId={}", username, user.getId());
        return convertToResponse(user);
    }

    /**
     * 获取当前登录用户信息
     */
    public UserResponse getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return convertToResponse(user);
    }

    /**
     * 修改当前用户密码：先验证原密码，再更新
     *
     * @param userId      用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        user.setPassword(PasswordUtil.encrypt(newPassword));
        updateById(user);
        log.info("用户修改密码成功: userId={}", userId);
    }

    /**
     * 更新用户个人资料（昵称和头像）
     *
     * @param userId   用户ID
     * @param nickname 新昵称
     * @param avatar   新头像
     * @return 更新后的用户信息
     */
    @Transactional
    public UserResponse updateProfile(Long userId, String nickname, String avatar) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setNickname(nickname);
        user.setAvatar(avatar);
        updateById(user);
        return convertToResponse(user);
    }

    /**
     * 创建用户（管理员功能）：校验用户名、加密密码、设置默认状态
     *
     * @param user 用户信息
     * @return 是否成功
     */
    @Transactional
    public boolean createUser(User user) {
        if (getByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        
        user.setPassword(PasswordUtil.encrypt(user.getPassword()));
        
        if (user.getStatus() == null) {
            user.setStatus(UserStatusEnum.ACTIVE);
        }
        
        user.setCreatedAt(LocalDateTime.now());
        
        return save(user);
    }
    
    /**
     * 更新用户（管理员功能）：保留原密码，不允许通过此接口修改密码
     *
     * @param user 用户信息
     * @return 是否成功
     */
    @Transactional
    public boolean updateUser(User user) {
        User existingUser = getById(user.getId());
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        user.setPassword(existingUser.getPassword());
        
        return updateById(user);
    }

    /**
     * 将 User 实体转换为 UserResponse 响应对象
     */
    public UserResponse convertToResponse(User user) {
        if (user == null) {
            return null;
        }
        UserResponse response = new UserResponse();
        BeanUtils.copyProperties(user, response);
        return response;
    }

    /**
     * 批量将 User 实体列表转换为 UserResponse 响应列表
     */
    public List<UserResponse> convertToResponses(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 将用户分页结果转换为响应分页结果
     */
    public PageResult<UserResponse> convertToPageResult(PageResult<User> pageResult) {
        if (pageResult == null) {
            return PageResult.empty(1, 10);
        }

        PageResult<UserResponse> response = new PageResult<>();
        response.setRecords(convertToResponses(pageResult.getRecords()));
        response.setTotal(pageResult.getTotal());
        response.setSize(pageResult.getSize());
        response.setCurrent(pageResult.getCurrent());
        response.setPages(pageResult.getPages());
        return response;
    }

    /**
     * 分页查询用户，支持关键词搜索和角色筛选
     *
     * @param pageRequest 分页参数
     * @param keyword     搜索关键词（匹配用户名或昵称）
     * @param role        角色筛选
     * @return 分页的用户响应结果
     */
    public PageResult<UserResponse> page(PageRequest pageRequest, String keyword, String role) {
        Page<User> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(User::getUsername, keyword)
                   .or()
                   .like(User::getNickname, keyword);
        }

        if (StringUtils.isNotBlank(role)) {
            wrapper.eq(User::getRole, role);
        }

        if (StringUtils.isNotBlank(pageRequest.getOrderBy())) {
            if (pageRequest.getAsc()) {
                wrapper.orderByAsc(User::getId);
            } else {
                wrapper.orderByDesc(User::getId);
            }
        } else {
            wrapper.orderByDesc(User::getId);
        }

        Page<User> result = page(page, wrapper);
        return convertToPageResult(PageResult.from(result));
    }

    /**
     * 检查用户是否可删除：存在进行中的考试时不允许删除
     */
    public void checkCanDelete(Long userId) {
        List<ExamSession> activeSessions = examSessionService.lambdaQuery()
                .eq(ExamSession::getStudentId, userId)
                .eq(ExamSession::getStatus, "IN_PROGRESS")
                .list();
        if (!activeSessions.isEmpty()) {
            throw new BusinessException("该用户有进行中的考试，无法删除");
        }
    }

    /**
     * 批量检查用户是否可删除，返回有进行中考试的用户ID集合
     *
     * @param userIds 待删除的用户ID列表
     * @return 有进行中考试的用户ID集合
     */
    public Set<Long> checkCanDeleteBatch(List<Long> userIds) {
        List<ExamSession> activeSessions = examSessionService.lambdaQuery()
                .in(ExamSession::getStudentId, userIds)
                .eq(ExamSession::getStatus, "IN_PROGRESS")
                .list();
        return activeSessions.stream()
                .map(ExamSession::getStudentId)
                .collect(Collectors.toSet());
    }
}