package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.PaperResponse;
import com.southcollege.exam.entity.Paper;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Paper → PaperResponse 映射器
 */
@Mapper(componentModel = "spring")
public interface PaperDtoMapper {

    PaperResponse toResponse(Paper paper);

    List<PaperResponse> toResponseList(List<Paper> papers);
}