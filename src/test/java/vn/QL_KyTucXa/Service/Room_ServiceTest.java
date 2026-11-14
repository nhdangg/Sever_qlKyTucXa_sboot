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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Room_ServiceTest {

    @Mock
    private Room_Repository roomRepository;

    @Mock
    private Floor_Repository floorRepository;

    @Mock
    private RoomType_Repository roomTypeRepository;

    @Mock
    private Bed_Repository bedRepository;

    @Mock
    private Contract_Repository contractRepository;

    @Mock
    private RoomAsset_Repository roomAssetRepository;

    @Mock
    private UtilityReading_Repository utilityReadingRepository;

    @Mock
    private MaintenanceRequest_Repository maintenanceRequestRepository;

    @Mock
    private Room_Mapper roomMapper;

    @InjectMocks
    private Room_Service roomService;

    private Floor floor;
    private RoomType roomType;
    private Room room;
    private Room_Request request;
    private Room_Response response;

    @BeforeEach
    void setUp() {
        floor = Floor.builder()
                .floorId("floor-01")
                .floorNumber(1)
                .build();

        roomType = RoomType.builder()
                .roomTypeId("type-01")
                .typeName("Phòng 4 người")
                .capacity(4)
                .build();

        room = Room.builder()
                .roomId("room-01")
                .roomNumber("P101")
                .floor(floor)
                .roomType(roomType)
                .capacity(4)
                .status("AVAILABLE")
                .build();

        request = Room_Request.builder()
                .floorId("floor-01")
                .roomTypeId("type-01")
                .roomNumber("P101")
                .capacity(4)
                .status("AVAILABLE")
                .build();

        response = Room_Response.builder()
                .roomId("room-01")
                .roomNumber("P101")
                .capacity(4)
                .status("AVAILABLE")
                .build();
    }

    @Test
    @DisplayName("Tạo phòng mới thành công và làm giàu số người đang ở từ hợp đồng ACTIVE")
    void create_success() {
        when(floorRepository.findById("floor-01")).thenReturn(Optional.of(floor));
        when(roomTypeRepository.findById("type-01")).thenReturn(Optional.of(roomType));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCase("floor-01", "P101")).thenReturn(false);
        when(roomMapper.toEntity(request)).thenReturn(room);
        when(roomRepository.save(any(Room.class))).thenReturn(room);
        when(roomMapper.toResponse(room)).thenReturn(response);
        List<Object[]> activeCountRows = new ArrayList<>();
        activeCountRows.add(new Object[]{"room-01", 3L});
        when(contractRepository.countActiveByRoomIds(anyCollection())).thenReturn(activeCountRows);

        Room_Response result = roomService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getRoomNumber()).isEqualTo("P101");
        assertThat(result.getCurrentOccupancy()).isEqualTo(3);
        verify(roomRepository).save(room);
        verify(bedRepository, times(4)).save(any(Bed.class));
    }

    @Test
    @DisplayName("Tạo phòng thất bại khi sức chứa phòng vượt quá sức chứa tối đa của loại phòng")
    void create_failed_capacityExceedsRoomTypeCapacity() {
        request.setCapacity(6);
        room.setCapacity(6);
        when(floorRepository.findById("floor-01")).thenReturn(Optional.of(floor));
        when(roomTypeRepository.findById("type-01")).thenReturn(Optional.of(roomType));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCase("floor-01", "P101")).thenReturn(false);
        when(roomMapper.toEntity(request)).thenReturn(room);

        AppException ex = assertThrows(AppException.class, () -> roomService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được vượt quá sức chứa tối đa của loại phòng");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo phòng thất bại khi không tìm thấy tầng")
    void create_failed_floorNotFound() {
        when(floorRepository.findById("floor-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FLOOR_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo phòng thất bại khi không tìm thấy loại phòng")
    void create_failed_roomTypeNotFound() {
        when(floorRepository.findById("floor-01")).thenReturn(Optional.of(floor));
        when(roomTypeRepository.findById("type-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo phòng thất bại khi trùng số phòng trên cùng tầng (uq_floor_room)")
    void create_failed_duplicateRoomNumber() {
        when(floorRepository.findById("floor-01")).thenReturn(Optional.of(floor));
        when(roomTypeRepository.findById("type-01")).thenReturn(Optional.of(roomType));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCase("floor-01", "P101")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại");
    }

    @Test
    @DisplayName("Tạo phòng thất bại khi trạng thái phòng không thuộc danh mục cho phép")
    void create_failed_invalidStatus() {
        request.setStatus("INVALID_STATUS");

        AppException ex = assertThrows(AppException.class, () -> roomService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Trạng thái phòng không hợp lệ");
    }

    @Test
    @DisplayName("Lấy thông tin phòng theo ID thành công")
    void getById_success() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(roomMapper.toResponse(room)).thenReturn(response);

        Room_Response result = roomService.getById("room-01");

        assertThat(result).isNotNull();
        assertThat(result.getRoomId()).isEqualTo("room-01");
    }

    @Test
    @DisplayName("Lấy thông tin phòng theo ID thất bại khi phòng không tồn tại")
    void getById_notFound() {
        when(roomRepository.findById("invalid-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomService.getById("invalid-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách phòng theo tầng thành công")
    void getByFloorId_success() {
        when(floorRepository.existsById("floor-01")).thenReturn(true);
        when(roomRepository.findByFloor_FloorId("floor-01")).thenReturn(List.of(room));
        when(roomMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Room_Response> result = roomService.getByFloorId("floor-01");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Lấy danh sách phòng theo tầng thất bại khi tầng không tồn tại")
    void getByFloorId_floorNotFound() {
        when(floorRepository.existsById("invalid-floor")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> roomService.getByFloorId("invalid-floor"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FLOOR_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tất cả phòng không phân trang")
    void getAll_success() {
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(roomMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Room_Response> result = roomService.getAll();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm và phân trang danh sách phòng theo bộ lọc đa tiêu chí")
    void getAll_withPagingAndSearch_success() {
        Page<Room> pageResult = new PageImpl<>(List.of(room));
        when(roomRepository.searchRooms(eq("101"), eq("bldg-01"), eq("floor-01"), eq("type-01"), eq("AVAILABLE"), eq(true), any(Pageable.class)))
                .thenReturn(pageResult);
        when(roomMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Room_Response> result = roomService.getAll(1, 10, "101", "bldg-01", "floor-01", "type-01", "AVAILABLE", true, "roomNumber", "asc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getCurrentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cập nhật phòng thành công")
    void update_success() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot("floor-01", "P101", "room-01")).thenReturn(false);
        when(bedRepository.countByRoom_RoomId("room-01")).thenReturn(2L);
        when(contractRepository.countActiveByRoomId("room-01")).thenReturn(2L);
        when(roomRepository.save(any(Room.class))).thenReturn(room);
        when(roomMapper.toResponse(room)).thenReturn(response);

        Room_Response result = roomService.update("room-01", request);

        assertThat(result).isNotNull();
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Cập nhật phòng thất bại khi đổi tầng và trùng số phòng trên tầng mới (lỗi uq_floor_room)")
    void update_failed_duplicateRoomNumber_whenFloorChanged() {
        request.setFloorId("floor-02");
        Floor floor2 = Floor.builder().floorId("floor-02").floorNumber(2).build();
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(floorRepository.findById("floor-02")).thenReturn(Optional.of(floor2));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot("floor-02", "P101", "room-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.update("room-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại ở tầng này");
    }

    @Test
    @DisplayName("Cập nhật phòng thất bại khi sức chứa mới vượt quá sức chứa tối đa của loại phòng")
    void update_failed_capacityExceedsRoomTypeCapacity() {
        request.setCapacity(8);
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot("floor-01", "P101", "room-01")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> roomService.update("room-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được vượt quá sức chứa tối đa của loại phòng");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật phòng thất bại khi sức chứa mới nhỏ hơn số giường hiện có")
    void update_failed_capacityLessThanBedCount() {
        request.setCapacity(2);
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot("floor-01", "P101", "room-01")).thenReturn(false);
        when(bedRepository.countByRoom_RoomId("room-01")).thenReturn(4L);

        AppException ex = assertThrows(AppException.class, () -> roomService.update("room-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được nhỏ hơn số giường hiện có");
    }

    @Test
    @DisplayName("Cập nhật phòng thất bại khi sức chứa mới nhỏ hơn số người đang ở")
    void update_failed_capacityLessThanOccupancy() {
        request.setCapacity(2);
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(roomRepository.existsByFloor_FloorIdAndRoomNumberIgnoreCaseAndRoomIdNot("floor-01", "P101", "room-01")).thenReturn(false);
        when(bedRepository.countByRoom_RoomId("room-01")).thenReturn(2L);
        when(contractRepository.countActiveByRoomId("room-01")).thenReturn(3L);

        AppException ex = assertThrows(AppException.class, () -> roomService.update("room-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được nhỏ hơn số người đang ở");
    }

    @Test
    @DisplayName("Cập nhật trạng thái thành công")
    void updateStatus_success() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);
        when(roomMapper.toResponse(room)).thenReturn(response);

        Room_Response result = roomService.updateStatus("room-01", "FULL");

        assertThat(result).isNotNull();
        assertThat(room.getStatus()).isEqualTo("FULL");
    }

    @Test
    @DisplayName("Cập nhật trạng thái thất bại khi giá trị không hợp lệ")
    void updateStatus_failed_invalidStatus() {
        AppException ex = assertThrows(AppException.class, () -> roomService.updateStatus("room-01", "UNKNOWN_STATUS"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Trạng thái phòng không hợp lệ");
    }

    @Test
    @DisplayName("Cập nhật trạng thái sang CLOSED thất bại khi còn người đang ở")
    void updateStatus_failed_closedWhenOccupied() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(contractRepository.countActiveByRoomId("room-01")).thenReturn(2L);

        AppException ex = assertThrows(AppException.class, () -> roomService.updateStatus("room-01", "CLOSED"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("khi còn người đang ở");
    }

    @Test
    @DisplayName("Cập nhật trạng thái sang MAINTENANCE thất bại khi còn người đang ở")
    void updateStatus_failed_maintenanceWhenOccupied() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(contractRepository.countActiveByRoomId("room-01")).thenReturn(1L);

        AppException ex = assertThrows(AppException.class, () -> roomService.updateStatus("room-01", "MAINTENANCE"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("khi còn người đang ở");
    }

    @Test
    @DisplayName("Xóa phòng thành công khi không có bất kỳ ràng buộc nào")
    void delete_success() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(contractRepository.existsByBed_Room_RoomId("room-01")).thenReturn(false);
        when(roomAssetRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(utilityReadingRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(maintenanceRequestRepository.existsByRoom_RoomId("room-01")).thenReturn(false);

        roomService.delete("room-01");

        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi phòng còn giường")
    void delete_failed_hasBeds() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoom_RoomId("room-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.delete("room-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đang có giường");
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi phòng đã có hợp đồng thuê")
    void delete_failed_hasContracts() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(contractRepository.existsByBed_Room_RoomId("room-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.delete("room-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("hợp đồng");
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi phòng còn tài sản thiết bị")
    void delete_failed_hasAssets() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(contractRepository.existsByBed_Room_RoomId("room-01")).thenReturn(false);
        when(roomAssetRepository.existsByRoom_RoomId("room-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.delete("room-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("tài sản");
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi phòng có chỉ số điện nước")
    void delete_failed_hasUtilityReadings() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(contractRepository.existsByBed_Room_RoomId("room-01")).thenReturn(false);
        when(roomAssetRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(utilityReadingRepository.existsByRoom_RoomId("room-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.delete("room-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("chỉ số điện nước");
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi phòng có yêu cầu bảo trì")
    void delete_failed_hasMaintenanceRequests() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(contractRepository.existsByBed_Room_RoomId("room-01")).thenReturn(false);
        when(roomAssetRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(utilityReadingRepository.existsByRoom_RoomId("room-01")).thenReturn(false);
        when(maintenanceRequestRepository.existsByRoom_RoomId("room-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomService.delete("room-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("yêu cầu bảo trì");
    }
}
