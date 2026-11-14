package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.Role_Request;
import vn.QL_KyTucXa.DTO.Response.Role_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Role_Mapper;
import vn.QL_KyTucXa.Repository.RolePermission_Repository;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Role_ServiceTest {

    @Mock
    private Role_Repository roleRepository;

    @Mock
    private User_Repository userRepository;

    @Mock
    private RolePermission_Repository rolePermissionRepository;

    @Mock
    private Role_Mapper roleMapper;

    @InjectMocks
    private Role_Service roleService;

    private Role customRole;
    private Role adminRole;
    private Role studentRole;
    private Role_Request request;
    private Role_Response response;

    @BeforeEach
    void setUp() {
        customRole = Role.builder()
                .roleId("role-custom")
                .roleCode("SECURITY_GUARD")
                .roleName("Bảo vệ")
                .description("Nhân viên bảo vệ ký túc xá")
                .build();

        adminRole = Role.builder()
                .roleId("role-admin")
                .roleCode("ADMIN")
                .roleName("Quản trị viên")
                .description("Toàn quyền hệ thống")
                .build();

        studentRole = Role.builder()
                .roleId("role-student")
                .roleCode("STUDENT")
                .roleName("Sinh viên")
                .description("Sinh viên nội trú")
                .build();

        request = Role_Request.builder()
                .roleCode("SECURITY_GUARD")
                .roleName("Bảo vệ")
                .description("Nhân viên bảo vệ ký túc xá")
                .build();

        response = Role_Response.builder()
                .roleId("role-custom")
                .roleCode("SECURITY_GUARD")
                .roleName("Bảo vệ")
                .description("Nhân viên bảo vệ ký túc xá")
                .build();
    }

    @Test
    @DisplayName("Tạo vai trò mới thành công và chuẩn hóa mã viết hoa")
    void create_success() {
        request.setRoleCode("security_guard");
        when(roleRepository.existsByRoleCode("SECURITY_GUARD")).thenReturn(false);
        when(roleMapper.toEntity(any(Role_Request.class))).thenReturn(customRole);
        when(roleRepository.save(any(Role.class))).thenReturn(customRole);
        when(roleMapper.toResponse(customRole)).thenReturn(response);

        Role_Response result = roleService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getRoleCode()).isEqualTo("SECURITY_GUARD");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    @DisplayName("Tạo vai trò thất bại khi mã vai trò đã tồn tại")
    void create_failed_duplicateRoleCode() {
        when(roleRepository.existsByRoleCode("SECURITY_GUARD")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roleService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại");
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo vai trò thất bại khi mã vai trò để trống")
    void create_failed_blankRoleCode() {
        request.setRoleCode("   ");

        AppException ex = assertThrows(AppException.class, () -> roleService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được để trống");
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy chi tiết vai trò theo ID thành công")
    void getById_success() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));
        when(roleMapper.toResponse(customRole)).thenReturn(response);

        Role_Response result = roleService.getById("role-custom");

        assertThat(result).isNotNull();
        assertThat(result.getRoleId()).isEqualTo("role-custom");
    }

    @Test
    @DisplayName("Lấy chi tiết vai trò ném lỗi ROLE_NOT_FOUND khi không tồn tại")
    void getById_notFound() {
        when(roleRepository.findById("role-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roleService.getById("role-999"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tất cả vai trò trong hệ thống")
    void getAll_success() {
        when(roleRepository.findAll()).thenReturn(List.of(customRole, adminRole, studentRole));
        when(roleMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Role_Response> result = roleService.getAll();

        assertThat(result).hasSize(1);
        verify(roleRepository).findAll();
    }

    @Test
    @DisplayName("Cập nhật vai trò thành công khi giữ nguyên roleCode")
    void update_success_sameRoleCode() {
        request.setRoleName("Đội bảo vệ ký túc xá");
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));
        when(roleRepository.save(any(Role.class))).thenReturn(customRole);
        when(roleMapper.toResponse(customRole)).thenReturn(response);

        Role_Response result = roleService.update("role-custom", request);

        assertThat(result).isNotNull();
        verify(roleMapper).updateEntityFromRequest(customRole, request);
        verify(roleRepository).save(customRole);
    }

    @Test
    @DisplayName("Cập nhật vai trò thất bại khi client cố tình thay đổi roleCode")
    void update_failed_whenChangingRoleCode() {
        request.setRoleCode("SECURITY_SUPERVISOR");
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));

        AppException ex = assertThrows(AppException.class, () -> roleService.update("role-custom", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không được phép thay đổi mã code vai trò");
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật vai trò ném lỗi ROLE_NOT_FOUND khi ID không tồn tại")
    void update_failed_notFound() {
        when(roleRepository.findById("role-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roleService.update("role-999", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    @DisplayName("Xóa vai trò tùy chỉnh thành công khi không có người dùng sử dụng")
    void delete_success_customRole() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));
        when(userRepository.existsByRole_RoleId("role-custom")).thenReturn(false);

        roleService.delete("role-custom");

        verify(rolePermissionRepository).deleteByRoleId("role-custom");
        verify(roleRepository).delete(customRole);
    }

    @Test
    @DisplayName("Xóa vai trò thất bại khi cố tình xóa vai trò hệ thống ADMIN")
    void delete_failed_systemRole_admin() {
        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(adminRole));

        AppException ex = assertThrows(AppException.class, () -> roleService.delete("role-admin"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không được phép xóa vai trò hệ thống cố định: ADMIN");
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa vai trò thất bại khi cố tình xóa vai trò hệ thống STUDENT")
    void delete_failed_systemRole_student() {
        when(roleRepository.findById("role-student")).thenReturn(Optional.of(studentRole));

        AppException ex = assertThrows(AppException.class, () -> roleService.delete("role-student"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không được phép xóa vai trò hệ thống cố định: STUDENT");
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa vai trò thất bại khi vai trò đang được gán cho tài khoản người dùng")
    void delete_failed_whenUsersExist() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));
        when(userRepository.existsByRole_RoleId("role-custom")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roleService.delete("role-custom"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể xóa vai trò đang có tài khoản người dùng sử dụng");
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa vai trò ném lỗi ROLE_NOT_FOUND khi ID không tồn tại")
    void delete_failed_notFound() {
        when(roleRepository.findById("role-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roleService.delete("role-999"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROLE_NOT_FOUND);
        verify(roleRepository, never()).delete(any());
    }
}

