package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

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