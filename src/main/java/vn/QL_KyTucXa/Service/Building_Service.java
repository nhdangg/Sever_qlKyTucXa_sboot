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
import vn.QL_KyTucXa.DTO.Request.Building_Request;
import vn.QL_KyTucXa.DTO.Response.Building_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Building;
import vn.QL_KyTucXa.Entity.Floor;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Building_Mapper;
import vn.QL_KyTucXa.Repository.Bed_Repository;
import vn.QL_KyTucXa.Repository.Building_Repository;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Floor_Repository;

import java.util.List;
import java.util.Set;

// Service quản lý thông tin tòa nhà ký túc xá, cấu trúc tầng và kiểm soát dữ liệu
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Building_Service {

    Building_Repository buildingRepository;
    Floor_Repository floorRepository;
    Bed_Repository bedRepository;
    Contract_Repository contractRepository;
    Building_Mapper buildingMapper;

    static Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "buildingId", "buildingCode", "buildingName", "numFloors", "genderType", "status", "createdAt",
            "updatedAt");

    // Tạo mới một tòa nhà và kiểm tra trùng mã tòa nhà
    @Transactional
    public Building_Response create(Building_Request request) {
        log.info("Bắt đầu tạo tòa nhà mới với mã: {}", request.getBuildingCode());

        if (buildingRepository.existsByBuildingCode(request.getBuildingCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã tòa nhà '" + request.getBuildingCode() + "' đã tồn tại");
        }

        Building toa_nha = buildingMapper.toEntity(request);
        toa_nha.setBuildingCode(request.getBuildingCode().toUpperCase());
        toa_nha = buildingRepository.save(toa_nha);

        return buildingMapper.toResponse(toa_nha);
    }

    // Lấy thông tin chi tiết một tòa nhà theo ID
    @Transactional(readOnly = true)
    public Building_Response getById(String buildingId) {
        log.info("Lấy thông tin tòa nhà theo ID: {}", buildingId);

        Building toa_nha = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new AppException(ErrorCode.BUILDING_NOT_FOUND));

        return buildingMapper.toResponse(toa_nha);
    }

    // Lấy danh sách tất cả các tòa nhà không phân trang
    @Transactional(readOnly = true)
    public List<Building_Response> getAll() {
        log.info("Lấy danh sách tất cả tòa nhà");

        List<Building> danh_sach_toa_nha = buildingRepository.findAll();
        return buildingMapper.toResponseList(danh_sach_toa_nha);
    }

    // Lấy danh sách tòa nhà có phân trang, tìm kiếm và sắp xếp an toàn theo
    // whitelist
    @Transactional(readOnly = true)
    public Page_Response<Building_Response> getAll(String keyword, int page, int size, String sortBy, String sortDir) {
        log.info("Lấy danh sách tòa nhà có phân trang: keyword={}, page={}, size={}, sortBy={}, sortDir={}",
                keyword, page, size, sortBy, sortDir);

        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, safeSortBy);
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Building> buildingPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            buildingPage = buildingRepository.searchByKeyword(keyword.trim(), pageable);
        } else {
            buildingPage = buildingRepository.findAll(pageable);
        }

        List<Building_Response> responseList = buildingMapper.toResponseList(buildingPage.getContent());

        return Page_Response.<Building_Response>builder()
                .currentPage(buildingPage.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(buildingPage.getTotalPages())
                .totalElements(buildingPage.getTotalElements())
                .hasPrevious(buildingPage.hasPrevious())
                .hasNext(buildingPage.hasNext())
                .isFirst(buildingPage.isFirst())
                .isLast(buildingPage.isLast())
                .data(responseList)
                .build();
    }

    // Cập nhật thông tin tòa nhà: kiểm tra trùng mã và không giảm số tầng thấp hơn
    // số tầng hiện có
    @Transactional
    public Building_Response update(String buildingId, Building_Request request) {
        log.info("Cập nhật thông tin tòa nhà có ID: {}", buildingId);

        Building toa_nha = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new AppException(ErrorCode.BUILDING_NOT_FOUND));

        if (!toa_nha.getBuildingCode().equalsIgnoreCase(request.getBuildingCode())
                && buildingRepository.existsByBuildingCode(request.getBuildingCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã tòa nhà '" + request.getBuildingCode() + "' đã được sử dụng");
        }

        // Kiểm tra không được giảm số tầng xuống thấp hơn số tầng đang có hoặc số thứ
        // tự tầng hiện tại
        if (request.getNumFloors() != null) {
            List<Floor> danh_sach_tang_hien_co = floorRepository.findByBuilding_BuildingId(buildingId);
            if (request.getNumFloors() < danh_sach_tang_hien_co.size()) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể giảm số tầng xuống " + request.getNumFloors()
                                + " vì tòa nhà đang có " + danh_sach_tang_hien_co.size() + " tầng trong hệ thống");
            }

            boolean hasHigherFloorNumber = danh_sach_tang_hien_co.stream()
                    .anyMatch(f -> f.getFloorNumber() != null && f.getFloorNumber() > request.getNumFloors());
            if (hasHigherFloorNumber) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể giảm số tầng xuống " + request.getNumFloors()
                                + " vì đang có tầng với số thứ tự lớn hơn " + request.getNumFloors());
            }
        }

        // Kiểm tra không cho đổi genderType sang giới tính khác nếu đang có sinh viên
        // khác giới cư trú
        if (request.getGenderType() != null && !request.getGenderType().isBlank()) {
            String newGender = request.getGenderType().trim().toUpperCase();
            String currentGender = toa_nha.getGenderType() != null ? toa_nha.getGenderType().trim().toUpperCase()
                    : "MIXED";
            if (!newGender.equals(currentGender)) {
                if ("MALE".equals(newGender)) {
                    if (contractRepository != null && contractRepository
                            .existsActiveContractsByBuildingIdAndStudentGenderIn(buildingId, List.of("FEMALE", "NU"))) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể chuyển tòa nhà sang dành riêng cho Nam vì đang có sinh viên Nữ cư trú");
                    }
                } else if ("FEMALE".equals(newGender)) {
                    if (contractRepository != null && contractRepository
                            .existsActiveContractsByBuildingIdAndStudentGenderIn(buildingId, List.of("MALE", "NAM"))) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể chuyển tòa nhà sang dành riêng cho Nữ vì đang có sinh viên Nam cư trú");
                    }
                }
            }
        }

        buildingMapper.updateEntityFromRequest(toa_nha, request);
        toa_nha = buildingRepository.save(toa_nha);

        return buildingMapper.toResponse(toa_nha);
    }

    // Xóa một tòa nhà theo ID: kiểm tra tòa nhà còn tầng hoặc phòng có người ở
    @Transactional
    public void delete(String buildingId) {
        log.info("Xóa tòa nhà có ID: {}", buildingId);

        Building toa_nha = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new AppException(ErrorCode.BUILDING_NOT_FOUND));

        // Kiểm tra nếu tòa nhà đang có giường có sinh viên cư trú hoặc giữ chỗ
        if (bedRepository.existsActiveBedsByBuildingId(buildingId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa tòa nhà vì đang có phòng/giường có sinh viên cư trú (OCCUPIED) hoặc giữ chỗ (RESERVED). Vui lòng thanh lý hợp đồng trước");
        }

        // Kiểm tra nếu tòa nhà còn tầng trực thuộc
        if (floorRepository.existsByBuilding_BuildingId(buildingId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa tòa nhà vì đang có các tầng và phòng trực thuộc. Vui lòng xóa các tầng trước");
        }

        buildingRepository.delete(toa_nha);
    }
}
