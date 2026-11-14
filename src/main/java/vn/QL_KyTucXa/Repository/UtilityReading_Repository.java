package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.UtilityReading;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilityReading_Repository extends JpaRepository<UtilityReading, String> {
    List<UtilityReading> findByRoom_RoomId(String roomId);
    boolean existsByRoom_RoomId(String roomId);
    boolean existsByServiceType_ServiceTypeId(String serviceTypeId);
    List<UtilityReading> findByReadingMonth(LocalDate readingMonth);
    List<UtilityReading> findByRoom_RoomIdAndReadingMonth(String roomId, LocalDate readingMonth);
    Optional<UtilityReading> findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
            String roomId, String serviceTypeId, LocalDate readingMonth);

    // Tìm bản ghi chỉ số gần nhất trước tháng ghi nhận để lấy chỉ số cuối kỳ làm đầu kỳ
    Optional<UtilityReading> findTopByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonthLessThanOrderByReadingMonthDesc(
            String roomId, String serviceTypeId, LocalDate readingMonth);

    // Tìm kiếm chỉ số điện nước có phân trang theo tòa nhà, phòng, loại dịch vụ và tháng ghi
    @Query("""
            SELECT u FROM UtilityReading u
            LEFT JOIN u.room r
            LEFT JOIN r.floor f
            LEFT JOIN f.building b
            LEFT JOIN u.serviceType st
            WHERE (:buildingId IS NULL OR :buildingId = '' OR b.buildingId = :buildingId)
              AND (:roomId IS NULL OR :roomId = '' OR r.roomId = :roomId)
              AND (:serviceTypeId IS NULL OR :serviceTypeId = '' OR st.serviceTypeId = :serviceTypeId)
              AND (:readingMonth IS NULL OR u.readingMonth = :readingMonth)
            """)
    Page<UtilityReading> searchUtilityReadings(
            @Param("buildingId") String buildingId,
            @Param("roomId") String roomId,
            @Param("serviceTypeId") String serviceTypeId,
            @Param("readingMonth") LocalDate readingMonth,
            Pageable pageable
    );
}
