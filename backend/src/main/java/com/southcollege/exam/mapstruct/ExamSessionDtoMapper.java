package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.ExamSessionResponse;
import com.southcollege.exam.entity.ExamSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * ExamSession → ExamSessionResponse 映射器
 * <p>嵌套的 Answer 需要显式映射为 AnswerResponse</p>
 */
@Mapper(componentModel = "spring")
public interface ExamSessionDtoMapper {

    @Mapping(target = "answers", source = "answers")
    ExamSessionResponse toResponse(ExamSession session);

    List<ExamSessionResponse> toResponseList(List<ExamSession> sessions);

    ExamSessionResponse.AnswerResponse toAnswerResponse(ExamSession.Answer answer);
}