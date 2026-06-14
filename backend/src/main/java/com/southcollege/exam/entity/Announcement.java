package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告实体
 * <p>表示系统中的公告通知，支持按类型和优先级分类。
 * 公告由管理员或教师发布，可指定发布时间和状态，通过 MyBatis-Plus 逻辑删除实现软删除。</p>
 *
 * @see com.southcollege.exam.service.AnnouncementService
 */
@Data
@TableName("announcements")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "公告标题不能为空")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private String type;

    private String priority;

    private String status;

    private Long publisherId;

    @TableField(exist = false)
    private String publisherName;

    private LocalDateTime publishedAt;

    @TableLogic
    private Integer deleted = 0;
}