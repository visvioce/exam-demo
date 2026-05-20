package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.request.QuestionSaveRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.QuestionResponse;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.QuestionService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题库管理控制器
 */
@Tag(name = "题库管理", description = "题目增删改查")
@RestController
@RequestMapping("/api/questions")
@RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public Result<List<QuestionResponse>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        return Result.success(questionService.convertToResponses(
                questionService.getByTeacherId(userId)));
    }

    @Operation(summary = "分页查询题目", description = "支持题型、难度、关键字筛选")
    @GetMapping("/page")
    public Result<PageResult<QuestionResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "题目类型") @RequestParam(required = false) String type,
            @Parameter(description = "题目难度") @RequestParam(required = false) String difficulty,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(questionService.convertToPageResult(
                questionService.page(pageRequest, teacherId, type, keyword, difficulty, userId, userRole)));
    }

    @GetMapping("/{id}")
    public Result<QuestionResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        Question question = questionService.getById(id);
        if (question == null) {
            return Result.error("题目不存在");
        }

        Long userId = SecurityUtil.getCurrentUserId(request);
        if (question.getTeacherId() == null || !question.getTeacherId().equals(userId)) {
            return Result.error("无权查看该题目");
        }

        return Result.success(questionService.convertToResponse(question));
    }

    @PostMapping
    public Result<Boolean> save(@Valid @RequestBody QuestionSaveRequest questionSaveRequest, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        Question question = new Question();
        question.setContent(questionSaveRequest.getContent());
        question.setType(questionSaveRequest.getType());
        question.setDifficulty(questionSaveRequest.getDifficulty());
        question.setSubject(questionSaveRequest.getSubject());
        question.setExplanation(questionSaveRequest.getExplanation());
        question.setOptions(questionSaveRequest.getOptions());
        question.setCorrectAnswer(questionSaveRequest.getCorrectAnswer());
        question.setScoringCriteria(questionSaveRequest.getScoringCriteria());
        question.setTeacherId(teacherId);
        return Result.success(questionService.save(question));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody QuestionSaveRequest questionSaveRequest, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        questionService.checkOwnership(id, userId, userRole);

        Question original = questionService.getById(id);
        if (original == null) {
            return Result.error("题目不存在");
        }

        if (questionSaveRequest.getContent() != null) {
            original.setContent(questionSaveRequest.getContent());
        }
        if (questionSaveRequest.getType() != null) {
            original.setType(questionSaveRequest.getType());
        }
        if (questionSaveRequest.getDifficulty() != null) {
            original.setDifficulty(questionSaveRequest.getDifficulty());
        }
        if (questionSaveRequest.getSubject() != null) {
            original.setSubject(questionSaveRequest.getSubject());
        }
        if (questionSaveRequest.getExplanation() != null) {
            original.setExplanation(questionSaveRequest.getExplanation());
        }
        if (questionSaveRequest.getOptions() != null) {
            original.setOptions(questionSaveRequest.getOptions());
        }
        if (questionSaveRequest.getCorrectAnswer() != null) {
            original.setCorrectAnswer(questionSaveRequest.getCorrectAnswer());
        }
        if (questionSaveRequest.getScoringCriteria() != null) {
            original.setScoringCriteria(questionSaveRequest.getScoringCriteria());
        }
        original.setId(id);
        return Result.success(questionService.updateById(original));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        questionService.checkOwnership(id, userId, userRole);
        questionService.removeById(id);
        return Result.success(true);
    }

    @GetMapping("/subjects")
    public Result<List<String>> getSubjects() {
        return Result.success(questionService.getDistinctSubjects());
    }

    @Operation(summary = "批量获取题目", description = "根据ID列表批量获取题目")
    @PostMapping("/batch")
    public Result<List<QuestionResponse>> getBatch(@RequestBody java.util.Map<String, java.util.List<Long>> body) {
        java.util.List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.success(java.util.List.of());
        }
        return Result.success(questionService.convertToResponses(questionService.listByIds(ids)));
    }

    @GetMapping("/available-count")
    public Result<java.util.Map<String, Object>> getAvailableCount(
            @RequestParam String type,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String difficulty,
            HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        long count = questionService.countQuestions(type, subject, difficulty, teacherId);
        return Result.success(java.util.Map.of("count", count));
    }
}