package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Building_ServiceTest {

    @Mock
    private Building_Repository buildingRepository;

    @Mock
    private Floor_Repository floorRepository;

    @Mock
    private Room_Repository roomRepository;

    @Mock
    private Bed_Repository bedRepository;

    @Mock
    private Contract_Repository contractRepository;

    @Mock
    private Building_Mapper buildingMapper;

    @InjectMocks
    private Building_Service buildingService;

    private Building building;
    private Building_Request request;
    private Building_Response response;

    @BeforeEach
    void setUp() {
        building = Building.builder()
                .buildingId("bld-01")
                .buildingCode("TOA_A")
                .buildingName("Tòa Nhà A")
                .numFloors(5)
                .genderType("MALE")
                .status("ACTIVE")
                .build();

        request = Building_Request.builder()
                .buildingCode("TOA_A")
                .buildingName("Tòa Nhà A")
                .numFloors(5)
                .genderType("MALE")
                .status("ACTIVE")
                .build();

        response = Building_Response.builder()
                .buildingId("bld-01")
                .buildingCode("TOA_A")
                .buildingName("Tòa Nhà A")
                .numFloors(5)
                .genderType("MALE")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Tạo mới tòa nhà thành công")
    void create_success() {
        when(buildingRepository.existsByBuildingCode("TOA_A")).thenReturn(false);
        when(buildingMapper.toEntity(request)).thenReturn(building);
        when(buildingRepository.save(any(Building.class))).thenReturn(building);
        when(buildingMapper.toResponse(building)).thenReturn(response);

        Building_Response result = buildingService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getBuildingCode()).isEqualTo("TOA_A");
        verify(buildingRepository).save(building);
    }

    @Test
    @DisplayName("Tạo tòa nhà thất bại khi mã tòa nhà đã tồn tại")
    void create_failed_duplicateCode() {
        when(buildingRepository.existsByBuildingCode("TOA_A")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> buildingService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy thông tin tòa nhà theo ID thành công")
    void getById_success() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(buildingMapper.toResponse(building)).thenReturn(response);

        Building_Response result = buildingService.getById("bld-01");

        assertThat(result).isNotNull();
        assertThat(result.getBuildingId()).isEqualTo("bld-01");
    }

    @Test
    @DisplayName("Lấy thông tin tòa nhà theo ID thất bại khi không tìm thấy")
    void getById_notFound() {
        when(buildingRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> buildingService.getById("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUILDING_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tất cả các tòa nhà")
    void getAll_success() {
        when(buildingRepository.findAll()).thenReturn(List.of(building));
        when(buildingMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Building_Response> result = buildingService.getAll();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Phân trang với sortBy không hợp lệ sẽ tự động dùng createdAt")
    void getAll_withInvalidSortBy_fallsBackToDefault() {
        Page<Building> page = new PageImpl<>(List.of(building));
        when(buildingRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(buildingMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Building_Response> result = buildingService.getAll(null, 1, 10, "invalid_column_injection", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        verify(buildingRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Cập nhật thông tin tòa nhà thành công khi số tầng hợp lệ")
    void update_success() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingId("bld-01")).thenReturn(List.of());
        when(buildingRepository.save(any(Building.class))).thenReturn(building);
        when(buildingMapper.toResponse(building)).thenReturn(response);

        Building_Response result = buildingService.update("bld-01", request);

        assertThat(result).isNotNull();
        verify(buildingRepository).save(building);
    }

    @Test
    @DisplayName("Cập nhật thất bại khi giảm numFloors xuống thấp hơn số tầng hiện có trong hệ thống")
    void update_failed_whenReduceNumFloorsBelowExisting() {
        Floor floor1 = Floor.builder().floorId("f-1").floorNumber(1).build();
        Floor floor2 = Floor.builder().floorId("f-2").floorNumber(2).build();
        Floor floor3 = Floor.builder().floorId("f-3").floorNumber(3).build();

        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingId("bld-01")).thenReturn(List.of(floor1, floor2, floor3));

        request.setNumFloors(2);

        AppException ex = assertThrows(AppException.class, () -> buildingService.update("bld-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể giảm số tầng xuống 2");
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật thất bại khi giảm numFloors xuống thấp hơn số thứ tự tầng hiện có")
    void update_failed_whenReduceNumFloorsBelowExistingFloorNumber() {
        Floor floor1 = Floor.builder().floorId("f-1").floorNumber(1).build();
        Floor floor4 = Floor.builder().floorId("f-4").floorNumber(4).build();

        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingId("bld-01")).thenReturn(List.of(floor1, floor4));

        request.setNumFloors(3);

        AppException ex = assertThrows(AppException.class, () -> buildingService.update("bld-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("lớn hơn 3");
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật thất bại khi đổi giới tính sang MALE nhưng đang có sinh viên Nữ cư trú")
    void update_failed_whenChangeGenderToMale_hasActiveFemaleContract() {
        building.setGenderType("MIXED");
        request.setGenderType("MALE");
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingId("bld-01")).thenReturn(List.of());
        when(contractRepository.existsActiveContractsByBuildingIdAndStudentGenderIn(eq("bld-01"), anyCollection()))
                .thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> buildingService.update("bld-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể chuyển tòa nhà sang dành riêng cho Nam vì đang có sinh viên Nữ cư trú");
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật thất bại khi đổi giới tính sang FEMALE nhưng đang có sinh viên Nam cư trú")
    void update_failed_whenChangeGenderToFemale_hasActiveMaleContract() {
        building.setGenderType("MIXED");
        request.setGenderType("FEMALE");
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingId("bld-01")).thenReturn(List.of());
        when(contractRepository.existsActiveContractsByBuildingIdAndStudentGenderIn(eq("bld-01"), anyCollection()))
                .thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> buildingService.update("bld-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể chuyển tòa nhà sang dành riêng cho Nữ vì đang có sinh viên Nam cư trú");
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xóa tòa nhà thành công khi không có phòng có người ở và không còn tầng trực thuộc")
    void delete_success() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(bedRepository.existsActiveBedsByBuildingId("bld-01")).thenReturn(false);
        when(floorRepository.existsByBuilding_BuildingId("bld-01")).thenReturn(false);

        buildingService.delete("bld-01");

        verify(buildingRepository).delete(building);
    }

    @Test
    @DisplayName("Xóa tòa nhà thất bại khi có phòng đang có sinh viên cư trú (OCCUPIED)")
    void delete_failed_whenHasOccupiedRooms() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(bedRepository.existsActiveBedsByBuildingId("bld-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> buildingService.delete("bld-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("sinh viên cư trú");
        verify(buildingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa tòa nhà thất bại khi tòa nhà còn tầng trực thuộc")
    void delete_failed_whenHasFloors() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(bedRepository.existsActiveBedsByBuildingId("bld-01")).thenReturn(false);
        when(floorRepository.existsByBuilding_BuildingId("bld-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> buildingService.delete("bld-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("các tầng và phòng trực thuộc");
        verify(buildingRepository, never()).delete(any());
    }
}
