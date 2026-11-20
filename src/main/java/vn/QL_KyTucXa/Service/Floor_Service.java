package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Floor_Request;
import vn.QL_KyTucXa.DTO.Response.Floor_Response;
import vn.QL_KyTucXa.Entity.Building;
import vn.QL_KyTucXa.Entity.Floor;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Floor_Mapper;
import vn.QL_KyTucXa.Repository.Bed_Repository;
import vn.QL_KyTucXa.Repository.Building_Repository;
import vn.QL_KyTucXa.Repository.Floor_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Floor_Service {

    Floor_Repository floorRepository;
    Building_Repository buildingRepository;
    Room_Repository roomRepository;
    Bed_Repository bedRepository;
    Floor_Mapper floorMapper;

    // Tạo mới tầng: kiểm tra tòa nhà tồn tại, số tầng hợp lệ và không vượt quá số tầng tối đa
    @Transactional
    public Floor_Response create(Floor_Request request) {
        log.info("Tạo mới tầng {} cho tòa nhà ID: {}", request.getFloorNumber(), request.getBuildingId());

        Building toa_nha = buildingRepository.findById(request.getBuildingId())
                .orElseThrow(() -> new AppException(ErrorCode.BUILDING_NOT_FOUND));

        // Kiểm tra số thứ tự tầng tối thiểu là 1
        if (request.getFloorNumber() == null || request.getFloorNumber() < 1) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Số tầng phải lớn hơn hoặc bằng 1");
        }

        // Kiểm tra số tầng không vượt quá số tầng của tòa nhà
        if (toa_nha.getNumFloors() != null && request.getFloorNumber() > toa_nha.getNumFloors()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Số tầng (" + request.getFloorNumber() + ") không được vượt quá số tầng tối đa của tòa nhà (" + toa_nha.getNumFloors() + ")");
        }

        // Kiểm tra trùng số tầng trong cùng tòa nhà
        if (floorRepository.findByBuilding_BuildingIdAndFloorNumber(request.getBuildingId(), request.getFloorNumber()).isPresent()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Tầng " + request.getFloorNumber() + " đã tồn tại trong tòa nhà này");
        }

        Floor tang = floorMapper.toEntity(request);
        tang.setBuilding(toa_nha);
        tang = floorRepository.save(tang);

        return floorMapper.toResponse(tang);
    }

    // Lấy thông tin chi tiết một tầng theo ID
    @Transactional(readOnly = true)
    public Floor_Response getById(String floorId) {
        Floor tang = floorRepository.findById(floorId)
                .orElseThrow(() -> new AppException(ErrorCode.FLOOR_NOT_FOUND));

        return floorMapper.toResponse(tang);
    }

    // Lấy danh sách các tầng theo tòa nhà
    @Transactional(readOnly = true)
    public List<Floor_Response> getByBuildingId(String buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new AppException(ErrorCode.BUILDING_NOT_FOUND);
        }

        List<Floor> danh_sach_tang = floorRepository.findByBuilding_BuildingId(buildingId);
        return floorMapper.toResponseList(danh_sach_tang);
    }

    // Lấy toàn bộ danh sách tầng danh mục dùng chung
    @Transactional(readOnly = true)
    public List<Floor_Response> getAll() {
        List<Floor> danh_sach_tang = floorRepository.findAll();
        return floorMapper.toResponseList(danh_sach_tang);
    }

    // Cập nhật thông tin tầng: kiểm tra số tầng hợp lệ và không vượt quá số tầng của tòa nhà
    @Transactional
    public Floor_Response update(String floorId, Floor_Request request) {
        Floor tang = floorRepository.findById(floorId)
                .orElseThrow(() -> new AppException(ErrorCode.FLOOR_NOT_FOUND));

        Building toa_nha_dich = tang.getBuilding();
        if (request.getBuildingId() != null && !request.getBuildingId().isBlank() &&
                (toa_nha_dich == null || !toa_nha_dich.getBuildingId().equals(request.getBuildingId()))) {
            if (roomRepository.existsByFloor_FloorId(floorId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể chuyển tầng sang tòa nhà khác khi tầng đã có phòng trực thuộc");
            }
            toa_nha_dich = buildingRepository.findById(request.getBuildingId())
                    .orElseThrow(() -> new AppException(ErrorCode.BUILDING_NOT_FOUND));
            tang.setBuilding(toa_nha_dich);
        }

        Integer targetFloorNumber = (request.getFloorNumber() != null) ? request.getFloorNumber() : tang.getFloorNumber();
        if (targetFloorNumber == null || targetFloorNumber < 1) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Số tầng phải lớn hơn hoặc bằng 1");
        }
        if (toa_nha_dich != null && toa_nha_dich.getNumFloors() != null && targetFloorNumber > toa_nha_dich.getNumFloors()) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Số tầng (" + targetFloorNumber + ") không được vượt quá số tầng tối đa của tòa nhà (" + toa_nha_dich.getNumFloors() + ")");
        }

        // Kiểm tra trùng lặp tầng trong tòa nhà đích (loại trừ chính bản ghi đang cập nhật để tránh vi phạm uq_building_floor)
        if (toa_nha_dich != null) {
            Optional<Floor> existingFloor = floorRepository.findByBuilding_BuildingIdAndFloorNumber(toa_nha_dich.getBuildingId(), targetFloorNumber);
            if (existingFloor.isPresent() && !existingFloor.get().getFloorId().equals(floorId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Tầng " + targetFloorNumber + " đã tồn tại trong tòa nhà này");
            }
        }

        floorMapper.updateEntityFromRequest(tang, request);
        tang = floorRepository.save(tang);

        return floorMapper.toResponse(tang);
    }

    // Xóa một tầng: kiểm tra không còn phòng trực thuộc trước khi xóa
    @Transactional
    public void delete(String floorId) {
        Floor tang = floorRepository.findById(floorId)
                .orElseThrow(() -> new AppException(ErrorCode.FLOOR_NOT_FOUND));

        // Kiểm tra nếu tầng đang có giường có sinh viên cư trú hoặc giữ chỗ
        if (bedRepository.existsActiveBedsByFloorId(floorId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa tầng vì đang có phòng/giường có sinh viên cư trú (OCCUPIED) hoặc giữ chỗ (RESERVED). Vui lòng thanh lý hợp đồng trước");
        }

        // Kiểm tra nếu tầng còn phòng trực thuộc
        if (roomRepository.existsByFloor_FloorId(floorId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa tầng vì đang có các phòng trực thuộc. Vui lòng xóa các phòng trước");
        }

        floorRepository.delete(tang);
    }
}
