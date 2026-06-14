package com.southcollege.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图响应 DTO
 */
@Data
public class CarouselResponse {

    private Long id;

    private String title;

    private String imageUrl;

    private String linkUrl;

    private String description;

    private Integer sortOrder;

    private String status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}