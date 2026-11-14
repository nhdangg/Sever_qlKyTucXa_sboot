package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.ServiceType_Request;
import vn.QL_KyTucXa.DTO.Response.ServiceType_Response;
import vn.QL_KyTucXa.Entity.ServiceType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.ServiceType_Mapper;
import vn.QL_KyTucXa.Repository.ServiceType_Repository;
import vn.QL_KyTucXa.Repository.UtilityReading_Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceType_ServiceTest {

    @Mock
    private ServiceType_Repository serviceTypeRepository;

    @Mock
    private ServiceType_Mapper serviceTypeMapper;

    @Mock
    private UtilityReading_Repository utilityReadingRepository;

    @InjectMocks
    private ServiceType_Service serviceTypeService;

    private ServiceType sampleServiceType;
    private ServiceType_Request sampleRequest;
    private ServiceType_Response sampleResponse;

    @BeforeEach
    void setUp() {
        sampleServiceType = ServiceType.builder()
                .serviceTypeId("st-1")
                .serviceCode("DIEN")
                .serviceName("Tiền điện")
                .unit("kWh")
                .unitPrice(new BigDecimal("3000.00"))
                .billingType("METERED")
                .description("Điện sinh hoạt")
                .build();

        sampleRequest = ServiceType_Request.builder()
                .serviceCode("DIEN")
                .serviceName("Tiền điện")
                .unit("kWh")
                .unitPrice(new BigDecimal("3000.00"))
                .billingType("METERED")
                .description("Điện sinh hoạt")
                .build();

        sampleResponse = ServiceType_Response.builder()
                .serviceTypeId("st-1")
                .serviceCode("DIEN")
                .serviceName("Tiền điện")
                .unit("kWh")
                .unitPrice(new BigDecimal("3000.00"))
                .billingType("METERED")
                .description("Điện sinh hoạt")
                .build();
    }

    // ==================== CREATE TESTS ====================

    @Test
    @DisplayName("create - Thành công với billingType chuẩn")
    void create_success_withValidBillingType() {
        when(serviceTypeRepository.existsByServiceCode("DIEN")).thenReturn(false);
        when(serviceTypeMapper.toEntity(any(ServiceType_Request.class))).thenReturn(sampleServiceType);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.create(sampleRequest);

        assertThat(result).isNotNull();
        assertThat(result.getServiceCode()).isEqualTo("DIEN");
        verify(serviceTypeRepository).save(any(ServiceType.class));
    }

    @Test
    @DisplayName("create - Thành công khi billingType là null (mặc định METERED, không NPE)")
    void create_success_withNullBillingType() {
        sampleRequest.setBillingType(null);
        when(serviceTypeRepository.existsByServiceCode("DIEN")).thenReturn(false);
        when(serviceTypeMapper.toEntity(any(ServiceType_Request.class))).thenReturn(sampleServiceType);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.create(sampleRequest);

        assertThat(result).isNotNull();
        assertThat(sampleRequest.getBillingType()).isEqualTo("METERED");
        verify(serviceTypeRepository).save(any(ServiceType.class));
    }

    @Test
    @DisplayName("create - Thành công khi billingType là chuỗi rỗng/trắng (mặc định METERED)")
    void create_success_withBlankBillingType() {
        sampleRequest.setBillingType("   ");
        when(serviceTypeRepository.existsByServiceCode("DIEN")).thenReturn(false);
        when(serviceTypeMapper.toEntity(any(ServiceType_Request.class))).thenReturn(sampleServiceType);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.create(sampleRequest);

        assertThat(result).isNotNull();
        assertThat(sampleRequest.getBillingType()).isEqualTo("METERED");
    }

    @Test
    @DisplayName("create - Thành công với chuẩn hóa chữ thường và khoảng trắng (' metered ' -> 'METERED')")
    void create_success_withCaseNormalization() {
        sampleRequest.setBillingType("  metered  ");
        when(serviceTypeRepository.existsByServiceCode("DIEN")).thenReturn(false);
        when(serviceTypeMapper.toEntity(any(ServiceType_Request.class))).thenReturn(sampleServiceType);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.create(sampleRequest);

        assertThat(result).isNotNull();
        assertThat(sampleRequest.getBillingType()).isEqualTo("METERED");
    }

    @Test
    @DisplayName("create - Thất bại khi billingType không hợp lệ")
    void create_failed_invalidBillingType() {
        sampleRequest.setBillingType("INVALID_BILLING");
        when(serviceTypeRepository.existsByServiceCode("DIEN")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.create(sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không tìm thấy phương thức tính phí 'INVALID_BILLING'");
        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Thất bại khi trùng mã dịch vụ")
    void create_failed_duplicateServiceCode() {
        when(serviceTypeRepository.existsByServiceCode("DIEN")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.create(sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Mã dịch vụ 'DIEN' đã tồn tại");
        verify(serviceTypeRepository, never()).save(any());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    @DisplayName("update - Thành công khi cập nhật billingType hợp lệ mới")
    void update_success_withNewBillingType() {
        sampleRequest.setBillingType("flat");
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.update("st-1", sampleRequest);

        assertThat(result).isNotNull();
        assertThat(sampleRequest.getBillingType()).isEqualTo("FLAT");
        verify(serviceTypeMapper).updateEntityFromRequest(sampleServiceType, sampleRequest);
        verify(serviceTypeRepository).save(sampleServiceType);
    }

    @Test
    @DisplayName("update - Thành công khi billingType là null (không bắt buộc, giữ nguyên không ném NPE)")
    void update_success_withNullBillingType() {
        sampleRequest.setBillingType(null);
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.update("st-1", sampleRequest);

        assertThat(result).isNotNull();
        assertThat(sampleRequest.getBillingType()).isNull();
        verify(serviceTypeMapper).updateEntityFromRequest(sampleServiceType, sampleRequest);
        verify(serviceTypeRepository).save(sampleServiceType);
    }

    @Test
    @DisplayName("update - Thành công khi billingType là chuỗi rỗng (gán null để giữ nguyên)")
    void update_success_withBlankBillingType() {
        sampleRequest.setBillingType("   ");
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.update("st-1", sampleRequest);

        assertThat(result).isNotNull();
        assertThat(sampleRequest.getBillingType()).isNull();
    }

    @Test
    @DisplayName("update - Thất bại khi billingType có gửi nhưng sai giá trị")
    void update_failed_invalidBillingType() {
        sampleRequest.setBillingType("UNKNOWN");
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.update("st-1", sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không tìm thấy phương thức tính phí 'UNKNOWN'");
        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Thất bại khi đổi sang mã dịch vụ đã tồn tại của dịch vụ khác")
    void update_failed_duplicateServiceCode() {
        sampleRequest.setServiceCode("NUOC");
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(serviceTypeRepository.existsByServiceCode("NUOC")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.update("st-1", sampleRequest));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Mã dịch vụ 'NUOC' đã tồn tại");
        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Cho phép giữ nguyên mã dịch vụ cũ không bị báo trùng")
    void update_success_sameServiceCode() {
        sampleRequest.setServiceCode("dien"); // Chữ thường cùng mã
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenReturn(sampleServiceType);
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.update("st-1", sampleRequest);

        assertThat(result).isNotNull();
        verify(serviceTypeRepository, never()).existsByServiceCode(any());
    }

    // ==================== DELETE TESTS ====================

    @Test
    @DisplayName("delete - Thành công khi dịch vụ chưa có chỉ số điện nước tham chiếu")
    void delete_success() {
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(utilityReadingRepository.existsByServiceType_ServiceTypeId("st-1")).thenReturn(false);

        serviceTypeService.delete("st-1");

        verify(serviceTypeRepository).delete(sampleServiceType);
    }

    @Test
    @DisplayName("delete - Chặn xóa khi dịch vụ đã có dữ liệu ghi chỉ số điện nước")
    void delete_failed_referencedByUtilityReading() {
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(utilityReadingRepository.existsByServiceType_ServiceTypeId("st-1")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.delete("st-1"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể xóa loại dịch vụ này vì đã có dữ liệu ghi chỉ số điện nước liên quan");
        verify(serviceTypeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete - Thất bại khi không tìm thấy loại dịch vụ")
    void delete_failed_notFound() {
        when(serviceTypeRepository.findById("st-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.delete("st-999"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(serviceTypeRepository, never()).delete(any());
    }

    // ==================== GET TESTS ====================

    @Test
    @DisplayName("getById - Thành công")
    void getById_success() {
        when(serviceTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleServiceType));
        when(serviceTypeMapper.toResponse(sampleServiceType)).thenReturn(sampleResponse);

        ServiceType_Response result = serviceTypeService.getById("st-1");

        assertThat(result).isNotNull();
        assertThat(result.getServiceTypeId()).isEqualTo("st-1");
    }

    @Test
    @DisplayName("getById - Thất bại khi không tìm thấy")
    void getById_failed_notFound() {
        when(serviceTypeRepository.findById("st-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.getById("st-999"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("getAll - Thành công")
    void getAll_success() {
        when(serviceTypeRepository.findAll()).thenReturn(List.of(sampleServiceType));
        when(serviceTypeMapper.toResponseList(anyList())).thenReturn(List.of(sampleResponse));

        List<ServiceType_Response> list = serviceTypeService.getAll();

        assertThat(list).hasSize(1);
    }
}

