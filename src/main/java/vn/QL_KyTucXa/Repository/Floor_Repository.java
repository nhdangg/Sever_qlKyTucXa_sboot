package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Floor;

import java.util.List;
import java.util.Optional;

@Repository
public interface Floor_Repository extends JpaRepository<Floor, String> {
    List<Floor> findByBuilding_BuildingId(String buildingId);
    Optional<Floor> findByBuilding_BuildingIdAndFloorNumber(String buildingId, Integer floorNumber);
    boolean existsByBuilding_BuildingId(String buildingId);
}
