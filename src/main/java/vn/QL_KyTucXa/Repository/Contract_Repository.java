package vn.QL_KyTucXa.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.Contract;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface Contract_Repository extends JpaRepository<Contract, String> {
    Optional<Contract> findByContractCode(String contractCode);
    boolean existsByContractCode(String contractCode);
    List<Contract> findByStudent_StudentId(String studentId);
    List<Contract> findByStudent_StudentIdAndStatus(String studentId, String status);
    boolean existsByStudent_StudentIdAndStatus(String studentId, String status);
    List<Contract> findByBed_BedId(String bedId);
    boolean existsByBed_BedId(String bedId);
    List<Contract> findByPeriod_PeriodId(String periodId);
    boolean existsByPeriod_PeriodId(String periodId);
    List<Contract> findByStatus(String status);
    List<Contract> findByStatusAndEndDateBefore(String status, LocalDate date);

    // Lấy danh sách hợp đồng có hiệu lực trong tháng của một phòng
    @Query("""
            SELECT c FROM Contract c
            JOIN c.bed b
            WHERE b.room.roomId = :roomId
              AND c.status IN ('ACTIVE', 'EXPIRED', 'TERMINATED')
              AND c.startDate <= :monthEnd
              AND c.endDate >= :monthStart
            """)
    List<Contract> findEffectiveContractsByRoomIdAndMonth(
            @Param("roomId") String roomId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd
    );

    // Đếm số hợp đồng đang có hiệu lực (ACTIVE) theo từng phòng để tối ưu hóa hiệu năng
    @Query("""
            SELECT b.room.roomId, COUNT(c)
            FROM Contract c
            JOIN c.bed b
            WHERE c.status = 'ACTIVE' AND b.room.roomId IN :ids
            GROUP BY b.room.roomId
            """)
    List<Object[]> countActiveByRoomIds(@Param("ids") Collection<String> ids);

    // Đếm số hợp đồng ACTIVE của một phòng cụ thể
    @Query("""
            SELECT COUNT(c)
            FROM Contract c
            JOIN c.bed b
            WHERE c.status = 'ACTIVE' AND b.room.roomId = :roomId
            """)
    long countActiveByRoomId(@Param("roomId") String roomId);

    // Lấy danh sách hợp đồng ACTIVE của một phòng
    @Query("""
            SELECT c
            FROM Contract c
            JOIN c.bed b
            WHERE c.status = 'ACTIVE' AND b.room.roomId = :roomId
            """)
    List<Contract> findActiveContractsByRoomId(@Param("roomId") String roomId);

    @Query("""
            SELECT c FROM Contract c
            JOIN FETCH c.student s
            JOIN FETCH s.user u
            JOIN c.bed b
            JOIN b.room r
            JOIN r.floor f
            JOIN f.building bg
            WHERE c.status = 'ACTIVE'
              AND bg.buildingId = :buildingId
              AND u.status = 'ACTIVE'
            """)
    List<Contract> findActiveContractsByBuildingId(@Param("buildingId") String buildingId);

    @Query("""
            SELECT c FROM Contract c
            JOIN FETCH c.student s
            JOIN FETCH s.user u
            JOIN c.bed b
            JOIN b.room r
            JOIN r.floor f
            WHERE c.status = 'ACTIVE'
              AND f.floorId = :floorId
              AND u.status = 'ACTIVE'
            """)
    List<Contract> findActiveContractsByFloorId(@Param("floorId") String floorId);

    @Query("""
            SELECT c FROM Contract c
            JOIN FETCH c.student s
            JOIN FETCH s.user u
            JOIN c.bed b
            WHERE c.status = 'ACTIVE'
              AND b.room.roomId = :roomId
              AND u.status = 'ACTIVE'
            """)
    List<Contract> findActiveContractsWithStudentAndUserByRoomId(@Param("roomId") String roomId);

    // Kiểm tra phòng có bất kỳ hợp đồng nào gắn với giường thuộc phòng hay không
    @Query("SELECT COUNT(c) > 0 FROM Contract c WHERE c.bed.room.roomId = :roomId")
    boolean existsByBed_Room_RoomId(@Param("roomId") String roomId);

    // Kiểm tra tòa nhà có hợp đồng ACTIVE với sinh viên thuộc danh sách giới tính chỉ định hay không
    @Query("""
            SELECT COUNT(c) > 0 FROM Contract c
            JOIN c.bed b
            JOIN b.room r
            JOIN r.floor f
            JOIN f.building bg
            JOIN c.student s
            WHERE bg.buildingId = :buildingId
              AND c.status = 'ACTIVE'
              AND UPPER(TRIM(s.gender)) IN :conflictingGenders
            """)
    boolean existsActiveContractsByBuildingIdAndStudentGenderIn(
            @Param("buildingId") String buildingId,
            @Param("conflictingGenders") Collection<String> conflictingGenders
    );

    @Query("""
            SELECT COUNT(c) > 0 FROM Contract c
            WHERE c.bed.bedId = :bedId
              AND c.status = 'ACTIVE'
              AND (:excludeContractId IS NULL OR c.contractId != :excludeContractId)
              AND (c.startDate <= :endDate AND c.endDate >= :startDate)
            """)
    boolean hasOverlappingActiveContract(
            @Param("bedId") String bedId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeContractId") String excludeContractId
    );

    @Query("""
            SELECT c FROM Contract c
            LEFT JOIN c.student s
            LEFT JOIN c.bed b
            LEFT JOIN b.room r
            LEFT JOIN r.floor f
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.studentCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(r.roomNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR c.status = :status)
              AND (:periodId IS NULL OR :periodId = '' OR c.period.periodId = :periodId)
              AND (:buildingId IS NULL OR :buildingId = '' OR f.building.buildingId = :buildingId)
              AND (:roomId IS NULL OR :roomId = '' OR r.roomId = :roomId)
            """)
    Page<Contract> searchContracts(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("periodId") String periodId,
            @Param("buildingId") String buildingId,
            @Param("roomId") String roomId,
            Pageable pageable
    );
}