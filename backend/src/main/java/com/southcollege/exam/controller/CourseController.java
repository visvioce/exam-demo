package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.PageRequest;
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
    public Result<PageResult<Course>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "课程状态") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(courseService.page(pageRequest, teacherId, status, keyword, userId, userRole));
    }

    @GetMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "获取全部课程", description = "不建议使用，数据量大时会有性能问题")
    public Result<List<Course>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(courseService.listWithTeacherNames());
        }
        return Result.success(courseService.getByTeacherId(userId));
    }

    @GetMapping("/active")
    public Result<List<Course>> getActiveCourses() {
        return Result.success(courseService.getActiveCourses());
    }

    @GetMapping("/my")
    public Result<List<Course>> getMyCourses(HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(courseService.getMyCourses(studentId));
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(courseService.getByIdWithPermission(id, userId, userRole));
    }

    @PostMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> save(@RequestBody Course course, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        course.setTeacherId(teacherId);
        return Result.success(courseService.save(course));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> update(@PathVariable Long id, @RequestBody Course course, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(courseService.updateCourse(id, course, userId, userRole));
    }

    @DeleteMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        courseService.checkOwnership(id, userId, userRole);
        courseService.checkCanDelete(id);
        return Result.success(courseService.removeById(id));
    }

    @PostMapping("/{id}/join")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> joinCourse(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        courseService.joinCourse(id, studentId);
        return Result.success();
    }

    @PostMapping("/{id}/leave")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> leaveCourse(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        courseService.leaveCourse(id, studentId);
        return Result.success();
    }

    @GetMapping("/{id}/joined")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Boolean> checkJoined(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        boolean joined = courseService.isCourseMember(id, studentId);
        return Result.success(joined);
    }

    @GetMapping("/{id}/members")
    public Result<List<UserResponse>> getCourseMembers(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(userService.convertToResponses(
                courseService.getCourseMembersWithPermission(id, userId, userRole)));
    }
}