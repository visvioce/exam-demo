package com.southcollege.exam.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.CarouselSaveRequest;
import com.southcollege.exam.dto.response.CarouselResponse;
import com.southcollege.exam.entity.Carousel;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.CarouselMapper;
import com.southcollege.exam.mapstruct.CarouselDtoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarouselService extends ServiceImpl<CarouselMapper, Carousel> {

    private final CarouselDtoMapper carouselDtoMapper;

    public CarouselService(CarouselDtoMapper carouselDtoMapper) {
        this.carouselDtoMapper = carouselDtoMapper;
    }

    public List<Carousel> getActive() {
        return baseMapper.selectActive();
    }

    /**
     * 创建轮播图
     * @param request 创建请求
     * @return 是否成功
     */
    public boolean createCarousel(CarouselSaveRequest request) {
        Carousel carousel = new Carousel();
        carousel.setTitle(request.getTitle());
        carousel.setImageUrl(request.getImageUrl());
        carousel.setLinkUrl(request.getLinkUrl());
        carousel.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) {
            carousel.setStatus(request.getIsActive() ? "ACTIVE" : "INACTIVE");
        }
        return save(carousel);
    }

    /**
     * 更新轮播图
     * @param id 轮播图ID
     * @param request 更新请求
     * @return 是否成功
     */
    public boolean updateCarousel(Long id, CarouselSaveRequest request) {
        Carousel existing = getById(id);
        if (existing == null) {
            throw new BusinessException("轮播图不存在");
        }
        existing.setTitle(request.getTitle());
        existing.setImageUrl(request.getImageUrl());
        existing.setLinkUrl(request.getLinkUrl());
        existing.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) {
            existing.setStatus(request.getIsActive() ? "ACTIVE" : "INACTIVE");
        }
        return updateById(existing);
    }

    public CarouselResponse convertToResponse(Carousel entity) {
        if (entity == null) return null;
        return carouselDtoMapper.toResponse(entity);
    }

    public List<CarouselResponse> convertToResponses(List<Carousel> entities) {
        if (entities == null || entities.isEmpty()) return List.of();
        return carouselDtoMapper.toResponseList(entities);
    }
}
