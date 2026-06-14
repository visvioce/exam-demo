package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程实体
 * <p>表示教师创建的一门课程，包含课程基本信息（名称、编号、描述、封面）、学分、状态和截止日期。
 * 课程由教师管理，学生通过 {@link CourseMember} 关联加入课程并参加其中的考试。</p>
 *
 * @see com.southcollege.exam.service.CourseService
 * @see CourseMember
 */
@Data
@TableName("courses")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "课程名称不能为空")
    private String name;

    private String code;

    private String description;

    private String coverUrl;

    private Long teacherId;

    @TableField(exist = false)
    private String teacherName;

    private BigDecimal credits;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime deadline;

    @TableLogic
    private Integer deleted = 0;
}