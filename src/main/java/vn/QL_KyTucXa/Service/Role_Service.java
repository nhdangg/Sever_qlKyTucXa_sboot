package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Role_Service {

    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            "ADMIN", "MANAGER", "STAFF", "STUDENT"
    );

    Role_Repository roleRepository;
    User_Repository userRepository;
    RolePermission_Repository rolePermissionRepository;
    Role_Mapper roleMapper;

    // Tạo mới vai trò hệ thống
    @Transactional
    public Role_Response create(Role_Request request) {
        log.info("Tạo mới vai trò: {}", request.getRoleCode());

        String roleCode = request.getRoleCode() != null ? request.getRoleCode().trim().toUpperCase() : null;
        if (roleCode == null || roleCode.isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã vai trò không được để trống");
        }

        if (roleRepository.existsByRoleCode(roleCode)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã vai trò '" + roleCode + "' đã tồn tại");
        }

        request.setRoleCode(roleCode);
        Role vai_tro = roleMapper.toEntity(request);
        vai_tro.setRoleCode(roleCode);
        vai_tro = roleRepository.save(vai_tro);

        return roleMapper.toResponse(vai_tro);
    }

    // Lấy chi tiết vai trò theo ID
    @Transactional(readOnly = true)
    public Role_Response getById(String roleId) {
        Role vai_tro = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        Role_Response ket_qua = roleMapper.toResponse(vai_tro);
        ket_qua.setPermissions(rolePermissionRepository.findPermissionCodesByRoleId(vai_tro.getRoleId()));
        return ket_qua;
    }

    // Lấy danh sách tất cả vai trò (kèm danh sách mã quyền từ bảng vaiTroQuyen)
    @Transactional(readOnly = true)
    public List<Role_Response> getAll() {
        List<Role> danh_sach_vai_tro = roleRepository.findAll();
        return danh_sach_vai_tro.stream().map(vai_tro -> {
            Role_Response ket_qua = roleMapper.toResponse(vai_tro);
            ket_qua.setPermissions(rolePermissionRepository.findPermissionCodesByRoleId(vai_tro.getRoleId()));
            return ket_qua;
        }).toList();
    }

    // Cập nhật thông tin vai trò (không cho phép thay đổi roleCode)
    @Transactional
    public Role_Response update(String roleId, Role_Request request) {
        Role vai_tro = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        // Chặn đổi mã vai trò để bảo vệ tính nhất quán của hệ thống phân quyền và các quy tắc nghiệp vụ
        if (request.getRoleCode() != null &&
                !vai_tro.getRoleCode().equalsIgnoreCase(request.getRoleCode().trim())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không được phép thay đổi mã code vai trò (roleCode) vì ảnh hưởng đến các quy tắc bảo mật hệ thống");
        }

        String originalRoleCode = vai_tro.getRoleCode();
        roleMapper.updateEntityFromRequest(vai_tro, request);
        vai_tro.setRoleCode(originalRoleCode);
        vai_tro = roleRepository.save(vai_tro);

        return roleMapper.toResponse(vai_tro);
    }

    // Xóa vai trò: chặn xóa vai trò hệ thống, chặn xóa khi còn người dùng
    @Transactional
    public void delete(String roleId) {
        Role vai_tro = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        // Chặn xóa các vai trò hệ thống cố định
        if (SYSTEM_ROLE_CODES.contains(vai_tro.getRoleCode().toUpperCase())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không được phép xóa vai trò hệ thống cố định: " + vai_tro.getRoleCode());
        }

        // Chặn xóa vai trò nếu đang có tài khoản người dùng gắn với vai trò này
        if (userRepository.existsByRole_RoleId(roleId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa vai trò đang có tài khoản người dùng sử dụng");
        }

        // Dọn dẹp phân quyền của vai trò trong bảng vaiTroQuyen trước khi xóa
        rolePermissionRepository.deleteByRoleId(roleId);

        roleRepository.delete(vai_tro);
        log.info("Đã xóa vai trò: {} ({})", vai_tro.getRoleName(), vai_tro.getRoleCode());
    }
}

