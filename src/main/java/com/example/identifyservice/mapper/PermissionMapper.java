package com.example.identifyservice.mapper;

import com.example.identifyservice.dto.request.PermissionRequest;
import com.example.identifyservice.dto.request.UserCreationRequest;
import com.example.identifyservice.dto.request.UserUpdateRequest;
import com.example.identifyservice.dto.response.PermissionResponse;
import com.example.identifyservice.dto.response.UserResponse;
import com.example.identifyservice.entity.Permission;
import com.example.identifyservice.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);
}
