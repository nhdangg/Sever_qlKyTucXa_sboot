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
import vn.QL_KyTucXa.DTO.Request.UserSelfUpdate_Request;
import vn.QL_KyTucXa.DTO.Request.User_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.User_Mapper;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class User_Service {

    User_Repository userRepository;
    Role_Repository roleRepository;
    User_Mapper userMapper;
    PasswordEncoder passwordEncoder;
    RefreshTokenService refreshTokenService;
    CurrentUser_Service currentUserService;
    Student_Repository studentRepository;
    Staff_Repository staffRepository;
    AuditLog_Service auditLogService;

    /**
     * Tạo mới tài khoản người dùng từ trang Quản lý Người dùng
     */
    @Transactional
    public User_Response create(User_Request request) {
        log.info("Bắt đầu tạo tài khoản người dùng: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Email '" + request.getEmail() + "' đã tồn tại");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.PASSWORD_BLANK);
        }

        Role vai_tro = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        String roleCode = vai_tro.getRoleCode() != null ? vai_tro.getRoleCode().trim().toUpperCase() : "";

        // Chặn người không phải ADMIN tạo tài khoản ADMIN hoặc MANAGER
        boolean callerIsAdmin = currentUserService != null && currentUserService.hasRole("ADMIN");
        if (("ADMIN".equals(roleCode) || "MANAGER".equals(roleCode)) && !callerIsAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản trị viên cấp cao (ADMIN) mới có quyền tạo tài khoản có vai trò Quản lý (MANAGER) hoặc Quản trị viên (ADMIN)");
        }

        if ("STUDENT".equals(roleCode)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể tạo tài khoản sinh viên trực tiếp tại đây. Vui lòng tạo tại mục Quản lý Sinh viên để đồng bộ hồ sơ sinh viên.");
        }
        if ("STAFF".equals(roleCode)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể tạo tài khoản nhân viên trực tiếp tại đây. Vui lòng tạo tại mục Quản lý Nhân viên để đồng bộ hồ sơ nhân viên.");
        }

        User nguoi_dung = userMapper.toEntity(request);
        nguoi_dung.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        nguoi_dung.setRole(vai_tro);
        nguoi_dung.setStatus(
                request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus().trim().toUpperCase()
                        : "ACTIVE");
        nguoi_dung.setIsEmailVerified(false);
        nguoi_dung.setFailedLoginAttempts(0);

        nguoi_dung = userRepository.save(nguoi_dung);

        if (auditLogService != null) {
            auditLogService.recordAction("CREATE_USER", "nguoiDung", nguoi_dung.getUserId(), null,
                    "Username: " + nguoi_dung.getUsername() + ", Role: STUDENT");
        }

        return sanitizeUserResponse(userMapper.toResponse(nguoi_dung), nguoi_dung.getUserId());
    }

    /**
     * Tạo tài khoản từ luồng Đăng ký công khai (ghi đè role STUDENT, status ACTIVE,
     * cấm gán telegramChatId)
     */
    @Transactional
    public User_Response createFromRegistration(User_Request request) {
        log.info("Đăng ký tài khoản công khai: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED, "UserName đã tồn tại hãy thay đổi UserName khác.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Email '" + request.getEmail() + "' đã tồn tại");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.PASSWORD_BLANK);
        }

        Role vai_tro_sinh_vien = roleRepository.findByRoleCode("STUDENT")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò Sinh viên"));

        User nguoi_dung = userMapper.toEntity(request);
        nguoi_dung.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        nguoi_dung.setRole(vai_tro_sinh_vien);
        // Ghi đè trạng thái và xóa telegramChatId để đảm bảo an toàn
        nguoi_dung.setStatus("ACTIVE");
        nguoi_dung.setTelegramChatId(null);
        nguoi_dung.setIsEmailVerified(false);
        nguoi_dung.setFailedLoginAttempts(0);

        nguoi_dung = userRepository.save(nguoi_dung);

        if (auditLogService != null) {
            auditLogService.recordAction("CREATE_USER", "nguoiDung", nguoi_dung.getUserId(), null,
                    "Username: " + nguoi_dung.getUsername() + ", Role: STUDENT");
        }

        return sanitizeUserResponse(userMapper.toResponse(nguoi_dung), nguoi_dung.getUserId());
    }

    /**
     * Lấy thông tin chi tiết người dùng theo ID
     */
    @Transactional(readOnly = true)
    public User_Response getById(String userId) {
        log.info("Lấy thông tin người dùng theo ID: {}", userId);

        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return sanitizeUserResponse(userMapper.toResponse(nguoi_dung), nguoi_dung.getUserId());
    }

    /**
     * Lấy danh sách tất cả người dùng
     */
    @Transactional(readOnly = true)
    public List<User_Response> getAll() {
        log.info("Lấy danh sách tất cả người dùng");

        List<User> danh_sach_nguoi_dung = userRepository.findAll();
        return danh_sach_nguoi_dung.stream()
                .map(userMapper::toResponse)
                .map(r -> sanitizeUserResponse(r, r.getUserId()))
                .toList();
    }

    /**
     * Tìm kiếm và phân trang người dùng theo từ khóa, vai trò và trạng thái
     */
    @Transactional(readOnly = true)
    public Page_Response<User_Response> searchUsers(String keyword, String roleId, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String cleanRoleId = (roleId != null && !roleId.isBlank()) ? roleId.trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<User> trang_nguoi_dung = userRepository.searchUsers(cleanKeyword, cleanRoleId, cleanStatus, pageable);
        List<User_Response> content = trang_nguoi_dung.getContent().stream()
                .map(userMapper::toResponse)
                .map(r -> sanitizeUserResponse(r, r.getUserId()))
                .toList();

        return Page_Response.<User_Response>builder()
                .currentPage(trang_nguoi_dung.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_nguoi_dung.getTotalPages())
                .totalElements(trang_nguoi_dung.getTotalElements())
                .hasPrevious(trang_nguoi_dung.hasPrevious())
                .hasNext(trang_nguoi_dung.hasNext())
                .isFirst(trang_nguoi_dung.isFirst())
                .isLast(trang_nguoi_dung.isLast())
                .data(content)
                .build();
    }

    /**
     * Cập nhật thông tin người dùng (kiểm tra quyền bảo vệ ADMIN, tự hạ quyền, thu
     * hồi token khi đổi vai trò)
     */
    @Transactional
    public User_Response update(String userId, User_Request request) {
        log.info("Cập nhật thông tin người dùng: {}", userId);

        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean targetIsAdmin = nguoi_dung.getRole() != null && "ADMIN".equalsIgnoreCase(nguoi_dung.getRole().getRoleCode());
        boolean callerIsAdmin = currentUserService != null && currentUserService.hasRole("ADMIN");

        // Chặn người không phải ADMIN sửa tài khoản ADMIN
        if (targetIsAdmin && !callerIsAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản trị viên cấp cao mới có quyền chỉnh sửa tài khoản ADMIN");
        }

        if (!nguoi_dung.getUsername().equalsIgnoreCase(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        if (!nguoi_dung.getEmail().equalsIgnoreCase(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Email đã được sử dụng bởi tài khoản khác");
            }
            nguoi_dung.setEmail(request.getEmail());
            nguoi_dung.setIsEmailVerified(false);
        }

        // Kiểm tra và xử lý khi đổi vai trò
        if (request.getRoleId() != null && !nguoi_dung.getRole().getRoleId().equals(request.getRoleId())) {
            Role vai_tro_moi = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

            String newRoleCode = vai_tro_moi.getRoleCode() != null ? vai_tro_moi.getRoleCode().trim().toUpperCase() : "";

            // Chặn người không phải ADMIN nâng quyền thành ADMIN hoặc MANAGER
            if (("ADMIN".equals(newRoleCode) || "MANAGER".equals(newRoleCode)) && !callerIsAdmin) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản trị viên cấp cao (ADMIN) mới có quyền gán vai trò Quản lý (MANAGER) hoặc Quản trị viên (ADMIN)");
            }

            // Chặn hạ quyền ADMIN cuối cùng hoặc tự hạ quyền ADMIN của chính mình
            if (targetIsAdmin && !"ADMIN".equalsIgnoreCase(newRoleCode)) {
                String currentUserId = resolveCurrentUserId();
                if (userId.equals(currentUserId)) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Không thể tự hạ vai trò Quản trị viên của chính mình");
                }
                long activeAdminCount = userRepository.countByRole_RoleCodeAndStatus("ADMIN", "ACTIVE");
                if (activeAdminCount <= 1) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Không thể hạ vai trò của Quản trị viên (ADMIN) cuối cùng của hệ thống");
                }
            }

            nguoi_dung.setRole(vai_tro_moi);

            // Thu hồi toàn bộ Refresh Token của user khi đổi vai trò vì vai trò gắn liền
            // với token
            if (refreshTokenService != null) {
                refreshTokenService.revokeAllActiveByUserId(userId);
            }
        }

        userMapper.updateEntityFromRequest(nguoi_dung, request);

        // Đổi mật khẩu
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            nguoi_dung.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            nguoi_dung.setMustChangePassword(false);
            if (refreshTokenService != null) {
                refreshTokenService.revokeAllActiveByUserId(userId);
            }
        }

        // Kiểm tra trạng thái nếu gửi kèm trong update (không cho đi vòng updateStatus)
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String normalizedStatus = request.getStatus().trim().toUpperCase();
            if (!normalizedStatus.equals(nguoi_dung.getStatus())) {
                if (!normalizedStatus.equals("ACTIVE") && !normalizedStatus.equals("BANNED")
                        && !normalizedStatus.equals("LOCKED") && !normalizedStatus.equals("INACTIVE")) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Trạng thái chỉ nhận ACTIVE, BANNED, LOCKED hoặc INACTIVE");
                }
                if (!"ACTIVE".equals(normalizedStatus)) {
                    String currentUserId = resolveCurrentUserId();
                    if (userId.equals(currentUserId)) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể tự khóa tài khoản của chính mình");
                    }
                    if (targetIsAdmin) {
                        long activeAdminCount = userRepository.countByRole_RoleCodeAndStatus("ADMIN", "ACTIVE");
                        if (activeAdminCount <= 1) {
                            throw new AppException(ErrorCode.OPERATION_FAILED,
                                    "Không thể khóa tài khoản Quản trị viên (ADMIN) cuối cùng của hệ thống");
                        }
                    }
                    if (refreshTokenService != null) {
                        refreshTokenService.revokeAllActiveByUserId(userId);
                    }
                } else {
                    nguoi_dung.setFailedLoginAttempts(0);
                }
                nguoi_dung.setStatus(normalizedStatus);
            }
        }

        nguoi_dung = userRepository.save(nguoi_dung);
        return sanitizeUserResponse(userMapper.toResponse(nguoi_dung), nguoi_dung.getUserId());
    }

    /**
     * Khóa (BANNED/LOCKED) hoặc mở khóa (ACTIVE) tài khoản
     */
    @Transactional
    public User_Response updateStatus(String userId, String status, boolean callerIsAdmin) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("ACTIVE") && !normalized.equals("BANNED")
                && !normalized.equals("LOCKED") && !normalized.equals("INACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái chỉ nhận ACTIVE (mở khóa) hoặc BANNED / LOCKED / INACTIVE (khóa)");
        }

        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean targetIsAdmin = nguoi_dung.getRole() != null && "ADMIN".equalsIgnoreCase(nguoi_dung.getRole().getRoleCode());
        if (targetIsAdmin && !callerIsAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản trị viên cấp cao mới có quyền thay đổi trạng thái tài khoản ADMIN");
        }

        if (!"ACTIVE".equals(normalized)) {
            // Không cho admin tự khóa chính mình
            String currentUserId = resolveCurrentUserId();
            if (userId.equals(currentUserId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể tự khóa tài khoản của chính mình");
            }

            // Không cho khóa admin cuối cùng
            if (targetIsAdmin) {
                long activeAdminCount = userRepository.countByRole_RoleCodeAndStatus("ADMIN", "ACTIVE");
                if (activeAdminCount <= 1) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Không thể khóa tài khoản Quản trị viên (ADMIN) cuối cùng của hệ thống");
                }
            }

            // Thu hồi toàn bộ Refresh Token của user để cắt phiên đăng nhập ngay lập tức
            if (refreshTokenService != null) {
                refreshTokenService.revokeAllActiveByUserId(userId);
            }
        } else {
            nguoi_dung.setFailedLoginAttempts(0);
        }

        nguoi_dung.setStatus(normalized);
        User nguoi_dung_da_luu = userRepository.save(nguoi_dung);

        if (auditLogService != null) {
            auditLogService.recordAction("UPDATE_USER_STATUS", "nguoiDung", userId, null, "Status: " + normalized);
        }

        return sanitizeUserResponse(userMapper.toResponse(nguoi_dung_da_luu), nguoi_dung.getUserId());
    }

    /**
     * Người dùng tự cập nhật email, số điện thoại, mật khẩu của chính mình
     */
    @Transactional
    public User_Response updateSelf(String userId, UserSelfUpdate_Request request) {
        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !nguoi_dung.getEmail().equalsIgnoreCase(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Email đã được sử dụng bởi tài khoản khác");
            }
            nguoi_dung.setEmail(request.getEmail());
            // Đặt lại isEmailVerified = false khi đổi email
            nguoi_dung.setIsEmailVerified(false);
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            nguoi_dung.setPhone(request.getPhone());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể đổi mật khẩu qua cập nhật thông tin cá nhân. Vui lòng sử dụng chức năng đổi mật khẩu chuyên biệt (/api/users/me/change-password) có xác thực mật khẩu hiện tại");
        }

        return sanitizeUserResponse(userMapper.toResponse(userRepository.save(nguoi_dung)), nguoi_dung.getUserId());
    }

    /**
     * Đổi mật khẩu cá nhân có xác thực mật khẩu hiện tại
     */
    @Transactional
    public void changePassword(String userId, vn.QL_KyTucXa.DTO.Request.ChangePassword_Request request) {
        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!passwordEncoder.matches(request.getCurrentPassword(), nguoi_dung.getPasswordHash())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mật khẩu hiện tại không chính xác");
        }

        nguoi_dung.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        nguoi_dung.setMustChangePassword(false);
        userRepository.save(nguoi_dung);

        if (refreshTokenService != null) {
            refreshTokenService.revokeAllActiveByUserId(userId);
        }
    }

    /**
     * Cập nhật Telegram Chat ID phục vụ xác thực 2FA (Chỉ chính chủ hoặc Quản trị
     * viên được đổi)
     * Đối với tài khoản Quản trị viên (ADMIN), bắt buộc nhập lại mật khẩu hiện tại
     * để xác thực bảo mật
     */
    @Transactional
    public User_Response updateTelegram2FA(String userId, String telegramChatId, String confirmPassword) {
        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String currentUserId = resolveCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User nguoi_dung_hien_tai = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        boolean callerIsAdmin = currentUserService != null && currentUserService.hasRole("ADMIN");
        boolean isSelf = currentUserId.equals(userId);

        if (!isSelf && !callerIsAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ chính chủ tài khoản hoặc Quản trị viên mới được phép cập nhật Telegram Chat ID");
        }

        boolean targetIsAdmin = nguoi_dung.getRole() != null && "ADMIN".equalsIgnoreCase(nguoi_dung.getRole().getRoleCode());

        // Nếu cập nhật Telegram 2FA cho tài khoản ADMIN, bắt buộc xác nhận lại mật khẩu
        // của người đang thao tác
        if (targetIsAdmin) {
            if (confirmPassword == null || confirmPassword.isBlank()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Vui lòng nhập mật khẩu xác nhận để cập nhật cấu hình Telegram 2FA cho Quản trị viên");
            }
            if (!passwordEncoder.matches(confirmPassword, nguoi_dung_hien_tai.getPasswordHash())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Mật khẩu xác nhận không chính xác");
            }
        }

        String cleanChatId = (telegramChatId != null && !telegramChatId.isBlank())
                ? telegramChatId.trim()
                : null;

        nguoi_dung.setTelegramChatId(cleanChatId);
        return sanitizeUserResponse(userMapper.toResponse(userRepository.save(nguoi_dung)), nguoi_dung.getUserId());
    }

    @Transactional
    public User_Response updateTelegram2FA(String userId, String telegramChatId) {
        return updateTelegram2FA(userId, telegramChatId, null);
    }

    /**
     * Xóa tài khoản người dùng theo ID (Kiểm tra ràng buộc sinh viên, nhân viên,
     * quản trị viên)
     */
    @Transactional
    public void delete(String userId) {
        log.info("Xóa người dùng có ID: {}", userId);

        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean targetIsAdmin = nguoi_dung.getRole() != null && "ADMIN".equalsIgnoreCase(nguoi_dung.getRole().getRoleCode());
        boolean callerIsAdmin = currentUserService != null && currentUserService.hasRole("ADMIN");

        // 1. Kiểm tra quyền bảo vệ ADMIN
        if (targetIsAdmin) {
            if (!callerIsAdmin) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Chỉ Quản trị viên cấp cao mới có quyền xóa tài khoản ADMIN");
            }
            String currentUserId = resolveCurrentUserId();
            if (userId.equals(currentUserId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể tự xóa tài khoản của chính mình");
            }
            long adminCount = userRepository.countByRole_RoleCode("ADMIN");
            if (adminCount <= 1) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể xóa tài khoản Quản trị viên (ADMIN) cuối cùng của hệ thống");
            }
        }

        // 2. Chặn xóa nếu tài khoản đang liên kết với hồ sơ Sinh viên hoặc Nhân viên
        if (studentRepository != null && studentRepository.existsByUser_UserId(userId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Tài khoản đã liên kết với hồ sơ Sinh viên, không thể xóa trực tiếp. Vui lòng khóa tài khoản thay vì xóa.");
        }

        if (staffRepository != null && staffRepository.existsByUser_UserId(userId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Tài khoản đã liên kết với hồ sơ Nhân viên, không thể xóa trực tiếp. Vui lòng khóa tài khoản thay vì xóa.");
        }

        // 3. Khóa tài khoản (soft-delete sang INACTIVE) và thu hồi toàn bộ Refresh
        // Token thay vì xóa cứng
        // (để bảo toàn toàn vẹn dữ liệu và tránh lỗi Foreign Key với nhật ký, thông
        // báo, yêu cầu sửa chữa, refresh token)
        nguoi_dung.setStatus("INACTIVE");
        userRepository.save(nguoi_dung);

        if (auditLogService != null) {
            auditLogService.recordAction("DELETE_USER", "nguoiDung", userId, Map.of("username", nguoi_dung.getUsername()),
                    null);
        }

        if (refreshTokenService != null) {
            refreshTokenService.revokeAllActiveByUserId(userId);
        }
    }

    /**
     * Ẩn các trường nhạy cảm (failedLoginAttempts, telegramChatId) đối với người
     * không có quyền quản trị
     */
    private User_Response sanitizeUserResponse(User_Response response, String targetUserId) {
        if (response == null)
            return null;

        boolean isAdminOrManager = currentUserService != null
                && (currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER"));

        boolean isSelf = false;
        String currentUserId = resolveCurrentUserId();
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            isSelf = true;
        }

        // Nếu không phải ADMIN/MANAGER: ẩn failedLoginAttempts
        if (!isAdminOrManager) {
            response.setFailedLoginAttempts(null);
            // Ẩn telegramChatId nếu không phải chính chủ
            if (!isSelf) {
                response.setTelegramChatId(null);
            }
        }

        return response;
    }

    private String resolveCurrentUserId() {
        if (currentUserService != null) {
            try {
                return currentUserService.userId();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
