package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.CourseResponse;
import com.southcollege.exam.entity.Course;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Course → CourseResponse 映射器
 */
@Mapper(componentModel = "spring")
public interface CourseDtoMapper {

    CourseResponse toResponse(Course course);

    List<CourseResponse> toResponseList(List<Course> courses);
}