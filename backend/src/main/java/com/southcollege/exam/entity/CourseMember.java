package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程-学生关联实体
 * <p>表示学生加入课程的成员关系，记录学生加入课程的时间。
 * 通过 {@code courseId} 关联 {@link Course}，通过 {@code studentId} 关联学生用户。</p>
 *
 * @see Course
 * @see com.southcollege.exam.service.CourseMemberService
 */
@Data
@TableName("course_members")
public class CourseMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long studentId;

    private LocalDateTime joinedAt;
}
