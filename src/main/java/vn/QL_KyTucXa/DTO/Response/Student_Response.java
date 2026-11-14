package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Student_Response {
    String studentId;
    String userId;
    String username;
    String studentCode;
    String fullName;
    LocalDate dob;
    String gender;
    String nationalId;
    String faculty;
    String className;
    String hometown;
    String permanentAddress;
    String phone;
    String email;
    String avatarUrl;
    String emergencyContactName;
    String emergencyContactPhone;
    String emergencyContactRelation;
    String status;
    Boolean isResident;
    String roomNumber;
    String bedNumber;
    String contractCode;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
