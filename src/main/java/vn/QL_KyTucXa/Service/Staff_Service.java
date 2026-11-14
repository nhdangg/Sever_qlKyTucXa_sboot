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
import vn.QL_KyTucXa.DTO.Request.Staff_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Staff_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Staff_Mapper;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Staff_Service {

    Staff_Repository staffRepository;
    User_Repository userRepository;
    Role_Repository roleRepository;
    Staff_Mapper staffMapper;
    Student_Repository studentRepository;
    PasswordEncoder passwordEncoder;
    RefreshTokenService refreshTokenService;

    // Các vai trò được phép liên kết với hồ sơ nhân viên
    public static final Set<String> ALLOWED_STAFF_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "staffId", "staffCode", "fullName", "department", "position", "createdAt", "updatedAt"
    );

    @Transactional
    public Staff_Response create(Staff_Request request) {
        String staffCode = request.getStaffCode() != null ? request.getStaffCode().trim() : "";
        log.info("Tạo mới nhân viên: {}", staffCode);

        // Chuẩn hóa số điện thoại: rỗng đổi thành null trước khi map
        if (request.getPhone() != null && request.getPhone().isBlank()) {
            request.setPhone(null);
        } else if (request.getPhone() != null) {
            request.setPhone(request.getPhone().trim());
        }

        User user;
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            // Kiểm tra vai trò của tài khoản: phải là nhân viên, chặn tài khoản STUDENT
            if (user.getRole() == null
                    || !ALLOWED_STAFF_ROLES.contains(user.getRole().getRoleCode().trim().toUpperCase())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng phải có vai trò nhân viên (STAFF hoặc MANAGER)");
            }

            if (staffRepository.existsByStaffCode(staffCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Mã nhân viên '" + staffCode + "' đã tồn tại");
            }

            // Chặn tài khoản người dùng đã liên kết với nhân viên khác
            if (staffRepository.findByUser_UserId(request.getUserId()).isPresent()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng này đã liên kết với nhân viên khác");
            }

            // Chặn tài khoản người dùng đã có hồ sơ sinh viên
            if (studentRepository.findByUser_UserId(request.getUserId()).isPresent()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng này đã có hồ sơ sinh viên");
            }
        } else {
            if (staffRepository.existsByStaffCode(staffCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Mã nhân viên '" + staffCode + "' đã tồn tại");
            }

            // Quy trình chuẩn: Tự động khởi tạo tài khoản User cho nhân viên
            // Username = Mã NV, Mật khẩu mặc định = Mã NV, Role = STAFF
            if (userRepository.existsByUsername(staffCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng với tên đăng nhập (Mã NV) '" + staffCode + "' đã tồn tại");
            }

            String userEmail = (request.getEmail() != null && !request.getEmail().isBlank())
                    ? request.getEmail().trim()
                    : staffCode.toLowerCase() + "@staff.edu.vn";

            if (userRepository.existsByEmail(userEmail)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Email '" + userEmail + "' đã được sử dụng bởi tài khoản khác");
            }

            Role staffRole = roleRepository.findByRoleCode("STAFF")
                    .orElseThrow(
                            () -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò hệ thống STAFF"));

            user = User.builder()
                    .username(staffCode)
                    .passwordHash(passwordEncoder.encode(staffCode))
                    .email(userEmail)
                    .phone(request.getPhone())
                    .role(staffRole)
                    .status("ACTIVE")
                    .failedLoginAttempts(0)
                    .isEmailVerified(false)
                    .mustChangePassword(true)
                    .build();

            user = userRepository.save(user);
        }

        Staff staff = staffMapper.toEntity(request);
        staff.setStaffCode(staffCode);
        staff.setUser(user);
        staff = staffRepository.save(staff);

        return staffMapper.toResponse(staff);
    }

    @Transactional(readOnly = true)
    public Staff_Response getById(String staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        return staffMapper.toResponse(staff);
    }

    @Transactional(readOnly = true)
    public Staff_Response getByStaffCode(String staffCode) {
        Staff staff = staffRepository.findByStaffCode(staffCode)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        return staffMapper.toResponse(staff);
    }

    @Transactional(readOnly = true)
    public List<Staff_Response> getAll() {
        List<Staff> list = staffRepository.findAll();
        return staffMapper.toResponseList(list);
    }

    @Transactional(readOnly = true)
    public Page_Response<Staff_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public Page_Response<Staff_Response> getAll(int page, int size, String keyword, String department, String status,
            String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String dept = (department != null && !department.isBlank()) ? department.trim() : null;
        String st = (status != null && !status.isBlank()) ? status.trim() : null;

        Page<Staff> pageResult = staffRepository.searchStaffs(kw, dept, st, pageable);
        List<Staff_Response> data = staffMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Staff_Response>builder()
                .currentPage(pageResult.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasPrevious(pageResult.hasPrevious())
                .hasNext(pageResult.hasNext())
                .isFirst(pageResult.isFirst())
                .isLast(pageResult.isLast())
                .data(data)
                .build();
    }

    @Transactional
    public Staff_Response update(String staffId, Staff_Request request) {
        log.info("Cập nhật thông tin nhân viên ID: {}", staffId);

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        if (!staff.getStaffCode().equalsIgnoreCase(request.getStaffCode()) &&
                staffRepository.existsByStaffCode(request.getStaffCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã nhân viên '" + request.getStaffCode() + "' đã tồn tại");
        }

        // Kiểm tra khi đổi tài khoản người dùng liên kết
        if (request.getUserId() != null
                && (staff.getUser() == null || !staff.getUser().getUserId().equals(request.getUserId()))) {
            // Chặn gán tài khoản đã liên kết với nhân viên khác
            if (staffRepository.existsByUser_UserIdAndStaffIdNot(request.getUserId(), staffId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng này đã liên kết với nhân viên khác");
            }

            // Chặn gán tài khoản đã có hồ sơ sinh viên
            if (studentRepository.findByUser_UserId(request.getUserId()).isPresent()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng này đã có hồ sơ sinh viên");
            }

            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            // Kiểm tra vai trò của tài khoản mới: phải là nhân viên
            if (user.getRole() == null
                    || !ALLOWED_STAFF_ROLES.contains(user.getRole().getRoleCode().trim().toUpperCase())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tài khoản người dùng phải có vai trò nhân viên (STAFF hoặc MANAGER)");
            }

            staff.setUser(user);
        }

        // Xử lý số điện thoại: null = giữ nguyên, rỗng = xóa (set null), còn lại = cập
        // nhật
        if (request.getPhone() != null) {
            if (request.getPhone().isBlank()) {
                request.setPhone(null);
                staff.setPhone(null);
            } else {
                String trimmedPhone = request.getPhone().trim();
                request.setPhone(trimmedPhone);
                staff.setPhone(trimmedPhone);
            }
        }

        staffMapper.updateEntityFromRequest(staff, request);

        // Nếu cập nhật trạng thái nhân viên:
        // - INACTIVE: khóa tài khoản người dùng và thu hồi toàn bộ refresh token
        // - ACTIVE: mở khóa lại tài khoản người dùng nếu đang bị INACTIVE
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String normalizedStatus = request.getStatus().trim().toUpperCase();
            staff.setStatus(normalizedStatus);
            if ("INACTIVE".equalsIgnoreCase(normalizedStatus)) {
                if (staff.getUser() != null) {
                    staff.getUser().setStatus("INACTIVE");
                    userRepository.save(staff.getUser());
                    if (refreshTokenService != null) {
                        refreshTokenService.revokeAllActiveByUserId(staff.getUser().getUserId());
                    }
                }
            } else if ("ACTIVE".equalsIgnoreCase(normalizedStatus)) {
                if (staff.getUser() != null && "INACTIVE".equalsIgnoreCase(staff.getUser().getStatus())) {
                    staff.getUser().setStatus("ACTIVE");
                    userRepository.save(staff.getUser());
                }
            }
        }

        staff = staffRepository.save(staff);

        return staffMapper.toResponse(staff);
    }

    @Transactional
    public void delete(String staffId) {
        log.info("Xử lý cho nhân viên nghỉ việc ID: {}", staffId);

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        // Tránh lỗi khóa ngoại với các bảng liên kết (hợp đồng, thanh toán, vi
        // phạm...),
        // chuyển trạng thái nhân viên sang INACTIVE (nghỉ việc) và khóa tài khoản đăng
        // nhập
        staff.setStatus("INACTIVE");
        staffRepository.save(staff);

        User user = staff.getUser();
        if (user != null) {
            user.setStatus("INACTIVE");
            userRepository.save(user);
            if (refreshTokenService != null) {
                refreshTokenService.revokeAllActiveByUserId(user.getUserId());
            }
        }
    }
}
