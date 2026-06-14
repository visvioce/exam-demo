package com.southcollege.exam.mapstruct;

import com.southcollege.exam.dto.response.UserResponse;
import com.southcollege.exam.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * User → UserResponse 映射器
 * <p>status 字段从 UserStatusEnum 转换为 String 代码</p>
 */
@Mapper(componentModel = "spring")
public interface UserDtoMapper {

    @Mapping(target = "status", expression = "java(user.getStatus() != null ? user.getStatus().getCode() : null)")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}