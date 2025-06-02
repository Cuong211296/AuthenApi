package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.PermissionRequest;
import com.example.identifyservice.dto.response.PermissionResponse;
import com.example.identifyservice.entity.Permission;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.mapper.PermissionMapper;
import com.example.identifyservice.repository.PermissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionService {
    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;

    public PermissionResponse create(PermissionRequest request) {

        Permission permissions = permissionMapper.toPermission(request);
        permissions = permissionRepository.save(permissions);
        return permissionMapper.toPermissionResponse(permissions);
    }

    public List<PermissionResponse> getAll(){
        var permissions = permissionRepository.findAll();
        return permissions.stream().map(permissionMapper::toPermissionResponse).toList();
    }

    public void delete(String permission){
        permissionRepository.findById(permission).orElseThrow(()-> new AppException(ErrorCode.PERMISSION_NOT_EXIST));
        permissionRepository.deleteById(permission);
    }


}
