package com.southcollege.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.dto.response.StatsResponse;
import com.southcollege.exam.entity.Course;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
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
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<StatsResponse> getStats(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);

        StatsResponse stats = new StatsResponse();

        stats.setUserCount(0L);
        stats.setCourseCount(courseService.count(new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId)));
        stats.setQuestionCount(questionService.count(new LambdaQueryWrapper<Question>()
                .eq(Question::getTeacherId, userId)));
        stats.setExamCount(examService.count(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getTeacherId, userId)));

        return Result.success(stats);
    }
}