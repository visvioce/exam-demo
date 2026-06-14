package com.southcollege.exam.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.dto.response.StatsResponse;
import com.southcollege.exam.service.CourseService;
import com.southcollege.exam.service.ExamService;
import com.southcollege.exam.service.QuestionService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计查询控制器
 * 提供仪表盘统计数据查询功能
 */
@Tag(name = "统计查询", description = "仪表盘统计数据")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final CourseService courseService;
    private final QuestionService questionService;
    private final ExamService examService;

    public StatsController(CourseService courseService,
                           QuestionService questionService, ExamService examService) {
        this.courseService = courseService;
        this.questionService = questionService;
        this.examService = examService;
    }

    @Operation(summary = "获取统计数据", description = "根据用户角色返回仪表盘统计数字")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<StatsResponse> getStats(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);

        StatsResponse stats = new StatsResponse();
        stats.setUserCount(0L);
        stats.setCourseCount(courseService.countByTeacherId(userId));
        stats.setQuestionCount(questionService.countByTeacherId(userId));
        stats.setExamCount(examService.countByTeacherId(userId));

        return Result.success(stats);
    }
}