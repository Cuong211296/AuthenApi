package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.type.descriptor.jdbc.TimestampWithTimeZoneJdbcType;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
     String id;
     String username;
     String firstname;
     String lastname;
     LocalDate dob;
     Set<RoleResponse> roles;

}
