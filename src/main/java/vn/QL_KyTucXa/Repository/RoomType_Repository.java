package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.RoomType;

import java.util.Optional;

@Repository
public interface RoomType_Repository extends JpaRepository<RoomType, String> {
    Optional<RoomType> findByTypeName(String typeName);

    boolean existsByTypeName(String typeName);

}