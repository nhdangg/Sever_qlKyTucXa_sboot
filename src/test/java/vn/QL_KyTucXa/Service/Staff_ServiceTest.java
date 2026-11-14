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
import vn.QL_KyTucXa.DTO.Request.Staff_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Staff_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Staff_Mapper;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Staff_ServiceTest {

    @Mock
    private Staff_Repository staffRepository;

    @Mock
    private User_Repository userRepository;

    @Mock
    private Role_Repository roleRepository;

    @Mock
    private Staff_Mapper staffMapper;

    @Mock
    private Student_Repository studentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private Staff_Service staffService;

    private User staffUser;
    private User studentUser;
    private Role staffRole;
    private Role studentRole;
    private Staff sampleStaff;
    private Staff_Request sampleRequest;
    private Staff_Response sampleResponse;

    @BeforeEach
    void setUp() {
        staffRole = Role.builder()
                .roleId("role-staff")
                .roleCode("STAFF")
                .roleName("Nhân viên")
                .build();

        studentRole = Role.builder()
                .roleId("role-student")
                .roleCode("STUDENT")
                .roleName("Sinh viên")
                .build();

        staffUser = User.builder()
                .userId("user-staff-1")
                .username("nhanvien1")
                .role(staffRole)
                .status("ACTIVE")
                .build();

        studentUser = User.builder()
                .userId("user-student-1")
                .username("sinhvien1")
                .role(studentRole)
                .status("ACTIVE")
                .build();

        sampleStaff = Staff.builder()
                .staffId("staff-1")
                .staffCode("NV001")
                .fullName("Nguyễn Văn A")
                .position("Quản lý tòa nhà")
                .department("Ban QL KTX")
                .phone("0988776655")
                .email("nva@kytucxa.edu.vn")
                .hireDate(LocalDate.of(2023, 1, 15))
                .status("ACTIVE")
                .user(staffUser)
                .build();

        sampleRequest = Staff_Request.builder()
                .userId("user-staff-1")
                .staffCode("NV001")
                .fullName("Nguyễn Văn A")
                .position("Quản lý tòa nhà")
                .department("Ban QL KTX")
                .phone("0988776655")
                .email("nva@kytucxa.edu.vn")
                .hireDate(LocalDate.of(2023, 1, 15))
                .status("ACTIVE")
                .build();

        sampleResponse = Staff_Response.builder()
                .staffId("staff-1")
                .userId("user-staff-1")
                .username("nhanvien1")
                .staffCode("NV001")
                .fullName("Nguyễn Văn A")
                .phone("0988776655")
                .status("ACTIVE")
                .build();
    }

    // ==================== CREATE TESTS ====================

    @Test
    @DisplayName("create - Thành công khi tài khoản hợp lệ, có vai trò STAFF")
    void create_success() {
        when(userRepository.findById("user-staff-1")).thenReturn(Optional.of(staffUser));
        when(staffRepository.existsByStaffCode("NV001")).thenReturn(false);
        when(staffRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.empty());
        when(studentRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.empty());
        when(staffMapper.toEntity(any(Staff_Request.class))).thenReturn(sampleStaff);
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        Staff_Response response = staffService.create(sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStaffCode()).isEqualTo("NV001");
        verify(staffRepository).save(sampleStaff);
    }

    @Test
    @DisplayName("create - Tự động tạo tài khoản User với vai trò STAFF khi userId để trống")
    void create_autoCreateUser_success() {
        Staff_Request newReq = Staff_Request.builder()
                .staffCode("NV999")
                .fullName("Lê Văn Mới")
                .position("Bảo vệ")
                .email("levanmoi@staff.edu.vn")
                .phone("0911223344")
                .status("ACTIVE")
                .build();

        when(staffRepository.existsByStaffCode("NV999")).thenReturn(false);
        when(userRepository.existsByUsername("NV999")).thenReturn(false);
        when(userRepository.existsByEmail("levanmoi@staff.edu.vn")).thenReturn(false);
        when(roleRepository.findByRoleCode("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode("NV999")).thenReturn("hashed_nv999");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Staff createdStaff = Staff.builder()
                .staffId("staff-999")
                .staffCode("NV999")
                .fullName("Lê Văn Mới")
                .build();

        when(staffMapper.toEntity(newReq)).thenReturn(createdStaff);
        when(staffRepository.save(any(Staff.class))).thenReturn(createdStaff);
        when(staffMapper.toResponse(createdStaff)).thenReturn(sampleResponse);

        Staff_Response res = staffService.create(newReq);

        assertThat(res).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(staffRepository).save(createdStaff);
    }

    @Test
    @DisplayName("create - Thành công và đổi số điện thoại rỗng \"\" thành null trước khi lưu")
    void create_success_blankPhoneConvertsToNull() {
        sampleRequest.setPhone("   ");
        when(userRepository.findById("user-staff-1")).thenReturn(Optional.of(staffUser));
        when(staffRepository.existsByStaffCode("NV001")).thenReturn(false);
        when(staffRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.empty());
        when(studentRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.empty());
        when(staffMapper.toEntity(any(Staff_Request.class))).thenReturn(sampleStaff);
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        staffService.create(sampleRequest);

        assertThat(sampleRequest.getPhone()).isNull();
        verify(staffRepository).save(sampleStaff);
    }

    @Test
    @DisplayName("create - Thất bại khi tài khoản liên kết có vai trò STUDENT")
    void create_failed_userIsStudentRole() {
        sampleRequest.setUserId("user-student-1");
        when(userRepository.findById("user-student-1")).thenReturn(Optional.of(studentUser));

        AppException ex = assertThrows(AppException.class, () -> staffService.create(sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Tài khoản người dùng phải có vai trò nhân viên");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Thất bại khi tài khoản đã có hồ sơ sinh viên")
    void create_failed_userAlreadyHasStudentProfile() {
        when(userRepository.findById("user-staff-1")).thenReturn(Optional.of(staffUser));
        when(staffRepository.existsByStaffCode("NV001")).thenReturn(false);
        when(staffRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.empty());
        when(studentRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.of(new Student()));

        AppException ex = assertThrows(AppException.class, () -> staffService.create(sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Tài khoản người dùng này đã có hồ sơ sinh viên");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Thất bại khi tài khoản đã liên kết với nhân viên khác")
    void create_failed_userAlreadyLinkedToAnotherStaff() {
        when(userRepository.findById("user-staff-1")).thenReturn(Optional.of(staffUser));
        when(staffRepository.existsByStaffCode("NV001")).thenReturn(false);
        when(staffRepository.findByUser_UserId("user-staff-1")).thenReturn(Optional.of(new Staff()));

        AppException ex = assertThrows(AppException.class, () -> staffService.create(sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("liên kết với nhân viên khác");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Thất bại khi mã nhân viên đã tồn tại")
    void create_failed_duplicateStaffCode() {
        when(userRepository.findById("user-staff-1")).thenReturn(Optional.of(staffUser));
        when(staffRepository.existsByStaffCode("NV001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> staffService.create(sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Mã nhân viên 'NV001' đã tồn tại");
        verify(staffRepository, never()).save(any());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    @DisplayName("update - Thành công khi không đổi tài khoản liên kết")
    void update_success_sameUser() {
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        Staff_Response response = staffService.update("staff-1", sampleRequest);

        assertThat(response).isNotNull();
        verify(staffMapper).updateEntityFromRequest(sampleStaff, sampleRequest);
        verify(staffRepository).save(sampleStaff);
    }

    @Test
    @DisplayName("update - Thành công khi đổi sang tài khoản nhân viên hợp lệ mới")
    void update_success_changeToValidStaffUser() {
        User newStaffUser = User.builder()
                .userId("user-staff-2")
                .username("nhanvien2")
                .role(staffRole)
                .status("ACTIVE")
                .build();
        sampleRequest.setUserId("user-staff-2");

        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.existsByUser_UserIdAndStaffIdNot("user-staff-2", "staff-1")).thenReturn(false);
        when(studentRepository.findByUser_UserId("user-staff-2")).thenReturn(Optional.empty());
        when(userRepository.findById("user-staff-2")).thenReturn(Optional.of(newStaffUser));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        Staff_Response response = staffService.update("staff-1", sampleRequest);

        assertThat(response).isNotNull();
        assertThat(sampleStaff.getUser()).isEqualTo(newStaffUser);
        verify(staffRepository).save(sampleStaff);
    }

    @Test
    @DisplayName("update - Thất bại khi đổi sang tài khoản đã liên kết với nhân viên khác (chặn lỗi 500 unique)")
    void update_failed_changeUser_alreadyLinkedToAnotherStaff() {
        sampleRequest.setUserId("user-staff-2");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.existsByUser_UserIdAndStaffIdNot("user-staff-2", "staff-1")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> staffService.update("staff-1", sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("liên kết với nhân viên khác");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Thất bại khi đổi sang tài khoản có hồ sơ sinh viên")
    void update_failed_changeUser_hasStudentProfile() {
        sampleRequest.setUserId("user-student-1");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.existsByUser_UserIdAndStaffIdNot("user-student-1", "staff-1")).thenReturn(false);
        when(studentRepository.findByUser_UserId("user-student-1")).thenReturn(Optional.of(new Student()));

        AppException ex = assertThrows(AppException.class, () -> staffService.update("staff-1", sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã có hồ sơ sinh viên");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Thất bại khi đổi sang tài khoản có vai trò STUDENT")
    void update_failed_changeUser_studentRole() {
        sampleRequest.setUserId("user-student-1");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.existsByUser_UserIdAndStaffIdNot("user-student-1", "staff-1")).thenReturn(false);
        when(studentRepository.findByUser_UserId("user-student-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-student-1")).thenReturn(Optional.of(studentUser));

        AppException ex = assertThrows(AppException.class, () -> staffService.update("staff-1", sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Tài khoản người dùng phải có vai trò nhân viên");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Thành công khi đổi số điện thoại rỗng \"\" thành null")
    void update_success_blankPhoneConvertsToNull() {
        sampleRequest.setPhone("   ");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        staffService.update("staff-1", sampleRequest);

        assertThat(sampleStaff.getPhone()).isNull();
        verify(staffRepository).save(sampleStaff);
    }

    @Test
    @DisplayName("update - Đồng bộ khóa tài khoản người dùng và thu hồi token khi cập nhật nhân viên sang INACTIVE")
    void update_syncUserInactive_whenStaffInactive() {
        sampleRequest.setStatus("INACTIVE");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        staffService.update("staff-1", sampleRequest);

        assertThat(sampleStaff.getStatus()).isEqualTo("INACTIVE");
        assertThat(staffUser.getStatus()).isEqualTo("INACTIVE");
        verify(userRepository).save(staffUser);
        verify(refreshTokenService).revokeAllActiveByUserId("user-staff-1");
    }

    @Test
    @DisplayName("update - Đồng bộ mở khóa tài khoản người dùng khi cập nhật nhân viên sang ACTIVE nếu user đang INACTIVE")
    void update_syncUserActive_whenStaffActive() {
        sampleStaff.setStatus("INACTIVE");
        staffUser.setStatus("INACTIVE");
        sampleRequest.setStatus("ACTIVE");
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        staffService.update("staff-1", sampleRequest);

        assertThat(sampleStaff.getStatus()).isEqualTo("ACTIVE");
        assertThat(staffUser.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(staffUser);
    }

    // ==================== DELETE (NGHỈ VIỆC) TESTS ====================

    @Test
    @DisplayName("delete - Chuyển trạng thái nhân viên sang INACTIVE, khóa tài khoản và thu hồi token")
    void delete_success_softDeleteAndLockUser() {
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));

        staffService.delete("staff-1");

        assertThat(sampleStaff.getStatus()).isEqualTo("INACTIVE");
        assertThat(staffUser.getStatus()).isEqualTo("INACTIVE");
        verify(staffRepository).save(sampleStaff);
        verify(userRepository).save(staffUser);
        verify(refreshTokenService).revokeAllActiveByUserId("user-staff-1");
        verify(staffRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete - Thất bại khi không tìm thấy nhân viên")
    void delete_failed_staffNotFound() {
        when(staffRepository.findById("staff-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> staffService.delete("staff-999"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STAFF_NOT_FOUND);
        verify(staffRepository, never()).save(any());
    }

    // ==================== GET TESTS ====================

    @Test
    @DisplayName("getById - Thành công")
    void getById_success() {
        when(staffRepository.findById("staff-1")).thenReturn(Optional.of(sampleStaff));
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        Staff_Response response = staffService.getById("staff-1");

        assertThat(response).isNotNull();
        assertThat(response.getStaffId()).isEqualTo("staff-1");
    }

    @Test
    @DisplayName("getByStaffCode - Thành công")
    void getByStaffCode_success() {
        when(staffRepository.findByStaffCode("NV001")).thenReturn(Optional.of(sampleStaff));
        when(staffMapper.toResponse(sampleStaff)).thenReturn(sampleResponse);

        Staff_Response response = staffService.getByStaffCode("NV001");

        assertThat(response).isNotNull();
        assertThat(response.getStaffCode()).isEqualTo("NV001");
    }

    @Test
    @DisplayName("getAll - Phân trang thành công")
    void getAll_paged_success() {
        Page<Staff> page = new PageImpl<>(List.of(sampleStaff));
        when(staffRepository.searchStaffs(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(staffMapper.toResponseList(anyList())).thenReturn(List.of(sampleResponse));

        Page_Response<Staff_Response> response = staffService.getAll(1, 10, "staffCode", "asc");

        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(1);
    }

    @Test
    @DisplayName("getAll - Tìm kiếm theo từ khóa, phòng ban và trạng thái")
    void getAll_withSearchFilters_success() {
        Page<Staff> page = new PageImpl<>(List.of(sampleStaff));
        when(staffRepository.searchStaffs(eq("NV001"), eq("Ban QL KTX"), eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(page);
        when(staffMapper.toResponseList(anyList())).thenReturn(List.of(sampleResponse));

        Page_Response<Staff_Response> response = staffService.getAll(1, 10, "NV001", "Ban QL KTX", "ACTIVE",
                "staffCode", "asc");

        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(1);
        verify(staffRepository).searchStaffs(eq("NV001"), eq("Ban QL KTX"), eq("ACTIVE"), any(Pageable.class));
    }
}
