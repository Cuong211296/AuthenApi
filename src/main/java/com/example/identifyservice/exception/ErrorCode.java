package com.example.identifyservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999,"Uncategorizied exception", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_EXISTED(1001,"User Existed", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003,"Username must be at least 3 characters", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(1004,"Password has been at least 8 characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005,"User not Existed", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006,"Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007,"You do not have permission", HttpStatus.FORBIDDEN),
    DOB_INVALID(1008,"Your age must be at least {min}", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_EXIST(1009,"Permission not exist", HttpStatus.NOT_FOUND),
    INVALID_KEY(1010, "Uncategorized error", HttpStatus.BAD_REQUEST);


    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
