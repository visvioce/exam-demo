package com.southcollege.exam.controller;

import com.southcollege.exam.annotation.RequireRole;
import com.southcollege.exam.dto.request.CarouselSaveRequest;
import com.southcollege.exam.dto.response.CarouselResponse;
import com.southcollege.exam.dto.response.Result;
import com.southcollege.exam.entity.Carousel;
import com.southcollege.exam.enums.RoleEnum;
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
    @RequireRole({RoleEnum.ADMIN})
    public Result<List<CarouselResponse>> list() {
        return Result.success(carouselService.convertToResponses(carouselService.list()));
    }

    @Operation(summary = "获取轮播图详情", description = "根据ID获取轮播图详细信息")
    @GetMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN})
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
    @RequireRole({RoleEnum.ADMIN})
    public Result<Boolean> save(@Valid @RequestBody CarouselSaveRequest carouselSaveRequest) {
        Carousel carousel = new Carousel();
        carousel.setTitle(carouselSaveRequest.getTitle());
        carousel.setImageUrl(carouselSaveRequest.getImageUrl());
        carousel.setLinkUrl(carouselSaveRequest.getLinkUrl());
        carousel.setSortOrder(carouselSaveRequest.getSortOrder());
        if (carouselSaveRequest.getIsActive() != null) {
            carousel.setStatus(carouselSaveRequest.getIsActive() ? "ACTIVE" : "INACTIVE");
        }
        return Result.success(carouselService.save(carousel));
    }

    @Operation(summary = "更新轮播图", description = "修改指定的轮播图信息")
    @PutMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN})
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody CarouselSaveRequest carouselSaveRequest) {
        Carousel existing = carouselService.getById(id);
        if (existing == null) {
            return Result.error("轮播图不存在");
        }
        existing.setTitle(carouselSaveRequest.getTitle());
        existing.setImageUrl(carouselSaveRequest.getImageUrl());
        existing.setLinkUrl(carouselSaveRequest.getLinkUrl());
        existing.setSortOrder(carouselSaveRequest.getSortOrder());
        if (carouselSaveRequest.getIsActive() != null) {
            existing.setStatus(carouselSaveRequest.getIsActive() ? "ACTIVE" : "INACTIVE");
        }
        return Result.success(carouselService.updateById(existing));
    }

    @Operation(summary = "删除轮播图", description = "删除指定的轮播图")
    @DeleteMapping("/{id}")
    @RequireRole({RoleEnum.ADMIN})
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(carouselService.removeById(id));
    }
}
