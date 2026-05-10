package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Paper;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.service.PaperService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试卷管理控制器
 * 提供试卷的增删改查和自动组卷功能，
 * 支持管理员/教师的数据隔离
 */
@Tag(name = "试卷管理", description = "试卷增删改查")
@RestController
@RequestMapping("/api/papers")
@RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @GetMapping
    public Result<List<Paper>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(paperService.listWithCourseNames());
        }
        return Result.success(paperService.getByTeacherId(userId));
    }

    @Operation(summary = "分页查询试卷", description = "支持关键字搜索和教师筛选")
    @GetMapping("/page")
    public Result<PageResult<Paper>> page(
            PageRequest pageRequest,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "课程ID") @RequestParam(required = false) Long courseId,
            @Parameter(description = "组卷方式") @RequestParam(required = false) String type,
            @Parameter(description = "试卷状态") @RequestParam(required = false) String status,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        return Result.success(paperService.pageWithFilters(pageRequest, keyword, teacherId, courseId, type, status, userId, userRole));
    }

    @GetMapping("/{id}")
    public Result<Paper> getById(@PathVariable Long id, HttpServletRequest request) {
        Paper paper = paperService.getByIdWithCourseName(id);
        if (paper == null) {
            throw new com.southcollege.exam.exception.BusinessException("试卷不存在");
        }

        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(paper);
        }
        if (paper.getTeacherId() == null || !paper.getTeacherId().equals(userId)) {
            throw new com.southcollege.exam.exception.BusinessException("无权查看该试卷");
        }

        return Result.success(paper);
    }

    @GetMapping("/course/{courseId}")
    public Result<List<Paper>> getByCourseId(@PathVariable Long courseId, HttpServletRequest request) {
        List<Paper> papers = paperService.getByCourseId(courseId);

        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(papers);
        }
        return Result.success(papers.stream()
                .filter(p -> p.getTeacherId() != null && p.getTeacherId().equals(userId))
                .toList());
    }

    @GetMapping("/exam/{paperId}")
    @RequireRole({RoleEnum.ADMIN, RoleEnum.TEACHER})
    public Result<Paper> getForExam(@PathVariable Long paperId, HttpServletRequest request) {
        Paper paper = paperService.getById(paperId);
        if (paper == null) {
            throw new com.southcollege.exam.exception.BusinessException("试卷不存在");
        }

        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return Result.success(paper);
        }
        if (!paper.getTeacherId().equals(userId)) {
            throw new com.southcollege.exam.exception.BusinessException("无权查看该试卷");
        }

        return Result.success(paper);
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody Paper paper, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        paper.setTeacherId(teacherId);
        if (paper.getStatus() == null) {
            paper.setStatus("DRAFT");
        }
        return Result.success(paperService.save(paper));
    }

    @PostMapping("/auto-generate")
    public Result<Paper> autoGenerate(@RequestBody com.southcollege.exam.dto.request.AutoGeneratePaperRequest request, HttpServletRequest httpRequest) {
        Long teacherId = SecurityUtil.getCurrentUserId(httpRequest);
        return Result.success(paperService.autoGenerate(request, teacherId));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody Paper paper, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);

        paperService.checkOwnership(id, userId, userRole);

        Paper originalPaper = paperService.getById(id);
        if (originalPaper == null) {
            throw new com.southcollege.exam.exception.BusinessException("试卷不存在");
        }

        paper.setId(id);
        paper.setTeacherId(originalPaper.getTeacherId());

        return Result.success(paperService.updateById(paper));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        String userRole = SecurityUtil.getCurrentUserRole(request);
        paperService.checkOwnership(id, userId, userRole);
        paperService.checkCanDelete(id);

        return Result.success(paperService.removeById(id));
    }
}