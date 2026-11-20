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
import vn.QL_KyTucXa.DTO.Request.RoomType_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomType_Response;
import vn.QL_KyTucXa.Entity.RoomType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RoomType_Mapper;
import vn.QL_KyTucXa.Repository.RoomType_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomType_Service {

    RoomType_Repository roomTypeRepository;
    RoomType_Mapper roomTypeMapper;
    Room_Repository room_Repository;

    static Set<String> ALLOWED_SORT_FIELDS = Set.of("roomTypeId", "typeName", "capacity", "areaM2", "pricePerMonth",
            "createdAt");

    @Transactional
    public RoomType_Response create(RoomType_Request request) {
        log.info("Tạo loại phòng mới: {}", request.getTypeName());

        if (roomTypeRepository.existsByTypeName(request.getTypeName())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Loại phòng '" + request.getTypeName() + "' đã tồn tại");
        }
        RoomType loai_phong = roomTypeMapper.toEntity(request);
        loai_phong = roomTypeRepository.save(loai_phong);
        return roomTypeMapper.toResponse(loai_phong);
    }

    @Transactional(readOnly = true)
    public RoomType_Response getById(String roomTypeId) {
        RoomType loai_phong = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại phòng"));

        return roomTypeMapper.toResponse(loai_phong);
    }

    @Transactional(readOnly = true)
    public List<RoomType_Response> getAll() {
        List<RoomType> danh_sach_loai_phong = roomTypeRepository.findAll();
        return roomTypeMapper.toResponseList(danh_sach_loai_phong);
    }

    @Transactional(readOnly = true)
    public Page_Response<RoomType_Response> getAll(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "typeName";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<RoomType> trang_loai_phong = roomTypeRepository.findAll(pageable);
        List<RoomType_Response> data = roomTypeMapper.toResponseList(trang_loai_phong.getContent());

        return Page_Response.<RoomType_Response>builder()
                .currentPage(trang_loai_phong.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_loai_phong.getTotalPages())
                .totalElements(trang_loai_phong.getTotalElements())
                .hasPrevious(trang_loai_phong.hasPrevious())
                .hasNext(trang_loai_phong.hasNext())
                .isFirst(trang_loai_phong.isFirst())
                .isLast(trang_loai_phong.isLast())
                .data(data)
                .build();
    }

    @Transactional
    public RoomType_Response update(String roomTypeId, RoomType_Request request) {
        RoomType loai_phong = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại phòng"));

        if (!loai_phong.getTypeName().equalsIgnoreCase(request.getTypeName())
                && roomTypeRepository.existsByTypeName(request.getTypeName())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Loại phòng '" + request.getTypeName() + "' đã tồn tại");
        }

        roomTypeMapper.updateEntityFromRequest(loai_phong, request);
        loai_phong = roomTypeRepository.save(loai_phong);
        return roomTypeMapper.toResponse(loai_phong);
    }

    @Transactional
    public void delete(String roomTypeId) {
        RoomType loai_phong = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại phòng"));

        if (room_Repository.existsByRoomType_RoomTypeId(roomTypeId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Loại phòng '" + loai_phong.getTypeName() + "' đang được sử dụng ở phòng khác");
        }
        roomTypeRepository.delete(loai_phong);
    }
}
