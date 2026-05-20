package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.AnnouncementSaveRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.AnnouncementResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Announcement;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.AnnouncementService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公告管理控制器
 * 提供系统公告的增删改查功能，支持教师创建公告和管理员全局管理
 */
@Tag(name = "公告管理", description = "系统公告增删改查")
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<List<AnnouncementResponse>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        List<Announcement> announcements = announcementService.listVisibleAnnouncements(userId, userRole);
        announcementService.fillPublisherNames(announcements);
        return Result.success(announcementService.convertToResponses(announcements));
    }

    @Operation(summary = "分页查询公告", description = "支持关键字搜索和状态筛选")
    @GetMapping("/page")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<PageResult<AnnouncementResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "类型筛选") @RequestParam(required = false) String type,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(announcementService.convertToPageResult(announcementService.page(pageRequest, keyword, status, type, userId, userRole)));
    }

    @GetMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<AnnouncementResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        Announcement announcement = announcementService.getVisibleAnnouncementById(id, userId, userRole);
        if (announcement != null) {
            announcementService.fillPublisherNames(List.of(announcement));
        }
        return Result.success(announcementService.convertToResponse(announcement));
    }

    @PostMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> save(@Valid @RequestBody AnnouncementSaveRequest announcementSaveRequest, HttpServletRequest request) {
        Long publisherId = SecurityUtil.getCurrentUserId(request);
        Announcement announcement = new Announcement();
        announcement.setTitle(announcementSaveRequest.getTitle());
        announcement.setContent(announcementSaveRequest.getContent());
        if (announcementSaveRequest.getIsPinned() != null && announcementSaveRequest.getIsPinned()) {
            announcement.setPriority("HIGH");
        }
        if (announcementSaveRequest.getStatus() != null) {
            announcement.setStatus(announcementSaveRequest.getStatus());
        }
        announcementService.prepareForCreate(announcement, publisherId);
        return Result.success(announcementService.save(announcement));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody AnnouncementSaveRequest announcementSaveRequest, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        Announcement announcement = new Announcement();
        announcement.setTitle(announcementSaveRequest.getTitle());
        announcement.setContent(announcementSaveRequest.getContent());
        if (announcementSaveRequest.getIsPinned() != null && announcementSaveRequest.getIsPinned()) {
            announcement.setPriority("HIGH");
        }
        if (announcementSaveRequest.getStatus() != null) {
            announcement.setStatus(announcementSaveRequest.getStatus());
        }
        announcementService.prepareForUpdate(id, announcement, userId, userRole);
        return Result.success(announcementService.updateById(announcement));
    }

    @DeleteMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        announcementService.checkOwnership(id, userId, userRole);
        return Result.success(announcementService.removeById(id));
    }
}