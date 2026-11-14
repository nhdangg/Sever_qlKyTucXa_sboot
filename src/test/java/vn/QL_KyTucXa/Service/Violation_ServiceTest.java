package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.Violation_Request;
import vn.QL_KyTucXa.DTO.Response.Violation_Response;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.Violation;
import vn.QL_KyTucXa.Entity.ViolationType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Violation_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.InvoiceItem_Repository;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.ViolationType_Repository;
import vn.QL_KyTucXa.Repository.Violation_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Violation_ServiceTest {

    @Mock
    private Violation_Repository violationRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private ViolationType_Repository violationTypeRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private Invoice_Repository invoiceRepository;
    @Mock
    private InvoiceItem_Repository invoiceItemRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private Violation_Mapper violationMapper;

    @InjectMocks
    private Violation_Service violationService;

    private Student student;
    private ViolationType violationType;
    private Violation violation;
    private Violation_Request request;
    private Violation_Response response;

    @BeforeEach
    void setUp() {
        student = Student.builder().studentId("st-01").studentCode("SV001").fullName("Nguyễn Văn B").build();
        violationType = ViolationType.builder()
                .violationTypeId("vt-01")
                .violationName("Gây mất trật tự ban đêm")
                .severityLevel("MINOR")
                .penaltyPoints(5)
                .fineAmount(BigDecimal.ZERO)
                .build();

        violation = Violation.builder()
                .violationId("v-01")
                .student(student)
                .violationType(violationType)
                .violationDate(LocalDate.now())
                .fineAmount(BigDecimal.ZERO)
                .penaltyPointsApplied(5)
                .status("RECORDED")
                .build();

        request = Violation_Request.builder()
                .studentId("st-01")
                .violationTypeId("vt-01")
                .violationDate(LocalDate.now())
                .fineAmount(BigDecimal.ZERO)
                .penaltyPointsApplied(5)
                .build();

        response = Violation_Response.builder()
                .violationId("v-01")
                .studentId("st-01")
                .status("RECORDED")
                .build();

        lenient().when(currentUserService.hasRole("STUDENT")).thenReturn(false);
        lenient().when(currentUserService.hasRole("ADMIN")).thenReturn(true);
    }

    @Test
    @DisplayName("Lập biên bản vi phạm thành công")
    void create_success() {
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(violationTypeRepository.findById("vt-01")).thenReturn(Optional.of(violationType));
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());
        when(violationRepository.save(any(Violation.class))).thenReturn(violation);
        when(violationMapper.toResponse(any(Violation.class))).thenReturn(response);

        Violation_Response res = violationService.create(request);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo("RECORDED");
        verify(violationRepository).save(any(Violation.class));
    }

    @Test
    @DisplayName("Lập biên bản thất bại khi sinh viên không tồn tại (không dự phòng người đầu tiên)")
    void create_failed_studentNotFound() {
        when(studentRepository.findById("st-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> violationService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Lập biên bản thất bại khi loại vi phạm không tồn tại (không tự sinh)")
    void create_failed_violationTypeNotFound() {
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(violationTypeRepository.findById("vt-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> violationService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Lập biên bản thất bại khi ngày vi phạm ở tương lai")
    void create_failed_futureDate() {
        request.setViolationDate(LocalDate.now().plusDays(2));
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(violationTypeRepository.findById("vt-01")).thenReturn(Optional.of(violationType));

        AppException ex = assertThrows(AppException.class, () -> violationService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa biên bản chuyển trạng thái CANCELLED")
    void delete_softDelete() {
        when(violationRepository.findById("v-01")).thenReturn(Optional.of(violation));

        violationService.delete("v-01");

        assertThat(violation.getStatus()).isEqualTo("CANCELLED");
        verify(violationRepository).save(violation);
    }

    @Test
    @DisplayName("Nhân viên thường không được chỉ định người xử lý khác: bắt buộc lấy từ JWT")
    void create_nonAdmin_ignoresRequestedHandledByAndUsesJwt() {
        request.setHandledBy("staff-fake");
        vn.QL_KyTucXa.Entity.Staff jwtStaff = vn.QL_KyTucXa.Entity.Staff.builder().staffId("staff-real").build();

        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(violationTypeRepository.findById("vt-01")).thenReturn(Optional.of(violationType));
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.findStaffId()).thenReturn(Optional.of("staff-real"));
        when(staffRepository.findById("staff-real")).thenReturn(Optional.of(jwtStaff));
        when(violationRepository.save(any(Violation.class))).thenAnswer(i -> i.getArgument(0));
        when(violationMapper.toResponse(any(Violation.class))).thenReturn(response);

        Violation_Response res = violationService.create(request);

        assertThat(res).isNotNull();
        verify(staffRepository, never()).findById("staff-fake");
        verify(violationRepository).save(argThat(v -> v.getHandledBy() != null && "staff-real".equals(v.getHandledBy().getStaffId())));
    }

    @Test
    @DisplayName("Sinh viên xem chi tiết biên bản của sinh viên khác: bị chặn lỗi UNAUTHORIZED")
    void getById_studentUnauthorized_whenAccessingOtherStudentViolation() {
        when(violationRepository.findById("v-01")).thenReturn(Optional.of(violation));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("st-other"));

        AppException ex = assertThrows(AppException.class, () -> violationService.getById("v-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Chặn sửa số tiền phạt khi khoản phạt đã vào hóa đơn")
    void update_blocksFineAmountChange_whenFineInInvoice() {
        when(violationRepository.findById("v-01")).thenReturn(Optional.of(violation));
        when(invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", "v-01")).thenReturn(true);

        Violation_Request updateReq = Violation_Request.builder()
                .fineAmount(new BigDecimal("500000"))
                .build();

        AppException ex = assertThrows(AppException.class, () -> violationService.update("v-01", updateReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Chặn markAsPaid khi khoản phạt đang nằm trong hóa đơn chưa thanh toán")
    void markAsPaid_blocks_whenFineInUnpaidInvoice() {
        when(violationRepository.findById("v-01")).thenReturn(Optional.of(violation));
        vn.QL_KyTucXa.Entity.Invoice unpaidInv = vn.QL_KyTucXa.Entity.Invoice.builder()
                .invoiceId("inv-01")
                .invoiceCode("HD-001")
                .status("ISSUED")
                .build();
        vn.QL_KyTucXa.Entity.InvoiceItem fineItem = vn.QL_KyTucXa.Entity.InvoiceItem.builder()
                .invoice(unpaidInv)
                .itemType("FINE")
                .referenceId("v-01")
                .build();

        when(invoiceItemRepository.findByItemTypeAndReferenceId("FINE", "v-01")).thenReturn(List.of(fineItem));

        AppException ex = assertThrows(AppException.class, () -> violationService.markAsPaid("v-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tổng hợp điểm kỷ luật của sinh viên tính đúng điểm và mức cảnh cáo")
    void getStudentViolationSummary_success() {
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(violationRepository.findByStudent_StudentId("st-01")).thenReturn(List.of(violation));
        when(contractRepository.findByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(List.of());

        vn.QL_KyTucXa.DTO.Response.StudentViolationSummary_Response summary =
                violationService.getStudentViolationSummary("st-01");

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalPenaltyPoints()).isEqualTo(5);
        assertThat(summary.getWarningLevel()).isEqualTo("LEVEL_1");
    }
}

