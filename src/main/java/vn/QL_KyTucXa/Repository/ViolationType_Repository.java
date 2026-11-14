package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.ViolationType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ViolationType_Repository extends JpaRepository<ViolationType, String> {
    List<ViolationType> findBySeverityLevel(String severityLevel);
    Optional<ViolationType> findByViolationName(String violationName);
    boolean existsByViolationName(String violationName);
    boolean existsByViolationNameAndViolationTypeIdNot(String violationName, String violationTypeId);
}
