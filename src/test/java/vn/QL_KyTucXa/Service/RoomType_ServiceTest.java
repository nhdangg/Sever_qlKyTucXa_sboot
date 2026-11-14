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
import vn.QL_KyTucXa.DTO.Request.RoomType_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomType_Response;
import vn.QL_KyTucXa.Entity.RoomType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RoomType_Mapper;
import vn.QL_KyTucXa.Repository.RoomType_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomType_ServiceTest {

    @Mock
    private RoomType_Repository roomTypeRepository;

    @Mock
    private RoomType_Mapper roomTypeMapper;

    @Mock
    private Room_Repository room_Repository;

    @InjectMocks
    private RoomType_Service roomTypeService;

    private RoomType roomType;
    private RoomType_Request request;
    private RoomType_Response response;

    @BeforeEach
    void setUp() {
        roomType = RoomType.builder()
                .roomTypeId("rt-01")
                .typeName("Phòng 4 người tiêu chuẩn")
                .capacity(4)
                .areaM2(BigDecimal.valueOf(25))
                .pricePerMonth(BigDecimal.valueOf(500000))
                .build();

        request = RoomType_Request.builder()
                .typeName("Phòng 4 người tiêu chuẩn")
                .capacity(4)
                .areaM2(BigDecimal.valueOf(25))
                .pricePerMonth(BigDecimal.valueOf(500000))
                .build();

        response = RoomType_Response.builder()
                .roomTypeId("rt-01")
                .typeName("Phòng 4 người tiêu chuẩn")
                .capacity(4)
                .pricePerMonth(BigDecimal.valueOf(500000))
                .build();
    }

    @Test
    @DisplayName("Tạo loại phòng thành công")
    void create_success() {
        when(roomTypeRepository.existsByTypeName("Phòng 4 người tiêu chuẩn")).thenReturn(false);
        when(roomTypeMapper.toEntity(request)).thenReturn(roomType);
        when(roomTypeRepository.save(roomType)).thenReturn(roomType);
        when(roomTypeMapper.toResponse(roomType)).thenReturn(response);

        RoomType_Response res = roomTypeService.create(request);

        assertThat(res).isNotNull();
        assertThat(res.getTypeName()).isEqualTo("Phòng 4 người tiêu chuẩn");
        verify(roomTypeRepository).save(roomType);
    }

    @Test
    @DisplayName("Tạo loại phòng thất bại khi tên bị trùng")
    void create_failed_duplicateName() {
        when(roomTypeRepository.existsByTypeName("Phòng 4 người tiêu chuẩn")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomTypeService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại");
        verify(roomTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy chi tiết loại phòng theo ID thành công")
    void getById_success() {
        when(roomTypeRepository.findById("rt-01")).thenReturn(Optional.of(roomType));
        when(roomTypeMapper.toResponse(roomType)).thenReturn(response);

        RoomType_Response res = roomTypeService.getById("rt-01");

        assertThat(res).isNotNull();
        assertThat(res.getRoomTypeId()).isEqualTo("rt-01");
    }

    @Test
    @DisplayName("Lấy chi tiết loại phòng thất bại khi không tìm thấy")
    void getById_notFound() {
        when(roomTypeRepository.findById("unknown")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> roomTypeService.getById("unknown"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách tất cả loại phòng không phân trang")
    void getAll_unpaged_success() {
        when(roomTypeRepository.findAll()).thenReturn(List.of(roomType));
        when(roomTypeMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<RoomType_Response> list = roomTypeService.getAll();

        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("Phân trang loại phòng thành công với whitelist sort và fallback khi sortBy không hợp lệ")
    void getAll_paged_withSortFallback() {
        Page<RoomType> pageResult = new PageImpl<>(List.of(roomType));
        when(roomTypeRepository.findAll(any(Pageable.class))).thenReturn(pageResult);
        when(roomTypeMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<RoomType_Response> res = roomTypeService.getAll(1, 10, "sql_injection_column", "asc");

        assertThat(res).isNotNull();
        assertThat(res.getData()).hasSize(1);
        verify(roomTypeRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Cập nhật loại phòng thành công")
    void update_success() {
        when(roomTypeRepository.findById("rt-01")).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.save(roomType)).thenReturn(roomType);
        when(roomTypeMapper.toResponse(roomType)).thenReturn(response);

        RoomType_Response res = roomTypeService.update("rt-01", request);

        assertThat(res).isNotNull();
        verify(roomTypeRepository).save(roomType);
    }

    @Test
    @DisplayName("Xóa loại phòng thành công khi chưa có phòng sử dụng")
    void delete_success() {
        when(roomTypeRepository.findById("rt-01")).thenReturn(Optional.of(roomType));
        when(room_Repository.existsByRoomType_RoomTypeId("rt-01")).thenReturn(false);

        roomTypeService.delete("rt-01");

        verify(roomTypeRepository).delete(roomType);
    }

    @Test
    @DisplayName("Xóa loại phòng thất bại khi đang có phòng sử dụng")
    void delete_failed_inUse() {
        when(roomTypeRepository.findById("rt-01")).thenReturn(Optional.of(roomType));
        when(room_Repository.existsByRoomType_RoomTypeId("rt-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomTypeService.delete("rt-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đang được sử dụng ở phòng khác");
        verify(roomTypeRepository, never()).delete(any());
    }
}

