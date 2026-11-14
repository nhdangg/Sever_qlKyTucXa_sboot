package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.ViolationType_Request;
import vn.QL_KyTucXa.DTO.Response.ViolationType_Response;
import vn.QL_KyTucXa.Entity.ViolationType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.ViolationType_Mapper;
import vn.QL_KyTucXa.Repository.ViolationType_Repository;
import vn.QL_KyTucXa.Repository.Violation_Repository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViolationType_ServiceTest {

    @Mock
    private ViolationType_Repository violationTypeRepository;
    @Mock
    private Violation_Repository violationRepository;
    @Mock
    private ViolationType_Mapper violationTypeMapper;
    @Mock
    private CurrentUser_Service currentUserService;

    @InjectMocks
    private ViolationType_Service violationTypeService;

    private ViolationType violationType;
    private ViolationType_Request request;
    private ViolationType_Response response;

    @BeforeEach
    void setUp() {
        violationType = ViolationType.builder()
                .violationTypeId("vt-01")
                .violationName("Sử dụng thiết bị công suất cao")
                .severityLevel("MEDIUM")
                .penaltyPoints(10)
                .fineAmount(new BigDecimal("200000"))
                .build();

        request = ViolationType_Request.builder()
                .violationName("Sử dụng thiết bị công suất cao")
                .severityLevel("MEDIUM")
                .penaltyPoints(10)
                .fineAmount(new BigDecimal("200000"))
                .build();

        response = ViolationType_Response.builder()
                .violationTypeId("vt-01")
                .violationName("Sử dụng thiết bị công suất cao")
                .severityLevel("MEDIUM")
                .build();

        lenient().when(currentUserService.hasRole("ADMIN")).thenReturn(true);
    }

    @Test
    @DisplayName("Tạo loại vi phạm thành công")
    void create_success() {
        when(violationTypeRepository.existsByViolationName("Sử dụng thiết bị công suất cao")).thenReturn(false);
        when(violationTypeMapper.toEntity(request)).thenReturn(violationType);
        when(violationTypeRepository.save(any(ViolationType.class))).thenReturn(violationType);
        when(violationTypeMapper.toResponse(violationType)).thenReturn(response);

        ViolationType_Response res = violationTypeService.create(request);

        assertThat(res).isNotNull();
        assertThat(res.getViolationName()).isEqualTo("Sử dụng thiết bị công suất cao");
        verify(violationTypeRepository).save(any(ViolationType.class));
    }

    @Test
    @DisplayName("Tạo loại vi phạm thất bại khi trùng tên")
    void create_failed_duplicateName() {
        when(violationTypeRepository.existsByViolationName("Sử dụng thiết bị công suất cao")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> violationTypeService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa loại vi phạm thất bại khi đã có biên bản kỷ luật sử dụng")
    void delete_failed_alreadyUsedInViolations() {
        when(violationTypeRepository.findById("vt-01")).thenReturn(Optional.of(violationType));
        when(violationRepository.existsByViolationType_ViolationTypeId("vt-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> violationTypeService.delete("vt-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa loại vi phạm thành công khi chưa ai sử dụng")
    void delete_success() {
        when(violationTypeRepository.findById("vt-01")).thenReturn(Optional.of(violationType));
        when(violationRepository.existsByViolationType_ViolationTypeId("vt-01")).thenReturn(false);

        violationTypeService.delete("vt-01");

        verify(violationTypeRepository).delete(violationType);
    }
}

