package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Permission;

import java.util.List;
import java.util.Optional;

@Repository
public interface Permission_Repository extends JpaRepository<Permission, String> {
    Optional<Permission> findByPermissionCode(String permissionCode);
    List<Permission> findByModule(String module);
    boolean existsByPermissionCode(String permissionCode);
}