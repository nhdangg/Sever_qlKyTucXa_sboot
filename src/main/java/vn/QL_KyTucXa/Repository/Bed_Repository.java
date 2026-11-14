package vn.QL_KyTucXa.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Bed;

import java.util.List;
import java.util.Optional;

@Repository
public interface Bed_Repository extends JpaRepository<Bed, String> {
    List<Bed> findByRoom_RoomId(String roomId);

    long countByRoom_RoomId(String roomId);

    boolean existsByRoom_RoomId(String roomId);

    List<Bed> findByStatus(String status);

    Optional<Bed> findByRoom_RoomIdAndBedNumber(String roomId, String bedNumber);

    // Khóa bi quan (PESSIMISTIC_WRITE) để chống xung đột chọn cùng giường khi duyệt chuyển phòng
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.bedId = :bedId")
    Optional<Bed> findByIdForUpdate(@Param("bedId") String bedId);

    // Trong Bed_Repository.java:
    @Query("""
            SELECT b FROM Bed b
            JOIN FETCH b.room r
            JOIN FETCH r.floor f
            JOIN FETCH f.building bg
            WHERE r.roomId = :roomId
              AND b.status = :status
            ORDER BY b.bedNumber ASC
            """)
    List<Bed> findByRoom_RoomIdAndStatus(@Param("roomId") String roomId, @Param("status") String status);

    @Query("""
            SELECT b FROM Bed b
            JOIN FETCH b.room r
            JOIN FETCH r.floor f
            JOIN FETCH f.building bg
            WHERE bg.buildingId = :buildingId
              AND b.status = :status
            ORDER BY r.roomNumber ASC, b.bedNumber ASC
            """)
    List<Bed> findByBuilding_BuildingIdAndStatus(@Param("buildingId") String buildingId,
            @Param("status") String status);

    @Query("""
            SELECT COUNT(b) > 0 FROM Bed b
            JOIN b.room r
            JOIN r.floor f
            JOIN f.building bg
            WHERE bg.buildingId = :buildingId
              AND (b.status = 'OCCUPIED' OR b.status = 'RESERVED')
            """)
    boolean existsActiveBedsByBuildingId(@Param("buildingId") String buildingId);

    @Query("""
            SELECT COUNT(b) > 0 FROM Bed b
            JOIN b.room r
            JOIN r.floor f
            WHERE f.floorId = :floorId
              AND (b.status = 'OCCUPIED' OR b.status = 'RESERVED')
            """)
    boolean existsActiveBedsByFloorId(@Param("floorId") String floorId);

    @Query("""
            SELECT b FROM Bed b
            JOIN b.room r
            JOIN r.floor f
            JOIN f.building bg
            WHERE (:buildingId IS NULL OR bg.buildingId = :buildingId)
              AND (:roomId IS NULL OR r.roomId = :roomId)
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<Bed> searchBeds(
            @Param("buildingId") String buildingId,
            @Param("roomId") String roomId,
            @Param("status") String status,
            Pageable pageable
    );
}