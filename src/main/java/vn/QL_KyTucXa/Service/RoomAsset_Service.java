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
import vn.QL_KyTucXa.DTO.Request.RoomAsset_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomAsset_Response;
import vn.QL_KyTucXa.Entity.Asset;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.RoomAsset;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RoomAsset_Mapper;
import vn.QL_KyTucXa.Repository.Asset_Repository;
import vn.QL_KyTucXa.Repository.RoomAsset_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomAsset_Service {

    RoomAsset_Repository roomAssetRepository;
    Room_Repository roomRepository;
    Asset_Repository assetRepository;
    RoomAsset_Mapper roomAssetMapper;

    static Set<String> UNASSIGNABLE_ASSET_STATUSES = Set.of("BROKEN", "DAMAGED", "LIQUIDATED", "DISPOSED", "DISCONTINUED");
    static Set<String> ALLOWED_SORT_FIELDS = Set.of("assignedDate", "quantity", "roomAssetId");

    // Phân bổ tài sản vào phòng: kiểm tra trạng thái tài sản, ngày bàn giao và cộng
    // dồn số lượng nếu trùng
    @Transactional
    public RoomAsset_Response create(RoomAsset_Request request) {
        log.info("Phân bổ tài sản ID: {} vào phòng ID: {}", request.getAssetId(), request.getRoomId());

        if (request.getAssignedDate() != null && request.getAssignedDate().isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày bàn giao không được vượt quá ngày hiện tại");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Số lượng tài sản phải lớn hơn 0");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if ("CLOSED".equalsIgnoreCase(room.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân bổ tài sản vào phòng đang đóng cửa");
        }

        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));

        if (asset.getStatus() != null && UNASSIGNABLE_ASSET_STATUSES.contains(asset.getStatus().trim().toUpperCase())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể phân bổ tài sản đang ở trạng thái '" + asset.getStatus() + "' (hỏng hoặc đã thanh lý)");
        }

        // Chặn trùng lặp cặp (phòng, tài sản) bằng cách cộng dồn số lượng vào bản ghi
        // hiện có
        Optional<RoomAsset> existingOpt = roomAssetRepository.findByRoom_RoomIdAndAsset_AssetId(request.getRoomId(),
                request.getAssetId());
        if (existingOpt.isPresent()) {
            RoomAsset existing = existingOpt.get();
            log.info("Tài sản ID: {} đã tồn tại ở phòng ID: {}, tiến hành cộng dồn thêm số lượng {}",
                    request.getAssetId(), request.getRoomId(), request.getQuantity());
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            if (request.getConditionNote() != null && !request.getConditionNote().isBlank()) {
                existing.setConditionNote(request.getConditionNote().trim());
            }
            if (request.getAssignedDate() != null) {
                existing.setAssignedDate(request.getAssignedDate());
            }
            existing = roomAssetRepository.save(existing);
            return roomAssetMapper.toResponse(existing);
        }

        RoomAsset roomAsset = roomAssetMapper.toEntity(request);
        roomAsset.setRoom(room);
        roomAsset.setAsset(asset);
        roomAsset = roomAssetRepository.save(roomAsset);

        return roomAssetMapper.toResponse(roomAsset);
    }

    // Lấy toàn bộ danh sách tài sản phòng không phân trang
    @Transactional(readOnly = true)
    public List<RoomAsset_Response> getAll() {
        return roomAssetMapper.toResponseList(roomAssetRepository.findAll());
    }

    // Lấy danh sách tài sản phòng có phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<RoomAsset_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và phân trang tài sản phòng theo tòa nhà, phòng và tài sản
    @Transactional(readOnly = true)
    public Page_Response<RoomAsset_Response> getAll(
            int page,
            int size,
            String buildingId,
            String roomId,
            String assetId,
            String sortBy,
            String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "assignedDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String cleanBuildingId = (buildingId != null && !buildingId.isBlank()) ? buildingId.trim() : null;
        String cleanRoomId = (roomId != null && !roomId.isBlank()) ? roomId.trim() : null;
        String cleanAssetId = (assetId != null && !assetId.isBlank()) ? assetId.trim() : null;

        Page<RoomAsset> pageResult = roomAssetRepository.searchRoomAssets(
                cleanBuildingId,
                cleanRoomId,
                cleanAssetId,
                pageable);

        List<RoomAsset_Response> data = roomAssetMapper.toResponseList(pageResult.getContent());

        return Page_Response.<RoomAsset_Response>builder()
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

    // Lấy chi tiết thông tin phân bổ tài sản theo ID
    @Transactional(readOnly = true)
    public RoomAsset_Response getById(String roomAssetId) {
        RoomAsset roomAsset = roomAssetRepository.findById(roomAssetId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin tài sản phòng"));

        return roomAssetMapper.toResponse(roomAsset);
    }

    // Lấy danh sách tài sản thuộc một phòng cụ thể
    @Transactional(readOnly = true)
    public List<RoomAsset_Response> getByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        List<RoomAsset> list = roomAssetRepository.findByRoom_RoomId(roomId);
        return roomAssetMapper.toResponseList(list);
    }

    // Lấy danh sách phòng đang sử dụng một tài sản cụ thể theo assetId
    @Transactional(readOnly = true)
    public List<RoomAsset_Response> getByAssetId(String assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản");
        }

        List<RoomAsset> list = roomAssetRepository.findByAsset_AssetId(assetId);
        return roomAssetMapper.toResponseList(list);
    }

    // Cập nhật thông tin phân bổ tài sản: kiểm tra ngày bàn giao, trạng thái và
    // tránh trùng lặp
    @Transactional
    public RoomAsset_Response update(String roomAssetId, RoomAsset_Request request) {
        if (request.getAssignedDate() != null && request.getAssignedDate().isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày bàn giao không được vượt quá ngày hiện tại");
        }

        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Số lượng tài sản phải lớn hơn 0");
        }

        RoomAsset roomAsset = roomAssetRepository.findById(roomAssetId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin tài sản phòng"));

        String targetRoomId = (request.getRoomId() != null && !request.getRoomId().isBlank())
                ? request.getRoomId()
                : roomAsset.getRoom().getRoomId();

        if (!roomAsset.getRoom().getRoomId().equals(targetRoomId)) {
            Room room = roomRepository.findById(targetRoomId)
                    .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
            if ("CLOSED".equalsIgnoreCase(room.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân bổ tài sản vào phòng đang đóng cửa");
            }
            roomAsset.setRoom(room);
        }

        String targetAssetId = (request.getAssetId() != null && !request.getAssetId().isBlank())
                ? request.getAssetId()
                : roomAsset.getAsset().getAssetId();

        if (!roomAsset.getAsset().getAssetId().equals(targetAssetId)) {
            Asset asset = assetRepository.findById(targetAssetId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));
            if (asset.getStatus() != null
                    && UNASSIGNABLE_ASSET_STATUSES.contains(asset.getStatus().trim().toUpperCase())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể phân bổ tài sản đang ở trạng thái '" + asset.getStatus()
                                + "' (hỏng hoặc đã thanh lý)");
            }
            roomAsset.setAsset(asset);
        }

        // Kiểm tra không để trùng lặp với bản ghi phân bổ khác trong cùng phòng
        if (roomAssetRepository.existsByRoom_RoomIdAndAsset_AssetIdAndRoomAssetIdNot(targetRoomId, targetAssetId,
                roomAssetId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Tài sản này đã được phân bổ cho phòng ở một bản ghi khác");
        }

        roomAssetMapper.updateEntityFromRequest(roomAsset, request);
        roomAsset = roomAssetRepository.save(roomAsset);

        return roomAssetMapper.toResponse(roomAsset);
    }

    // Xóa bản ghi phân bổ tài sản phòng
    @Transactional
    public void delete(String roomAssetId) {
        RoomAsset roomAsset = roomAssetRepository.findById(roomAssetId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin tài sản phòng"));

        roomAssetRepository.delete(roomAsset);
    }
}
