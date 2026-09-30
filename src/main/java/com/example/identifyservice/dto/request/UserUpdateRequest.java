package com.example.identifyservice.dto.request;

import com.example.identifyservice.validator.DobConstraint;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
     String id;
     @Size(min = 8, message = "PASSWORD_INVALID")
     String password;
     String firstname;
     String lastname;

     @DobConstraint(min = 10, message = "DOB_INVALID")
     LocalDate dob;
     List<String> roles;
}
