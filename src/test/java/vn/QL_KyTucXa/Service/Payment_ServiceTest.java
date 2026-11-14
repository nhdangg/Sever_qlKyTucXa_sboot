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
import vn.QL_KyTucXa.DTO.Request.Payment_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Payment_Response;
import vn.QL_KyTucXa.Entity.Invoice;
import vn.QL_KyTucXa.Entity.Payment;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Payment_Mapper;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Payment_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Payment_ServiceTest {

    @Mock
    private Payment_Repository paymentRepository;
    @Mock
    private Invoice_Repository invoiceRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private Payment_Mapper paymentMapper;
    @Mock
    private vn.QL_KyTucXa.Repository.Violation_Repository violationRepository;

    @InjectMocks
    private Payment_Service paymentService;

    private Invoice invoice;
    private Payment payment;
    private Payment_Request request;
    private Payment_Response response;
    private Staff staff;

    @BeforeEach
    void setUp() {
        staff = Staff.builder().staffId("staff-01").fullName("Thu ngân").build();

        invoice = Invoice.builder()
                .invoiceId("inv-01")
                .invoiceCode("INV-202611-001")
                .totalAmount(new BigDecimal("1000000.00"))
                .paidAmount(BigDecimal.ZERO)
                .status("ISSUED")
                .dueDate(LocalDate.now().plusDays(10))
                .build();

        payment = Payment.builder()
                .paymentId("pay-01")
                .paymentCode("PAY-001")
                .invoice(invoice)
                .paymentMethod("BANK_TRANSFER")
                .amount(new BigDecimal("500000.00"))
                .status("SUCCESS")
                .receivedBy(staff)
                .paymentDate(LocalDateTime.now())
                .build();

        request = Payment_Request.builder()
                .paymentCode("PAY-001")
                .invoiceId("inv-01")
                .paymentMethod("BANK_TRANSFER")
                .amount(new BigDecimal("500000.00"))
                .status("SUCCESS")
                .build();

        lenient().when(currentUserService.hasRole("STAFF")).thenReturn(true);
        lenient().when(currentUserService.hasRole("MANAGER")).thenReturn(true);
        lenient().when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        lenient().when(currentUserService.hasPermission("PAYMENT_MANAGE")).thenReturn(true);

        response = Payment_Response.builder()
                .paymentId("pay-01")
                .paymentCode("PAY-001")
                .invoiceId("inv-01")
                .amount(new BigDecimal("500000.00"))
                .status("SUCCESS")
                .build();
    }

    @Test
    @DisplayName("Thanh toán một phần hóa đơn thành công và chuyển trạng thái sang PARTIALLY_PAID")
    void create_success_partialPayment() {
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByPaymentCode("PAY-001")).thenReturn(false);
        when(paymentMapper.toEntity(request)).thenReturn(payment);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response);
        lenient().when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenReturn(List.of(payment));

        Payment_Response result = paymentService.create(request);

        assertThat(result).isNotNull();
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("500000.00");
        assertThat(invoice.getStatus()).isEqualTo("PARTIALLY_PAID");
        verify(invoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Thanh toán đủ số tiền hóa đơn chuyển trạng thái sang PAID")
    void create_success_fullPayment() {
        request.setAmount(new BigDecimal("1000000.00"));
        payment.setAmount(new BigDecimal("1000000.00"));

        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByPaymentCode("PAY-001")).thenReturn(false);
        when(paymentMapper.toEntity(request)).thenReturn(payment);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response);
        lenient().when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenReturn(List.of(payment));

        Payment_Response result = paymentService.create(request);

        assertThat(result).isNotNull();
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("1000000.00");
        assertThat(invoice.getStatus()).isEqualTo("PAID");
        verify(invoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Tự động sinh mã thanh toán khi không truyền paymentCode")
    void create_autoGeneratePaymentCode() {
        request.setPaymentCode(null);

        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(paymentMapper.toEntity(request)).thenReturn(payment);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response);
        lenient().when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenReturn(List.of(payment));

        Payment_Response result = paymentService.create(request);

        assertThat(result).isNotNull();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Thanh toán thất bại khi hóa đơn không tồn tại")
    void create_failed_invoiceNotFound() {
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVOICE_NOT_FOUND);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi hóa đơn đang ở trạng thái DRAFT")
    void create_failed_draftInvoice() {
        invoice.setStatus("DRAFT");
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi hóa đơn đã bị hủy CANCELLED")
    void create_failed_cancelledInvoice() {
        invoice.setStatus("CANCELLED");
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi hóa đơn đã thanh toán đầy đủ PAID")
    void create_failed_alreadyPaidInvoice() {
        invoice.setStatus("PAID");
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi số tiền thanh toán vượt quá số tiền còn lại")
    void create_failed_amountExceedsRemaining() {
        invoice.setPaidAmount(new BigDecimal("800000.00"));
        request.setAmount(new BigDecimal("300000.00")); // Còn lại 200,000 nhưng trả 300,000

        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi phương thức thanh toán không hợp lệ")
    void create_failed_invalidPaymentMethod() {
        request.setPaymentMethod("BITCOIN");

        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Hủy giao dịch thanh toán chuyển thành CANCELLED và tính lại tiền hóa đơn")
    void delete_cancelsPayment_andRecalculatesInvoice() {
        invoice.setPaidAmount(new BigDecimal("500000.00"));
        invoice.setStatus("PARTIALLY_PAID");

        payment.setStatus("SUCCESS");
        when(paymentRepository.findById("pay-01")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenAnswer(invocation -> List.of(payment));

        paymentService.delete("pay-01");

        assertThat(payment.getStatus()).isEqualTo("CANCELLED");
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo("ISSUED");
        verify(invoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Lấy thông tin giao dịch thanh toán theo ID")
    void getById_success() {
        when(paymentRepository.findById("pay-01")).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        Payment_Response result = paymentService.getById("pay-01");

        assertThat(result).isNotNull();
        assertThat(result.getPaymentId()).isEqualTo("pay-01");
    }

    @Test
    @DisplayName("Lấy danh sách giao dịch thanh toán theo mã hóa đơn")
    void getByInvoiceId_success() {
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));
        lenient().when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenReturn(List.of(payment));
        when(paymentMapper.toResponseList(List.of(payment))).thenReturn(List.of(response));

        List<Payment_Response> result = paymentService.getByInvoiceId("inv-01");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm giao dịch thanh toán theo tiêu chí phân trang")
    void getAll_withSearchCriteria() {
        Page<Payment> page = new PageImpl<>(List.of(payment));
        when(paymentRepository.searchPayments(eq("INV-202611-001"), eq("BANK_TRANSFER"), eq("SUCCESS"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(paymentMapper.toResponseList(List.of(payment))).thenReturn(List.of(response));

        Page_Response<Payment_Response> result = paymentService.getAll(
                1, 10, "INV-202611-001", "BANK_TRANSFER", "SUCCESS", LocalDate.now(), LocalDate.now(), "paymentDate", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Người thu tiền được lấy tự động từ JWT của nhân viên đang đăng nhập")
    void create_receiverResolvedFromJwt() {
        when(currentUserService.findStaffId()).thenReturn(Optional.of("staff-01"));
        when(staffRepository.findById("staff-01")).thenReturn(Optional.of(staff));

        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByPaymentCode("PAY-001")).thenReturn(false);
        when(paymentMapper.toEntity(request)).thenReturn(payment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(response);
        lenient().when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenReturn(List.of(payment));

        Payment_Response result = paymentService.create(request);

        assertThat(result).isNotNull();
        assertThat(payment.getReceivedBy()).isEqualTo(staff);
    }

    @Test
    @DisplayName("Client gửi receivedBy giả mạo nhưng không có quyền thì receivedBy bị gán null")
    void create_unauthorizedClientCannotSpoofReceiver() {
        request.setReceivedBy("spoofed-staff-999");
        request.setStatus("PENDING");
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());
        when(currentUserService.hasPermission("PAYMENT_MANAGE")).thenReturn(false);
        when(currentUserService.hasRole("STAFF")).thenReturn(false);
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);

        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByPaymentCode("PAY-001")).thenReturn(false);
        when(paymentMapper.toEntity(request)).thenReturn(payment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(response);
        lenient().when(paymentRepository.findByInvoice_InvoiceId("inv-01")).thenReturn(List.of(payment));

        Payment_Response result = paymentService.create(request);

        assertThat(result).isNotNull();
        assertThat(payment.getReceivedBy()).isNull();
    }

    @Test
    @DisplayName("Lấy giao dịch thanh toán theo ID thất bại khi sinh viên xem giao dịch của người khác")
    void getById_failed_unauthorizedForOtherStudent() {
        vn.QL_KyTucXa.Entity.Student stu = vn.QL_KyTucXa.Entity.Student.builder().studentId("stu-01").build();
        invoice.setStudent(stu);
        when(paymentRepository.findById("pay-01")).thenReturn(Optional.of(payment));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> paymentService.getById("pay-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lấy danh sách thanh toán theo mã hóa đơn thất bại khi sinh viên xem của người khác")
    void getByInvoiceId_failed_unauthorizedForOtherStudent() {
        vn.QL_KyTucXa.Entity.Student stu = vn.QL_KyTucXa.Entity.Student.builder().studentId("stu-01").build();
        invoice.setStudent(stu);
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> paymentService.getByInvoiceId("inv-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Tạo thanh toán thất bại khi sinh viên cố thanh toán cho hóa đơn của người khác")
    void create_failed_unauthorizedForOtherStudent() {
        vn.QL_KyTucXa.Entity.Student stu = vn.QL_KyTucXa.Entity.Student.builder().studentId("stu-01").build();
        invoice.setStudent(stu);
        when(invoiceRepository.findByIdForUpdate("inv-01")).thenReturn(Optional.of(invoice));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> paymentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
