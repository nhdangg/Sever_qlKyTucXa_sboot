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
import vn.QL_KyTucXa.DTO.Request.Room_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.Entity.Bed;
import vn.QL_KyTucXa.Entity.Floor;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.RoomType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Room_Mapper;
import vn.QL_KyTucXa.Repository.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Room_Service {

    Room_Repository roomRepository;
    Floor_Repository floorRepository;
    RoomType_Repository roomTypeRepository;
    Bed_Repository bedRepository;
    Contract_Repository contractRepository;
    RoomAsset_Repository roomAssetRepository;
    UtilityReading_Repository utilityReadingRepository;
    MaintenanceRequest_Repository maintenanceRequestRepository;
    Room_Mapper roomMapper;

    static Set<String> ALLOWED_STATUSES = Set.of("AVAILABLE", "FULL", "MAINTENANCE", "CLOSED");
    static Set<String> ALLOWED_SORT_FIELDS = Set.of("roomNumber", "capacity", "status", "createdAt", "updatedAt");

    // Bổ sung số lượng người đang ở (currentOccupancy) từ các hợp đồng ACTIVE bằng
    // một câu truy vấn gộp
    private List<Room_Response> enrichOccupancy(List<Room_Response> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }

        List<String> roomIds = responses.stream()
                .map(Room_Response::getRoomId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (roomIds.isEmpty()) {
            return responses;
        }

        List<Object[]> activeContractCounts = contractRepository.countActiveByRoomIds(roomIds);
        Map<String, Long> activeCountMap = activeContractCounts.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        for (Room_Response r : responses) {
            r.setCurrentOccupancy(activeCountMap.getOrDefault(r.getRoomId(), 0L).intValue());
        }
        return responses;
    }

    // Tạo mới phòng: kiểm tra tầng, loại phòng và tránh trùng lặp số phòng trên
    // cùng tầng
    @Transactional
    public Room_Response create(Room_Request request) {
        log.info("Tạo mới phòng số: {} tại tầng ID: {}", request.getRoomNumber(), request.getFloorId());

        String initialStatus = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().trim().toUpperCase()
                : "AVAILABLE";
        if (!ALLOWED_STATUSES.contains(initialStatus)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái phòng không hợp lệ. Cho phép: " + String.join(", ", ALLOWED_STATUSES));
        }

        Floor floor = floorRepository.findById(request.getFloorId())
                .orElseThrow(() -> new AppException(ErrorCode.FLOOR_NOT_FOUND));

        RoomType roomType = roomTypeRepository.findById(request.getRoomTypeId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại phòng"));

        String trimmedRoomNumber = request.getRoomNumber().trim();
        if (roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCase(request.getFloorId(), trimmedRoomNumber)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phòng số " + trimmedRoomNumber + " đã tồn tại ở tầng này");
        }

        Room room = roomMapper.toEntity(request);
        room.setFloor(floor);
        room.setRoomType(roomType);
        room.setRoomNumber(trimmedRoomNumber);

        if (room.getCapacity() == null || room.getCapacity() <= 0) {
            room.setCapacity(roomType.getCapacity() != null ? roomType.getCapacity() : 4);
        } else if (roomType.getCapacity() != null && room.getCapacity() > roomType.getCapacity()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sức chứa phòng (" + room.getCapacity() + ") không được vượt quá sức chứa tối đa của loại phòng (" + roomType.getCapacity() + ")");
        }
        room.setStatus(initialStatus);

        room = roomRepository.save(room);

        // Tự động khởi tạo các giường mặc định (G01, G02, ...) theo sức chứa của phòng
        int capacity = room.getCapacity() != null ? room.getCapacity() : 0;
        for (int i = 1; i <= capacity; i++) {
            String bedNumber = String.format("G%02d", i);
            Bed bed = Bed.builder()
                    .room(room)
                    .bedNumber(bedNumber)
                    .status("AVAILABLE")
                    .build();
            bedRepository.save(bed);
        }

        return enrichOccupancy(List.of(roomMapper.toResponse(room))).get(0);
    }

    // Lấy thông tin chi tiết một phòng theo ID kèm số người đang ở
    @Transactional(readOnly = true)
    public Room_Response getById(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        return enrichOccupancy(List.of(roomMapper.toResponse(room))).get(0);
    }

    // Lấy danh sách phòng theo ID tầng
    @Transactional(readOnly = true)
    public List<Room_Response> getByFloorId(String floorId) {
        if (!floorRepository.existsById(floorId)) {
            throw new AppException(ErrorCode.FLOOR_NOT_FOUND);
        }

        List<Room> rooms = roomRepository.findByFloor_FloorId(floorId);
        return enrichOccupancy(roomMapper.toResponseList(rooms));
    }

    @Transactional(readOnly = true)
    public List<Room_Response> getAll() {
        List<Room> rooms = roomRepository.findAll();
        return enrichOccupancy(roomMapper.toResponseList(rooms));
    }

    // Lấy danh sách phòng có phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<Room_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, null, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và lọc danh sách phòng theo nhiều tiêu chí kết hợp phân trang
    @Transactional(readOnly = true)
    public Page_Response<Room_Response> getAll(
            int page,
            int size,
            String roomNumber,
            String buildingId,
            String floorId,
            String roomTypeId,
            String status,
            Boolean hasAvailableBeds,
            String sortBy,
            String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "roomNumber";
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).descending()
                : Sort.by(safeSortBy).ascending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String cleanRoomNumber = (roomNumber != null && !roomNumber.isBlank()) ? roomNumber.trim() : null;
        String cleanBuildingId = (buildingId != null && !buildingId.isBlank()) ? buildingId.trim() : null;
        String cleanFloorId = (floorId != null && !floorId.isBlank()) ? floorId.trim() : null;
        String cleanRoomTypeId = (roomTypeId != null && !roomTypeId.isBlank()) ? roomTypeId.trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<Room> pageResult = roomRepository.searchRooms(
                cleanRoomNumber,
                cleanBuildingId,
                cleanFloorId,
                cleanRoomTypeId,
                cleanStatus,
                hasAvailableBeds,
                pageable);

        List<Room_Response> data = enrichOccupancy(roomMapper.toResponseList(pageResult.getContent()));

        return Page_Response.<Room_Response>builder()
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

    // Cập nhật thông tin phòng: kiểm tra ràng buộc duy nhất uq_floor_room, sức chứa
    // và trạng thái
    @Transactional
    public Room_Response update(String roomId, Room_Request request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        String targetFloorId = (request.getFloorId() != null && !request.getFloorId().isBlank())
                ? request.getFloorId()
                : room.getFloor().getFloorId();

        if (!room.getFloor().getFloorId().equals(targetFloorId)) {
            Floor floor = floorRepository.findById(targetFloorId)
                    .orElseThrow(() -> new AppException(ErrorCode.FLOOR_NOT_FOUND));
            room.setFloor(floor);
        }

        String targetRoomTypeId = (request.getRoomTypeId() != null && !request.getRoomTypeId().isBlank())
                ? request.getRoomTypeId()
                : room.getRoomType().getRoomTypeId();

        if (!room.getRoomType().getRoomTypeId().equals(targetRoomTypeId)) {
            RoomType roomType = roomTypeRepository.findById(targetRoomTypeId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại phòng"));
            room.setRoomType(roomType);
        }

        String targetRoomNumber = (request.getRoomNumber() != null && !request.getRoomNumber().isBlank())
                ? request.getRoomNumber().trim()
                : room.getRoomNumber();

        // Kiểm tra ràng buộc duy nhất uq_floor_room khi đổi tầng hoặc đổi số phòng
        if (roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot(targetFloorId, targetRoomNumber,
                roomId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phòng số " + targetRoomNumber + " đã tồn tại ở tầng này");
        }
        room.setRoomNumber(targetRoomNumber);

        // Kiểm tra sức chứa mới không được nhỏ hơn số giường hiện có hoặc số người đang
        // ở và không vượt quá sức chứa tối đa của loại phòng
        if (request.getCapacity() != null) {
            if (request.getCapacity() <= 0) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Sức chứa phòng phải lớn hơn 0");
            }
            if (room.getRoomType() != null && room.getRoomType().getCapacity() != null
                    && request.getCapacity() > room.getRoomType().getCapacity()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Sức chứa phòng (" + request.getCapacity() + ") không được vượt quá sức chứa tối đa của loại phòng ("
                                + room.getRoomType().getCapacity() + ")");
            }
            long currentBedCount = bedRepository.countByRoom_RoomId(roomId);
            if (request.getCapacity() < currentBedCount) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Sức chứa mới (" + request.getCapacity() + ") không được nhỏ hơn số giường hiện có ("
                                + currentBedCount + ")");
            }
            long currentOccupancy = contractRepository.countActiveByRoomId(roomId);
            if (request.getCapacity() < currentOccupancy) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Sức chứa mới (" + request.getCapacity() + ") không được nhỏ hơn số người đang ở ("
                                + currentOccupancy + ")");
            }
            room.setCapacity(request.getCapacity());
        }

        // Cập nhật trạng thái và chặn chuyển sang bảo trì hoặc đóng phòng khi còn người
        // ở
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String normalizedStatus = request.getStatus().trim().toUpperCase();
            if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Trạng thái phòng không hợp lệ. Cho phép: " + String.join(", ", ALLOWED_STATUSES));
            }
            if ("MAINTENANCE".equals(normalizedStatus) || "CLOSED".equals(normalizedStatus)) {
                long currentOccupancy = contractRepository.countActiveByRoomId(roomId);
                if (currentOccupancy > 0) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Không thể chuyển phòng sang trạng thái " + normalizedStatus + " khi còn người đang ở ("
                                    + currentOccupancy + " người)");
                }
            }
            room.setStatus(normalizedStatus);
        }

        room = roomRepository.save(room);
        return enrichOccupancy(List.of(roomMapper.toResponse(room))).get(0);
    }

    // Cập nhật trạng thái phòng: chỉ cho phép các trạng thái trong danh mục hợp lệ
    // và chặn đóng phòng đang có người ở
    @Transactional
    public Room_Response updateStatus(String roomId, String status) {
        if (status == null || status.isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Trạng thái phòng không được để trống");
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái phòng không hợp lệ. Cho phép: " + String.join(", ", ALLOWED_STATUSES));
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if ("MAINTENANCE".equals(normalizedStatus) || "CLOSED".equals(normalizedStatus)) {
            long currentOccupancy = contractRepository.countActiveByRoomId(roomId);
            if (currentOccupancy > 0) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể chuyển phòng sang trạng thái " + normalizedStatus + " khi còn người đang ở ("
                                + currentOccupancy + " người)");
            }
        }

        room.setStatus(normalizedStatus);
        return enrichOccupancy(List.of(roomMapper.toResponse(roomRepository.save(room)))).get(0);
    }

    // Xóa phòng: kiểm tra toàn bộ các ràng buộc khóa ngoại (giường, hợp đồng, tài
    // sản, điện nước, bảo trì)
    @Transactional
    public void delete(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if (bedRepository.existsByRoom_RoomId(roomId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa phòng đang có giường");
        }

        if (contractRepository.existsByBed_Room_RoomId(roomId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa phòng đã có dữ liệu hợp đồng thuê");
        }

        if (roomAssetRepository.existsByRoom_RoomId(roomId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa phòng đang có tài sản thiết bị");
        }

        if (utilityReadingRepository.existsByRoom_RoomId(roomId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa phòng đã có chỉ số điện nước");
        }

        if (maintenanceRequestRepository.existsByRoom_RoomId(roomId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa phòng đã có yêu cầu bảo trì sửa chữa");
        }

        roomRepository.delete(room);
    }
}
