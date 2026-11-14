package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Asset;

import java.util.List;
import java.util.Optional;

@Repository
public interface Asset_Repository extends JpaRepository<Asset, String> {
    Optional<Asset> findByAssetCode(String assetCode);

    boolean existsByAssetCode(String assetCode);

    List<Asset> findByStatus(String status);

    List<Asset> findByAssetCodeContainingIgnoreCase(String assetCode);

    List<Asset> findByCategoryContainingIgnoreCase(String category);

    List<Asset> findByAssetNameContainingIgnoreCase(String assetname);
}