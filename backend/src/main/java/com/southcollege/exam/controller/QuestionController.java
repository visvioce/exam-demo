package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
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
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题库管理控制器
 * 提供题目的增删改查功能，支持题型筛选、关键字搜索，
 * 管理员可管理全部题库，教师只能管理自己的题目
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
    public Result<List<Question>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(questionService.list());
        }
        return Result.success(questionService.getByTeacherId(userId));
    }

    @Operation(summary = "分页查询题目", description = "支持题型、关键字筛选")
    @GetMapping("/page")
    public Result<PageResult<Question>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "题目类型") @RequestParam(required = false) String type,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(questionService.page(pageRequest, teacherId, type, keyword, userId, userRole));
    }

    @GetMapping("/{id}")
    public Result<Question> getById(@PathVariable Long id, HttpServletRequest request) {
        Question question = questionService.getById(id);
        if (question == null) {
            return Result.error("题目不存在");
        }

        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (!RoleEnum.ADMIN.getCode().equals(userRole)
                && (question.getTeacherId() == null || !question.getTeacherId().equals(userId))) {
            return Result.error("无权查看该题目");
        }

        return Result.success(question);
    }

    @GetMapping("/teacher/{teacherId}")
    public Result<List<Question>> getByTeacherId(@PathVariable Long teacherId, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (!RoleEnum.ADMIN.getCode().equals(userRole) && !teacherId.equals(userId)) {
            return Result.error("无权查看该教师的题目");
        }
        return Result.success(questionService.getByTeacherId(teacherId));
    }

    @GetMapping("/type/{type}")
    public Result<List<Question>> getByType(
            @PathVariable String type,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);

        if (teacherId != null) {
            return Result.success(questionService.getByType(type, teacherId));
        }

        List<Question> questions = questionService.getByType(type);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(questions);
        }
        return Result.success(questions.stream()
                .filter(q -> q.getTeacherId() != null && q.getTeacherId().equals(userId))
                .toList());
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody Question question, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        question.setTeacherId(teacherId);
        return Result.success(questionService.save(question));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody Question question, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        questionService.checkOwnership(id, userId, userRole);

        Question original = questionService.getById(id);
        if (original == null) {
            return Result.error("题目不存在");
        }

        BeanUtils.copyProperties(question, original, "id", "teacherId", "createdAt");
        original.setId(id);
        return Result.success(questionService.updateById(original));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        questionService.checkOwnership(id, userId, userRole);
        return Result.success(questionService.removeById(id));
    }
}