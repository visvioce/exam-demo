package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.GradeSubjectiveRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.ExamResultResponse;
import com.southcollege.exam.dto.response.ExamSessionResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.ExamSession;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.ExamService;
import com.southcollege.exam.service.ExamSessionService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考试记录管理控制器
 * 提供考试记录的查询、分页和主观题评分功能，
 * 支持管理员/教师/学生的多角色数据隔离
 */
@Tag(name = "考试记录管理", description = "考试记录查询、主观题评分")
@RestController
@RequestMapping("/api/exam-sessions")
public class ExamSessionController {

    private final ExamSessionService examSessionService;
    private final ExamService examService;

    public ExamSessionController(ExamSessionService examSessionService, ExamService examService) {
        this.examSessionService = examSessionService;
        this.examService = examService;
    }

    @Operation(summary = "获取考试记录列表", description = "获取当前教师的所有考试记录")
    @GetMapping
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<List<ExamSessionResponse>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        List<ExamSession> sessions = examSessionService.getByTeacherId(userId);
        examSessionService.fillStudentNames(sessions);
        return Result.success(examSessionService.convertToResponses(sessions));
    }

    @Operation(summary = "分页查询考试记录", description = "支持考试ID、学生ID和状态筛选")
    @GetMapping("/page")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<PageResult<ExamSessionResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "考试ID") @RequestParam(required = false) Long examId,
            @Parameter(description = "学生ID") @RequestParam(required = false) Long studentId,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "评分状态") @RequestParam(required = false) String gradingStatus,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examSessionService.convertToPageResult(
                examSessionService.page(pageRequest, examId, studentId, status, gradingStatus, userId, userRole)));
    }

    @Operation(summary = "获取考试记录详情", description = "根据ID获取考试记录的详细信息")
    @GetMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<ExamSessionResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);

        ExamSession session = examSessionService.getById(id);
        if (session == null) {
            return Result.error("记录不存在");
        }

        Exam exam = examService.getById(session.getExamId());
        if (exam == null) {
            return Result.error("考试不存在");
        }

        boolean isTeacher = userId.equals(exam.getTeacherId());
        boolean isOwner = session.getStudentId().equals(userId);

        if (!isTeacher && !isOwner) {
            return Result.error("无权查看该考试记录");
        }

        return Result.success(examSessionService.convertToResponse(session));
    }

    @Operation(summary = "按考试ID获取记录", description = "获取指定考试的所有考生记录")
    @GetMapping("/exam/{examId}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<List<ExamSessionResponse>> getByExamId(@PathVariable Long examId, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);

        Exam exam = examService.getById(examId);
        if (exam == null) {
            return Result.error("考试不存在");
        }

        if (!userId.equals(exam.getTeacherId())) {
            return Result.error("无权查看该考试的记录");
        }

        List<ExamSession> sessions = examSessionService.getByExamId(examId);
        examSessionService.fillStudentNames(sessions);
        return Result.success(examSessionService.convertToResponses(sessions));
    }

    @Operation(summary = "获取我的考试记录", description = "学生获取自己所有的考试记录")
    @GetMapping("/my")
    @RequireRole(RoleEnum.STUDENT)
    public Result<List<ExamSessionResponse>> getMySessions(HttpServletRequest request) {
        Long studentId = SecurityUtil.getCurrentUserId(request);
        return Result.success(examSessionService.convertToResponses(
                examSessionService.getByStudentId(studentId)));
    }

    @Operation(summary = "获取待评分记录", description = "获取当前教师所有待评分的考试记录")
    @GetMapping("/pending-grading")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<List<ExamSessionResponse>> getPendingGradingSessions(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        List<ExamSession> sessions = examSessionService.getPendingGradingSessions(userId);

        examSessionService.fillStudentNames(sessions);
        return Result.success(examSessionService.convertToResponses(sessions));
    }

    @Operation(summary = "按考试ID获取待评分记录", description = "获取指定考试中待评分的考生记录")
    @GetMapping("/pending-grading/exam/{examId}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<List<ExamSessionResponse>> getPendingGradingByExamId(@PathVariable Long examId, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);

        Exam exam = examService.getById(examId);
        if (exam == null) {
            return Result.error("考试不存在");
        }

        if (!userId.equals(exam.getTeacherId())) {
            return Result.error("无权查看该考试的记录");
        }

        List<ExamSession> sessions = examSessionService.getPendingGradingByExamId(examId);
        examSessionService.fillStudentNames(sessions);
        return Result.success(examSessionService.convertToResponses(sessions));
    }

    @Operation(summary = "主观题评分", description = "对考生的主观题答案进行评分")
    @PostMapping("/grade")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Void> gradeSubjectiveAnswers(@Valid @RequestBody GradeSubjectiveRequest request, HttpServletRequest servletRequest) {
        Long userId = SecurityUtil.getCurrentUserId(servletRequest);
        String userRole = SecurityUtil.getCurrentUserRole(servletRequest);
        examService.gradeSubjectiveAnswers(userId, userRole, request);
        return Result.success();
    }

    @Operation(summary = "按考试自动评分", description = "对指定考试的所有客观题进行自动评分")
    @PostMapping("/exam/{examId}/auto-grade")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Integer> autoGradeByExam(@PathVariable Long examId, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        int processedCount = examService.autoGradeByExam(examId, userId, userRole);
        return Result.success(processedCount);
    }

    @Operation(summary = "获取考试结果", description = "获取指定考试记录的成绩详情")
    @GetMapping("/{id}/result")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER, RoleEnum.STUDENT})
    public Result<ExamResultResponse> getExamResult(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(examService.getExamResult(id, userId, userRole));
    }
}