package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.RoomAsset;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAsset_Repository extends JpaRepository<RoomAsset, String> {
    List<RoomAsset> findByRoom_RoomId(String roomId);

    boolean existsByRoom_RoomId(String roomId);

    List<RoomAsset> findByAsset_AssetId(String assetId);

    boolean existsByAsset_AssetId(String assetId);

    Optional<RoomAsset> findByRoom_RoomIdAndAsset_AssetId(String roomId, String assetId);

    boolean existsByRoom_RoomIdAndAsset_AssetId(String roomId, String assetId);

    boolean existsByRoom_RoomIdAndAsset_AssetIdAndRoomAssetIdNot(String roomId, String assetId, String roomAssetId);

    // Tìm kiếm và lọc danh sách tài sản phòng theo tòa nhà, phòng và tài sản có phân trang
    @Query("""
            SELECT ra FROM RoomAsset ra
            LEFT JOIN ra.room r
            LEFT JOIN r.floor f
            LEFT JOIN f.building bg
            LEFT JOIN ra.asset a
            WHERE (:buildingId IS NULL OR :buildingId = '' OR bg.buildingId = :buildingId)
              AND (:roomId IS NULL OR :roomId = '' OR r.roomId = :roomId)
              AND (:assetId IS NULL OR :assetId = '' OR a.assetId = :assetId)
            """)
    Page<RoomAsset> searchRoomAssets(
            @Param("buildingId") String buildingId,
            @Param("roomId") String roomId,
            @Param("assetId") String assetId,
            Pageable pageable
    );
}
