package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface User_Repository extends JpaRepository<User, String> {
    @Override
    @EntityGraph(attributePaths = {"role"})
    Optional<User> findById(String userId);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByRole_RoleId(String roleId);
    boolean existsByRole_RoleId(String roleId);
    List<User> findByStatus(String status);

    long countByRole_RoleCodeAndStatus(String roleCode, String status);
    long countByRole_RoleCode(String roleCode);

    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND u.role IS NOT NULL AND u.role.roleCode IN ('ADMIN', 'STAFF', 'MANAGER')")
    List<User> findActiveStaffAndAdmins();

    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND u.role IS NOT NULL AND u.role.roleCode = 'STUDENT'")
    List<User> findActiveStudents();

    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.phone LIKE CONCAT('%', :keyword, '%')) AND " +
            "(:roleId IS NULL OR :roleId = '' OR u.role.roleId = :roleId) AND " +
            "(:status IS NULL OR :status = '' OR u.status = :status)")
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("roleId") String roleId,
            @Param("status") String status,
            Pageable pageable);
}