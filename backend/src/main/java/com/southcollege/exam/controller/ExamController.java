package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.ExamCreateRequest;
import com.southcollege.exam.dto.request.ExamUpdateRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.ExamResponse;
import com.southcollege.exam.dto.response.ExamSessionResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.QuestionForExamResponse;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.ExamSession;
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
    public Result<PageResult<ExamResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "课程ID") @RequestParam(required = false) Long courseId,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "考试状态") @RequestParam(required = false) String status,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.convertToPageResult(
                examService.page(pageRequest, courseId, teacherId, status, userId, userRole)));
    }

    @GetMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "获取当前用户的考试列表")
    public Result<List<ExamResponse>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.convertToResponses(examService.getByTeacherId(userId)));
    }

    @Operation(summary = "获取考试回顾题目", description = "获取考试结束后用于回顾的题目及正确答案")
    @GetMapping("/{id}/review")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<List<Exam.ExamQuestion>> getReviewQuestions(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.getReviewQuestions(id, userId, userRole));
    }

    @Operation(summary = "获取考试详情", description = "根据ID获取考试详细信息")
    @GetMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<ExamResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.convertToResponse(
                examService.getByIdWithPermission(id, userId, userRole)));
    }

    @Operation(summary = "创建考试", description = "创建一个新的考试")
    @PostMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> save(@Valid @RequestBody ExamCreateRequest examRequest, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.createExam(examRequest, teacherId));
    }

    @Operation(summary = "发布考试", description = "将考试发布给学生参与")
    @PostMapping("/{id}/publish")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Void> publish(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        examService.publishExam(id, userId, userRole);
        return Result.success();
    }

    @PostMapping("/{id}/end")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    @Operation(summary = "提前结束考试", description = "将进行中的考试提前结束")
    public Result<Void> end(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        examService.endExam(id, userId, userRole);
        return Result.success();
    }

    @Operation(summary = "更新考试", description = "修改指定的考试信息")
    @PutMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody ExamUpdateRequest examRequest, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.updateExam(id, examRequest, userId, userRole));
    }

    @Operation(summary = "删除考试", description = "删除指定的考试")
    @DeleteMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        examService.checkOwnership(id, userId, userRole);
        examService.checkCanDelete(id);
        examService.deleteExam(id);
        return Result.success(true);
    }

    @Operation(summary = "获取已发布考试", description = "学生获取所有已发布且可参加的考试列表")
    @GetMapping("/published")
    @RequireRole(RoleEnum.STUDENT)
    public Result<PageResult<ExamResponse>> getPublishedExams(
            @Valid PageRequest pageRequest,
            HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.convertToPageResult(
                examService.getPublishedExams(pageRequest, studentId)));
    }

    @Operation(summary = "获取我的考试", description = "学生获取自己参加过的考试列表")
    @GetMapping("/my")
    @RequireRole(RoleEnum.STUDENT)
    public Result<PageResult<ExamResponse>> getMyExams(
            @Valid PageRequest pageRequest,
            HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.convertToPageResult(
                examService.getMyExams(pageRequest, studentId)));
    }

    @Operation(summary = "获取考试题目", description = "学生获取考试的题目列表（不含正确答案）")
    @GetMapping("/{id}/questions")
    @RequireRole(RoleEnum.STUDENT)
    public Result<List<QuestionForExamResponse>> getExamQuestions(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.getExamQuestions(id, studentId));
    }

    @Operation(summary = "开始考试", description = "学生开始答题，创建考试会话")
    @PostMapping("/{id}/start")
    @RequireRole(RoleEnum.STUDENT)
    public Result<ExamSessionResponse> startExam(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examService.convertSessionToResponse(examService.startExam(id, studentId)));
    }

    @Operation(summary = "提交答卷", description = "学生提交考试答案")
    @PostMapping("/{id}/submit")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> submitExam(
            @PathVariable Long id,
            @Valid @RequestBody List<ExamSession.Answer> answers,
            HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        examService.submitExam(id, studentId, answers);
        return Result.success();
    }

    @Operation(summary = "自动保存答案", description = "学生考试过程中自动保存答案")
    @PostMapping("/{id}/auto-save")
    @RequireRole(RoleEnum.STUDENT)
    public Result<Void> autoSaveExam(
            @PathVariable Long id,
            @Valid @RequestBody List<ExamSession.Answer> answers,
            HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        examService.autoSaveExam(id, studentId, answers);
        return Result.success();
    }
}