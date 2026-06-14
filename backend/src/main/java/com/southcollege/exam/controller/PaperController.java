package com.southcollege.exam.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.request.PaperSaveRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.PaperResponse;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Paper;
import com.southcollege.exam.service.PaperService;
import com.southcollege.exam.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试卷管理控制器
 * 提供试卷的增删改查和自动组卷功能
 */
@Tag(name = "试卷管理", description = "试卷增删改查")
@RestController
@RequestMapping("/api/papers")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @Operation(summary = "获取试卷列表", description = "获取当前教师的所有试卷")
    @GetMapping
    public Result<List<PaperResponse>> list(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        return Result.success(paperService.convertToResponses(paperService.getByTeacherId(userId)));
    }

    @Operation(summary = "分页查询试卷", description = "支持关键字搜索和教师筛选")
    @GetMapping("/page")
    public Result<PageResult<PaperResponse>> page(
            @Valid PageRequest pageRequest,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        return Result.success(paperService.convertToPageResult(paperService.pageWithFilters(pageRequest, keyword, teacherId, userId)));
    }

    @Operation(summary = "获取试卷详情", description = "根据ID获取试卷详细信息")
    @GetMapping("/{id}")
    public Result<PaperResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        paperService.checkOwnership(id, userId);
        Paper paper = paperService.getById(id);
        return Result.success(paperService.convertToResponse(paper));
    }


    @Operation(summary = "创建试卷", description = "新增一份试卷")
    @PostMapping
    public Result<Boolean> save(@Valid @RequestBody PaperSaveRequest paperSaveRequest, HttpServletRequest request) {
        Long teacherId = SecurityUtil.getCurrentUserId(request);
        Paper paper = new Paper();
        paper.setName(paperSaveRequest.getName());
        paper.setDescription(paperSaveRequest.getDescription());
        paper.setQuestionIds(paperSaveRequest.getQuestionIds());
        paper.setTeacherId(teacherId);
        return Result.success(paperService.save(paper));
    }

    @Operation(summary = "自动组卷", description = "根据配置自动随机抽取题目生成试卷")
    @PostMapping("/auto-generate")
    public Result<PaperResponse> autoGenerate(@Valid @RequestBody com.southcollege.exam.dto.request.AutoGeneratePaperRequest request, HttpServletRequest httpRequest) {
        Long teacherId = SecurityUtil.getCurrentUserId(httpRequest);
        return Result.success(paperService.convertToResponse(paperService.autoGenerate(request, teacherId)));
    }

    @Operation(summary = "更新试卷", description = "修改指定的试卷信息")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody PaperSaveRequest paperSaveRequest, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);

        paperService.checkOwnership(id, userId);

        Paper originalPaper = paperService.getById(id);
        if (originalPaper == null) {
            throw new com.southcollege.exam.exception.BusinessException("试卷不存在");
        }

        Paper paper = new Paper();
        paper.setName(paperSaveRequest.getName());
        paper.setDescription(paperSaveRequest.getDescription());
        paper.setQuestionIds(paperSaveRequest.getQuestionIds());
        paper.setId(id);
        paper.setTeacherId(originalPaper.getTeacherId());

        return Result.success(paperService.updateById(paper));
    }

    @Operation(summary = "删除试卷", description = "删除指定的试卷")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId(request);
        paperService.checkOwnership(id, userId);

        return Result.success(paperService.removeById(id));
    }
}