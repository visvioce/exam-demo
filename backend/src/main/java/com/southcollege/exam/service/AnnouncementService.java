package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.entity.Announcement;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 公告服务
 * 管理系统公告的发布、查询和权限控制
 */
@Service
public class AnnouncementService extends ServiceImpl<AnnouncementMapper, Announcement> {

    private final UserService userService;

    public AnnouncementService(UserService userService) {
        this.userService = userService;
    }

    /**
     * 查询已发布的指定类型公告
     */
    public List<Announcement> getPublishedByType(String type) {
        return baseMapper.selectPublishedByType(type);
    }

    /**
     * 查询所有已发布公告
     */
    public List<Announcement> getPublishedAnnouncements() {
        return lambdaQuery()
                .eq(Announcement::getStatus, "PUBLISHED")
                .orderByDesc(Announcement::getPublishedAt)
                .list();
    }

    /**
     * 根据用户角色查询可见公告列表
     * <p>
     * 管理员：查看全部
     * 教师：查看已发布公告 + 自己创建的公告（含草稿）
     * 学生：仅查看已发布公告
     */
    public List<Announcement> listVisibleAnnouncements(Long userId, String userRole) {
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return lambdaQuery()
                    .orderByDesc(Announcement::getPublishedAt)
                    .orderByDesc(Announcement::getId)
                    .list();
        }

        if (RoleEnum.TEACHER.getCode().equals(userRole)) {
            return lambdaQuery()
                    .and(wrapper -> wrapper
                            .eq(Announcement::getStatus, "PUBLISHED")
                            .or()
                            .eq(Announcement::getPublisherId, userId))
                    .orderByDesc(Announcement::getPublishedAt)
                    .orderByDesc(Announcement::getId)
                    .list();
        }

        return getPublishedAnnouncements();
    }

    /**
     * 根据ID和用户权限查询公告详情
     * <p>
     * 学生只能查看已发布公告，教师还可查看自己创建的公告
     */
    public Announcement getVisibleAnnouncementById(Long id, Long userId, String userRole) {
        Announcement announcement = getById(id);
        if (announcement == null) {
            return null;
        }

        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return announcement;
        }

        boolean published = "PUBLISHED".equals(announcement.getStatus());
        boolean owner = announcement.getPublisherId() != null && announcement.getPublisherId().equals(userId);

        if (published || owner) {
            return announcement;
        }

        throw new BusinessException("无权查看该公告");
    }

    /**
     * 创建公告前的预处理：设置发布者、默认状态和发布时间
     */
    public Announcement prepareForCreate(Announcement announcement, Long publisherId) {
        announcement.setPublisherId(publisherId);
        if (announcement.getStatus() == null || announcement.getStatus().isBlank()) {
            announcement.setStatus("DRAFT");
        }
        announcement.setPublishedAt(resolvePublishedAt(announcement.getStatus(), null));
        return announcement;
    }

    /**
     * 更新公告前的预处理：权限校验、保留原创建者、处理发布时间
     */
    public Announcement prepareForUpdate(Long id, Announcement announcement, Long userId, String userRole) {
        Announcement existing = getById(id);
        if (existing == null) {
            throw new BusinessException("公告不存在");
        }

        checkOwnership(id, userId, userRole);

        announcement.setId(id);
        announcement.setPublisherId(existing.getPublisherId());
        announcement.setPublishedAt(resolvePublishedAt(announcement.getStatus(), existing.getPublishedAt()));
        return announcement;
    }

    /**
     * 根据状态决定发布时间：状态为"已发布"时返回发布时间，否则为null
     */
    private LocalDateTime resolvePublishedAt(String status, LocalDateTime existingPublishedAt) {
        if (!"PUBLISHED".equals(status)) {
            return null;
        }
        return existingPublishedAt != null ? existingPublishedAt : LocalDateTime.now();
    }

    /**
     * 校验公告操作权限，管理员可操作所有，普通用户只能操作自己的公告
     */
    public void checkOwnership(Long announcementId, Long userId, String userRole) {
        Announcement announcement = getById(announcementId);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        if (!RoleEnum.ADMIN.getCode().equals(userRole) && !announcement.getPublisherId().equals(userId)) {
            throw new BusinessException("无权操作该公告");
        }
    }

    /**
     * 分页查询公告，支持关键词搜索、状态和类型筛选
     * <p>
     * 根据角色自动进行数据隔离：
     * 管理员查看全部，教师可查看已发布+自己的，学生仅查看已发布
     *
     * @param pageRequest     分页参数
     * @param keyword         搜索关键词（匹配标题）
     * @param status          状态筛选
     * @param type            类型筛选
     * @param currentUserId   当前用户ID
     * @param currentUserRole 当前用户角色
     * @return 分页结果，包含发布者姓名
     */
    public PageResult<Announcement> page(PageRequest pageRequest, String keyword, String status, String type,
                                          Long currentUserId, String currentUserRole) {
        Page<Announcement> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();

        // 角色数据隔离
        if (RoleEnum.ADMIN.getCode().equals(currentUserRole)) {
            // 管理员无限制
        } else if (RoleEnum.TEACHER.getCode().equals(currentUserRole)) {
            wrapper.and(w -> w.eq(Announcement::getStatus, "PUBLISHED")
                    .or()
                    .eq(Announcement::getPublisherId, currentUserId));
        } else {
            wrapper.eq(Announcement::getStatus, "PUBLISHED");
        }

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(Announcement::getTitle, keyword);
        }

        // 学生不支持手动筛选状态（由数据隔离自动处理）
        if (StringUtils.isNotBlank(status) && !RoleEnum.STUDENT.getCode().equals(currentUserRole)) {
            wrapper.eq(Announcement::getStatus, status);
        }

        if (StringUtils.isNotBlank(type)) {
            wrapper.eq(Announcement::getType, type);
        }

        wrapper.orderByDesc(Announcement::getPublishedAt);
        Page<Announcement> result = page(page, wrapper);
        fillPublisherNames(result.getRecords());
        return PageResult.from(result);
    }

    /**
     * 批量填充公告列表中的发布者显示名称
     *
     * @param announcements 公告列表
     */
    public void fillPublisherNames(List<Announcement> announcements) {
        if (announcements == null || announcements.isEmpty()) {
            return;
        }
        List<Long> publisherIds = announcements.stream()
                .map(Announcement::getPublisherId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> nameMap = userService.getDisplayNameMap(publisherIds);
        for (Announcement announcement : announcements) {
            if (announcement.getPublisherId() == null) {
                continue;
            }
            String displayName = nameMap.get(announcement.getPublisherId());
            if (StringUtils.isNotBlank(displayName)) {
                announcement.setPublisherName(displayName);
            }
        }
    }
}