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
    INVALID_KEY(1010, "Uncategorized error", HttpStatus.BAD_REQUEST),
    INVALID_INPUT(1011, "Invalid input", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(2001, "Product not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(2002, "Category not found", HttpStatus.NOT_FOUND),
    VARIANT_NOT_FOUND(2003, "Product variant not found or unavailable", HttpStatus.NOT_FOUND),
    SLUG_EXISTED(2004, "Slug already exists", HttpStatus.BAD_REQUEST),
    SKU_EXISTED(2005, "SKU already exists", HttpStatus.BAD_REQUEST),
    OUT_OF_STOCK(2006, "Not enough stock", HttpStatus.CONFLICT),
    INVALID_QUANTITY(2007, "Invalid quantity", HttpStatus.BAD_REQUEST),
    CART_EMPTY(2008, "Cart is empty", HttpStatus.BAD_REQUEST),
    INVALID_PROVINCE(2009, "Province is not supported", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(2010, "Order not found", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS(2011, "Invalid order status change", HttpStatus.BAD_REQUEST),
    ORDER_NOT_PAYABLE(2012, "Order cannot be paid", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_SIGNATURE(2013, "Invalid payment signature", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH(2014, "Payment amount mismatch", HttpStatus.BAD_REQUEST),
    PAYMENT_GATEWAY_ERROR(2015, "Payment gateway error", HttpStatus.BAD_GATEWAY),
    PROVINCE_EXISTED(2016, "Province already exists", HttpStatus.BAD_REQUEST),
    VARIANT_EXISTED(2017, "Variant with this size and colour already exists", HttpStatus.BAD_REQUEST);


    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
