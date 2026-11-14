package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class User_ServiceTest {

    @Mock
    private User_Repository userRepository;

    @Mock
    private Role_Repository roleRepository;

    @Mock
    private User_Mapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CurrentUser_Service currentUserService;

    @Mock
    private Student_Repository studentRepository;

    @Mock
    private Staff_Repository staffRepository;

    @InjectMocks
    private User_Service userService;

    private User user;
    private Role role;
    private Role studentRole;
    private User_Request request;
    private User_Response response;

    @BeforeEach
    void setUp() {
        role = Role.builder()
                .roleId("role-123")
                .roleCode("ADMIN")
                .roleName("Quản trị viên")
                .build();

        studentRole = Role.builder()
                .roleId("role-stu")
                .roleCode("STUDENT")
                .roleName("Sinh viên")
                .build();

        user = User.builder()
                .userId("user-123")
                .username("testuser")
                .passwordHash("hashed_password")
                .email("test@example.com")
                .phone("0912345678")
                .role(role)
                .status("ACTIVE")
                .build();

        request = User_Request.builder()
                .username("testuser")
                .password("password123")
                .email("test@example.com")
                .phone("0912345678")
                .roleId("role-123")
                .status("ACTIVE")
                .build();

        response = User_Response.builder()
                .userId("user-123")
                .username("testuser")
                .email("test@example.com")
                .status("ACTIVE")
                .build();

        lenient().when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        lenient().when(currentUserService.userId()).thenReturn("caller-admin-id");
    }

    @Test
    @DisplayName("Tạo user thành công khi thông tin hợp lệ")
    void create_success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findById("role-123")).thenReturn(Optional.of(role));
        when(userMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        User_Response result = userService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Tạo user thất bại khi username đã tồn tại")
    void create_failed_usernameExisted() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_EXISTED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo user thất bại khi email đã tồn tại")
    void create_failed_emailExisted() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo user thất bại khi role không tồn tại")
    void create_failed_roleNotFound() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findById("role-123")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROLE_NOT_FOUND);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo user thất bại khi chọn vai trò STUDENT tại trang quản lý người dùng")
    void create_failed_studentRoleBlocked() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findById("role-123")).thenReturn(Optional.of(studentRole));

        AppException ex = assertThrows(AppException.class, () -> userService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể tạo tài khoản sinh viên trực tiếp tại đây");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo user thất bại khi chọn vai trò STAFF tại trang quản lý người dùng")
    void create_failed_staffRoleBlocked() {
        Role staffRole = Role.builder().roleId("role-stf").roleCode("STAFF").build();
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findById("role-123")).thenReturn(Optional.of(staffRole));

        AppException ex = assertThrows(AppException.class, () -> userService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể tạo tài khoản nhân viên trực tiếp tại đây");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đăng ký công khai ghi đè status ACTIVE và cấm gán telegramChatId")
    void createFromRegistration_success() {
        User_Request regReq = User_Request.builder()
                .username("new_student")
                .password("pass123")
                .email("student@uni.edu.vn")
                .status("BANNED")
                .telegramChatId("malicious_chat_id")
                .build();

        User studentUser = User.builder()
                .userId("stu-user-1")
                .username("new_student")
                .role(studentRole)
                .status("ACTIVE")
                .build();

        when(userRepository.existsByUsername("new_student")).thenReturn(false);
        when(userRepository.existsByEmail("student@uni.edu.vn")).thenReturn(false);
        when(roleRepository.findByRoleCode("STUDENT")).thenReturn(Optional.of(studentRole));
        when(userMapper.toEntity(regReq)).thenReturn(studentUser);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(studentUser);
        when(userMapper.toResponse(studentUser)).thenReturn(User_Response.builder().username("new_student").build());

        User_Response res = userService.createFromRegistration(regReq);

        assertThat(res).isNotNull();
        assertThat(studentUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(studentUser.getTelegramChatId()).isNull();
    }

    @Test
    @DisplayName("Lấy user theo ID thành công")
    void getById_success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        User_Response result = userService.getById("user-123");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Lấy user theo ID thất bại khi không tìm thấy")
    void getById_notFound() {
        when(userRepository.findById("invalid-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.getById("invalid-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXISTED);
    }

    @Test
    @DisplayName("Lấy danh sách tất cả user thành công")
    void getAll_success() {
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        List<User_Response> result = userService.getAll();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm user có phân trang thành công")
    void searchUsers_success() {
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.searchUsers(eq("test"), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(response);

        Page_Response<User_Response> result = userService.searchUsers("test", null, null, 1, 10);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Cập nhật thông tin user thành công khi caller là ADMIN")
    void update_success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        User_Response result = userService.update("user-123", request);

        assertThat(result).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Cập nhật tài khoản ADMIN thất bại khi caller không phải ADMIN")
    void update_adminTarget_nonAdminCaller_throwsUnauthorized() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> userService.update("user-123", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đổi vai trò người dùng thu hồi toàn bộ Refresh Token của phiên cũ")
    void update_roleChange_revokesTokens() {
        Role staffRole = Role.builder().roleId("role-staff").roleCode("STAFF").build();
        User normalUser = User.builder().userId("user-norm").role(studentRole).username("norm").email("n@mail.com").build();
        User_Request updateReq = User_Request.builder().username("norm").email("n@mail.com").roleId("role-staff").build();

        when(userRepository.findById("user-norm")).thenReturn(Optional.of(normalUser));
        when(roleRepository.findById("role-staff")).thenReturn(Optional.of(staffRole));
        when(userRepository.save(any(User.class))).thenReturn(normalUser);
        when(userMapper.toResponse(normalUser)).thenReturn(response);

        userService.update("user-norm", updateReq);

        verify(refreshTokenService).revokeAllActiveByUserId("user-norm");
    }

    @Test
    @DisplayName("Khóa tài khoản thành công và thu hồi toàn bộ Refresh Token")
    void updateStatus_lockUser_revokesTokens() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.countByRole_RoleCodeAndStatus("ADMIN", "ACTIVE")).thenReturn(3L);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        userService.updateStatus("user-123", "BANNED", true);

        assertThat(user.getStatus()).isEqualTo("BANNED");
        verify(refreshTokenService).revokeAllActiveByUserId("user-123");
    }

    @Test
    @DisplayName("Cập nhật trạng thái người dùng sang INACTIVE thành công")
    void updateStatus_inactiveUser_success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.countByRole_RoleCodeAndStatus("ADMIN", "ACTIVE")).thenReturn(3L);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        userService.updateStatus("user-123", "INACTIVE", true);

        assertThat(user.getStatus()).isEqualTo("INACTIVE");
        verify(refreshTokenService).revokeAllActiveByUserId("user-123");
    }

    @Test
    @DisplayName("Không cho phép tự khóa tài khoản của chính mình")
    void updateStatus_selfLock_throwsException() {
        when(currentUserService.userId()).thenReturn("user-123");
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class, () -> userService.updateStatus("user-123", "BANNED", true));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể tự khóa tài khoản của chính mình");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không cho phép khóa tài khoản ADMIN cuối cùng của hệ thống")
    void updateStatus_lastAdmin_throwsException() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.countByRole_RoleCodeAndStatus("ADMIN", "ACTIVE")).thenReturn(1L);

        AppException ex = assertThrows(AppException.class, () -> userService.updateStatus("user-123", "BANNED", true));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("ADMIN) cuối cùng");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Người dùng tự cập nhật email thì đặt lại isEmailVerified = false")
    void updateSelf_changeEmail_resetsVerification() {
        user.setIsEmailVerified(true);
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("newemail@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserSelfUpdate_Request selfReq = UserSelfUpdate_Request.builder()
                .email("newemail@test.com")
                .build();

        userService.updateSelf("user-123", selfReq);

        assertThat(user.getIsEmailVerified()).isFalse();
        assertThat(user.getEmail()).isEqualTo("newemail@test.com");
    }

    @Test
    @DisplayName("Chặn đổi mật khẩu qua updateSelf, yêu cầu dùng changePassword")
    void updateSelf_withPassword_throwsException() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        UserSelfUpdate_Request selfReq = UserSelfUpdate_Request.builder()
                .password("newpass123")
                .build();

        AppException ex = assertThrows(AppException.class, () -> userService.updateSelf("user-123", selfReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể đổi mật khẩu qua cập nhật thông tin cá nhân");
    }

    @Test
    @DisplayName("Khóa user (INACTIVE) thành công khi xóa tài khoản không có ràng buộc hồ sơ")
    void delete_success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.countByRole_RoleCode("ADMIN")).thenReturn(2L);
        when(studentRepository.existsByUser_UserId("user-123")).thenReturn(false);
        when(staffRepository.existsByUser_UserId("user-123")).thenReturn(false);

        userService.delete("user-123");

        verify(refreshTokenService).revokeAllActiveByUserId("user-123");
        verify(userRepository).save(user);
        assertThat(user.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("Chặn xóa tài khoản khi đã có hồ sơ Sinh viên")
    void delete_hasStudentRecord_throwsException() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.countByRole_RoleCode("ADMIN")).thenReturn(2L);
        when(studentRepository.existsByUser_UserId("user-123")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.delete("user-123"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Sinh viên");
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Chặn xóa tài khoản khi đã có hồ sơ Nhân viên")
    void delete_hasStaffRecord_throwsException() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(userRepository.countByRole_RoleCode("ADMIN")).thenReturn(2L);
        when(studentRepository.existsByUser_UserId("user-123")).thenReturn(false);
        when(staffRepository.existsByUser_UserId("user-123")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.delete("user-123"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Nhân viên");
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Chặn admin tự xóa tài khoản của chính mình")
    void delete_selfDelete_throwsException() {
        when(currentUserService.userId()).thenReturn("user-123");
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class, () -> userService.delete("user-123"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể tự xóa tài khoản của chính mình");
        verify(userRepository, never()).delete(any());
    }
}
