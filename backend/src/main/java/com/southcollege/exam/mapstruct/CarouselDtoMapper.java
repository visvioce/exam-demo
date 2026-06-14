package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.CarouselResponse;
import com.southcollege.exam.entity.Carousel;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Carousel → CarouselResponse 映射器
 */
@Mapper(componentModel = "spring")
public interface CarouselDtoMapper {

    CarouselResponse toResponse(Carousel entity);

    List<CarouselResponse> toResponseList(List<Carousel> entities);
}