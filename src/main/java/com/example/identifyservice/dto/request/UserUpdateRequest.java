package com.example.identifyservice.dto.request;

import com.example.identifyservice.validator.DobConstraint;
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
     String password;
     String firstname;
     String lastname;

     @DobConstraint(min = 10, message = "DOB_INVALID")
     LocalDate dob;
     List<String> roles;
}
