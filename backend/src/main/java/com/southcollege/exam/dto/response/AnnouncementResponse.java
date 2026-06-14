package com.southcollege.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告响应 DTO
 */
@Data
public class AnnouncementResponse {

    private Long id;

    private String title;

    private String content;

    private String type;

    private String priority;

    private String status;

    private Long publisherId;

    private String publisherName;

    private LocalDateTime publishedAt;
}