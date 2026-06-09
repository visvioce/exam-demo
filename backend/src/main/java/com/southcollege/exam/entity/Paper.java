package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷实体
 * <p>
 * 表示一份试卷，仅存储题目ID列表，用于选题工具。
 * 分值信息不在试卷中，而是在考试创建时配置。
 * 删除的题目通过懒清理策略在加载时自动从 questionIds 中移除。
 * </p>
 *
 * @see com.southcollege.exam.service.PaperService
 */
@Data
@TableName(value = "papers", autoResultMap = true)
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷名称 */
    @NotBlank(message = "试卷名称不能为空")
    private String name;

    /** 试卷描述/说明 */
    private String description;

    /** 创建该试卷的教师ID {@link User#id} */
    private Long teacherId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // ========== JSON 字段 ==========

    /**
     * 试卷题目ID列表（JSON序列化存储）
     * 对应数据库 question_ids 列，格式：[1, 2, 3, ...]
     */
    @TableField(value = "question_ids", typeHandler = JacksonTypeHandler.class)
    private List<Long> questionIds;
}