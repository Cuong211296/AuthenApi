package com.example.identifyservice.dto.request;

import com.example.identifyservice.validator.DobConstraint;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.type.descriptor.jdbc.TimestampWithTimeZoneJdbcType;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.time.LocalDate;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
//    private String id;
//    @Size(min = 3,message = "USERNAME_INVALID")
//    private String username;
//    @Size(min = 8,message = "PASSWORD_INVALID")
//    private String password;
//    private String firstname;
//    private String lastname;
//    private LocalDate dob;

    //Sau khi dung lombok
      String id;
    @Size(min = 3,message = "USERNAME_INVALID")
     String username;
    @Size(min = 8,message = "PASSWORD_INVALID")
     String password;
     String firstname;
     String lastname;

     @DobConstraint(min = 10, message = "DOB_INVALID")
     LocalDate dob;



}
