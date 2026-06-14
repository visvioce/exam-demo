package com.southcollege.exam.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.southcollege.exam.dto.request.CarouselSaveRequest;
import com.southcollege.exam.dto.response.CarouselResponse;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.service.CarouselService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 轮播图管理控制器
 * 提供首页轮播图的增删改查功能，
 * 管理员可管理全部，普通用户查看公开的轮播图
 */
@Tag(name = "轮播图管理", description = "首页轮播图增删改查")
@RestController
@RequestMapping("/api/carousels")
public class CarouselController {

    private final CarouselService carouselService;

    public CarouselController(CarouselService carouselService) {
        this.carouselService = carouselService;
    }

    @Operation(summary = "获取全部轮播图", description = "获取所有轮播图列表（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<CarouselResponse>> list() {
        return Result.success(carouselService.convertToResponses(carouselService.list()));
    }

    @Operation(summary = "获取轮播图详情", description = "根据ID获取轮播图详细信息")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CarouselResponse> getById(@PathVariable Long id) {
        return Result.success(carouselService.convertToResponse(carouselService.getById(id)));
    }

    @Operation(summary = "获取活跃轮播图", description = "获取所有激活状态的轮播图（公开接口）")
    @GetMapping("/active")
    public Result<List<CarouselResponse>> getActive() {
        return Result.success(carouselService.convertToResponses(carouselService.getActive()));
    }

    @Operation(summary = "创建轮播图", description = "新增一个轮播图")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> save(@Valid @RequestBody CarouselSaveRequest carouselSaveRequest) {
        return Result.success(carouselService.createCarousel(carouselSaveRequest));
    }

    @Operation(summary = "更新轮播图", description = "修改指定的轮播图信息")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody CarouselSaveRequest carouselSaveRequest) {
        return Result.success(carouselService.updateCarousel(id, carouselSaveRequest));
    }

    @Operation(summary = "删除轮播图", description = "删除指定的轮播图")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(carouselService.removeById(id));
    }
}
