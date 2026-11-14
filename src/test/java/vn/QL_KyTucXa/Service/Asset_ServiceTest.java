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
import vn.QL_KyTucXa.DTO.Request.Asset_Request;
import vn.QL_KyTucXa.DTO.Response.Asset_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Asset;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Asset_Mapper;
import vn.QL_KyTucXa.Repository.Asset_Repository;
import vn.QL_KyTucXa.Repository.RoomAsset_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Asset_ServiceTest {

    @Mock
    private Asset_Repository assetRepository;

    @Mock
    private RoomAsset_Repository roomAssetRepository;

    @Mock
    private Asset_Mapper assetMapper;

    @InjectMocks
    private Asset_Service assetService;

    private Asset asset;
    private Asset_Request request;
    private Asset_Response response;

    @BeforeEach
    void setUp() {
        asset = Asset.builder()
                .assetId("asset-01")
                .assetCode("BAN-01")
                .assetName("Bàn làm việc")
                .category("Nội thất")
                .unitPrice(new BigDecimal("500000.00"))
                .status("IN_USE")
                .build();

        request = Asset_Request.builder()
                .assetCode("BAN-01")
                .assetName("Bàn làm việc")
                .category("Nội thất")
                .unitPrice(new BigDecimal("500000.00"))
                .status("IN_USE")
                .build();

        response = Asset_Response.builder()
                .assetId("asset-01")
                .assetCode("BAN-01")
                .assetName("Bàn làm việc")
                .category("Nội thất")
                .unitPrice(new BigDecimal("500000.00"))
                .status("IN_USE")
                .build();
    }

    @Test
    @DisplayName("Tạo tài sản mới thành công")
    void create_success() {
        when(assetRepository.existsByAssetCode("BAN-01")).thenReturn(false);
        when(assetMapper.toEntity(request)).thenReturn(asset);
        when(assetRepository.save(any(Asset.class))).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(response);

        Asset_Response result = assetService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getAssetCode()).isEqualTo("BAN-01");
        verify(assetRepository).save(asset);
    }

    @Test
    @DisplayName("Tạo tài sản thất bại khi trùng mã tài sản")
    void create_failed_duplicateCode() {
        when(assetRepository.existsByAssetCode("BAN-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> assetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại");
    }

    @Test
    @DisplayName("Tạo tài sản thất bại khi hạn bảo hành trước ngày mua")
    void create_failed_warrantyBeforePurchaseDate() {
        request.setPurchaseDate(LocalDate.of(2025, 6, 1));
        request.setWarrantyUntil(LocalDate.of(2025, 1, 1));

        AppException ex = assertThrows(AppException.class, () -> assetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Hạn bảo hành không được trước ngày mua");
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo tài sản thất bại khi trạng thái không hợp lệ")
    void create_failed_invalidStatus() {
        request.setStatus("INVALID_STATUS");

        AppException ex = assertThrows(AppException.class, () -> assetService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Trạng thái tài sản không hợp lệ");
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật tài sản thành công chỉ gọi save() một lần")
    void update_success() {
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(response);

        Asset_Response result = assetService.update("asset-01", request);

        assertThat(result).isNotNull();
        verify(assetRepository, times(1)).save(asset);
    }

    @Test
    @DisplayName("Cập nhật tài sản thất bại khi hạn bảo hành trước ngày mua")
    void update_failed_warrantyBeforePurchaseDate() {
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        request.setPurchaseDate(LocalDate.of(2025, 6, 1));
        request.setWarrantyUntil(LocalDate.of(2025, 1, 1));

        AppException ex = assertThrows(AppException.class, () -> assetService.update("asset-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Hạn bảo hành không được trước ngày mua");
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Phân trang danh sách tài sản với whitelist sort fallback")
    void getAll_paged_withSortFallback() {
        Page<Asset> page = new PageImpl<>(List.of(asset));
        when(assetRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(assetMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Asset_Response> res = assetService.getAll(1, 10, "invalid_sort_col", "asc");

        assertThat(res).isNotNull();
        assertThat(res.getData()).hasSize(1);
        verify(assetRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Lấy thông tin tài sản theo ID thành công")
    void getById_success() {
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(assetMapper.toResponse(asset)).thenReturn(response);

        Asset_Response result = assetService.getById("asset-01");

        assertThat(result).isNotNull();
        assertThat(result.getAssetId()).isEqualTo("asset-01");
    }

    @Test
    @DisplayName("Lấy thông tin tài sản theo ID thất bại khi không tìm thấy")
    void getById_notFound() {
        when(assetRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> assetService.getById("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Xóa tài sản thành công khi chưa gán cho phòng nào")
    void delete_success() {
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(roomAssetRepository.existsByAsset_AssetId("asset-01")).thenReturn(false);

        assetService.delete("asset-01");

        verify(assetRepository).delete(asset);
    }

    @Test
    @DisplayName("Xóa tài sản thất bại khi tài sản không tồn tại")
    void delete_failed_notFound() {
        when(assetRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> assetService.delete("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Xóa tài sản thất bại khi tài sản đang được bố trí tại các phòng (ASSET_IN_USE)")
    void delete_failed_inUse() {
        when(assetRepository.findById("asset-01")).thenReturn(Optional.of(asset));
        when(roomAssetRepository.existsByAsset_AssetId("asset-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> assetService.delete("asset-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ASSET_IN_USE);
    }
}

