package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.ExamCreateRequest;
import com.southcollege.exam.dto.request.ExamUpdateRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.ExamResultResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.QuestionForExamResponse;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.ExamSession;
import com.southcollege.exam.entity.Paper;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.CourseService;
import com.southcollege.exam.service.ExamService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考试管理控制器
 * 提供考试的全生命周期管理：创建、更新、发布、取消、删除，
 * 以及学生端功能：查看可用考试、开始考试、自动保存、提交答卷和查看结果
 */
@Tag(name = "考试管理", description = "考试增删改查、发布取消")
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;
    private final CourseService courseService;

    public ExamController(ExamService examService, CourseService courseService) {
        this.examService = examService;
        this.courseService = courseService;
    }

    @GetMapping("/page")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "分页查询考试", description = "管理员和教师可访问，教师只能查看自己创建的考试")
    public Result<PageResult<Exam>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "课程ID") @RequestParam(required = false) Long courseId,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "考试状态") @RequestParam(required = false) String status,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.page(pageRequest, courseId, teacherId, status, userId, userRole));
    }

    @GetMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "获取全部考试", description = "不建议使用，数据量大时会有性能问题")
    public Result<List<Exam>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(examService.listWithDisplayFields());
        }
        return Result.success(examService.getByTeacherId(userId));
    }

    @GetMapping("/{id}")
    public Result<Exam> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.getByIdWithPermission(id, userId, userRole));
    }

    @PostMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> save(@Valid @RequestBody ExamCreateRequest examRequest, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.createExam(examRequest, teacherId));
    }

    @PostMapping("/{id}/publish")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Void> publish(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        examService.checkOwnership(id, userId, userRole);
        examService.publishExam(id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        examService.checkOwnership(id, userId, userRole);
        examService.cancelExam(id);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody ExamUpdateRequest examRequest, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.updateExam(id, examRequest, userId, userRole));
    }

    @DeleteMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        examService.checkOwnership(id, userId, userRole);
        examService.checkCanDelete(id);
        return Result.success(examService.removeById(id));
    }

    @GetMapping("/published")
    public Result<List<Exam>> getPublishedExams(HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.getPublishedExams(studentId));
    }

    @GetMapping("/my")
    @RequireRole(RoleEnum.STUDENT)
    public Result<List<Exam>> getMyExams(HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.getMyExams(studentId));
    }

    @GetMapping("/{id}/questions")
    @RequireRole(RoleEnum.STUDENT)
    public Result<List<QuestionForExamResponse>> getExamQuestions(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.getExamQuestions(id, studentId));
    }

    @PostMapping("/{id}/start")
    @RequireRole(RoleEnum.STUDENT)
    public Result<ExamSession> startExam(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.startExam(id, studentId));
    }

    @PostMapping("/{id}/submit")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> submitExam(
            @PathVariable Long id,
            @RequestBody List<ExamSession.Answer> answers,
            HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        examService.submitExam(id, studentId, answers);
        return Result.success();
    }

    @PostMapping("/{id}/auto-save")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> autoSaveExam(
            @PathVariable Long id,
            @RequestBody List<ExamSession.Answer> answers,
            HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        examService.autoSaveExam(id, studentId, answers);
        return Result.success();
    }

    @GetMapping("/{id}/paper")
    public Result<Paper> getExamPaper(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.getExamPaper(id, userId, userRole));
    }

    @GetMapping("/{id}/review-questions")
    public Result<List<Question>> getReviewQuestions(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.getReviewQuestions(id, userId, userRole));
    }
}