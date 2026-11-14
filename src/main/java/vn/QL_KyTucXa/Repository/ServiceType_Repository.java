package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.ServiceType;

import java.util.Optional;

@Repository
public interface ServiceType_Repository extends JpaRepository<ServiceType, String> {
    Optional<ServiceType> findByServiceCode(String serviceCode);
    boolean existsByServiceCode(String serviceCode);
}