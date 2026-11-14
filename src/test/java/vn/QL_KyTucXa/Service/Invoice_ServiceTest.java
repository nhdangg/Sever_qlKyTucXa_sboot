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
import vn.QL_KyTucXa.DTO.Request.InvoiceGenerate_Request;
import vn.QL_KyTucXa.DTO.Request.Invoice_Request;
import vn.QL_KyTucXa.DTO.Response.Invoice_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Invoice;
import vn.QL_KyTucXa.Entity.InvoiceItem;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Invoice_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Payment_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Invoice_ServiceTest {

    @Mock
    private Invoice_Repository invoiceRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private Payment_Repository paymentRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private Invoice_Mapper invoiceMapper;
    @Mock
    private SystemSetting_Service systemSettingService;
    @Mock
    private vn.QL_KyTucXa.Repository.UtilityReading_Repository utilityReadingRepository;
    @Mock
    private UtilityReading_Service utilityReadingService;
    @Mock
    private vn.QL_KyTucXa.Repository.VehicleRegistration_Repository vehicleRegistrationRepository;
    @Mock
    private vn.QL_KyTucXa.Repository.Violation_Repository violationRepository;
    @Mock
    private vn.QL_KyTucXa.Repository.InvoiceItem_Repository invoiceItemRepository;

    @InjectMocks
    private Invoice_Service invoiceService;

    private Contract contract;
    private Student student;
    private Invoice invoice;
    private Invoice_Request request;
    private Invoice_Response response;

    @BeforeEach
    void setUp() {
        student = Student.builder()
                .studentId("stu-01")
                .studentCode("SV001")
                .fullName("Nguyễn Văn A")
                .build();

        contract = Contract.builder()
                .contractId("hd-01")
                .contractCode("HD001")
                .student(student)
                .monthlyFee(new BigDecimal("1500000.00"))
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 6, 30))
                .status("ACTIVE")
                .build();

        invoice = Invoice.builder()
                .invoiceId("inv-01")
                .invoiceCode("INV-202610-SV001")
                .contract(contract)
                .student(student)
                .status("DRAFT")
                .invoiceMonth(LocalDate.of(2026, 10, 1))
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(10))
                .subtotalAmount(new BigDecimal("1500000.00"))
                .totalAmount(new BigDecimal("1500000.00"))
                .paidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();

        request = Invoice_Request.builder()
                .invoiceCode("INV-202610-SV001")
                .contractId("hd-01")
                .studentId("stu-01")
                .invoiceMonth(LocalDate.of(2026, 10, 15))
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(10))
                .status("DRAFT")
                .build();

        response = Invoice_Response.builder()
                .invoiceId("inv-01")
                .invoiceCode("INV-202610-SV001")
                .status("DRAFT")
                .build();

        lenient().when(systemSettingService.getIntSetting(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(systemSettingService.getDecimalSetting(anyString(), any(BigDecimal.class))).thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    @DisplayName("Tạo hóa đơn thành công - chuẩn hóa ngày đầu tháng và lấy student từ hợp đồng")
    void create_success() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(eq("hd-01"), eq(LocalDate.of(2026, 10, 1)))).thenReturn(false);
        when(invoiceRepository.existsByInvoiceCode("INV-202610-SV001")).thenReturn(false);
        when(invoiceMapper.toEntity(request)).thenReturn(invoice);
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(response);

        Invoice_Response result = invoiceService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getInvoiceCode()).isEqualTo("INV-202610-SV001");
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Tạo hóa đơn thất bại khi không tìm thấy hợp đồng")
    void create_failed_contractNotFound() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invoiceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo hóa đơn thất bại khi sinh viên không khớp với hợp đồng")
    void create_failed_studentMismatch() {
        request.setStudentId("different-student-id");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));

        AppException ex = assertThrows(AppException.class, () -> invoiceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hóa đơn thất bại khi đã tồn tại hóa đơn cho hợp đồng trong tháng")
    void create_failed_duplicateInvoiceMonth() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(eq("hd-01"), eq(LocalDate.of(2026, 10, 1)))).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> invoiceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hóa đơn thất bại khi mã hóa đơn đã tồn tại")
    void create_failed_duplicateInvoiceCode() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(eq("hd-01"), eq(LocalDate.of(2026, 10, 1)))).thenReturn(false);
        when(invoiceRepository.existsByInvoiceCode("INV-202610-SV001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> invoiceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hóa đơn có dòng chi tiết thì subtotal luôn được tính từ tổng các dòng và làm tròn scale 2")
    void create_withInvoiceItems_calculatesSubtotalFromItems() {
        InvoiceItem item1 = InvoiceItem.builder().quantity(new BigDecimal("2")).unitPrice(new BigDecimal("100.555")).build();
        InvoiceItem item2 = InvoiceItem.builder().quantity(new BigDecimal("1")).unitPrice(new BigDecimal("50.00")).build();
        invoice.setInvoiceItems(new ArrayList<>(List.of(item1, item2)));

        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(any(), any())).thenReturn(false);
        when(invoiceRepository.existsByInvoiceCode(any())).thenReturn(false);
        when(invoiceMapper.toEntity(request)).thenReturn(invoice);
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(response);

        Invoice_Response result = invoiceService.create(request);

        assertThat(result).isNotNull();
        // 2 * 100.56 = 201.12; 1 * 50.00 = 50.00; total = 251.12
        assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("251.12"));
    }

    @Test
    @DisplayName("Phát hành hóa đơn (ISSUE) thành công khi đang ở trạng thái DRAFT")
    void issue_success_whenDraft() {
        invoice.setStatus("DRAFT");
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(response);

        Invoice_Response result = invoiceService.issue("inv-01");

        assertThat(result).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo("ISSUED");
        verify(invoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Phát hành hóa đơn thất bại khi không ở trạng thái DRAFT")
    void issue_failed_whenNotDraft() {
        invoice.setStatus("PAID");
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> invoiceService.issue("inv-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Cập nhật hóa đơn thất bại khi hóa đơn đã thanh toán hoàn tất (PAID)")
    void update_failed_whenPaid() {
        invoice.setStatus("PAID");
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> invoiceService.update("inv-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa hóa đơn thành công khi hóa đơn là DRAFT và chưa phát sinh giao dịch thanh toán")
    void delete_success() {
        invoice.setStatus("DRAFT");
        invoice.setPaidAmount(BigDecimal.ZERO);
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoice_InvoiceId("inv-01")).thenReturn(false);

        invoiceService.delete("inv-01");

        verify(invoiceRepository).delete(invoice);
    }

    @Test
    @DisplayName("Xóa hóa đơn thất bại khi hóa đơn đã thanh toán (PAID)")
    void delete_failed_whenPaid() {
        invoice.setStatus("PAID");
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));

        AppException ex = assertThrows(AppException.class, () -> invoiceService.delete("inv-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa hóa đơn thất bại khi hóa đơn đã có giao dịch thu tiền")
    void delete_failed_whenPaymentExists() {
        invoice.setStatus("DRAFT");
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoice_InvoiceId("inv-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> invoiceService.delete("inv-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Lấy hóa đơn theo ID thất bại khi sinh viên cố tình xem hóa đơn của người khác")
    void getById_failed_unauthorizedForOtherStudent() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(invoiceRepository.findById("inv-01")).thenReturn(Optional.of(invoice));
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-student-id"));

        AppException ex = assertThrows(AppException.class, () -> invoiceService.getById("inv-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lấy danh sách hóa đơn theo sinh viên thất bại khi sinh viên xem hồ sơ của người khác")
    void getByStudentId_failed_unauthorizedForOtherStudent() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("my-student-id"));

        AppException ex = assertThrows(AppException.class, () -> invoiceService.getByStudentId("different-student-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lập hóa đơn hàng loạt (batch) cho các hợp đồng đang ACTIVE")
    void generateBatch_success() {
        InvoiceGenerate_Request batchReq = InvoiceGenerate_Request.builder()
                .invoiceMonth(LocalDate.of(2026, 11, 1))
                .dueDate(LocalDate.of(2026, 11, 15))
                .build();

        when(contractRepository.findByStatus("ACTIVE")).thenReturn(List.of(contract));
        when(invoiceRepository.findByContract_ContractIdAndInvoiceMonth(eq("hd-01"), eq(LocalDate.of(2026, 11, 1)))).thenReturn(List.of());
        when(invoiceRepository.existsByInvoiceCode(any())).thenReturn(false);
        when(invoiceRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Invoice_Response> result = invoiceService.generateBatch(batchReq);

        assertThat(result).hasSize(1);
        verify(invoiceRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Quét và cập nhật trạng thái hóa đơn quá hạn (OVERDUE) kèm tính phí phạt trễ hạn 5%")
    void processOverdueInvoices_success() {
        Invoice overdueInv = Invoice.builder()
                .invoiceId("inv-overdue")
                .invoiceCode("INV-OVERDUE-01")
                .status("ISSUED")
                .subtotalAmount(new BigDecimal("1000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .lateFeeAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().minusDays(5))
                .build();

        when(invoiceRepository.findOverdueInvoices(any(LocalDate.class))).thenReturn(List.of(overdueInv));

        int processed = invoiceService.processOverdueInvoices();

        assertThat(processed).isEqualTo(1);
        assertThat(overdueInv.getStatus()).isEqualTo("OVERDUE");
        // 5% của 1,000,000 = 50,000.00
        assertThat(overdueInv.getLateFeeAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(overdueInv.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1050000.00"));
        verify(invoiceRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Quét hóa đơn quá hạn PARTIALLY_PAID: giữ nguyên trạng thái PARTIALLY_PAID và tính phạt trên số tiền còn thiếu")
    void processOverdueInvoices_partiallyPaid_keepsStatus() {
        Invoice partialInv = Invoice.builder()
                .invoiceId("inv-partial")
                .invoiceCode("INV-PARTIAL-01")
                .status("PARTIALLY_PAID")
                .subtotalAmount(new BigDecimal("1000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .paidAmount(new BigDecimal("400000.00")) // Đã trả 400k, còn thiếu 600k
                .lateFeeAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().minusDays(5))
                .build();

        when(invoiceRepository.findOverdueInvoices(any(LocalDate.class))).thenReturn(List.of(partialInv));

        int processed = invoiceService.processOverdueInvoices();

        assertThat(processed).isEqualTo(1);
        assertThat(partialInv.getStatus()).isEqualTo("PARTIALLY_PAID");
        // 5% của 600,000 = 30,000.00
        assertThat(partialInv.getLateFeeAmount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(partialInv.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1030000.00"));
        verify(invoiceRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Tìm kiếm phân trang thành công với các trường hợp lệ")
    void search_success() {
        Page<Invoice> page = new PageImpl<>(List.of(invoice));
        when(invoiceRepository.searchInvoices(eq("SV001"), eq("DRAFT"), eq(LocalDate.of(2026, 10, 1)), any(Pageable.class)))
                .thenReturn(page);
        when(invoiceMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Invoice_Response> result = invoiceService.search("SV001", "DRAFT", LocalDate.of(2026, 10, 15), 1, 10, "dueDate", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm an toàn khi sortBy nằm ngoài danh sách cho phép thì fallback về dueDate")
    void search_safeFallback_invalidSortField() {
        Page<Invoice> page = new PageImpl<>(List.of(invoice));
        when(invoiceRepository.searchInvoices(any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(invoiceMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Invoice_Response> result = invoiceService.search(null, null, null, 1, 10, "invalidField; DROP TABLE", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }
}
