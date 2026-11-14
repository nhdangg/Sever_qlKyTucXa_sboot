package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Building;

import java.util.List;
import java.util.Optional;

@Repository
public interface Building_Repository extends JpaRepository<Building, String> {

    Optional<Building> findByBuildingCode(String buildingCode);

    boolean existsByBuildingCode(String buildingCode);

    List<Building> findByStatus(String status);

    /**
     * Tìm kiếm và phân trang theo từ khóa (mã tòa hoặc tên tòa)
     */
    @Query("SELECT b FROM Building b WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(b.buildingCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Building> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}