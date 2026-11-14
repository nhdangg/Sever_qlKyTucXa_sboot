package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.RolePermission;
import vn.QL_KyTucXa.Entity.RolePermissionId;

import java.util.List;

@Repository
public interface RolePermission_Repository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByRoleId(String roleId);
    List<RolePermission> findByPermissionId(String permissionId);
    boolean existsByPermissionId(String permissionId);
    void deleteByRoleId(String roleId);

    /** Lấy danh sách mã quyền (permissionCode) của một vai trò, dùng để nhúng vào JWT scope. */
    @Query("select p.permissionCode from RolePermission rp join rp.permission p where rp.roleId = :roleId")
    List<String> findPermissionCodesByRoleId(@Param("roleId") String roleId);
}