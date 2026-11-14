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
import vn.QL_KyTucXa.DTO.Request.RegistrationPeriod_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RegistrationPeriod_Response;
import vn.QL_KyTucXa.Entity.RegistrationPeriod;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RegistrationPeriod_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.RegistrationPeriod_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationPeriod_ServiceTest {

    @Mock
    private RegistrationPeriod_Repository periodRepository;

    @Mock
    private Contract_Repository contractRepository;

    @Mock
    private RegistrationPeriod_Mapper periodMapper;

    @InjectMocks
    private RegistrationPeriod_Service periodService;

    private RegistrationPeriod period;
    private RegistrationPeriod_Request request;
    private RegistrationPeriod_Response response;

    @BeforeEach
    void setUp() {
        period = RegistrationPeriod.builder()
                .periodId("period-01")
                .periodName("Đợt 1 Học kỳ 1")
                .academicYear("2026-2027")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(30))
                .registrationDeadline(LocalDate.now().plusDays(25))
                .status("UPCOMING")
                .build();

        request = RegistrationPeriod_Request.builder()
                .periodName("Đợt 1 Học kỳ 1")
                .academicYear("2026-2027")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(30))
                .registrationDeadline(LocalDate.now().plusDays(25))
                .build();

        response = RegistrationPeriod_Response.builder()
                .periodId("period-01")
                .periodName("Đợt 1 Học kỳ 1")
                .academicYear("2026-2027")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(30))
                .registrationDeadline(LocalDate.now().plusDays(25))
                .status("UPCOMING")
                .build();
    }

    @Test
    @DisplayName("Tạo mới đợt đăng ký thành công và tự động tính trạng thái UPCOMING khi ngày bắt đầu ở tương lai")
    void create_success_autoCalculateUpcomingStatus() {
        when(periodRepository.hasOverlappingPeriod(any(), any(), eq("2026-2027"), isNull())).thenReturn(false);
        when(periodMapper.toEntity(any(RegistrationPeriod_Request.class))).thenReturn(period);
        when(periodRepository.save(any(RegistrationPeriod.class))).thenReturn(period);
        when(periodMapper.toResponse(period)).thenReturn(response);

        RegistrationPeriod_Response result = periodService.create(request);

        assertThat(result).isNotNull();
        assertThat(request.getStatus()).isEqualTo("UPCOMING");
        verify(periodRepository).save(any(RegistrationPeriod.class));
    }

    @Test
    @DisplayName("Tạo mới đợt đăng ký thành công với trạng thái hợp lệ do client truyền")
    void create_success_withExplicitValidStatus() {
        request.setStatus("open");
        when(periodRepository.hasOverlappingPeriod(any(), any(), eq("2026-2027"), isNull())).thenReturn(false);
        when(periodMapper.toEntity(any(RegistrationPeriod_Request.class))).thenReturn(period);
        when(periodRepository.save(any(RegistrationPeriod.class))).thenReturn(period);
        when(periodMapper.toResponse(period)).thenReturn(response);

        RegistrationPeriod_Response result = periodService.create(request);

        assertThat(result).isNotNull();
        assertThat(request.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Tạo mới thất bại khi ngày kết thúc trước ngày bắt đầu")
    void create_failed_endDateBeforeStartDate() {
        request.setStartDate(LocalDate.now().plusDays(10));
        request.setEndDate(LocalDate.now().plusDays(5));

        AppException ex = assertThrows(AppException.class, () -> periodService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
    }

    @Test
    @DisplayName("Tạo mới thất bại khi hạn nộp hồ sơ trước ngày bắt đầu đợt")
    void create_failed_deadlineBeforeStartDate() {
        request.setStartDate(LocalDate.now().plusDays(10));
        request.setEndDate(LocalDate.now().plusDays(30));
        request.setRegistrationDeadline(LocalDate.now().plusDays(5));

        AppException ex = assertThrows(AppException.class, () -> periodService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Hạn nộp hồ sơ đăng ký không thể trước ngày bắt đầu đợt");
    }

    @Test
    @DisplayName("Tạo mới thất bại khi hạn nộp hồ sơ sau ngày kết thúc đợt")
    void create_failed_deadlineAfterEndDate() {
        request.setStartDate(LocalDate.now().plusDays(5));
        request.setEndDate(LocalDate.now().plusDays(20));
        request.setRegistrationDeadline(LocalDate.now().plusDays(25));

        AppException ex = assertThrows(AppException.class, () -> periodService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Hạn nộp hồ sơ đăng ký không thể sau ngày kết thúc đợt");
    }

    @Test
    @DisplayName("Tạo mới thất bại khi đợt đăng ký bị trùng lặp thời gian trong cùng năm học")
    void create_failed_overlappingPeriod() {
        when(periodRepository.hasOverlappingPeriod(any(), any(), eq("2026-2027"), isNull())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> periodService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("trùng lặp với đợt đăng ký khác trong năm học");
    }

    @Test
    @DisplayName("Tạo mới thất bại khi trạng thái client gửi không nằm trong whitelist")
    void create_failed_invalidStatus() {
        request.setStatus("INVALID_STATUS");

        AppException ex = assertThrows(AppException.class, () -> periodService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không hợp lệ. Cho phép:");
    }

    @Test
    @DisplayName("Lấy chi tiết đợt đăng ký theo ID thành công")
    void getById_success() {
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(periodMapper.toResponse(period)).thenReturn(response);

        RegistrationPeriod_Response result = periodService.getById("period-01");

        assertThat(result).isNotNull();
        assertThat(result.getPeriodId()).isEqualTo("period-01");
    }

    @Test
    @DisplayName("Lấy chi tiết đợt đăng ký ném lỗi khi ID không tồn tại")
    void getById_notFound() {
        when(periodRepository.findById("period-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> periodService.getById("period-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lấy danh sách đợt đăng ký lọc theo cả năm học và trạng thái")
    void getAll_unpaged_filterByYearAndStatus() {
        when(periodRepository.findByAcademicYearAndStatus("2026-2027", "UPCOMING")).thenReturn(List.of(period));
        when(periodMapper.toResponseList(List.of(period))).thenReturn(List.of(response));

        List<RegistrationPeriod_Response> result = periodService.getAll("2026-2027", "upcoming");

        assertThat(result).hasSize(1);
        verify(periodRepository).findByAcademicYearAndStatus("2026-2027", "UPCOMING");
    }

    @Test
    @DisplayName("Lấy danh sách đợt đăng ký lọc chỉ theo năm học")
    void getAll_unpaged_filterByYearOnly() {
        when(periodRepository.findByAcademicYear("2026-2027")).thenReturn(List.of(period));
        when(periodMapper.toResponseList(List.of(period))).thenReturn(List.of(response));

        List<RegistrationPeriod_Response> result = periodService.getAll("2026-2027", null);

        assertThat(result).hasSize(1);
        verify(periodRepository).findByAcademicYear("2026-2027");
    }

    @Test
    @DisplayName("Lấy danh sách đợt đăng ký lọc chỉ theo trạng thái")
    void getAll_unpaged_filterByStatusOnly() {
        when(periodRepository.findByStatus("OPEN")).thenReturn(List.of(period));
        when(periodMapper.toResponseList(List.of(period))).thenReturn(List.of(response));

        List<RegistrationPeriod_Response> result = periodService.getAll(null, "open");

        assertThat(result).hasSize(1);
        verify(periodRepository).findByStatus("OPEN");
    }

    @Test
    @DisplayName("Lấy danh sách phân trang đợt đăng ký với tiêu chí tìm kiếm")
    void getAll_paged_withSearchCriteria() {
        Page<RegistrationPeriod> page = new PageImpl<>(List.of(period));
        when(periodRepository.searchPeriods(eq("2026-2027"), eq("UPCOMING"), any(Pageable.class))).thenReturn(page);
        when(periodMapper.toResponseList(List.of(period))).thenReturn(List.of(response));

        Page_Response<RegistrationPeriod_Response> result = periodService.getAll(
                1, 10, "2026-2027", "UPCOMING", "startDate", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Cập nhật thông tin đợt đăng ký thành công")
    void update_success() {
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(periodRepository.hasOverlappingPeriod(any(), any(), eq("2026-2027"), eq("period-01"))).thenReturn(false);
        when(periodRepository.save(any(RegistrationPeriod.class))).thenReturn(period);
        when(periodMapper.toResponse(period)).thenReturn(response);

        RegistrationPeriod_Response result = periodService.update("period-01", request);

        assertThat(result).isNotNull();
        verify(periodMapper).updateEntityFromRequest(period, request);
        verify(periodRepository).save(period);
    }

    @Test
    @DisplayName("Cập nhật đợt đăng ký đã CANCELLED mà không gửi status thì giữ nguyên CANCELLED, không tự tính lại theo ngày")
    void update_cancelledPeriod_keepsStatusCancelledWhenStatusNotProvided() {
        period.setStatus("CANCELLED");
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(periodRepository.hasOverlappingPeriod(any(), any(), eq("2026-2027"), eq("period-01"))).thenReturn(false);
        when(periodRepository.save(any(RegistrationPeriod.class))).thenReturn(period);
        when(periodMapper.toResponse(period)).thenReturn(response);

        RegistrationPeriod_Request updateReq = RegistrationPeriod_Request.builder()
                .periodName("Đợt cập nhật")
                .academicYear("2026-2027")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .registrationDeadline(LocalDate.now().plusDays(5))
                .status(null)
                .build();

        periodService.update("period-01", updateReq);

        assertThat(updateReq.getStatus()).isEqualTo("CANCELLED");
        verify(periodRepository).save(period);
    }

    @Test
    @DisplayName("Xóa đợt đăng ký thành công khi chưa phát sinh hợp đồng")
    void delete_success_whenNoContracts() {
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(contractRepository.existsByPeriod_PeriodId("period-01")).thenReturn(false);

        periodService.delete("period-01");

        verify(periodRepository).delete(period);
    }

    @Test
    @DisplayName("Xóa đợt đăng ký thất bại khi đã phát sinh hợp đồng sinh viên (chặn lỗi FK)")
    void delete_failed_whenContractsExist() {
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(contractRepository.existsByPeriod_PeriodId("period-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> periodService.delete("period-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể xóa đợt đăng ký đã phát sinh hợp đồng");
        verify(periodRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Tác vụ tự động quét và cập nhật trạng thái đợt đăng ký theo ngày")
    void autoUpdatePeriodStatuses_transitionsStatuses() {
        // Đợt 1: Ngày bắt đầu ở quá khứ, chưa hết hạn -> Chuyển thành OPEN
        RegistrationPeriod periodToOpen = RegistrationPeriod.builder()
                .periodId("p-open")
                .periodName("Đợt mở")
                .startDate(LocalDate.now().minusDays(2))
                .endDate(LocalDate.now().plusDays(10))
                .registrationDeadline(LocalDate.now().plusDays(5))
                .status("UPCOMING")
                .build();

        // Đợt 2: Đã quá hạn đăng ký -> Chuyển thành CLOSED
        RegistrationPeriod periodToClose = RegistrationPeriod.builder()
                .periodId("p-close")
                .periodName("Đợt đóng")
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().minusDays(1))
                .registrationDeadline(LocalDate.now().minusDays(3))
                .status("OPEN")
                .build();

        // Đợt 3: Đã bị CANCELLED bằng tay -> Giữ nguyên CANCELLED
        RegistrationPeriod cancelledPeriod = RegistrationPeriod.builder()
                .periodId("p-cancelled")
                .periodName("Đợt hủy")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .status("CANCELLED")
                .build();

        when(periodRepository.findAll()).thenReturn(List.of(periodToOpen, periodToClose, cancelledPeriod));

        int updated = periodService.autoUpdatePeriodStatuses();

        assertThat(updated).isEqualTo(2);
        assertThat(periodToOpen.getStatus()).isEqualTo("OPEN");
        assertThat(periodToClose.getStatus()).isEqualTo("CLOSED");
        assertThat(cancelledPeriod.getStatus()).isEqualTo("CANCELLED");
        verify(periodRepository, times(2)).save(any(RegistrationPeriod.class));
    }
}

