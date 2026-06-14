package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.ExamResponse;
import com.southcollege.exam.entity.Exam;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Exam → ExamResponse 映射器
 * <p>统计字段（participantCount/submittedCount/pendingGradingCount）由 Service 注入</p>
 */
@Mapper(componentModel = "spring")
public interface ExamDtoMapper {

    @Mapping(target = "participantCount", ignore = true)
    @Mapping(target = "submittedCount", ignore = true)
    @Mapping(target = "pendingGradingCount", ignore = true)
    ExamResponse toResponse(Exam exam);

    List<ExamResponse> toResponseList(List<Exam> exams);
}