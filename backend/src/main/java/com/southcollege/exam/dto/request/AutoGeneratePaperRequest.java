package com.southcollege.exam.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AutoGeneratePaperRequest {

    @NotBlank(message = "试卷名称不能为空")
    @Size(max = 200, message = "试卷名称长度不能超过200个字符")
    private String name;

    @Size(max = 200, message = "试卷描述长度不能超过200个字符")
    private String description;

    private String subject;

    private String difficulty;

    @Valid
    private TypeConfig singleChoice;

    @Valid
    private TypeConfig multipleChoice;

    @Valid
    private TypeConfig trueFalse;

    @Valid
    private TypeConfig fillBlank;

    @Valid
    private TypeConfig essay;

    @Positive(message = "单选题数量必须大于0")
    private Integer singleChoiceCount;

    @Positive(message = "多选题数量必须大于0")
    private Integer multipleChoiceCount;

    @Positive(message = "判断题数量必须大于0")
    private Integer trueFalseCount;

    @Positive(message = "填空题数量必须大于0")
    private Integer fillBlankCount;

    @Positive(message = "简答题数量必须大于0")
    private Integer essayCount;
}