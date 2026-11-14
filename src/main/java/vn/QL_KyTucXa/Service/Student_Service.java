package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.StudentSelfUpdate_Request;
import vn.QL_KyTucXa.DTO.Request.Student_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.StudentBatch_Response;
import vn.QL_KyTucXa.DTO.Response.Student_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Student_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;
import vn.QL_KyTucXa.Config.WebMvcConfig;
import vn.QL_KyTucXa.Entity.Contract;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Student_Service {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "studentCode", "fullName", "dob", "gender", "nationalId",
            "faculty", "className", "hometown", "phone", "email",
            "status", "createdAt", "updatedAt");

    Student_Repository studentRepository;
    Student_Mapper studentMapper;
    User_Repository userRepository;
    Role_Repository roleRepository;
    PasswordEncoder passwordEncoder;
    WebMvcConfig webMvcConfig;
    CurrentUser_Service currentUserService;
    Contract_Repository contractRepository;
    RefreshTokenService refreshTokenService;

    @Transactional
    public Student_Response create(Student_Request request) {
        String studentCode = request.getStudentCode().trim();
        String nationalId = request.getNationalId() != null && !request.getNationalId().isBlank()
                ? request.getNationalId().trim()
                : null;

        // 1. Kiểm tra tính duy nhất của Mã sinh viên (studentCode)
        if (studentRepository.existsByStudentCode(studentCode)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã số sinh viên '" + studentCode + "' đã tồn tại trong hệ thống");
        }

        // 2. Kiểm tra tính duy nhất của CCCD/Định danh (nếu có nhập)
        if (nationalId != null && studentRepository.existsByNationalId(nationalId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Số CCCD/Định danh '" + nationalId + "' đã tồn tại ở hồ sơ sinh viên khác");
        }

        // 3. Xác định hoặc tự động tạo tài khoản User cho sinh viên
        User user;
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            // Kiểm tra vai trò tài khoản: phải là STUDENT
            if (user.getRole() == null || !"STUDENT".equalsIgnoreCase(user.getRole().getRoleCode().trim())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng phải có vai trò sinh viên (STUDENT)");
            }

            if (studentRepository.findByUser_UserId(request.getUserId()).isPresent()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng này đã liên kết với hồ sơ sinh viên khác");
            }
        } else {
            // Quy trình chuẩn: Tự động khởi tạo tài khoản User với username = MSSV,
            // password = MSSV, role = STUDENT
            if (userRepository.existsByUsername(studentCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng với tên đăng nhập (MSSV) '" + studentCode + "' đã tồn tại");
            }

            String userEmail = (request.getEmail() != null && !request.getEmail().isBlank())
                    ? request.getEmail().trim()
                    : studentCode.toLowerCase() + "@student.edu.vn";

            if (userRepository.existsByEmail(userEmail)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Email '" + userEmail + "' đã được sử dụng bởi tài khoản khác");
            }

            Role studentRole = roleRepository.findByRoleCode("STUDENT")
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND,
                            "Không tìm thấy vai trò hệ thống STUDENT"));

            user = User.builder()
                    .username(studentCode)
                    .passwordHash(passwordEncoder.encode(studentCode))
                    .email(userEmail)
                    .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim()
                            : null)
                    .role(studentRole)
                    .status("ACTIVE")
                    .failedLoginAttempts(0)
                    .isEmailVerified(false)
                    .mustChangePassword(true)
                    .build();

            user = userRepository.save(user);
        }

        // 4. Chuẩn hóa số điện thoại: rỗng thành null
        if (request.getPhone() != null && request.getPhone().isBlank()) {
            request.setPhone(null);
        } else if (request.getPhone() != null) {
            request.setPhone(request.getPhone().trim());
        }

        Student student = studentMapper.toEntity(request);
        student.setStudentCode(studentCode);
        student.setNationalId(nationalId);
        student.setUser(user);
        if (request.getAvatarUrl() != null) {
            student.setAvatarUrl(request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim());
        }
        if (student.getStatus() == null || student.getStatus().isBlank()) {
            student.setStatus("ACTIVE");
        }
        student = studentRepository.save(student);

        return studentMapper.toResponse(student);
    }

    /**
     * Nhập hàng loạt hồ sơ sinh viên từ file Excel / danh sách
     * Tự động khởi tạo tài khoản đăng nhập cho từng sinh viên hợp lệ
     * Thu thập danh sách lỗi cụ thể theo từng dòng để phản hồi chi tiết cho Quản
     * trị viên
     */
    public StudentBatch_Response createBatch(List<Student_Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return StudentBatch_Response.builder()
                    .total(0)
                    .successCount(0)
                    .failedCount(0)
                    .successStudentCodes(java.util.Collections.emptyList())
                    .errors(java.util.Collections.emptyList())
                    .build();
        }

        Role studentRole = roleRepository.findByRoleCode("STUDENT")
                .orElseThrow(
                        () -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò hệ thống STUDENT"));

        java.util.List<String> successCodes = new java.util.ArrayList<>();
        java.util.List<StudentBatch_Response.StudentBatchError> errors = new java.util.ArrayList<>();
        java.util.Set<String> seenCodesInBatch = new java.util.HashSet<>();
        java.util.Set<String> seenNationalIdsInBatch = new java.util.HashSet<>();

        for (int i = 0; i < requests.size(); i++) {
            Student_Request req = requests.get(i);
            int rowNumber = i + 1;

            if (req == null) {
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, "-", "Dữ liệu dòng trống"));
                continue;
            }

            String studentCode = req.getStudentCode() != null ? req.getStudentCode().trim() : "";
            if (studentCode.isBlank()) {
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, "-",
                        "Mã số sinh viên (MSSV) không được để trống"));
                continue;
            }

            String fullName = req.getFullName() != null ? req.getFullName().trim() : "";
            if (fullName.isBlank()) {
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                        "Họ và tên không được để trống"));
                continue;
            }

            String rawPhone = req.getPhone() != null ? req.getPhone().trim().replaceAll("[^0-9+]", "") : "";
            if (rawPhone.isBlank()) {
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                        "Số điện thoại không được để trống"));
                continue;
            }

            // Kiểm tra trùng mã trong cùng file
            if (!seenCodesInBatch.add(studentCode.toUpperCase())) {
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                        "MSSV bị trùng lặp trong cùng tệp tải lên"));
                continue;
            }

            // Kiểm tra trùng trong CSDL
            if (studentRepository.existsByStudentCode(studentCode) || userRepository.existsByUsername(studentCode)) {
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                        "MSSV '" + studentCode + "' đã tồn tại trong hệ thống"));
                continue;
            }

            // Kiểm tra CCCD nếu có
            String nationalId = req.getNationalId() != null && !req.getNationalId().isBlank()
                    ? req.getNationalId().trim()
                    : null;
            if (nationalId != null) {
                if (!seenNationalIdsInBatch.add(nationalId)) {
                    errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                            "Số CCCD '" + nationalId + "' bị trùng trong tệp tải lên"));
                    continue;
                }
                if (studentRepository.existsByNationalId(nationalId)) {
                    errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                            "Số CCCD '" + nationalId + "' đã tồn tại ở sinh viên khác trong hệ thống"));
                    continue;
                }
            }

            // Chuẩn hóa giới tính
            String rawGender = req.getGender() != null ? req.getGender().trim().toUpperCase() : "MALE";
            String gender = "MALE";
            if (rawGender.contains("NỮ") || rawGender.contains("NU") || "FEMALE".equals(rawGender)) {
                gender = "FEMALE";
            }

            // Chuẩn hóa Email
            String email = (req.getEmail() != null && !req.getEmail().isBlank()) ? req.getEmail().trim() : null;
            String userEmail = email != null ? email : studentCode.toLowerCase() + "@student.edu.vn";
            if (userRepository.existsByEmail(userEmail)) {
                userEmail = studentCode.toLowerCase() + "." + System.currentTimeMillis() % 10000 + "@student.edu.vn";
            }

            try {
                // Khởi tạo tài khoản User
                User user = User.builder()
                        .username(studentCode)
                        .passwordHash(passwordEncoder.encode(studentCode))
                        .email(userEmail)
                        .phone(rawPhone)
                        .role(studentRole)
                        .status("ACTIVE")
                        .failedLoginAttempts(0)
                        .isEmailVerified(false)
                        .mustChangePassword(true)
                        .build();
                user = userRepository.save(user);

                // Khởi tạo hồ sơ Sinh viên
                Student student = Student.builder()
                        .studentCode(studentCode)
                        .fullName(fullName)
                        .gender(gender)
                        .dob(req.getDob())
                        .phone(rawPhone)
                        .email(email)
                        .nationalId(nationalId)
                        .faculty(req.getFaculty() != null && !req.getFaculty().isBlank() ? req.getFaculty().trim()
                                : null)
                        .className(
                                req.getClassName() != null && !req.getClassName().isBlank() ? req.getClassName().trim()
                                        : null)
                        .hometown(req.getHometown() != null && !req.getHometown().isBlank() ? req.getHometown().trim()
                                : null)
                        .permanentAddress(req.getPermanentAddress() != null && !req.getPermanentAddress().isBlank()
                                ? req.getPermanentAddress().trim()
                                : null)
                        .status("ACTIVE")
                        .user(user)
                        .build();

                studentRepository.save(student);
                successCodes.add(studentCode);
            } catch (Exception ex) {
                log.error("Lỗi khi thêm sinh viên dòng {}: {}", rowNumber, ex.getMessage());
                errors.add(new StudentBatch_Response.StudentBatchError(rowNumber, studentCode,
                        "Lỗi hệ thống: " + ex.getMessage()));
            }
        }

        return StudentBatch_Response.builder()
                .total(requests.size())
                .successCount(successCodes.size())
                .failedCount(errors.size())
                .successStudentCodes(successCodes)
                .errors(errors)
                .build();
    }

    @Transactional(readOnly = true)
    public Student_Response getById(String studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        checkViewPermission(student);

        Student_Response res = studentMapper.toResponse(student);
        enrichResidentInfo(res);
        return res;
    }

    @Transactional(readOnly = true)
    public Student_Response getByStudentCode(String studentCode) {
        Student student = studentRepository.findByStudentCode(studentCode)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        checkViewPermission(student);

        Student_Response res = studentMapper.toResponse(student);
        enrichResidentInfo(res);
        return res;
    }

    @Transactional(readOnly = true)
    public Student_Response getByUserId(String userId) {
        Student student = studentRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND,
                        "Hồ sơ sinh viên chưa hoàn tất. Vui lòng cập nhật đầy đủ thông tin hồ sơ và giới tính"));

        checkViewPermission(student);

        Student_Response res = studentMapper.toResponse(student);
        enrichResidentInfo(res);
        return res;
    }

    @Transactional(readOnly = true)
    public List<Student_Response> getAll() {
        return studentMapper.toResponseList(studentRepository.findAll());
    }

    @Transactional(readOnly = true)
    public Page_Response<Student_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, null, null, null, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public Page_Response<Student_Response> getAll(int page, int size, String keyword, String faculty,
            String className, String gender, String status, Boolean isResident) {
        return getAll(page, size, keyword, faculty, className, gender, status, isResident, "studentCode", "asc");
    }

    @Transactional(readOnly = true)
    public Page_Response<Student_Response> getAll(
            int page, int size,
            String keyword,
            String faculty,
            String className,
            String gender,
            String status,
            Boolean isResident,
            String sortBy,
            String sortDir) {
        int pageNumber = Math.max(page - 1, 0);
        int pageSize = Math.max(size, 1);

        String effectiveSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy.trim()))
                ? sortBy.trim()
                : "studentCode";

        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(effectiveSortBy).descending()
                : Sort.by(effectiveSortBy).ascending();

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        Page<Student> pageResult = studentRepository.searchStudents(
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (faculty != null && !faculty.isBlank()) ? faculty.trim() : null,
                (className != null && !className.isBlank()) ? className.trim() : null,
                (gender != null && !gender.isBlank()) ? gender.trim().toUpperCase() : null,
                (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null,
                isResident,
                pageable);

        List<Student_Response> data = studentMapper.toResponseList(pageResult.getContent());
        enrichResidentInfo(data);

        return Page_Response.<Student_Response>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasPrevious(pageResult.hasPrevious())
                .hasNext(pageResult.hasNext())
                .isFirst(pageResult.isFirst())
                .isLast(pageResult.isLast())
                .data(data)
                .build();
    }

    /**
     * Dành riêng cho sinh viên tự cập nhật thông tin cá nhân (chỉ liên hệ, địa chỉ,
     * người thân, CCCD)
     * Ngăn chặn việc tự đổi studentCode, status, className, faculty, gender, userId
     */
    @Transactional
    public Student_Response updateMyProfile(StudentSelfUpdate_Request request) {
        String currentUserId = currentUserService != null ? currentUserService.userId() : null;
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Student student = studentRepository.findByUser_UserId(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        // Xử lý CCCD/Định danh: null = giữ nguyên, rỗng = xóa (set null), còn lại = cập
        // nhật và kiểm tra trùng lặp
        if (request.getNationalId() != null) {
            if (request.getNationalId().isBlank()) {
                request.setNationalId(null);
                student.setNationalId(null);
            } else {
                String trimmedNationalId = request.getNationalId().trim();
                if (studentRepository.existsByNationalIdAndStudentIdNot(trimmedNationalId, student.getStudentId())) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Số CCCD/Định danh '" + trimmedNationalId + "' đã được đăng ký bởi sinh viên khác");
                }
                request.setNationalId(trimmedNationalId);
                student.setNationalId(trimmedNationalId);
            }
        }

        // Xử lý Số điện thoại: null = giữ nguyên, rỗng = xóa (set null), còn lại = cập
        // nhật
        if (request.getPhone() != null) {
            if (request.getPhone().isBlank()) {
                request.setPhone(null);
                student.setPhone(null);
            } else {
                String trimmedPhone = request.getPhone().trim();
                request.setPhone(trimmedPhone);
                student.setPhone(trimmedPhone);
            }
        }

        if (request.getDob() != null) {
            student.setDob(request.getDob());
        }
        if (request.getHometown() != null) {
            student.setHometown(request.getHometown().isBlank() ? null : request.getHometown().trim());
        }
        if (request.getPermanentAddress() != null) {
            student.setPermanentAddress(
                    request.getPermanentAddress().isBlank() ? null : request.getPermanentAddress().trim());
        }
        if (request.getEmail() != null) {
            student.setEmail(request.getEmail().isBlank() ? null : request.getEmail().trim());
        }
        String oldAvatarUrl = student.getAvatarUrl();
        if (request.getAvatarUrl() != null) {
            String newAvatarUrl = request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim();
            student.setAvatarUrl(newAvatarUrl);
            if (oldAvatarUrl != null && !oldAvatarUrl.equals(newAvatarUrl)) {
                deleteOldAvatarFile(oldAvatarUrl);
            }
        }
        if (request.getEmergencyContactName() != null) {
            student.setEmergencyContactName(
                    request.getEmergencyContactName().isBlank() ? null : request.getEmergencyContactName().trim());
        }
        if (request.getEmergencyContactPhone() != null) {
            student.setEmergencyContactPhone(
                    request.getEmergencyContactPhone().isBlank() ? null : request.getEmergencyContactPhone().trim());
        }
        if (request.getEmergencyContactRelation() != null) {
            student.setEmergencyContactRelation(request.getEmergencyContactRelation().isBlank() ? null
                    : request.getEmergencyContactRelation().trim());
        }

        student = studentRepository.save(student);

        return studentMapper.toResponse(student);
    }

    @Transactional
    public Student_Response update(String studentId, Student_Request request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        // 1. Kiểm tra MSSV nếu có cập nhật: không được trùng với sinh viên khác
        String trimmedStudentCode = request.getStudentCode() != null ? request.getStudentCode().trim() : null;
        if (trimmedStudentCode != null && !trimmedStudentCode.isEmpty()
                && !trimmedStudentCode.equals(student.getStudentCode())) {
            if (studentRepository.existsByStudentCode(trimmedStudentCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Mã số sinh viên '" + trimmedStudentCode + "' đã tồn tại trong hệ thống");
            }
            student.setStudentCode(trimmedStudentCode);
        }

        // 2. Kiểm tra CCCD/Định danh nếu có cập nhật: không được trùng với sinh viên
        // khác
        if (request.getNationalId() != null) {
            if (request.getNationalId().isBlank()) {
                request.setNationalId(null);
                student.setNationalId(null);
            } else {
                String trimmedNationalId = request.getNationalId().trim();
                if (studentRepository.existsByNationalIdAndStudentIdNot(trimmedNationalId, studentId)) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Số CCCD/Định danh '" + trimmedNationalId + "' đã tồn tại ở hồ sơ sinh viên khác");
                }
                request.setNationalId(trimmedNationalId);
                student.setNationalId(trimmedNationalId);
            }
        }

        // 4. Nếu người gọi không phải là quản trị/quản lý/nhân viên (ví dụ sinh viên
        // gọi trực
        // tiếp API này)
        // thì chặn chỉnh sửa các trường nhạy cảm: studentCode, status, className,
        // faculty, gender
        boolean isManager = currentUserService != null && (currentUserService.hasRole("ADMIN")
                || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF")
                || currentUserService.hasPermission("STUDENT_MANAGE"));

        if (!isManager && currentUserService != null) {
            String currentUserId;
            try {
                currentUserId = currentUserService.userId();
            } catch (Exception e) {
                currentUserId = null;
            }
            if (student.getUser() == null || !student.getUser().getUserId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền chỉnh sửa hồ sơ sinh viên khác");
            }
            // Bỏ qua các field nhạy cảm khi sinh viên gọi
            request.setStudentCode(student.getStudentCode());
            request.setStatus(student.getStatus());
            request.setClassName(student.getClassName());
            request.setFaculty(student.getFaculty());
            request.setGender(student.getGender());
            if (student.getUser() != null) {
                request.setUserId(student.getUser().getUserId());
            }
        }

        // 5. Xử lý số điện thoại: null = giữ nguyên, rỗng = xóa (set null), còn lại =
        // cập nhật
        if (request.getPhone() != null) {
            if (request.getPhone().isBlank()) {
                request.setPhone(null);
                student.setPhone(null);
            } else {
                String trimmedPhone = request.getPhone().trim();
                request.setPhone(trimmedPhone);
                student.setPhone(trimmedPhone);
            }
        }

        String oldAvatarUrl = student.getAvatarUrl();
        studentMapper.updateEntityFromRequest(student, request);
        if (request.getAvatarUrl() != null) {
            String newAvatarUrl = request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim();
            student.setAvatarUrl(newAvatarUrl);
            if (oldAvatarUrl != null && !oldAvatarUrl.equals(newAvatarUrl)) {
                deleteOldAvatarFile(oldAvatarUrl);
            }
        }

        // Xử lý cập nhật trạng thái nếu có: đồng bộ tài khoản người dùng và kiểm tra
        // hợp đồng lưu trú ACTIVE
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String newStatus = request.getStatus().trim().toUpperCase();
            String currentStatus = student.getStatus() != null ? student.getStatus().trim().toUpperCase() : "ACTIVE";
            if (!newStatus.equals(currentStatus)) {
                if ("INACTIVE".equalsIgnoreCase(newStatus)) {
                    // Chặn ngừng hoạt động sinh viên nếu vẫn còn hợp đồng cư trú ACTIVE
                    if (contractRepository.existsByStudent_StudentIdAndStatus(studentId, "ACTIVE")) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể chuyển trạng thái ngừng hoạt động (INACTIVE) cho sinh viên đang có hợp đồng lưu trú ACTIVE");
                    }
                    student.setStatus("INACTIVE");
                    if (student.getUser() != null) {
                        student.getUser().setStatus("INACTIVE");
                        userRepository.save(student.getUser());
                        if (refreshTokenService != null) {
                            refreshTokenService.revokeAllActiveByUserId(student.getUser().getUserId());
                        }
                    }
                } else if ("PENDING".equalsIgnoreCase(newStatus)) {
                    if (contractRepository.existsByStudent_StudentIdAndStatus(studentId, "ACTIVE")) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể chuyển sinh viên đang có hợp đồng lưu trú ACTIVE về trạng thái Chờ duyệt (PENDING)");
                    }
                    student.setStatus("PENDING");
                } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
                    student.setStatus("ACTIVE");
                    if (student.getUser() != null && "INACTIVE".equalsIgnoreCase(student.getUser().getStatus())) {
                        student.getUser().setStatus("ACTIVE");
                        userRepository.save(student.getUser());
                    }
                } else {
                    student.setStatus(newStatus);
                }
            }
        }

        student = studentRepository.save(student);

        return studentMapper.toResponse(student);
    }

    /**
     * Ngừng hoạt động hồ sơ sinh viên (Soft delete: INACTIVE)
     */
    public void delete(String studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        // Kiểm tra hợp đồng ACTIVE
        if (contractRepository.existsByStudent_StudentIdAndStatus(studentId, "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa hoặc ngừng hoạt động sinh viên đang có hợp đồng lưu trú ACTIVE");
        }

        // Chuyển trạng thái sang INACTIVE và khóa tài khoản đăng nhập
        student.setStatus("INACTIVE");
        studentRepository.save(student);

        if (student.getUser() != null) {
            student.getUser().setStatus("INACTIVE");
            userRepository.save(student.getUser());
            if (refreshTokenService != null) {
                refreshTokenService.revokeAllActiveByUserId(student.getUser().getUserId());
            }
        }
    }

    /**
     * Kiểm tra quyền xem: Chỉ chính chủ sinh viên hoặc nhân viên/quản lý mới được
     * xem
     */
    private void checkViewPermission(Student student) {
        if (currentUserService == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Quản lý hoặc nhân viên có quyền xem toàn bộ sinh viên
        if (currentUserService.hasRole("ADMIN")
                || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("STAFF")
                || currentUserService.hasPermission("STUDENT_MANAGE")) {
            return;
        }

        String currentUserId;
        try {
            currentUserId = currentUserService.userId();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (student.getUser() == null || !student.getUser().getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem thông tin hồ sơ của sinh viên khác");
        }
    }

    /**
     * Lưu file ảnh đại diện sinh viên vào thư mục upload được cấu hình
     * (app.upload.dir)
     * Trả về đường dẫn tương đối để lưu vào CSDL (ví dụ: /uploads/avatars/uuid.png)
     */
    public String saveAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > 25 * 1024 * 1024) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Dung lượng ảnh tối đa cho phép là 25MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        Set<String> allowedExtensions = Set.of("jpg", "jpeg", "png", "gif", "webp");
        if (!allowedExtensions.contains(extension)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ chấp nhận các định dạng ảnh: JPG, JPEG, PNG, GIF, WEBP");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        try {
            Path targetDir = webMvcConfig.getUploadDir().resolve("avatars");
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);

            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/avatars/" + fileName;
        } catch (IOException e) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể lưu tệp ảnh đại diện: " + e.getMessage());
        }
    }

    /**
     * Tự động xóa file ảnh đại diện cũ trên ổ đĩa khi sinh viên đổi ảnh mới hoặc gỡ
     * ảnh
     * Tránh tình trạng lưu ảnh mồ côi (orphan files) gây rác bộ nhớ máy chủ
     */
    public void deleteOldAvatarFile(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }

        String trimmed = avatarUrl.trim();
        // Bỏ qua nếu là liên kết ảnh bên ngoài internet
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return;
        }

        // Bỏ qua query string hoặc hash nếu có (ví dụ: ?v=123 hoặc #xyz)
        if (trimmed.contains("?")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('?'));
        }
        if (trimmed.contains("#")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('#'));
        }

        // Trích xuất tên file (ví dụ: /uploads/avatars/abc.png hoặc
        // /QLyKyTucXa/uploads/avatars/abc.png -> abc.png)
        String fileName = trimmed;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }
        if (fileName.contains("\\")) {
            fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
        }
        fileName = fileName.trim();

        if (fileName.isEmpty() || fileName.contains("..")) {
            return;
        }

        // Kiểm tra và xóa file ở tất cả các thư mục uploads khả dĩ
        java.util.List<Path> candidateDirs = java.util.List.of(
                webMvcConfig.getUploadDir().resolve("avatars"),
                webMvcConfig.getUploadDir(),
                java.nio.file.Paths.get("uploads", "avatars").toAbsolutePath().normalize(),
                java.nio.file.Paths.get("uploads").toAbsolutePath().normalize(),
                java.nio.file.Paths.get("QL_KyTucXa", "uploads", "avatars").toAbsolutePath().normalize(),
                java.nio.file.Paths.get("QL_KyTucXa", "uploads").toAbsolutePath().normalize());

        for (Path dir : candidateDirs) {
            try {
                Path target = dir.resolve(fileName);
                if (Files.exists(target)) {
                    Files.deleteIfExists(target);
                    log.info("=> ĐÃ XÓA FILE ẢNH ĐẠI DIỆN CŨ TRÁNH RÁC DỮ LIỆU: {}", target);
                }
            } catch (IOException e) {
                log.warn("Không thể xóa file ảnh cũ '{}' tại '{}': {}", fileName, dir, e.getMessage());
            }
        }
    }

    private void enrichResidentInfo(Student_Response resp) {
        if (resp == null || resp.getStudentId() == null || contractRepository == null)
            return;
        try {
            List<Contract> contracts = contractRepository.findByStudent_StudentIdAndStatus(resp.getStudentId(),
                    "ACTIVE");
            if (contracts != null && !contracts.isEmpty()) {
                Contract c = contracts.get(0);
                resp.setIsResident(true);
                resp.setContractCode(c.getContractCode());
                if (c.getBed() != null) {
                    resp.setBedNumber(c.getBed().getBedNumber());
                    if (c.getBed().getRoom() != null) {
                        resp.setRoomNumber(c.getBed().getRoom().getRoomNumber());
                    }
                }
            } else {
                resp.setIsResident(false);
            }
        } catch (Exception e) {
            log.warn("Lỗi khi nạp thông tin chỗ ở KTX của sinh viên {}: {}", resp.getStudentId(), e.getMessage());
            resp.setIsResident(false);
        }
    }

    private void enrichResidentInfo(List<Student_Response> list) {
        if (list == null || list.isEmpty() || contractRepository == null)
            return;
        try {
            List<Contract> activeContracts = contractRepository.findByStatus("ACTIVE");
            if (activeContracts == null || activeContracts.isEmpty()) {
                for (Student_Response resp : list) {
                    resp.setIsResident(false);
                }
                return;
            }

            Map<String, Contract> contractMap = new HashMap<>();
            for (Contract c : activeContracts) {
                if (c.getStudent() != null && c.getStudent().getStudentId() != null) {
                    contractMap.putIfAbsent(c.getStudent().getStudentId(), c);
                }
            }

            for (Student_Response resp : list) {
                Contract c = contractMap.get(resp.getStudentId());
                if (c != null) {
                    resp.setIsResident(true);
                    resp.setContractCode(c.getContractCode());
                    if (c.getBed() != null) {
                        resp.setBedNumber(c.getBed().getBedNumber());
                        if (c.getBed().getRoom() != null) {
                            resp.setRoomNumber(c.getBed().getRoom().getRoomNumber());
                        }
                    }
                } else {
                    resp.setIsResident(false);
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi khi nạp danh sách chỗ ở KTX cho sinh viên: {}", e.getMessage());
            for (Student_Response resp : list) {
                if (resp.getIsResident() == null) {
                    resp.setIsResident(false);
                }
            }
        }
    }
}
