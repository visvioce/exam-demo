package com.southcollege.exam.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.response.CarouselResponse;
import com.southcollege.exam.entity.Carousel;
import com.southcollege.exam.mapper.CarouselMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarouselService extends ServiceImpl<CarouselMapper, Carousel> {

    public List<Carousel> getActive() {
        return baseMapper.selectActive();
    }

    public CarouselResponse convertToResponse(Carousel entity) {
        if (entity == null) return null;
        CarouselResponse response = new CarouselResponse();
        BeanUtils.copyProperties(entity, response);
        return response;
    }

    public List<CarouselResponse> convertToResponses(List<Carousel> entities) {
        if (entities == null || entities.isEmpty()) return List.of();
        return entities.stream().map(this::convertToResponse).toList();
    }
}
