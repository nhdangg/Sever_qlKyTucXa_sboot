package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.VehicleRegistration;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface VehicleRegistration_Repository extends JpaRepository<VehicleRegistration, String> {
        List<VehicleRegistration> findByStudent_StudentId(String studentId);

        List<VehicleRegistration> findByStudent_StudentIdAndStatus(String studentId, String status);

        List<VehicleRegistration> findByStatus(String status);

        List<VehicleRegistration> findByStatusAndExpiryDateBefore(String status, LocalDate date);

        List<VehicleRegistration> findByLicensePlate(String licensePlate);

        long countByStudent_StudentIdAndStatusIn(String studentId, Collection<String> statuses);

        // Lấy danh sách phương tiện gửi đang hoạt động của sinh viên trong kỳ tháng
        @Query("""
                SELECT v FROM VehicleRegistration v
                WHERE v.student.studentId = :studentId
                  AND v.status = 'ACTIVE'
                  AND v.registrationDate <= :monthEnd
                  AND (v.expiryDate IS NULL OR v.expiryDate >= :monthStart)
                """)
        List<VehicleRegistration> findActiveVehiclesForStudentInMonth(
                @Param("studentId") String studentId,
                @Param("monthStart") LocalDate monthStart,
                @Param("monthEnd") LocalDate monthEnd);

        // Tìm kiếm phương tiện gửi có phân trang theo biển số, thông tin sinh viên,
        // loại xe, trạng thái và sắp hết hạn
        @Query("""
                        SELECT v FROM VehicleRegistration v
                        LEFT JOIN v.student s
                        WHERE (:licensePlate IS NULL OR :licensePlate = ''
                               OR LOWER(REPLACE(REPLACE(REPLACE(v.licensePlate, ' ', ''), '.', ''), '-', ''))
                                  LIKE LOWER(CONCAT('%', :licensePlate, '%')))
                          AND (:keyword IS NULL OR :keyword = ''
                               OR LOWER(COALESCE(s.studentCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                               OR LOWER(COALESCE(s.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
                          AND (:vehicleType IS NULL OR :vehicleType = '' OR UPPER(v.vehicleType) = UPPER(:vehicleType))
                          AND (:status IS NULL OR :status = '' OR UPPER(v.status) = UPPER(:status))
                          AND (:expiringBefore IS NULL OR (v.expiryDate IS NOT NULL AND v.expiryDate <= :expiringBefore AND v.status = 'ACTIVE'))
                        """)
        Page<VehicleRegistration> searchVehicles(
                        @Param("licensePlate") String licensePlate,
                        @Param("keyword") String keyword,
                        @Param("vehicleType") String vehicleType,
                        @Param("status") String status,
                        @Param("expiringBefore") LocalDate expiringBefore,
                        Pageable pageable);
}
