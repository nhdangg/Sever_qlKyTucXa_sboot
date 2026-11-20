package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Bed_Request;
import vn.QL_KyTucXa.DTO.Response.Bed_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Bed;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Bed_Mapper;
import vn.QL_KyTucXa.Repository.Bed_Repository;
import vn.QL_KyTucXa.Repository.Building_Repository;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.util.List;
import java.util.Set;

// Service quản lý thông tin giường ngủ, trạng thái hoạt động và sức chứa phòng
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Bed_Service {

    Bed_Repository bedRepository;
    Room_Repository roomRepository;
    Building_Repository buildingRepository;
    Contract_Repository contractRepository;
    Bed_Mapper bedMapper;

    static Set<String> ALLOWED_MANUAL_STATUSES = Set.of("AVAILABLE", "MAINTENANCE");
    static Set<String> ALLOWED_SORT_FIELDS = Set.of("bedNumber", "status", "bedId");

    // Tạo mới giường: kiểm tra trùng mã và giới hạn theo sức chứa capacity của
    // phòng
    @Transactional
    public Bed_Response create(Bed_Request request) {
        log.info("Tạo mới giường số: {} trong phòng ID: {}", request.getBedNumber(), request.getRoomId());

        Room phong = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if (phong.getCapacity() != null && bedRepository.countByRoom_RoomId(request.getRoomId()) >= phong.getCapacity()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Phòng " + phong.getRoomNumber()
                    + " đã đạt sức chứa tối đa (" + phong.getCapacity() + " giường), không thể tạo thêm");
        }

        if (bedRepository.findByRoom_RoomIdAndBedNumber(request.getRoomId(), request.getBedNumber()).isPresent()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường số " + request.getBedNumber() + " đã tồn tại trong phòng này");
        }

        Bed giuong = bedMapper.toEntity(request);
        giuong.setRoom(phong);
        if (giuong.getStatus() == null || !ALLOWED_MANUAL_STATUSES.contains(giuong.getStatus().toUpperCase())) {
            giuong.setStatus("AVAILABLE");
        }
        giuong = bedRepository.save(giuong);
        syncRoomStatus(phong);

        return bedMapper.toResponse(giuong);
    }

    // Lấy thông tin chi tiết một giường theo ID
    @Transactional(readOnly = true)
    public Bed_Response getById(String bedId) {
        Bed giuong = bedRepository.findById(bedId)
                .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));

        return bedMapper.toResponse(giuong);
    }

    // Lấy danh sách tất cả giường thuộc một phòng
    @Transactional(readOnly = true)
    public List<Bed_Response> getByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        List<Bed> danh_sach_giuong = bedRepository.findByRoom_RoomId(roomId);
        return bedMapper.toResponseList(danh_sach_giuong);
    }

    // Lấy danh sách theo giường còn trống
    @Transactional(readOnly = true)
    public List<Bed_Response> getBedsByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        List<Bed> danh_sach_giuong_trong = bedRepository.findByRoom_RoomIdAndStatus(roomId, "AVAILABLE");
        return bedMapper.toResponseList(danh_sach_giuong_trong);
    }

    // Lấy danh sách giường trống theo tòa nhà
    @Transactional(readOnly = true)
    public List<Bed_Response> getBedsByBuildingId(String buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new AppException(ErrorCode.BUILDING_NOT_FOUND);
        }

        List<Bed> danh_sach_giuong_trong = bedRepository.findByBuilding_BuildingIdAndStatus(buildingId, "AVAILABLE");
        return bedMapper.toResponseList(danh_sach_giuong_trong);
    }

    // Lấy danh sách toàn bộ giường trong hệ thống
    @Transactional(readOnly = true)
    public List<Bed_Response> getAll() {
        List<Bed> danh_sach_giuong = bedRepository.findAll();
        return bedMapper.toResponseList(danh_sach_giuong);
    }

    // Tìm kiếm và phân trang giường (hỗ trợ lọc giường trống cho form hợp đồng)
    @Transactional(readOnly = true)
    public Page_Response<Bed_Response> searchBeds(
            String buildingId,
            String roomId,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "bedNumber";
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).descending()
                : Sort.by(safeSortBy).ascending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String cleanBuildingId = (buildingId != null && !buildingId.isBlank()) ? buildingId.trim() : null;
        String cleanRoomId = (roomId != null && !roomId.isBlank()) ? roomId.trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<Bed> pageResult = bedRepository.searchBeds(cleanBuildingId, cleanRoomId, cleanStatus, pageable);
        List<Bed_Response> data = bedMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Bed_Response>builder()
                .currentPage(pageResult.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasPrevious(pageResult.hasPrevious())
                .hasNext(pageResult.hasNext())
                .isFirst(pageResult.isFirst())
                .isLast(pageResult.isLast())
                .data(data)
                .build();
    }

    // Cập nhật thông tin giường: kiểm tra mã giường và trạng thái hợp lệ
    @Transactional
    public Bed_Response update(String bedId, Bed_Request request) {
        Bed giuong = bedRepository.findById(bedId)
                .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));

        if (!giuong.getBedNumber().equalsIgnoreCase(request.getBedNumber()) &&
                bedRepository.findByRoom_RoomIdAndBedNumber(giuong.getRoom().getRoomId(), request.getBedNumber())
                        .isPresent()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường số " + request.getBedNumber() + " đã tồn tại trong phòng này");
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            validateManualStatusChange(giuong, request.getStatus().trim().toUpperCase());
        }

        bedMapper.updateEntityFromRequest(giuong, request);
        giuong = bedRepository.save(giuong);
        syncRoomStatus(giuong.getRoom());

        return bedMapper.toResponse(giuong);
    }

    // Cập nhật trạng thái giường: chỉ cho phép AVAILABLE hoặc MAINTENANCE
    @Transactional
    public Bed_Response updateStatus(String bedId, String status) {
        if (status == null || status.isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Trạng thái giường không được để trống");
        }

        String newStatus = status.trim().toUpperCase();
        if ("OCCUPIED".equalsIgnoreCase(newStatus)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể gán trạng thái OCCUPIED thủ công. Trạng thái này do hợp đồng tự động quản lý");
        }
        if (!ALLOWED_MANUAL_STATUSES.contains(newStatus)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái giường không hợp lệ. Chỉ chấp nhận AVAILABLE hoặc MAINTENANCE");
        }

        Bed giuong = bedRepository.findById(bedId)
                .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));

        if ("OCCUPIED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đang có người ở (OCCUPIED). Vui lòng cập nhật hoặc thanh lý hợp đồng trước");
        }
        if ("RESERVED".equalsIgnoreCase(giuong.getStatus()) && !newStatus.equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đang được giữ chỗ (RESERVED) cho đơn đăng ký chờ xét duyệt. Vui lòng phê duyệt hoặc từ chối đơn trước khi đổi trạng thái");
        }

        giuong.setStatus(newStatus);
        giuong = bedRepository.save(giuong);
        syncRoomStatus(giuong.getRoom());
        return bedMapper.toResponse(giuong);
    }

    // Xóa giường: kiểm tra giường có người ở hoặc đã có dữ liệu hợp đồng ràng buộc
    @Transactional
    public void delete(String bedId) {
        Bed giuong = bedRepository.findById(bedId)
                .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));

        if ("OCCUPIED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa giường đang có người ở (OCCUPIED)");
        }
        if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa giường đang được giữ chỗ (RESERVED) cho đơn chờ duyệt");
        }

        if (contractRepository.existsByBed_BedId(bedId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa giường đã có dữ liệu hợp đồng lưu trú. Vui lòng chuyển trạng thái sang MAINTENANCE");
        }

        Room phong = giuong.getRoom();
        bedRepository.delete(giuong);
        syncRoomStatus(phong);
    }

    // Tự động đồng bộ trạng thái phòng dựa trên trạng thái các giường
    private void syncRoomStatus(Room room) {
        if (room == null || room.getRoomId() == null)
            return;
        if ("CLOSED".equalsIgnoreCase(room.getStatus())) {
            return;
        }

        List<Bed> beds = bedRepository.findByRoom_RoomId(room.getRoomId());
        if (beds == null || beds.isEmpty()) {
            return;
        }

        long maintenanceCount = beds.stream().filter(b -> "MAINTENANCE".equalsIgnoreCase(b.getStatus())).count();
        long availableCount = beds.stream().filter(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus())).count();
        long occupiedCount = beds.stream()
                .filter(b -> "OCCUPIED".equalsIgnoreCase(b.getStatus()) || "RESERVED".equalsIgnoreCase(b.getStatus()))
                .count();

        if (maintenanceCount == beds.size()) {
            room.setStatus("MAINTENANCE");
        } else if (availableCount == 0 && occupiedCount > 0) {
            room.setStatus("FULL");
        } else {
            room.setStatus("AVAILABLE");
        }
        roomRepository.save(room);
    }

    // Kiểm tra tính hợp lệ khi cập nhật trạng thái giường thủ công
    private void validateManualStatusChange(Bed bed, String newStatus) {
        if ("OCCUPIED".equalsIgnoreCase(newStatus)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể gán trạng thái OCCUPIED thủ công. Trạng thái này do hợp đồng tự động quản lý");
        }
        if (!ALLOWED_MANUAL_STATUSES.contains(newStatus)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái giường không hợp lệ. Chỉ chấp nhận AVAILABLE hoặc MAINTENANCE");
        }
        if ("OCCUPIED".equalsIgnoreCase(bed.getStatus()) && !newStatus.equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đang có người ở (OCCUPIED). Vui lòng cập nhật hoặc thanh lý hợp đồng trước");
        }
        if ("RESERVED".equalsIgnoreCase(bed.getStatus()) && !newStatus.equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đang được giữ chỗ (RESERVED) cho đơn đăng ký chờ xét duyệt. Vui lòng phê duyệt hoặc từ chối đơn trước khi đổi trạng thái");
        }
    }
}
