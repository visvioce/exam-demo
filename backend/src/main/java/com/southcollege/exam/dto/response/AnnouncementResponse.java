package com.southcollege.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

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