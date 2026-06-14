package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图实体
 * <p>表示系统首页或课程页面的轮播展示图，包含图片链接、跳转链接、排序和展示时段。
 * 支持设置展示起止时间（{@code startAt}/{@code endAt}）和排序权重（{@code sortOrder}），通过 MyBatis-Plus 逻辑删除实现软删除。</p>
 *
 * @see com.southcollege.exam.service.CarouselService
 */
@Data
@TableName("carousels")
public class Carousel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "轮播图标题不能为空")
    private String title;

    @NotBlank(message = "轮播图链接不能为空")
    private String imageUrl;

    private String linkUrl;

    private String description;

    private Integer sortOrder;

    private String status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @TableLogic
    private Integer deleted = 0;
}