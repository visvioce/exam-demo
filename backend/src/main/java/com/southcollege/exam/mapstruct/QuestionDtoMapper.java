package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.QuestionResponse;
import com.southcollege.exam.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Question → QuestionResponse 映射器
 * <p>嵌套的 Option/ScoringCriterion 通过 MapStruct 自动映射到 OptionResponse/ScoringCriterionResponse。
 * correctAnswer/explanation 的隐藏由 Service 层根据角色控制。</p>
 */
@Mapper(componentModel = "spring")
public interface QuestionDtoMapper {

    @Mapping(target = "options", source = "options")
    @Mapping(target = "scoringCriteria", source = "scoringCriteria")
    QuestionResponse toResponse(Question question);

    List<QuestionResponse> toResponseList(List<Question> questions);

    QuestionResponse.OptionResponse toOptionResponse(Question.Option option);

    QuestionResponse.ScoringCriterionResponse toScoringCriterionResponse(Question.ScoringCriterion criterion);
}