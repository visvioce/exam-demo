package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.AnnouncementResponse;
import com.southcollege.exam.entity.Announcement;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Announcement → AnnouncementResponse 映射器
 */
@Mapper(componentModel = "spring")
public interface AnnouncementDtoMapper {

    AnnouncementResponse toResponse(Announcement entity);

    List<AnnouncementResponse> toResponseList(List<Announcement> entities);
}