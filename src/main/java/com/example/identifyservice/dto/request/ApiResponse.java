package com.example.identifyservice.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse <T> {
     @Builder.Default
     private int code=1000;
     private String message;
     private T result;

     public static <T> ApiResponse<T> ok(T result) {
          return ApiResponse.<T>builder().result(result).build();
     }

}
