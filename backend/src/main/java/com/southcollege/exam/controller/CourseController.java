package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.CourseSaveRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.CourseResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.dto.response.UserResponse;
import com.southcollege.exam.entity.Course;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.CourseService;
import com.southcollege.exam.service.UserService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程管理控制器
 * 提供课程的增删改查、加入/退出课程和成员管理功能，
 * 支持管理员/教师/学生的多角色权限控制
 */
@Tag(name = "课程管理", description = "课程增删改查、加入/退出课程")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final UserService userService;

    public CourseController(CourseService courseService, UserService userService) {
        this.courseService = courseService;
        this.userService = userService;
    }

    @GetMapping("/page")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "分页查询课程", description = "管理员和教师可访问，教师只能查看自己创建的课程")
    public Result<PageResult<CourseResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "课程状态") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(courseService.convertToPageResult(
                courseService.page(pageRequest, teacherId, status, keyword, userId, userRole)));
    }

    @GetMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "获取全部课程", description = "不建议使用，数据量大时会有性能问题")
    public Result<List<CourseResponse>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        return Result.success(courseService.convertToResponses(courseService.getByTeacherId(userId)));
    }

    @Operation(summary = "获取活跃课程", description = "获取所有活跃状态的课程列表（公开接口）")
    @GetMapping("/active")
    public Result<List<CourseResponse>> getActiveCourses() {
        return Result.success(courseService.convertToResponses(
                courseService.getActiveCourses()));
    }

    @Operation(summary = "获取我的课程", description = "学生获取自己已加入的课程列表")
    @GetMapping("/my")
    @RequireRole(RoleEnum.STUDENT)
    public Result<List<CourseResponse>> getMyCourses(HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(courseService.convertToResponses(
                courseService.getMyCourses(studentId)));
    }

    @Operation(summary = "获取课程详情", description = "根据ID获取课程详细信息")
    @GetMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<CourseResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(courseService.convertToResponse(
                courseService.getByIdWithPermission(id, userId, userRole)));
    }

    @Operation(summary = "创建课程", description = "新增一个课程")
    @PostMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> save(@Valid @RequestBody CourseSaveRequest courseSaveRequest, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        Course course = new Course();
        course.setName(courseSaveRequest.getName());
        course.setCode(courseSaveRequest.getCode());
        course.setDescription(courseSaveRequest.getDescription());
        course.setCoverUrl(courseSaveRequest.getCoverUrl());
        course.setTeacherId(teacherId);
        course.setCredits(courseSaveRequest.getCredits() != null ? courseSaveRequest.getCredits() : new java.math.BigDecimal("1.0"));
        course.setStatus("ACTIVE");
        return Result.success(courseService.save(course));
    }

    @Operation(summary = "更新课程", description = "修改指定的课程信息")
    @PutMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody CourseSaveRequest courseSaveRequest, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        Course course = new Course();
        course.setName(courseSaveRequest.getName());
        course.setCode(courseSaveRequest.getCode());
        course.setDescription(courseSaveRequest.getDescription());
        course.setCoverUrl(courseSaveRequest.getCoverUrl());
        return Result.success(courseService.updateCourse(id, course, userId, userRole));
    }

    @Operation(summary = "删除课程", description = "删除指定的课程")
    @DeleteMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        courseService.checkOwnership(id, userId, userRole);
        courseService.deleteCourse(id);
        return Result.success(true);
    }

    @Operation(summary = "加入课程", description = "学生加入指定课程")
    @PostMapping("/{id}/join")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> joinCourse(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        courseService.joinCourse(id, studentId);
        return Result.success();
    }

    @Operation(summary = "退出课程", description = "学生退出指定课程")
    @PostMapping("/{id}/leave")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> leaveCourse(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        courseService.leaveCourse(id, studentId);
        return Result.success();
    }

    @Operation(summary = "检查是否已加入", description = "检查当前学生是否已加入指定课程")
    @GetMapping("/{id}/joined")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Boolean> checkJoined(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        boolean joined = courseService.isCourseMember(id, studentId);
        return Result.success(joined);
    }

    @Operation(summary = "获取课程成员", description = "获取指定课程的成员列表")
    @GetMapping("/{id}/members")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<List<UserResponse>> getCourseMembers(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(userService.convertToResponses(
                courseService.getCourseMembersWithPermission(id, userId, userRole)));
    }
}