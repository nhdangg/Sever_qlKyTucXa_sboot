package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Permission_Request;
import vn.QL_KyTucXa.DTO.Response.Permission_Response;
import vn.QL_KyTucXa.Entity.Permission;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Permission_Mapper;
import vn.QL_KyTucXa.Repository.Permission_Repository;
import vn.QL_KyTucXa.Repository.RolePermission_Repository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Permission_Service {

    Permission_Repository permissionRepository;
    RolePermission_Repository rolePermissionRepository;
    Permission_Mapper permissionMapper;

    // Tạo mới quyền hạn hệ thống với mã quyền viết hoa chuẩn hóa
    @Transactional
    public Permission_Response create(Permission_Request request) {
        log.info("Tạo mới quyền hạn: {}", request.getPermissionCode());

        String code = request.getPermissionCode() != null ? request.getPermissionCode().trim().toUpperCase() : null;
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã quyền hạn không được để trống");
        }

        if (permissionRepository.existsByPermissionCode(code)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã quyền '" + code + "' đã tồn tại");
        }

        request.setPermissionCode(code);
        Permission permission = permissionMapper.toEntity(request);
        permission.setPermissionCode(code);
        permission = permissionRepository.save(permission);

        return permissionMapper.toResponse(permission);
    }

    // Lấy chi tiết quyền hạn theo mã ID
    @Transactional(readOnly = true)
    public Permission_Response getById(String permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quyền hạn"));

        return permissionMapper.toResponse(permission);
    }

    // Lấy danh sách quyền hạn theo phân hệ chức năng
    @Transactional(readOnly = true)
    public List<Permission_Response> getByModule(String module) {
        List<Permission> list = permissionRepository.findByModule(module);
        return permissionMapper.toResponseList(list);
    }

    // Lấy tất cả danh mục quyền hạn hệ thống
    @Transactional(readOnly = true)
    public List<Permission_Response> getAll() {
        List<Permission> list = permissionRepository.findAll();
        return permissionMapper.toResponseList(list);
    }

    // Cập nhật thông tin quyền hạn không cho phép đổi permissionCode vì đã gắn với @PreAuthorize và JWT token
    @Transactional
    public Permission_Response update(String permissionId, Permission_Request request) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quyền hạn"));

        // Chặn đổi mã quyền vì permissionCode được tham chiếu trong mã nguồn @PreAuthorize và JWT scope đã phát hành
        if (request.getPermissionCode() != null &&
                !permission.getPermissionCode().equalsIgnoreCase(request.getPermissionCode().trim())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không được phép thay đổi mã quyền hạn (permissionCode) vì đang được sử dụng trong hệ thống phân quyền");
        }

        String immutableCode = permission.getPermissionCode();
        permissionMapper.updateEntityFromRequest(permission, request);
        permission.setPermissionCode(immutableCode);
        permission = permissionRepository.save(permission);

        return permissionMapper.toResponse(permission);
    }

    // Xóa quyền hạn có kiểm tra ràng buộc vai trò để tránh lỗi khóa ngoại FK
    @Transactional
    public void delete(String permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quyền hạn"));

        // Kiểm tra quyền hạn đã được gán cho vai trò nào chưa
        if (rolePermissionRepository.existsByPermissionId(permissionId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa quyền hạn đang được gán cho vai trò trong hệ thống");
        }

        permissionRepository.delete(permission);
    }
}
