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

        Room phong = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if ("CLOSED".equalsIgnoreCase(phong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân bổ tài sản vào phòng đang đóng cửa");
        }

        Asset tai_san = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));

        if (tai_san.getStatus() != null && UNASSIGNABLE_ASSET_STATUSES.contains(tai_san.getStatus().trim().toUpperCase())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể phân bổ tài sản đang ở trạng thái '" + tai_san.getStatus() + "' (hỏng hoặc đã thanh lý)");
        }

        // Chặn trùng lặp cặp (phòng, tài sản) bằng cách cộng dồn số lượng vào bản ghi
        // hiện có
        Optional<RoomAsset> tai_san_phong_ton_tai = roomAssetRepository.findByRoom_RoomIdAndAsset_AssetId(request.getRoomId(),
                request.getAssetId());
        if (tai_san_phong_ton_tai.isPresent()) {
            RoomAsset tai_san_phong_hien_co = tai_san_phong_ton_tai.get();
            log.info("Tài sản ID: {} đã tồn tại ở phòng ID: {}, tiến hành cộng dồn thêm số lượng {}",
                    request.getAssetId(), request.getRoomId(), request.getQuantity());
            tai_san_phong_hien_co.setQuantity(tai_san_phong_hien_co.getQuantity() + request.getQuantity());
            if (request.getConditionNote() != null && !request.getConditionNote().isBlank()) {
                tai_san_phong_hien_co.setConditionNote(request.getConditionNote().trim());
            }
            if (request.getAssignedDate() != null) {
                tai_san_phong_hien_co.setAssignedDate(request.getAssignedDate());
            }
            tai_san_phong_hien_co = roomAssetRepository.save(tai_san_phong_hien_co);
            return roomAssetMapper.toResponse(tai_san_phong_hien_co);
        }

        RoomAsset tai_san_phong = roomAssetMapper.toEntity(request);
        tai_san_phong.setRoom(phong);
        tai_san_phong.setAsset(tai_san);
        tai_san_phong = roomAssetRepository.save(tai_san_phong);

        return roomAssetMapper.toResponse(tai_san_phong);
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

        Page<RoomAsset> trang_tai_san_phong = roomAssetRepository.searchRoomAssets(
                cleanBuildingId,
                cleanRoomId,
                cleanAssetId,
                pageable);

        List<RoomAsset_Response> data = roomAssetMapper.toResponseList(trang_tai_san_phong.getContent());

        return Page_Response.<RoomAsset_Response>builder()
                .currentPage(trang_tai_san_phong.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_tai_san_phong.getTotalPages())
                .totalElements(trang_tai_san_phong.getTotalElements())
                .hasPrevious(trang_tai_san_phong.hasPrevious())
                .hasNext(trang_tai_san_phong.hasNext())
                .isFirst(trang_tai_san_phong.isFirst())
                .isLast(trang_tai_san_phong.isLast())
                .data(data)
                .build();
    }

    // Lấy chi tiết thông tin phân bổ tài sản theo ID
    @Transactional(readOnly = true)
    public RoomAsset_Response getById(String roomAssetId) {
        RoomAsset tai_san_phong = roomAssetRepository.findById(roomAssetId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin tài sản phòng"));

        return roomAssetMapper.toResponse(tai_san_phong);
    }

    // Lấy danh sách tài sản thuộc một phòng cụ thể
    @Transactional(readOnly = true)
    public List<RoomAsset_Response> getByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        List<RoomAsset> danh_sach_tai_san_phong = roomAssetRepository.findByRoom_RoomId(roomId);
        return roomAssetMapper.toResponseList(danh_sach_tai_san_phong);
    }

    // Lấy danh sách phòng đang sử dụng một tài sản cụ thể theo assetId
    @Transactional(readOnly = true)
    public List<RoomAsset_Response> getByAssetId(String assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản");
        }

        List<RoomAsset> danh_sach_tai_san_phong = roomAssetRepository.findByAsset_AssetId(assetId);
        return roomAssetMapper.toResponseList(danh_sach_tai_san_phong);
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

        RoomAsset tai_san_phong = roomAssetRepository.findById(roomAssetId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin tài sản phòng"));

        String targetRoomId = (request.getRoomId() != null && !request.getRoomId().isBlank())
                ? request.getRoomId()
                : tai_san_phong.getRoom().getRoomId();

        if (!tai_san_phong.getRoom().getRoomId().equals(targetRoomId)) {
            Room phong = roomRepository.findById(targetRoomId)
                    .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
            if ("CLOSED".equalsIgnoreCase(phong.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể phân bổ tài sản vào phòng đang đóng cửa");
            }
            tai_san_phong.setRoom(phong);
        }

        String targetAssetId = (request.getAssetId() != null && !request.getAssetId().isBlank())
                ? request.getAssetId()
                : tai_san_phong.getAsset().getAssetId();

        if (!tai_san_phong.getAsset().getAssetId().equals(targetAssetId)) {
            Asset tai_san = assetRepository.findById(targetAssetId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));
            if (tai_san.getStatus() != null
                    && UNASSIGNABLE_ASSET_STATUSES.contains(tai_san.getStatus().trim().toUpperCase())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể phân bổ tài sản đang ở trạng thái '" + tai_san.getStatus()
                                + "' (hỏng hoặc đã thanh lý)");
            }
            tai_san_phong.setAsset(tai_san);
        }

        // Kiểm tra không để trùng lặp với bản ghi phân bổ khác trong cùng phòng
        if (roomAssetRepository.existsByRoom_RoomIdAndAsset_AssetIdAndRoomAssetIdNot(targetRoomId, targetAssetId,
                roomAssetId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Tài sản này đã được phân bổ cho phòng ở một bản ghi khác");
        }

        roomAssetMapper.updateEntityFromRequest(tai_san_phong, request);
        tai_san_phong = roomAssetRepository.save(tai_san_phong);

        return roomAssetMapper.toResponse(tai_san_phong);
    }

    // Xóa bản ghi phân bổ tài sản phòng
    @Transactional
    public void delete(String roomAssetId) {
        RoomAsset tai_san_phong = roomAssetRepository.findById(roomAssetId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin tài sản phòng"));

        roomAssetRepository.delete(tai_san_phong);
    }
}
