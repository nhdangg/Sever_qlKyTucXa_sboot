package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.Floor_Request;
import vn.QL_KyTucXa.DTO.Response.Floor_Response;
import vn.QL_KyTucXa.Entity.Building;
import vn.QL_KyTucXa.Entity.Floor;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Floor_Mapper;
import vn.QL_KyTucXa.Repository.Building_Repository;
import vn.QL_KyTucXa.Repository.Floor_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Floor_ServiceTest {

    @Mock
    private Floor_Repository floorRepository;

    @Mock
    private Building_Repository buildingRepository;

    @Mock
    private Room_Repository roomRepository;

    @Mock
    private vn.QL_KyTucXa.Repository.Bed_Repository bedRepository;

    @Mock
    private Floor_Mapper floorMapper;

    @InjectMocks
    private Floor_Service floorService;

    private Building building;
    private Floor floor;
    private Floor_Request request;
    private Floor_Response response;

    @BeforeEach
    void setUp() {
        building = Building.builder()
                .buildingId("bld-01")
                .buildingCode("TOA_A")
                .buildingName("Tòa Nhà A")
                .numFloors(5)
                .build();

        floor = Floor.builder()
                .floorId("fl-01")
                .building(building)
                .floorNumber(2)
                .description("Tầng 2")
                .build();

        request = Floor_Request.builder()
                .buildingId("bld-01")
                .floorNumber(2)
                .description("Tầng 2")
                .build();

        response = Floor_Response.builder()
                .floorId("fl-01")
                .buildingId("bld-01")
                .buildingName("Tòa Nhà A")
                .floorNumber(2)
                .description("Tầng 2")
                .build();
    }

    @Test
    @DisplayName("Tạo mới tầng thành công khi số tầng hợp lệ")
    void create_success() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingIdAndFloorNumber("bld-01", 2)).thenReturn(Optional.empty());
        when(floorMapper.toEntity(request)).thenReturn(floor);
        when(floorRepository.save(any(Floor.class))).thenReturn(floor);
        when(floorMapper.toResponse(floor)).thenReturn(response);

        Floor_Response result = floorService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getFloorNumber()).isEqualTo(2);
        verify(floorRepository).save(floor);
    }

    @Test
    @DisplayName("Tạo tầng thất bại khi số tầng vượt quá số tầng tối đa của tòa nhà")
    void create_failed_floorNumberExceedsBuildingNumFloors() {
        request.setFloorNumber(6); // Tòa nhà chỉ có 5 tầng
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));

        AppException ex = assertThrows(AppException.class, () -> floorService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được vượt quá số tầng tối đa");
        verify(floorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo tầng thất bại khi số tầng nhỏ hơn 1")
    void create_failed_invalidFloorNumber() {
        request.setFloorNumber(0);
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));

        AppException ex = assertThrows(AppException.class, () -> floorService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("lớn hơn hoặc bằng 1");
    }

    @Test
    @DisplayName("Tạo tầng thất bại khi số tầng đã tồn tại trong tòa nhà")
    void create_failed_duplicateFloorNumber() {
        when(buildingRepository.findById("bld-01")).thenReturn(Optional.of(building));
        when(floorRepository.findByBuilding_BuildingIdAndFloorNumber("bld-01", 2)).thenReturn(Optional.of(floor));

        AppException ex = assertThrows(AppException.class, () -> floorService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại trong tòa nhà này");
    }

    @Test
    @DisplayName("Lấy chi tiết tầng theo ID thành công")
    void getById_success() {
        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(floorMapper.toResponse(floor)).thenReturn(response);

        Floor_Response result = floorService.getById("fl-01");

        assertThat(result).isNotNull();
        assertThat(result.getFloorId()).isEqualTo("fl-01");
    }

    @Test
    @DisplayName("Lấy chi tiết tầng thất bại khi không tìm thấy")
    void getById_notFound() {
        when(floorRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> floorService.getById("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FLOOR_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tầng theo tòa nhà thành công")
    void getByBuildingId_success() {
        when(buildingRepository.existsById("bld-01")).thenReturn(true);
        when(floorRepository.findByBuilding_BuildingId("bld-01")).thenReturn(List.of(floor));
        when(floorMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Floor_Response> list = floorService.getByBuildingId("bld-01");

        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("Lấy danh sách tầng theo tòa nhà thất bại khi tòa nhà không tồn tại")
    void getByBuildingId_failed_buildingNotFound() {
        when(buildingRepository.existsById("unknown-id")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> floorService.getByBuildingId("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUILDING_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy tất cả tầng thành công")
    void getAll_success() {
        when(floorRepository.findAll()).thenReturn(List.of(floor));
        when(floorMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Floor_Response> list = floorService.getAll();

        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("Cập nhật tầng thành công")
    void update_success() {
        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(floorRepository.save(floor)).thenReturn(floor);
        when(floorMapper.toResponse(floor)).thenReturn(response);

        Floor_Response result = floorService.update("fl-01", request);

        assertThat(result).isNotNull();
        verify(floorRepository).save(floor);
    }

    @Test
    @DisplayName("Cập nhật tầng thất bại khi đổi sang tòa nhà mới nhưng số tầng bị trùng với tầng đã có của tòa nhà đích")
    void update_failed_duplicateFloorNumberInTargetBuilding() {
        Building targetBuilding = Building.builder()
                .buildingId("bld-02")
                .buildingName("Tòa Nhà B")
                .numFloors(10)
                .build();
        Floor otherFloor = Floor.builder()
                .floorId("fl-99")
                .building(targetBuilding)
                .floorNumber(2)
                .build();

        Floor_Request updateReq = Floor_Request.builder()
                .buildingId("bld-02")
                .floorNumber(2)
                .build();

        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(buildingRepository.findById("bld-02")).thenReturn(Optional.of(targetBuilding));
        when(floorRepository.findByBuilding_BuildingIdAndFloorNumber("bld-02", 2)).thenReturn(Optional.of(otherFloor));

        AppException ex = assertThrows(AppException.class, () -> floorService.update("fl-01", updateReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Tầng 2 đã tồn tại");
    }

    @Test
    @DisplayName("Cập nhật tầng thất bại khi đổi sang tòa nhà khác nhưng tầng đã có phòng trực thuộc")
    void update_failed_changeBuildingWhenHasRooms() {
        Floor_Request updateReq = Floor_Request.builder()
                .buildingId("bld-02")
                .floorNumber(2)
                .build();

        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(roomRepository.existsByFloor_FloorId("fl-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> floorService.update("fl-01", updateReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể chuyển tầng sang tòa nhà khác khi tầng đã có phòng trực thuộc");
        verify(floorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xóa tầng thành công khi không còn phòng trực thuộc")
    void delete_success() {
        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(bedRepository.existsActiveBedsByFloorId("fl-01")).thenReturn(false);
        when(roomRepository.existsByFloor_FloorId("fl-01")).thenReturn(false);

        floorService.delete("fl-01");

        verify(floorRepository).delete(floor);
    }

    @Test
    @DisplayName("Xóa tầng thất bại khi còn phòng trực thuộc (chặn lỗi khóa ngoại)")
    void delete_failed_whenHasRooms() {
        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(bedRepository.existsActiveBedsByFloorId("fl-01")).thenReturn(false);
        when(roomRepository.existsByFloor_FloorId("fl-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> floorService.delete("fl-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("các phòng trực thuộc");
        verify(floorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa tầng thất bại khi có phòng đang có sinh viên cư trú")
    void delete_failed_whenHasOccupiedRooms() {
        when(floorRepository.findById("fl-01")).thenReturn(Optional.of(floor));
        when(bedRepository.existsActiveBedsByFloorId("fl-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> floorService.delete("fl-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("sinh viên cư trú");
        verify(floorRepository, never()).delete(any());
    }
}

