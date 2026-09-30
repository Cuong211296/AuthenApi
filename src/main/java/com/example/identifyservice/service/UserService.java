package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.UserCreationRequest;
import com.example.identifyservice.dto.request.UserUpdateRequest;
import com.example.identifyservice.dto.response.UserResponse;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.mapper.UserMapper;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
@Slf4j
public class UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    RoleRepository roleRepository;

    PasswordEncoder passwordEncoder;


    public UserResponse createUser(UserCreationRequest request){
        if (userRepository.existsByUsername(request.getUsername()))
            throw new AppException(ErrorCode.USER_EXISTED);

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setRoles(new HashSet<>(java.util.Set.of(userRole)));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    public UserResponse getMyInfo(){
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return userMapper.toUserResponse(user);
    }
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isSelf = authentication != null && user.getUsername().equals(authentication.getName());
        if (!isAdmin && !isSelf) throw new AppException(ErrorCode.UNAUTHORIZED);

        userMapper.updateUser(user, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (isAdmin && request.getRoles() != null) {
            user.setRoles(new HashSet<>(roleRepository.findAllById(request.getRoles())));
        }

        return userMapper.toUserResponse(userRepository.save(user));
    }
    //@PreAuthorize("hasAuthority('CREATE_POST')")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String userId) {
        userRepository.findById(userId).orElseThrow(() ->new AppException(ErrorCode.USER_NOT_EXISTED));
        userRepository.deleteById(userId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getUsers(){
        log.info("In method get Users");
        return userRepository.findAll().stream()
                .map(userMapper::toUserResponse)
                .toList();
    }
    @PostAuthorize("returnObject.username == authentication.name")
    public UserResponse getUser(String id) {
        return userMapper.toUserResponse(userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED)));
    }
}


