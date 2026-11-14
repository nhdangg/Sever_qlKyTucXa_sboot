package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.Permission_Request;
import vn.QL_KyTucXa.DTO.Response.Permission_Response;
import vn.QL_KyTucXa.Entity.Permission;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Permission_Mapper;
import vn.QL_KyTucXa.Repository.Permission_Repository;
import vn.QL_KyTucXa.Repository.RolePermission_Repository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Permission_ServiceTest {

    @Mock
    private Permission_Repository permissionRepository;

    @Mock
    private RolePermission_Repository rolePermissionRepository;

    @Mock
    private Permission_Mapper permissionMapper;

    @InjectMocks
    private Permission_Service permissionService;

    private Permission permission;
    private Permission_Request request;
    private Permission_Response response;

    @BeforeEach
    void setUp() {
        permission = Permission.builder()
                .permissionId("perm-01")
                .permissionCode("USER_VIEW")
                .module("USER")
                .description("Xem người dùng")
                .build();

        request = Permission_Request.builder()
                .permissionCode("USER_VIEW")
                .module("USER")
                .description("Xem người dùng")
                .build();

        response = Permission_Response.builder()
                .permissionId("perm-01")
                .permissionCode("USER_VIEW")
                .module("USER")
                .description("Xem người dùng")
                .build();
    }

    @Test
    @DisplayName("Tạo quyền hạn mới thành công và chuẩn hóa mã viết hoa")
    void create_success() {
        request.setPermissionCode("user_view");
        when(permissionRepository.existsByPermissionCode("USER_VIEW")).thenReturn(false);
        when(permissionMapper.toEntity(any(Permission_Request.class))).thenReturn(permission);
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);
        when(permissionMapper.toResponse(permission)).thenReturn(response);

        Permission_Response result = permissionService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getPermissionCode()).isEqualTo("USER_VIEW");
        verify(permissionRepository).save(any(Permission.class));
    }

    @Test
    @DisplayName("Tạo quyền hạn thất bại khi mã quyền đã tồn tại")
    void create_failed_duplicateCode() {
        when(permissionRepository.existsByPermissionCode("USER_VIEW")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> permissionService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(permissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo quyền hạn thất bại khi mã quyền để trống")
    void create_failed_blankCode() {
        request.setPermissionCode("   ");

        AppException ex = assertThrows(AppException.class, () -> permissionService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(permissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy chi tiết quyền hạn theo ID thành công")
    void getById_success() {
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.of(permission));
        when(permissionMapper.toResponse(permission)).thenReturn(response);

        Permission_Response result = permissionService.getById("perm-01");

        assertThat(result).isNotNull();
        assertThat(result.getPermissionId()).isEqualTo("perm-01");
    }

    @Test
    @DisplayName("Lấy chi tiết quyền hạn ném lỗi khi ID không tồn tại")
    void getById_notFound() {
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> permissionService.getById("perm-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách quyền hạn theo phân hệ module")
    void getByModule_success() {
        when(permissionRepository.findByModule("USER")).thenReturn(List.of(permission));
        when(permissionMapper.toResponseList(List.of(permission))).thenReturn(List.of(response));

        List<Permission_Response> result = permissionService.getByModule("USER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModule()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Lấy tất cả danh mục quyền hạn hệ thống")
    void getAll_success() {
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(permissionMapper.toResponseList(List.of(permission))).thenReturn(List.of(response));

        List<Permission_Response> result = permissionService.getAll();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Cập nhật thông tin quyền hạn thành công khi giữ nguyên permissionCode")
    void update_success_sameCode() {
        request.setDescription("Xem thông tin người dùng hệ thống");
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.of(permission));
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);
        when(permissionMapper.toResponse(permission)).thenReturn(response);

        Permission_Response result = permissionService.update("perm-01", request);

        assertThat(result).isNotNull();
        verify(permissionMapper).updateEntityFromRequest(permission, request);
        verify(permissionRepository).save(permission);
    }

    @Test
    @DisplayName("Cập nhật quyền hạn thất bại khi client cố ý thay đổi permissionCode")
    void update_failed_whenChangingPermissionCode() {
        request.setPermissionCode("USER_VIEW_CHANGED");
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.of(permission));

        AppException ex = assertThrows(AppException.class, () -> permissionService.update("perm-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không được phép thay đổi mã quyền hạn");
        verify(permissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật quyền hạn ném lỗi khi không tìm thấy ID")
    void update_failed_notFound() {
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> permissionService.update("perm-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Xóa quyền hạn thành công khi chưa được gán cho vai trò nào")
    void delete_success() {
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.existsByPermissionId("perm-01")).thenReturn(false);

        permissionService.delete("perm-01");

        verify(permissionRepository).delete(permission);
    }

    @Test
    @DisplayName("Xóa quyền hạn thất bại khi quyền đang được gán cho vai trò (chặn lỗi FK)")
    void delete_failed_whenAssignedToRole() {
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.existsByPermissionId("perm-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> permissionService.delete("perm-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể xóa quyền hạn đang được gán cho vai trò");
        verify(permissionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa quyền hạn ném lỗi khi không tìm thấy ID")
    void delete_failed_notFound() {
        when(permissionRepository.findById("perm-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> permissionService.delete("perm-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(permissionRepository, never()).delete(any());
    }
}

