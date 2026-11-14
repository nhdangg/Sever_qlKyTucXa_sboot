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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomAsset_ServiceTest {

    @Mock
    private RoomAsset_Repository roomAssetRepository;

    @Mock
    private Room_Repository roomRepository;

    @Mock
    private Asset_Repository assetRepository;

    @Mock
    private RoomAsset_Mapper roomAssetMapper;

    @InjectMocks
    private RoomAsset_Service roomAssetService;

    private Room room;
    private Asset asset;
    private RoomAsset roomAsset;
    private RoomAsset_Request request;
    private RoomAsset_Response response;

    @BeforeEach
    void setUp() {
        room = Room.builder()
                .roomId("room-01")
                .roomNumber("P101")
                .status("AVAILABLE")
                .build();

        asset = Asset.builder()
                .assetId("asset-01")
                .assetCode("GHE-001")
                .assetName("Ghế xoay")
                .status("IN_USE")
                .build();

        roomAsset = RoomAsset.builder()
                .roomAssetId("ra-01")
                .room(room)
                .asset(asset)
                .quantity(2)
                .assignedDate(LocalDate.now().minusDays(1))
                .conditionNote("Còn tốt")
                .build();

        request = RoomAsset_Request.builder()
                .roomId("room-01")
                .assetId("asset-01")
                .quantity(2)
                .assignedDate(LocalDate.now().minusDays(1))
                .conditionNote("Còn tốt")
                .build();

        response = RoomAsset_Response.builder()
                .roomAssetId("ra-01")
                .roomId("room-01")
                .roomNumber("P101")
                .assetId("asset-01")
                .assetCode("GHE-001")
                .assetName("Ghế xoay")
                .quantity(2)
                .assignedDate(LocalDate.now().minusDays(1))
                .conditionNote("Còn tốt")
                .build();
    }

    @Test
    @DisplayName("Gán mới tài sản vào phòng thành công")
    void create_success_new() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(roomAssetRepository.findByRoom_RoomIdAndAsset_AssetId("room-01", "asset-01")).thenReturn(Optional.empty());
        when(roomAssetMapper.toEntity(request)).thenReturn(roomAsset);
        when(roomAssetRepository.save(any(RoomAsset.class))).thenReturn(roomAsset);
        when(roomAssetMapper.toResponse(roomAsset)).thenReturn(response);

        RoomAsset_Response result = roomAssetService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(2);
        verify(roomAssetRepository).save(roomAsset);
    }

    @Test
    @DisplayName("Gán trùng tài sản vào cùng phòng -> cộng dồn số lượng thành công")
    void create_success_accumulateQuantity() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(roomAssetRepository.findByRoom_RoomIdAndAsset_AssetId("room-01", "asset-01")).thenReturn(Optional.of(roomAsset));
        when(roomAssetRepository.save(any(RoomAsset.class))).thenReturn(roomAsset);
        response.setQuantity(4);
        when(roomAssetMapper.toResponse(roomAsset)).thenReturn(response);

        RoomAsset_Response result = roomAssetService.create(request);

        assertThat(result).isNotNull();
        assertThat(roomAsset.getQuantity()).isEqualTo(4); // 2 + 2 = 4
        verify(roomAssetRepository).save(roomAsset);
    }

    @Test
    @DisplayName("Gán trùng tài sản khi request không truyền ngày bàn giao -> giữ nguyên ngày bàn giao cũ")
    void create_success_accumulateQuantity_retainsOriginalAssignedDateWhenRequestDateNull() {
        LocalDate originalDate = LocalDate.now().minusDays(5);
        roomAsset.setAssignedDate(originalDate);
        request.setAssignedDate(null);

        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(roomAssetRepository.findByRoom_RoomIdAndAsset_AssetId("room-01", "asset-01")).thenReturn(Optional.of(roomAsset));
        when(roomAssetRepository.save(any(RoomAsset.class))).thenReturn(roomAsset);
        when(roomAssetMapper.toResponse(roomAsset)).thenReturn(response);

        roomAssetService.create(request);

        assertThat(roomAsset.getAssignedDate()).isEqualTo(originalDate);
        verify(roomAssetRepository).save(roomAsset);
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi ngày bàn giao ở tương lai")
    void create_failed_futureAssignedDate() {
        request.setAssignedDate(LocalDate.now().plusDays(2));

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Ngày bàn giao không được vượt quá ngày hiện tại");
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi số lượng nhỏ hơn hoặc bằng 0")
    void create_failed_invalidQuantity() {
        request.setQuantity(0);

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Số lượng tài sản phải lớn hơn 0");
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi phòng không tồn tại")
    void create_failed_roomNotFound() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi phòng đang đóng cửa (CLOSED)")
    void create_failed_roomClosed() {
        room.setStatus("CLOSED");
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("phòng đang đóng cửa");
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi tài sản không tồn tại")
    void create_failed_assetNotFound() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(assetRepository.findById("asset-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi tài sản bị hỏng (BROKEN)")
    void create_failed_assetBroken() {
        asset.setStatus("BROKEN");
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("hỏng hoặc đã thanh lý");
    }

    @Test
    @DisplayName("Gán tài sản thất bại khi tài sản đã thanh lý (LIQUIDATED)")
    void create_failed_assetLiquidated() {
        asset.setStatus("LIQUIDATED");
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("hỏng hoặc đã thanh lý");
    }

    @Test
    @DisplayName("Lấy thông tin tài sản phòng theo ID thành công")
    void getById_success() {
        when(roomAssetRepository.findById("ra-01")).thenReturn(Optional.of(roomAsset));
        when(roomAssetMapper.toResponse(roomAsset)).thenReturn(response);

        RoomAsset_Response result = roomAssetService.getById("ra-01");

        assertThat(result).isNotNull();
        assertThat(result.getRoomAssetId()).isEqualTo("ra-01");
    }

    @Test
    @DisplayName("Lấy thông tin tài sản phòng theo ID thất bại khi không tìm thấy")
    void getById_notFound() {
        when(roomAssetRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.getById("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tài sản theo ID phòng thành công")
    void getByRoomId_success() {
        when(roomRepository.existsById("room-01")).thenReturn(true);
        when(roomAssetRepository.findByRoom_RoomId("room-01")).thenReturn(List.of(roomAsset));
        when(roomAssetMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<RoomAsset_Response> result = roomAssetService.getByRoomId("room-01");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Lấy danh sách phòng theo ID tài sản (findByAsset_AssetId) thành công")
    void getByAssetId_success() {
        when(assetRepository.existsById("asset-01")).thenReturn(true);
        when(roomAssetRepository.findByAsset_AssetId("asset-01")).thenReturn(List.of(roomAsset));
        when(roomAssetMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<RoomAsset_Response> result = roomAssetService.getByAssetId("asset-01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAssetId()).isEqualTo("asset-01");
    }

    @Test
    @DisplayName("Lấy danh sách phòng theo ID tài sản thất bại khi tài sản không tồn tại")
    void getByAssetId_notFound() {
        when(assetRepository.existsById("unknown-asset")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.getByAssetId("unknown-asset"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Tìm kiếm và phân trang tài sản phòng theo bộ lọc tòa, phòng, tài sản")
    void getAll_pagedAndSearch_success() {
        Page<RoomAsset> page = new PageImpl<>(List.of(roomAsset));
        when(roomAssetRepository.searchRoomAssets(eq("bldg-01"), eq("room-01"), eq("asset-01"), any(Pageable.class)))
                .thenReturn(page);
        when(roomAssetMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<RoomAsset_Response> result = roomAssetService.getAll(1, 10, "bldg-01", "room-01", "asset-01", "assignedDate", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getCurrentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cập nhật thông tin tài sản phòng thành công")
    void update_success() {
        when(roomAssetRepository.findById("ra-01")).thenReturn(Optional.of(roomAsset));
        when(roomAssetRepository.existsByRoom_RoomIdAndAsset_AssetIdAndRoomAssetIdNot("room-01", "asset-01", "ra-01")).thenReturn(false);
        when(roomAssetRepository.save(any(RoomAsset.class))).thenReturn(roomAsset);
        when(roomAssetMapper.toResponse(roomAsset)).thenReturn(response);

        RoomAsset_Response result = roomAssetService.update("ra-01", request);

        assertThat(result).isNotNull();
        verify(roomAssetRepository).save(roomAsset);
    }

    @Test
    @DisplayName("Cập nhật tài sản phòng thất bại khi trùng với một bản ghi khác trong phòng")
    void update_failed_duplicateOtherRecord() {
        when(roomAssetRepository.findById("ra-01")).thenReturn(Optional.of(roomAsset));
        when(roomAssetRepository.existsByRoom_RoomIdAndAsset_AssetIdAndRoomAssetIdNot("room-01", "asset-01", "ra-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.update("ra-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("ở một bản ghi khác");
    }

    @Test
    @DisplayName("Xóa phân bổ tài sản phòng thành công")
    void delete_success() {
        when(roomAssetRepository.findById("ra-01")).thenReturn(Optional.of(roomAsset));

        roomAssetService.delete("ra-01");

        verify(roomAssetRepository).delete(roomAsset);
    }

    @Test
    @DisplayName("Xóa phân bổ tài sản phòng thất bại khi không tìm thấy bản ghi")
    void delete_failed_notFound() {
        when(roomAssetRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomAssetService.delete("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}

