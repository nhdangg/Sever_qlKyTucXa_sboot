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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Bed_Repository bedRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.RoomTransfer_Repository roomTransferRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Invoice_Repository invoiceRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Payment_Repository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Feedback_Repository feedbackRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.VehicleRegistration_Repository vehicleRegistrationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Violation_Repository violationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Visitor_Repository visitorRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.NotificationRecipient_Repository notificationRecipientRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.Notification_Repository notificationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.RefreshToken_Repository refreshTokenRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.LogoutToken_Repository logoutTokenRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.AuditLog_Repository auditLogRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.experimental.NonFinal
    vn.QL_KyTucXa.Repository.MaintenanceRequest_Repository maintenanceRequestRepository;

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
        User nguoi_dung;
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            nguoi_dung = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            // Kiểm tra vai trò tài khoản: phải là STUDENT
            if (nguoi_dung.getRole() == null
                    || !"STUDENT".equalsIgnoreCase(nguoi_dung.getRole().getRoleCode().trim())) {
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

            Role vai_tro_sinh_vien = roleRepository.findByRoleCode("STUDENT")
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND,
                            "Không tìm thấy vai trò hệ thống STUDENT"));

            nguoi_dung = User.builder()
                    .username(studentCode)
                    .passwordHash(passwordEncoder.encode(studentCode))
                    .email(userEmail)
                    .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim()
                            : null)
                    .role(vai_tro_sinh_vien)
                    .status("ACTIVE")
                    .failedLoginAttempts(0)
                    .isEmailVerified(false)
                    .mustChangePassword(true)
                    .build();

            nguoi_dung = userRepository.save(nguoi_dung);
        }

        // 4. Chuẩn hóa số điện thoại: rỗng thành null
        if (request.getPhone() != null && request.getPhone().isBlank()) {
            request.setPhone(null);
        } else if (request.getPhone() != null) {
            request.setPhone(request.getPhone().trim());
        }

        Student sinh_vien = studentMapper.toEntity(request);
        sinh_vien.setStudentCode(studentCode);
        sinh_vien.setNationalId(nationalId);
        sinh_vien.setUser(nguoi_dung);
        if (request.getAvatarUrl() != null) {
            sinh_vien.setAvatarUrl(request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim());
        }
        if (sinh_vien.getStatus() == null || sinh_vien.getStatus().isBlank()) {
            sinh_vien.setStatus("ACTIVE");
        }
        sinh_vien = studentRepository.save(sinh_vien);

        return studentMapper.toResponse(sinh_vien);
    }

    // Nhập hàng loạt hồ sơ sinh viên từ file Excel hoặc danh sách
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

        Role vai_tro_sinh_vien = roleRepository.findByRoleCode("STUDENT")
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
                User nguoi_dung = User.builder()
                        .username(studentCode)
                        .passwordHash(passwordEncoder.encode(studentCode))
                        .email(userEmail)
                        .phone(rawPhone)
                        .role(vai_tro_sinh_vien)
                        .status("ACTIVE")
                        .failedLoginAttempts(0)
                        .isEmailVerified(false)
                        .mustChangePassword(true)
                        .build();
                nguoi_dung = userRepository.save(nguoi_dung);

                // Khởi tạo hồ sơ Sinh viên
                Student sinh_vien = Student.builder()
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
                        .user(nguoi_dung)
                        .build();

                studentRepository.save(sinh_vien);
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
        Student sinh_vien = studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        checkViewPermission(sinh_vien);

        Student_Response res = studentMapper.toResponse(sinh_vien);
        enrichResidentInfo(res);
        return res;
    }

    @Transactional(readOnly = true)
    public Student_Response getByStudentCode(String studentCode) {
        Student sinh_vien = studentRepository.findByStudentCode(studentCode)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        checkViewPermission(sinh_vien);

        Student_Response res = studentMapper.toResponse(sinh_vien);
        enrichResidentInfo(res);
        return res;
    }

    @Transactional(readOnly = true)
    public Student_Response getByUserId(String userId) {
        Student sinh_vien = studentRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND,
                        "Hồ sơ sinh viên chưa hoàn tất. Vui lòng cập nhật đầy đủ thông tin hồ sơ và giới tính"));

        checkViewPermission(sinh_vien);

        Student_Response res = studentMapper.toResponse(sinh_vien);
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

        Page<Student> trang_sinh_vien = studentRepository.searchStudents(
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (faculty != null && !faculty.isBlank()) ? faculty.trim() : null,
                (className != null && !className.isBlank()) ? className.trim() : null,
                (gender != null && !gender.isBlank()) ? gender.trim().toUpperCase() : null,
                (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null,
                isResident,
                pageable);

        List<Student_Response> data = studentMapper.toResponseList(trang_sinh_vien.getContent());
        enrichResidentInfo(data);

        return Page_Response.<Student_Response>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(trang_sinh_vien.getTotalPages())
                .totalElements(trang_sinh_vien.getTotalElements())
                .hasPrevious(trang_sinh_vien.hasPrevious())
                .hasNext(trang_sinh_vien.hasNext())
                .isFirst(trang_sinh_vien.isFirst())
                .isLast(trang_sinh_vien.isLast())
                .data(data)
                .build();
    }

    // Dành riêng cho sinh viên tự cập nhật thông tin cá nhân cơ bản
    @Transactional
    public Student_Response updateMyProfile(StudentSelfUpdate_Request request) {
        String currentUserId = currentUserService != null ? currentUserService.userId() : null;
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Student sinh_vien = studentRepository.findByUser_UserId(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        // Xử lý CCCD/Định danh: null = giữ nguyên, rỗng = xóa (set null), còn lại = cập
        // nhật và kiểm tra trùng lặp
        if (request.getNationalId() != null) {
            if (request.getNationalId().isBlank()) {
                request.setNationalId(null);
                sinh_vien.setNationalId(null);
            } else {
                String trimmedNationalId = request.getNationalId().trim();
                if (studentRepository.existsByNationalIdAndStudentIdNot(trimmedNationalId, sinh_vien.getStudentId())) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Số CCCD/Định danh '" + trimmedNationalId + "' đã được đăng ký bởi sinh viên khác");
                }
                request.setNationalId(trimmedNationalId);
                sinh_vien.setNationalId(trimmedNationalId);
            }
        }

        // Xử lý Số điện thoại: null = giữ nguyên, rỗng = xóa (set null), còn lại = cập
        // nhật
        if (request.getPhone() != null) {
            if (request.getPhone().isBlank()) {
                request.setPhone(null);
                sinh_vien.setPhone(null);
            } else {
                String trimmedPhone = request.getPhone().trim();
                request.setPhone(trimmedPhone);
                sinh_vien.setPhone(trimmedPhone);
            }
        }

        if (request.getDob() != null) {
            sinh_vien.setDob(request.getDob());
        }
        if (request.getHometown() != null) {
            sinh_vien.setHometown(request.getHometown().isBlank() ? null : request.getHometown().trim());
        }
        if (request.getPermanentAddress() != null) {
            sinh_vien.setPermanentAddress(
                    request.getPermanentAddress().isBlank() ? null : request.getPermanentAddress().trim());
        }
        if (request.getEmail() != null) {
            sinh_vien.setEmail(request.getEmail().isBlank() ? null : request.getEmail().trim());
        }
        String oldAvatarUrl = sinh_vien.getAvatarUrl();
        if (request.getAvatarUrl() != null) {
            String newAvatarUrl = request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim();
            sinh_vien.setAvatarUrl(newAvatarUrl);
            if (oldAvatarUrl != null && !oldAvatarUrl.equals(newAvatarUrl)) {
                deleteOldAvatarFile(oldAvatarUrl);
            }
        }
        if (request.getEmergencyContactName() != null) {
            sinh_vien.setEmergencyContactName(
                    request.getEmergencyContactName().isBlank() ? null : request.getEmergencyContactName().trim());
        }
        if (request.getEmergencyContactPhone() != null) {
            sinh_vien.setEmergencyContactPhone(
                    request.getEmergencyContactPhone().isBlank() ? null : request.getEmergencyContactPhone().trim());
        }
        if (request.getEmergencyContactRelation() != null) {
            sinh_vien.setEmergencyContactRelation(request.getEmergencyContactRelation().isBlank() ? null
                    : request.getEmergencyContactRelation().trim());
        }

        sinh_vien = studentRepository.save(sinh_vien);

        return studentMapper.toResponse(sinh_vien);
    }

    @Transactional
    public Student_Response update(String studentId, Student_Request request) {
        Student sinh_vien = studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        // 1. Kiểm tra MSSV nếu có cập nhật: không được trùng với sinh viên khác
        String trimmedStudentCode = request.getStudentCode() != null ? request.getStudentCode().trim() : null;
        if (trimmedStudentCode != null && !trimmedStudentCode.isEmpty()
                && !trimmedStudentCode.equals(sinh_vien.getStudentCode())) {
            if (studentRepository.existsByStudentCode(trimmedStudentCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Mã số sinh viên '" + trimmedStudentCode + "' đã tồn tại trong hệ thống");
            }
            sinh_vien.setStudentCode(trimmedStudentCode);
        }

        // 2. Kiểm tra CCCD/Định danh nếu có cập nhật: không được trùng với sinh viên
        // khác
        if (request.getNationalId() != null) {
            if (request.getNationalId().isBlank()) {
                request.setNationalId(null);
                sinh_vien.setNationalId(null);
            } else {
                String trimmedNationalId = request.getNationalId().trim();
                if (studentRepository.existsByNationalIdAndStudentIdNot(trimmedNationalId, studentId)) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Số CCCD/Định danh '" + trimmedNationalId + "' đã tồn tại ở hồ sơ sinh viên khác");
                }
                request.setNationalId(trimmedNationalId);
                sinh_vien.setNationalId(trimmedNationalId);
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
            if (sinh_vien.getUser() == null || !sinh_vien.getUser().getUserId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền chỉnh sửa hồ sơ sinh viên khác");
            }
            // Bỏ qua các field nhạy cảm khi sinh viên gọi
            request.setStudentCode(sinh_vien.getStudentCode());
            request.setStatus(sinh_vien.getStatus());
            request.setClassName(sinh_vien.getClassName());
            request.setFaculty(sinh_vien.getFaculty());
            request.setGender(sinh_vien.getGender());
            if (sinh_vien.getUser() != null) {
                request.setUserId(sinh_vien.getUser().getUserId());
            }
        }

        // 5. Xử lý số điện thoại: null = giữ nguyên, rỗng = xóa (set null), còn lại =
        // cập nhật
        if (request.getPhone() != null) {
            if (request.getPhone().isBlank()) {
                request.setPhone(null);
                sinh_vien.setPhone(null);
            } else {
                String trimmedPhone = request.getPhone().trim();
                request.setPhone(trimmedPhone);
                sinh_vien.setPhone(trimmedPhone);
            }
        }

        String oldAvatarUrl = sinh_vien.getAvatarUrl();
        studentMapper.updateEntityFromRequest(sinh_vien, request);
        if (request.getAvatarUrl() != null) {
            String newAvatarUrl = request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim();
            sinh_vien.setAvatarUrl(newAvatarUrl);
            if (oldAvatarUrl != null && !oldAvatarUrl.equals(newAvatarUrl)) {
                deleteOldAvatarFile(oldAvatarUrl);
            }
        }

        // Xử lý cập nhật trạng thái nếu có: đồng bộ tài khoản người dùng và kiểm tra
        // hợp đồng lưu trú ACTIVE
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String newStatus = request.getStatus().trim().toUpperCase();
            String currentStatus = sinh_vien.getStatus() != null ? sinh_vien.getStatus().trim().toUpperCase()
                    : "ACTIVE";
            if (!newStatus.equals(currentStatus)) {
                if ("INACTIVE".equalsIgnoreCase(newStatus)) {
                    // Chặn ngừng hoạt động sinh viên nếu vẫn còn hợp đồng cư trú ACTIVE
                    if (contractRepository.existsByStudent_StudentIdAndStatus(studentId, "ACTIVE")) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể chuyển trạng thái ngừng hoạt động (INACTIVE) cho sinh viên đang có hợp đồng lưu trú ACTIVE");
                    }
                    sinh_vien.setStatus("INACTIVE");
                    if (sinh_vien.getUser() != null) {
                        sinh_vien.getUser().setStatus("INACTIVE");
                        userRepository.save(sinh_vien.getUser());
                        if (refreshTokenService != null) {
                            refreshTokenService.revokeAllActiveByUserId(sinh_vien.getUser().getUserId());
                        }
                    }
                } else if ("PENDING".equalsIgnoreCase(newStatus)) {
                    if (contractRepository.existsByStudent_StudentIdAndStatus(studentId, "ACTIVE")) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể chuyển sinh viên đang có hợp đồng lưu trú ACTIVE về trạng thái Chờ duyệt (PENDING)");
                    }
                    sinh_vien.setStatus("PENDING");
                } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
                    sinh_vien.setStatus("ACTIVE");
                    if (sinh_vien.getUser() != null && "INACTIVE".equalsIgnoreCase(sinh_vien.getUser().getStatus())) {
                        sinh_vien.getUser().setStatus("ACTIVE");
                        userRepository.save(sinh_vien.getUser());
                    }
                } else {
                    sinh_vien.setStatus(newStatus);
                }
            }
        }

        sinh_vien = studentRepository.save(sinh_vien);

        return studentMapper.toResponse(sinh_vien);
    }

    // Xóa hồ sơ sinh viên (Hard delete khi không có hợp đồng ACTIVE)
    @Transactional
    public void delete(String studentId) {
        Student sinh_vien = studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));

        // Kiểm tra hợp đồng ACTIVE
        if (contractRepository.existsByStudent_StudentIdAndStatus(studentId, "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa hoặc ngừng hoạt động sinh viên đang có hợp đồng lưu trú ACTIVE");
        }

        User nguoi_dung = sinh_vien.getUser();
        sinh_vien.setStatus("INACTIVE");
        if (nguoi_dung != null) {
            nguoi_dung.setStatus("INACTIVE");
            if (refreshTokenService != null) {
                refreshTokenService.revokeAllActiveByUserId(nguoi_dung.getUserId());
            }
        }

        // Dọn dẹp các dữ liệu phụ thuộc của sinh viên trước khi xóa cứng
        if (feedbackRepository != null) {
            List<vn.QL_KyTucXa.Entity.Feedback> feedbacks = feedbackRepository.findByStudent_StudentId(studentId);
            if (!feedbacks.isEmpty()) {
                feedbackRepository.deleteAll(feedbacks);
            }
        }
        if (vehicleRegistrationRepository != null) {
            List<vn.QL_KyTucXa.Entity.VehicleRegistration> vehicles = vehicleRegistrationRepository.findByStudent_StudentId(studentId);
            if (!vehicles.isEmpty()) {
                vehicleRegistrationRepository.deleteAll(vehicles);
            }
        }
        if (violationRepository != null) {
            List<vn.QL_KyTucXa.Entity.Violation> violations = violationRepository.findByStudent_StudentId(studentId);
            if (!violations.isEmpty()) {
                violationRepository.deleteAll(violations);
            }
        }
        if (visitorRepository != null) {
            List<vn.QL_KyTucXa.Entity.Visitor> visitors = visitorRepository.findByHostStudent_StudentId(studentId);
            if (!visitors.isEmpty()) {
                visitorRepository.deleteAll(visitors);
            }
        }
        if (invoiceRepository != null) {
            List<vn.QL_KyTucXa.Entity.Invoice> invoices = invoiceRepository.findByStudent_StudentId(studentId);
            for (vn.QL_KyTucXa.Entity.Invoice inv : invoices) {
                if (paymentRepository != null) {
                    List<vn.QL_KyTucXa.Entity.Payment> payments = paymentRepository.findByInvoice_InvoiceId(inv.getInvoiceId());
                    if (!payments.isEmpty()) {
                        paymentRepository.deleteAll(payments);
                    }
                }
            }
            if (!invoices.isEmpty()) {
                invoiceRepository.deleteAll(invoices);
            }
        }
        if (roomTransferRepository != null) {
            List<vn.QL_KyTucXa.Entity.RoomTransfer> transfers = roomTransferRepository.findByContract_Student_StudentId(studentId);
            if (!transfers.isEmpty()) {
                roomTransferRepository.deleteAll(transfers);
            }
        }
        if (contractRepository != null) {
            List<Contract> contracts = contractRepository.findByStudent_StudentId(studentId);
            if (contracts != null && !contracts.isEmpty()) {
                for (Contract c : contracts) {
                    if (c.getBed() != null && "RESERVED".equalsIgnoreCase(c.getBed().getStatus()) && bedRepository != null) {
                        c.getBed().setStatus("AVAILABLE");
                        bedRepository.save(c.getBed());
                    }
                }
                contractRepository.deleteAll(contracts);
            }
        }

        // Xóa file ảnh đại diện cũ nếu có
        try {
            deleteOldAvatarFile(sinh_vien.getAvatarUrl());
        } catch (Exception ignored) {
        }

        // Xóa cứng bản ghi sinh viên khỏi CSDL
        studentRepository.delete(sinh_vien);

        // Dọn dẹp và xóa tài khoản User liên kết của sinh viên
        if (nguoi_dung != null && nguoi_dung.getUserId() != null) {
            String uid = nguoi_dung.getUserId();
            if (notificationRecipientRepository != null) {
                List<vn.QL_KyTucXa.Entity.NotificationRecipient> recipients = notificationRecipientRepository.findByUser_UserId(uid);
                if (!recipients.isEmpty()) {
                    notificationRecipientRepository.deleteAll(recipients);
                }
            }
            if (notificationRepository != null) {
                List<vn.QL_KyTucXa.Entity.Notification> createdNotifs = notificationRepository.findByCreatedBy_UserId(uid);
                for (vn.QL_KyTucXa.Entity.Notification n : createdNotifs) {
                    if (notificationRecipientRepository != null) {
                        List<vn.QL_KyTucXa.Entity.NotificationRecipient> nRecs = notificationRecipientRepository.findByNotification_NotificationId(n.getNotificationId());
                        if (!nRecs.isEmpty()) {
                            notificationRecipientRepository.deleteAll(nRecs);
                        }
                    }
                }
                if (!createdNotifs.isEmpty()) {
                    notificationRepository.deleteAll(createdNotifs);
                }
            }
            if (refreshTokenRepository != null) {
                List<vn.QL_KyTucXa.Entity.RefreshToken> tokens = refreshTokenRepository.findByUser_UserId(uid);
                if (!tokens.isEmpty()) {
                    refreshTokenRepository.deleteAll(tokens);
                }
            }
            if (logoutTokenRepository != null) {
                List<vn.QL_KyTucXa.Entity.LogoutToken> lTokens = logoutTokenRepository.findByUser_UserId(uid);
                if (!lTokens.isEmpty()) {
                    logoutTokenRepository.deleteAll(lTokens);
                }
            }
            if (maintenanceRequestRepository != null) {
                List<vn.QL_KyTucXa.Entity.MaintenanceRequest> mReqs = maintenanceRequestRepository.findByReportedBy_UserId(uid);
                if (!mReqs.isEmpty()) {
                    maintenanceRequestRepository.deleteAll(mReqs);
                }
            }
            if (auditLogRepository != null) {
                List<vn.QL_KyTucXa.Entity.AuditLog> logs = auditLogRepository.findByUser_UserId(uid);
                for (vn.QL_KyTucXa.Entity.AuditLog logItem : logs) {
                    logItem.setUser(null);
                }
                if (!logs.isEmpty()) {
                    auditLogRepository.saveAll(logs);
                }
            }
            userRepository.delete(nguoi_dung);
        }
    }

    // Kiểm tra quyền xem: Chỉ chính chủ sinh viên hoặc nhân viên/quản lý mới được xem
    private void checkViewPermission(Student sinh_vien) {
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

        if (sinh_vien.getUser() == null || !sinh_vien.getUser().getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem thông tin hồ sơ của sinh viên khác");
        }
    }

    // Lưu file ảnh đại diện sinh viên vào thư mục upload được cấu hình
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

    // Tự động xóa file ảnh đại diện cũ trên ổ đĩa khi sinh viên đổi ảnh mới hoặc gỡ ảnh
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
            List<Contract> danh_sach_hop_dong = contractRepository.findByStudent_StudentIdAndStatus(resp.getStudentId(),
                    "ACTIVE");
            if (danh_sach_hop_dong != null && !danh_sach_hop_dong.isEmpty()) {
                Contract hop_dong = danh_sach_hop_dong.get(0);
                resp.setIsResident(true);
                resp.setContractCode(hop_dong.getContractCode());
                if (hop_dong.getBed() != null) {
                    resp.setBedNumber(hop_dong.getBed().getBedNumber());
                    if (hop_dong.getBed().getRoom() != null) {
                        resp.setRoomNumber(hop_dong.getBed().getRoom().getRoomNumber());
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
            List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStatus("ACTIVE");
            if (danh_sach_hop_dong_hieu_luc == null || danh_sach_hop_dong_hieu_luc.isEmpty()) {
                for (Student_Response resp : list) {
                    resp.setIsResident(false);
                }
                return;
            }

            Map<String, Contract> contractMap = new HashMap<>();
            for (Contract hop_dong : danh_sach_hop_dong_hieu_luc) {
                if (hop_dong.getStudent() != null && hop_dong.getStudent().getStudentId() != null) {
                    contractMap.putIfAbsent(hop_dong.getStudent().getStudentId(), hop_dong);
                }
            }

            for (Student_Response resp : list) {
                Contract hop_dong = contractMap.get(resp.getStudentId());
                if (hop_dong != null) {
                    resp.setIsResident(true);
                    resp.setContractCode(hop_dong.getContractCode());
                    if (hop_dong.getBed() != null) {
                        resp.setBedNumber(hop_dong.getBed().getBedNumber());
                        if (hop_dong.getBed().getRoom() != null) {
                            resp.setRoomNumber(hop_dong.getBed().getRoom().getRoomNumber());
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
