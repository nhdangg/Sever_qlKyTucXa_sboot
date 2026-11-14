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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Bed_ServiceTest {

    @Mock
    private Bed_Repository bedRepository;

    @Mock
    private Room_Repository roomRepository;

    @Mock
    private Building_Repository buildingRepository;

    @Mock
    private Contract_Repository contractRepository;

    @Mock
    private Bed_Mapper bedMapper;

    @InjectMocks
    private Bed_Service bedService;

    private Room room;
    private Bed bed;
    private Bed_Request request;
    private Bed_Response response;

    @BeforeEach
    void setUp() {
        room = Room.builder()
                .roomId("room-01")
                .roomNumber("P101")
                .capacity(4)
                .build();

        bed = Bed.builder()
                .bedId("bed-01")
                .bedNumber("G01")
                .room(room)
                .status("AVAILABLE")
                .build();

        request = Bed_Request.builder()
                .roomId("room-01")
                .bedNumber("G01")
                .status("AVAILABLE")
                .build();

        response = Bed_Response.builder()
                .bedId("bed-01")
                .bedNumber("G01")
                .status("AVAILABLE")
                .build();
    }

    @Test
    @DisplayName("Tạo mới giường thành công khi chưa vượt quá sức chứa phòng")
    void create_success() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.countByRoom_RoomId("room-01")).thenReturn(2L);
        when(bedRepository.findByRoom_RoomIdAndBedNumber("room-01", "G01")).thenReturn(Optional.empty());
        when(bedMapper.toEntity(request)).thenReturn(bed);
        when(bedRepository.save(any(Bed.class))).thenReturn(bed);
        when(bedMapper.toResponse(bed)).thenReturn(response);

        Bed_Response result = bedService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getBedNumber()).isEqualTo("G01");
        verify(bedRepository).save(bed);
    }

    @Test
    @DisplayName("Tạo giường thất bại khi phòng đã đủ hoặc vượt sức chứa tối đa")
    void create_failed_exceedCapacity() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.countByRoom_RoomId("room-01")).thenReturn(4L);

        AppException ex = assertThrows(AppException.class, () -> bedService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("sức chứa tối đa");
        verify(bedRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo giường thất bại khi không tìm thấy phòng")
    void create_failed_roomNotFound() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> bedService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo giường thất bại khi số giường đã tồn tại trong phòng")
    void create_failed_duplicateBed() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(bedRepository.countByRoom_RoomId("room-01")).thenReturn(1L);
        when(bedRepository.findByRoom_RoomIdAndBedNumber("room-01", "G01")).thenReturn(Optional.of(bed));

        AppException ex = assertThrows(AppException.class, () -> bedService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Lấy thông tin giường theo ID thành công")
    void getById_success() {
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bed));
        when(bedMapper.toResponse(bed)).thenReturn(response);

        Bed_Response result = bedService.getById("bed-01");

        assertThat(result).isNotNull();
        assertThat(result.getBedId()).isEqualTo("bed-01");
    }

    @Test
    @DisplayName("Lấy thông tin giường thất bại khi không tìm thấy")
    void getById_notFound() {
        when(bedRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> bedService.getById("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BED_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tất cả giường")
    void getAll_success() {
        when(bedRepository.findAll()).thenReturn(List.of(bed));
        when(bedMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Bed_Response> result = bedService.getAll();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm phân trang giường thành công")
    void searchBeds_success() {
        Page<Bed> pageResult = new PageImpl<>(List.of(bed));
        when(bedRepository.searchBeds(eq("bld-01"), eq("room-01"), eq("AVAILABLE"), any(Pageable.class)))
                .thenReturn(pageResult);
        when(bedMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Bed_Response> result = bedService.searchBeds("bld-01", "room-01", "AVAILABLE", 1, 10, "bedNumber", "asc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getCurrentPage()).isEqualTo(1);
        verify(bedRepository).searchBeds(eq("bld-01"), eq("room-01"), eq("AVAILABLE"), any(Pageable.class));
    }

    @Test
    @DisplayName("Lấy danh sách giường còn trống theo phòng thành công")
    void getBedsByRoomId_success() {
        when(roomRepository.existsById("room-01")).thenReturn(true);
        when(bedRepository.findByRoom_RoomIdAndStatus("room-01", "AVAILABLE")).thenReturn(List.of(bed));
        when(bedMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Bed_Response> result = bedService.getBedsByRoomId("room-01");

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        verify(bedRepository).findByRoom_RoomIdAndStatus("room-01", "AVAILABLE");
    }

    @Test
    @DisplayName("Lấy danh sách giường trống theo phòng thất bại khi phòng không tồn tại")
    void getBedsByRoomId_roomNotFound() {
        when(roomRepository.existsById("unknown-room")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> bedService.getBedsByRoomId("unknown-room"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách giường còn trống theo tòa nhà thành công")
    void getBedsByBuildingId_success() {
        when(buildingRepository.existsById("bld-01")).thenReturn(true);
        when(bedRepository.findByBuilding_BuildingIdAndStatus("bld-01", "AVAILABLE")).thenReturn(List.of(bed));
        when(bedMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Bed_Response> result = bedService.getBedsByBuildingId("bld-01");

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        verify(bedRepository).findByBuilding_BuildingIdAndStatus("bld-01", "AVAILABLE");
    }

    @Test
    @DisplayName("Lấy danh sách giường trống theo tòa nhà thất bại khi tòa nhà không tồn tại")
    void getBedsByBuildingId_buildingNotFound() {
        when(buildingRepository.existsById("unknown-bld")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> bedService.getBedsByBuildingId("unknown-bld"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUILDING_NOT_FOUND);
    }

    @Test
    @DisplayName("Cập nhật trạng thái giường sang MAINTENANCE thành công")
    void updateStatus_success() {
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bed));
        when(bedRepository.save(any(Bed.class))).thenReturn(bed);
        when(bedMapper.toResponse(bed)).thenReturn(response);

        Bed_Response result = bedService.updateStatus("bed-01", "MAINTENANCE");

        assertThat(result).isNotNull();
        assertThat(bed.getStatus()).isEqualTo("MAINTENANCE");
        verify(bedRepository).save(bed);
    }

    @Test
    @DisplayName("Cập nhật trạng thái thất bại khi gán OCCUPIED bằng tay")
    void updateStatus_failed_assignOccupied() {
        AppException ex = assertThrows(AppException.class, () -> bedService.updateStatus("bed-01", "OCCUPIED"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể gán trạng thái OCCUPIED thủ công");
    }

    @Test
    @DisplayName("Cập nhật trạng thái thất bại khi gán giá trị không hợp lệ")
    void updateStatus_failed_invalidStatus() {
        AppException ex = assertThrows(AppException.class, () -> bedService.updateStatus("bed-01", "UNKNOWN_STATUS"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Cập nhật trạng thái thất bại khi giường đang có người ở (OCCUPIED)")
    void updateStatus_failed_whenBedCurrentlyOccupied() {
        bed.setStatus("OCCUPIED");
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bed));

        AppException ex = assertThrows(AppException.class, () -> bedService.updateStatus("bed-01", "MAINTENANCE"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đang có người ở");
    }

    @Test
    @DisplayName("Xóa giường thành công khi chưa từng có hợp đồng và không có người ở")
    void delete_success() {
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bed));
        when(contractRepository.existsByBed_BedId("bed-01")).thenReturn(false);

        bedService.delete("bed-01");

        verify(bedRepository).delete(bed);
    }

    @Test
    @DisplayName("Xóa giường thất bại khi giường đang có người ở (OCCUPIED)")
    void delete_failed_bedOccupied() {
        bed.setStatus("OCCUPIED");
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bed));

        AppException ex = assertThrows(AppException.class, () -> bedService.delete("bed-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đang có người ở");
        verify(bedRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa giường thất bại khi đã có dữ liệu hợp đồng liên kết (tránh lỗi FK)")
    void delete_failed_hasContract() {
        bed.setStatus("AVAILABLE");
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bed));
        when(contractRepository.existsByBed_BedId("bed-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> bedService.delete("bed-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("dữ liệu hợp đồng lưu trú");
        verify(bedRepository, never()).delete(any());
    }
}
