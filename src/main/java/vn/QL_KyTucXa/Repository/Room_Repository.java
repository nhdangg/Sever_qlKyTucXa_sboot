package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Room;

import java.util.List;
import java.util.Optional;

@Repository
public interface Room_Repository extends JpaRepository<Room, String> {
    List<Room> findByFloor_FloorId(String floorId);

    List<Room> findByRoomType_RoomTypeId(String roomTypeId);

    List<Room> findByStatus(String status);

    Optional<Room> findByFloor_FloorIdAndRoomNumber(String floorId, String roomNumber);

    boolean existsByFloor_FloorIdAndRoomNumberIgnoreCase(String floorId, String roomNumber);

    boolean existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot(String floorId, String roomNumber, String roomId);

    boolean existsByFloor_Building_BuildingIdAndStatus(String buildingId, String status);

    boolean existsByFloor_FloorId(String floorId);

    boolean existsByFloor_FloorIdAndStatus(String floorId, String status);

    boolean existsByRoomType_RoomTypeId(String roomTypeId);

    // Tìm kiếm phòng theo số phòng tòa tầng loại phòng trạng thái và tình trạng còn
    // giường trống
    @Query("""
            SELECT r FROM Room r
            LEFT JOIN r.floor f
            LEFT JOIN f.building bg
            LEFT JOIN r.roomType rt
            WHERE (:roomNumber IS NULL OR :roomNumber = '' OR LOWER(r.roomNumber) LIKE LOWER(CONCAT('%', :roomNumber, '%')))
              AND (:buildingId IS NULL OR :buildingId = '' OR bg.buildingId = :buildingId)
              AND (:floorId IS NULL OR :floorId = '' OR f.floorId = :floorId)
              AND (:roomTypeId IS NULL OR :roomTypeId = '' OR rt.roomTypeId = :roomTypeId)
              AND (:status IS NULL OR :status = '' OR r.status = :status)
              AND (:hasAvailableBeds IS NULL
                   OR (:hasAvailableBeds = true AND EXISTS (
                       SELECT 1 FROM Bed b WHERE b.room = r AND b.status = 'AVAILABLE'
                   ))
                   OR (:hasAvailableBeds = false AND NOT EXISTS (
                       SELECT 1 FROM Bed b WHERE b.room = r AND b.status = 'AVAILABLE'
                   )))
            """)
    Page<Room> searchRooms(
            @Param("roomNumber") String roomNumber,
            @Param("buildingId") String buildingId,
            @Param("floorId") String floorId,
            @Param("roomTypeId") String roomTypeId,
            @Param("status") String status,
            @Param("hasAvailableBeds") Boolean hasAvailableBeds,
            Pageable pageable);
}
