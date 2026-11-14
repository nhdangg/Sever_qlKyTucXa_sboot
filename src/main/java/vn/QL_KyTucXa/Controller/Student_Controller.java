package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.StudentSelfUpdate_Request;
import vn.QL_KyTucXa.DTO.Request.Student_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.StudentBatch_Response;
import vn.QL_KyTucXa.DTO.Response.Student_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.Student_Service;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Student_Controller {

    Student_Service studentService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_MANAGE')")
    public Api_Response<Student_Response> createStudent(@RequestBody @Valid Student_Request request) {
        return Api_Response.<Student_Response>builder()
                .result(studentService.create(request))
                .build();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('STUDENT_MANAGE') or hasRole('ADMIN') or hasRole('MANAGER')")
    public Api_Response<StudentBatch_Response> createStudentBatch(@RequestBody List<Student_Request> requests) {
        return Api_Response.<StudentBatch_Response>builder()
                .code(100)
                .message("Xử lý thêm hàng loạt sinh viên hoàn tất")
                .result(studentService.createBatch(requests))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Student_Response> getMyProfile() {
        return Api_Response.<Student_Response>builder()
                .result(studentService.getByUserId(currentUserService.userId()))
                .build();
    }

    @PutMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Student_Response> updateMyProfile(@RequestBody @Valid StudentSelfUpdate_Request request) {
        return Api_Response.<Student_Response>builder()
                .result(studentService.updateMyProfile(request))
                .build();
    }

    @PostMapping(value = "/upload-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = studentService.saveAvatarFile(file);
        return Api_Response.<Map<String, String>>builder()
                .code(100)
                .message("Lưu ảnh đại diện thành công")
                .result(Map.of("url", url))
                .build();
    }

    @GetMapping("/{studentId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW') or isAuthenticated()")
    public Api_Response<Student_Response> getStudentById(@PathVariable String studentId) {
        return Api_Response.<Student_Response>builder()
                .result(studentService.getById(studentId))
                .build();
    }

    @GetMapping("/code/{studentCode}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW') or isAuthenticated()")
    public Api_Response<Student_Response> getStudentByCode(@PathVariable String studentCode) {
        return Api_Response.<Student_Response>builder()
                .result(studentService.getByStudentCode(studentCode))
                .build();
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW') or isAuthenticated()")
    public Api_Response<Student_Response> getStudentByUserId(@PathVariable String userId) {
        return Api_Response.<Student_Response>builder()
                .result(studentService.getByUserId(userId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public Api_Response<Page_Response<Student_Response>> getAllStudents(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "faculty", required = false) String faculty,
            @RequestParam(value = "className", required = false) String className,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "isResident", required = false) Boolean isResident,
            @RequestParam(value = "sortBy", defaultValue = "studentCode") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        return Api_Response.<Page_Response<Student_Response>>builder()
                .result(studentService.getAll(page, size, keyword, faculty, className, gender, status, isResident, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public Api_Response<List<Student_Response>> getAllStudentsWithoutPaging() {
        return Api_Response.<List<Student_Response>>builder()
                .result(studentService.getAll())
                .build();
    }

    @PutMapping("/{studentId}")
    @PreAuthorize("hasAuthority('STUDENT_MANAGE') or isAuthenticated()")
    public Api_Response<Student_Response> updateStudent(
            @PathVariable String studentId,
            @RequestBody @Valid Student_Request request
    ) {
        return Api_Response.<Student_Response>builder()
                .result(studentService.update(studentId, request))
                .build();
    }

    @DeleteMapping("/{studentId}")
    @PreAuthorize("hasAuthority('STUDENT_MANAGE')")
    public Api_Response<String> deleteStudent(@PathVariable String studentId) {
        studentService.delete(studentId);
        return Api_Response.<String>builder()
                .result(studentId)
                .build();
    }
}
