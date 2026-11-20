package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Payment_Request;
import vn.QL_KyTucXa.DTO.Request.SepayWebhook_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Payment_Response;
import vn.QL_KyTucXa.Entity.Invoice;
import vn.QL_KyTucXa.Entity.InvoiceItem;
import vn.QL_KyTucXa.Entity.Payment;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Payment_Mapper;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Payment_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Violation_Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Payment_Service {

    private static final Set<String> ALLOWED_PAYMENT_METHODS = Set.of(
            "CASH", "BANK_TRANSFER", "VNPAY", "MOMO", "CREDIT_CARD");
    private static final Set<String> ALLOWED_PAYMENT_STATUSES = Set.of(
            "SUCCESS", "PENDING", "FAILED", "CANCELLED");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "paymentId", "paymentCode", "paymentMethod", "amount", "paymentDate", "status");

    Payment_Repository paymentRepository;
    Invoice_Repository invoiceRepository;
    Staff_Repository staffRepository;
    CurrentUser_Service currentUserService;
    Payment_Mapper paymentMapper;
    Violation_Repository violationRepository;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    SystemSetting_Service systemSettingService;

    @Value("${sepay.webhook-token:}")
    @lombok.experimental.NonFinal
    String sepayWebhookToken;

    // Tạo giao dịch thanh toán mới cho hóa đơn có khóa bi quan và cập nhật trạng
    // thái hóa đơn
    @Transactional
    public Payment_Response create(Payment_Request request) {
        log.info("Tạo giao dịch thanh toán cho hóa đơn: {}", request.getInvoiceId());

        // Sử dụng khóa PESSIMISTIC_WRITE để ngăn chặn race condition khi hai giao dịch
        // thanh toán đồng thời
        Invoice hoa_don = invoiceRepository.findByIdForUpdate(request.getInvoiceId())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if (hoa_don.getStudent() != null) {
            checkStudentAccessOwnership(hoa_don.getStudent().getStudentId());
        }

        // Chặn thanh toán cho hóa đơn DRAFT, CANCELLED hoặc đã PAID
        if ("DRAFT".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể thanh toán cho hóa đơn đang ở trạng thái DRAFT");
        }
        if ("CANCELLED".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể thanh toán cho hóa đơn đã bị hủy");
        }
        if ("PAID".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn này đã được thanh toán đầy đủ");
        }

        // Chuẩn hóa và kiểm tra số tiền thanh toán
        BigDecimal paymentAmount = (request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Số tiền thanh toán phải lớn hơn 0");
        }

        BigDecimal totalDue = (hoa_don.getTotalAmount() != null ? hoa_don.getTotalAmount() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPaid = (hoa_don.getPaidAmount() != null ? hoa_don.getPaidAmount() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = totalDue.subtract(currentPaid).setScale(2, RoundingMode.HALF_UP);

        if (paymentAmount.compareTo(remainingAmount) > 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Số tiền thanh toán (" + paymentAmount + ") vượt quá số tiền còn lại phải trả (" + remainingAmount
                            + ")");
        }

        // Tự sinh mã thanh toán nếu client không truyền
        String paymentCode = request.getPaymentCode();
        if (paymentCode == null || paymentCode.isBlank()) {
            paymentCode = generatePaymentCode();
        } else {
            paymentCode = paymentCode.trim();
            if (paymentRepository.existsByPaymentCode(paymentCode)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Mã thanh toán '" + paymentCode + "' đã tồn tại");
            }
        }

        // Kiểm tra và chuẩn hóa phương thức thanh toán
        String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod().trim().toUpperCase()
                : null;
        if (paymentMethod == null || !ALLOWED_PAYMENT_METHODS.contains(paymentMethod)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phương thức thanh toán '" + request.getPaymentMethod() + "' không hợp lệ. Cho phép: "
                            + ALLOWED_PAYMENT_METHODS);
        }

        // Kiểm tra quyền hạn khi tạo giao dịch thanh toán
        boolean isStaffOrAdmin = currentUserService != null && (currentUserService.hasRole("STAFF")
                || currentUserService.hasRole("MANAGER")
                || currentUserService.hasRole("ADMIN")
                || currentUserService.hasPermission("PAYMENT_MANAGE"));

        // Kiểm tra và chuẩn hóa trạng thái thanh toán
        String status = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().trim().toUpperCase()
                : "SUCCESS";

        // Sinh viên không được phép tự tạo giao dịch SUCCESS để tất toán hóa đơn
        if (!isStaffOrAdmin) {
            if ("SUCCESS".equalsIgnoreCase(request.getStatus())) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Chỉ cán bộ/nhân viên quản lý mới có quyền tạo giao dịch thanh toán thành công (SUCCESS). Giao dịch của sinh viên phải ở trạng thái PENDING");
            }
            status = "PENDING";
        }

        if (!ALLOWED_PAYMENT_STATUSES.contains(status)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái thanh toán '" + request.getStatus() + "' không hợp lệ. Cho phép: "
                            + ALLOWED_PAYMENT_STATUSES);
        }

        Payment thanh_toan = paymentMapper.toEntity(request);
        thanh_toan.setPaymentCode(paymentCode);
        thanh_toan.setInvoice(hoa_don);
        thanh_toan.setPaymentMethod(paymentMethod);
        thanh_toan.setAmount(paymentAmount);
        thanh_toan.setStatus(status);

        // Xác định nhân viên thu tiền từ JWT của người đăng nhập
        Staff nguoi_thu = resolveReceiver(request.getReceivedBy());
        thanh_toan.setReceivedBy(nguoi_thu);

        thanh_toan = paymentRepository.save(thanh_toan);

        // Nếu thanh toán thành công thì cập nhật lại số tiền đã trả và trạng thái hóa
        // đơn
        if ("SUCCESS".equalsIgnoreCase(status)) {
            recalculateInvoicePayment(hoa_don);
        }

        return paymentMapper.toResponse(thanh_toan);
    }

    // Xác nhận giao dịch PENDING thành SUCCESS (xử lý callback VNPAY/MoMo hoặc cán
    // bộ xác nhận tiền đã vào tài khoản)
    @Transactional
    public Payment_Response confirmPayment(String paymentId, String staffId, String transactionCode) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ cán bộ nhân viên hoặc quản trị viên mới có quyền xác nhận thanh toán");
        }

        Payment thanh_toan = paymentRepository.findById(paymentId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));

        if ("SUCCESS".equalsIgnoreCase(thanh_toan.getStatus())) {
            log.info("Giao dịch thanh toán {} đã ở trạng thái SUCCESS trước đó", paymentId);
            return paymentMapper.toResponse(thanh_toan);
        }

        if (!"PENDING".equalsIgnoreCase(thanh_toan.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể xác nhận giao dịch đang ở trạng thái PENDING (trạng thái hiện tại: "
                            + thanh_toan.getStatus() + ")");
        }

        Invoice hoa_don = invoiceRepository.findByIdForUpdate(thanh_toan.getInvoice().getInvoiceId())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn này đã được thanh toán hoàn tất (PAID)");
        }

        if ("CANCELLED".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn liên quan đã bị hủy (CANCELLED)");
        }

        BigDecimal currentPaid = hoa_don.getPaidAmount() != null ? hoa_don.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal totalDue = hoa_don.getTotalAmount() != null ? hoa_don.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal remainingDue = totalDue.subtract(currentPaid);
        if (thanh_toan.getAmount() != null && thanh_toan.getAmount().compareTo(remainingDue) > 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Số tiền xác nhận (" + thanh_toan.getAmount() + ") vượt quá số tiền còn nợ của hóa đơn (" + remainingDue + ")");
        }

        Staff nguoi_thu = null;
        if (staffId != null && !staffId.isBlank()) {
            nguoi_thu = staffRepository.findById(staffId).orElse(null);
        } else {
            nguoi_thu = resolveReceiver(null);
        }

        thanh_toan.setStatus("SUCCESS");
        if (nguoi_thu != null) {
            thanh_toan.setReceivedBy(nguoi_thu);
        }
        if (transactionCode != null && !transactionCode.isBlank()) {
            thanh_toan.setTransactionRef(transactionCode.trim());
        }

        thanh_toan = paymentRepository.save(thanh_toan);

        // Cập nhật lại số tiền đã thanh toán và trạng thái của hóa đơn
        recalculateInvoicePayment(hoa_don);

        log.info("Đã xác nhận thành công giao dịch thanh toán: {}, hóa đơn: {}", thanh_toan.getPaymentCode(),
                hoa_don.getInvoiceCode());
        return paymentMapper.toResponse(thanh_toan);
    }

    // Lấy thông tin chi tiết giao dịch thanh toán theo ID kèm kiểm tra quyền sở hữu
    // sinh viên
    @Transactional(readOnly = true)
    public Payment_Response getById(String paymentId) {
        Payment thanh_toan = paymentRepository.findById(paymentId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));

        if (thanh_toan.getInvoice() != null && thanh_toan.getInvoice().getStudent() != null) {
            checkStudentAccessOwnership(thanh_toan.getInvoice().getStudent().getStudentId());
        }

        return paymentMapper.toResponse(thanh_toan);
    }

    // Lấy danh sách giao dịch thanh toán theo mã hóa đơn kèm kiểm tra quyền sở hữu
    // sinh viên
    @Transactional(readOnly = true)
    public List<Payment_Response> getByInvoiceId(String invoiceId) {
        Invoice hoa_don = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if (hoa_don.getStudent() != null) {
            checkStudentAccessOwnership(hoa_don.getStudent().getStudentId());
        }

        List<Payment> danh_sach_thanh_toan = paymentRepository.findByInvoice_InvoiceId(invoiceId);
        return paymentMapper.toResponseList(danh_sach_thanh_toan);
    }

    // Lấy danh sách giao dịch thanh toán phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<Payment_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và phân trang giao dịch thanh toán theo mã hóa đơn, phương thức,
    // trạng thái và khoảng ngày
    @Transactional(readOnly = true)
    public Page_Response<Payment_Response> getAll(
            int page,
            int size,
            String invoiceCode,
            String paymentMethod,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền truy cập danh sách toàn bộ thanh toán");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "paymentDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String codeFilter = (invoiceCode != null && !invoiceCode.isBlank()) ? invoiceCode.trim() : null;
        String methodFilter = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.trim().toUpperCase()
                : null;
        String statusFilter = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        LocalDateTime startDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime endDateTime = toDate != null ? toDate.atTime(23, 59, 59) : null;

        Page<Payment> trang_thanh_toan = paymentRepository.searchPayments(
                codeFilter, methodFilter, statusFilter, startDateTime, endDateTime, pageable);
        List<Payment_Response> danh_sach_ket_qua = paymentMapper.toResponseList(trang_thanh_toan.getContent());

        return Page_Response.<Payment_Response>builder()
                .currentPage(trang_thanh_toan.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_thanh_toan.getTotalPages())
                .totalElements(trang_thanh_toan.getTotalElements())
                .hasPrevious(trang_thanh_toan.hasPrevious())
                .hasNext(trang_thanh_toan.hasNext())
                .isFirst(trang_thanh_toan.isFirst())
                .isLast(trang_thanh_toan.isLast())
                .data(danh_sach_ket_qua)
                .build();
    }

    // Hủy giao dịch thanh toán (chuyển sang CANCELLED) và tính toán lại số tiền đã
    // trả của hóa đơn
    @Transactional
    public void delete(String paymentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ cán bộ nhân viên hoặc quản trị viên mới có quyền hủy giao dịch thanh toán");
        }

        Payment thanh_toan = paymentRepository.findById(paymentId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));

        if ("CANCELLED".equalsIgnoreCase(thanh_toan.getStatus())) {
            log.info("Giao dịch thanh toán {} đã ở trạng thái CANCELLED trước đó", paymentId);
            return;
        }

        thanh_toan.setStatus("CANCELLED");
        paymentRepository.save(thanh_toan);

        // Khóa bi quan và tính toán lại hóa đơn
        if (thanh_toan.getInvoice() != null) {
            Invoice hoa_don = invoiceRepository.findByIdForUpdate(thanh_toan.getInvoice().getInvoiceId())
                    .orElse(thanh_toan.getInvoice());
            recalculateInvoicePayment(hoa_don);
        }
    }

    // Tính toán lại số tiền đã thanh toán và trạng thái của hóa đơn dựa trên các
    // giao dịch SUCCESS
    private void recalculateInvoicePayment(Invoice invoice) {
        List<Payment> danh_sach_thanh_toan = paymentRepository.findByInvoice_InvoiceId(invoice.getInvoiceId());
        BigDecimal totalPaid = danh_sach_thanh_toan.stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()) && p.getAmount() != null)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        invoice.setPaidAmount(totalPaid);

        BigDecimal totalDue = (invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
                invoice.setStatus("OVERDUE");
            } else {
                invoice.setStatus("ISSUED");
            }
        } else if (totalPaid.compareTo(totalDue) >= 0) {
            invoice.setStatus("PAID");

            // Tự động chuyển các biên bản vi phạm kỷ luật sang trạng thái PAID nếu tiền
            // phạt đã thanh toán
            if (violationRepository != null && invoice.getInvoiceItems() != null) {
                for (InvoiceItem muc_hoa_don : invoice.getInvoiceItems()) {
                    if ("FINE".equalsIgnoreCase(muc_hoa_don.getItemType()) && muc_hoa_don.getReferenceId() != null) {
                        violationRepository.findById(muc_hoa_don.getReferenceId()).ifPresent(v -> {
                            if (!"PAID".equalsIgnoreCase(v.getStatus())) {
                                v.setStatus("PAID");
                                violationRepository.save(v);
                                log.info("Biên bản vi phạm {} đã được tự động thanh toán theo hóa đơn {}",
                                        v.getViolationId(), invoice.getInvoiceCode());
                            }
                        });
                    }
                }
            }
        } else {
            invoice.setStatus("PARTIALLY_PAID");
        }

        // Nếu hóa đơn không còn ở trạng thái PAID (ví dụ vừa hủy một giao dịch SUCCESS làm totalPaid < totalDue),
        // cần hoàn lại trạng thái các biên bản vi phạm FINE từ PAID về RECORDED
        if (totalPaid.compareTo(totalDue) < 0 && violationRepository != null && invoice.getInvoiceItems() != null) {
            for (InvoiceItem muc_hoa_don : invoice.getInvoiceItems()) {
                if ("FINE".equalsIgnoreCase(muc_hoa_don.getItemType()) && muc_hoa_don.getReferenceId() != null) {
                    violationRepository.findById(muc_hoa_don.getReferenceId()).ifPresent(v -> {
                        if ("PAID".equalsIgnoreCase(v.getStatus())) {
                            v.setStatus("RECORDED");
                            violationRepository.save(v);
                            log.info("Biên bản vi phạm {} đã được hoàn về RECORDED do hóa đơn {} chưa thanh toán đủ",
                                    v.getViolationId(), invoice.getInvoiceCode());
                        }
                    });
                }
            }
        }

        invoiceRepository.save(invoice);
        log.info("Hóa đơn {} cập nhật lại: paidAmount={}, status={}", invoice.getInvoiceCode(), totalPaid,
                invoice.getStatus());
    }

    // Tự sinh mã thanh toán theo định dạng PAY-yyyyMMdd-XXX
    private String generatePaymentCode() {
        String prefix = "PAY-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        int counter = 1;
        String candidate;
        do {
            candidate = String.format("%s%03d", prefix, counter++);
        } while (paymentRepository.existsByPaymentCode(candidate));
        return candidate;
    }

    // Xác định nhân viên thu tiền bắt buộc lấy từ JWT của người đăng nhập
    private Staff resolveReceiver(String requestReceivedBy) {
        if (currentUserService != null) {
            try {
                Optional<String> currentStaffId = currentUserService.findStaffId();
                if (currentStaffId.isPresent()) {
                    return staffRepository.findById(currentStaffId.get()).orElse(null);
                }
                // Chỉ người dùng có quyền quản trị hoặc PAYMENT_MANAGE mới được phép chỉ định
                // nhân viên thu tiền
                if ((currentUserService.hasPermission("PAYMENT_MANAGE") || currentUserService.hasRole("ADMIN"))
                        && requestReceivedBy != null && !requestReceivedBy.isBlank()) {
                    return staffRepository.findById(requestReceivedBy.trim()).orElse(null);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // Kiểm tra quyền sở hữu đối với tài khoản sinh viên (Fail-closed)
    private void checkStudentAccessOwnership(String targetStudentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse(null);
            if (currentStudentId == null || !currentStudentId.equals(targetStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không có quyền truy cập giao dịch thanh toán của sinh viên khác");
            }
        }
    }

    // Xác thực API Key từ SePay Webhook
    public boolean validateSepayWebhookToken(String authorizationHeader) {
        String configuredToken = sepayWebhookToken != null && !sepayWebhookToken.isBlank()
                ? sepayWebhookToken.trim()
                : (systemSettingService != null ? systemSettingService.getSettingValue("SEPAY_WEBHOOK_TOKEN", "") : "");

        if (configuredToken != null) {
            configuredToken = configuredToken.replaceAll("^\"|\"$", "").trim();
        }

        if (configuredToken == null || configuredToken.isBlank()) {
            return true; // Nếu chưa cấu hình token trên hệ thống, cho phép kết nối để test linh hoạt
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }

        String auth = authorizationHeader.trim();
        // Hỗ trợ cả định dạng "Apikey KEY", "Bearer KEY" hoặc "KEY"
        if (auth.toLowerCase().startsWith("apikey ")) {
            auth = auth.substring(7).trim();
        } else if (auth.toLowerCase().startsWith("bearer ")) {
            auth = auth.substring(7).trim();
        }

        return configuredToken.equals(auth);
    }

    /**
     * Xử lý Webhook tự động nhận thông báo biến động số dư từ SePay (VietinBank / MB / VCB)
     */
    @Transactional
    public Map<String, Object> processSepayWebhook(SepayWebhook_Request webhook) {
        if (webhook == null) {
            return Map.of("success", false, "message", "Payload rỗng");
        }

        log.info("Nhận Webhook từ SePay: gateway={}, amount={}, content={}, referenceCode={}",
                webhook.getGateway(), webhook.getTransferAmount(), webhook.getContent(), webhook.getReferenceCode());

        // 1. Kiểm tra trường hợp test webhook từ SePay (gửi thử)
        if (webhook.getId() == null || webhook.getId() == 0
                || (webhook.getContent() != null && webhook.getContent().toUpperCase().contains("TEST"))) {
            log.info("Nhận tín hiệu TEST thành công từ SePay Webhook");
            return Map.of("success", true, "message", "Kết nối SePay Webhook thành công");
        }

        // 2. Chỉ xử lý biến động số dư tiền vào (transferType = in)
        if (webhook.getTransferType() != null && !"in".equalsIgnoreCase(webhook.getTransferType().trim())) {
            log.info("Bỏ qua giao dịch tiền ra (transferType={})", webhook.getTransferType());
            return Map.of("success", true, "message", "Bỏ qua giao dịch tiền ra");
        }

        BigDecimal transferAmount = webhook.getTransferAmount();
        if (transferAmount == null || transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Số tiền chuyển khoản không hợp lệ: {}", transferAmount);
            return Map.of("success", false, "message", "Số tiền chuyển khoản không hợp lệ");
        }

        // 3. Chống ghi đè hoặc trùng lặp giao dịch (Idempotency)
        String refCode = webhook.getReferenceCode() != null && !webhook.getReferenceCode().isBlank()
                ? webhook.getReferenceCode().trim()
                : (webhook.getId() != null ? "SEPAY-" + webhook.getId() : null);

        if (refCode != null && paymentRepository.existsByTransactionRef(refCode)) {
            log.info("Giao dịch tham chiếu ngân hàng '{}' đã được ghi nhận trước đó, bỏ qua trùng lặp", refCode);
            return Map.of("success", true, "message", "Giao dịch đã được ghi nhận trước đó");
        }

        // 4. Trích xuất mã hóa đơn từ nội dung chuyển khoản
        String rawContent = ((webhook.getContent() != null ? webhook.getContent() : "") + " "
                + (webhook.getDescription() != null ? webhook.getDescription() : "")).trim();

        Invoice hoa_don = findMatchingInvoice(rawContent);
        if (hoa_don == null) {
            log.warn("Không tìm thấy hóa đơn khớp với nội dung chuyển khoản: '{}'", rawContent);
            return Map.of("success", true, "message", "Không tìm thấy hóa đơn khớp với nội dung chuyển khoản: " + rawContent);
        }

        // Khóa hóa đơn để cập nhật an toàn
        hoa_don = invoiceRepository.findByIdForUpdate(hoa_don.getInvoiceId()).orElse(hoa_don);

        if ("PAID".equalsIgnoreCase(hoa_don.getStatus())) {
            log.info("Hóa đơn {} đã được thanh toán hoàn tất (PAID) trước đó", hoa_don.getInvoiceCode());
            return Map.of("success", true, "message", "Hóa đơn " + hoa_don.getInvoiceCode() + " đã thanh toán hoàn tất trước đó");
        }

        if ("CANCELLED".equalsIgnoreCase(hoa_don.getStatus())) {
            log.warn("Hóa đơn {} đã bị hủy (CANCELLED), không thể gạch nợ tự động", hoa_don.getInvoiceCode());
            return Map.of("success", false, "message", "Hóa đơn " + hoa_don.getInvoiceCode() + " đã bị hủy");
        }

        // 5. Tạo bản ghi thanh toán tự động
        String paymentCode = generatePaymentCode();
        Payment thanh_toan = Payment.builder()
                .paymentCode(paymentCode)
                .invoice(hoa_don)
                .paymentMethod("BANK_TRANSFER")
                .amount(transferAmount.setScale(2, RoundingMode.HALF_UP))
                .transactionRef(refCode)
                .status("SUCCESS")
                .note(String.format("Tự động gạch nợ qua SePay (%s) - TK: %s - GD: %s",
                        webhook.getGateway() != null ? webhook.getGateway() : "Ngân hàng",
                        webhook.getAccountNumber() != null ? webhook.getAccountNumber() : "",
                        refCode != null ? refCode : ""))
                .build();

        paymentRepository.save(thanh_toan);

        // 6. Tính toán lại số tiền đã thanh toán và cập nhật trạng thái hóa đơn
        recalculateInvoicePayment(hoa_don);

        // 7. Gửi thông báo cho sinh viên nếu có
        if (notificationService != null && hoa_don.getStudent() != null) {
            try {
                String notifTitle = "Thanh toán thành công hóa đơn " + hoa_don.getInvoiceCode();
                String notifContent = String.format("Hệ thống đã tự động ghi nhận thanh toán %s VNĐ qua SePay (%s) cho hóa đơn %s. Trạng thái hiện tại: %s.",
                        transferAmount.toPlainString(),
                        webhook.getGateway() != null ? webhook.getGateway() : "Ngân hàng",
                        hoa_don.getInvoiceCode(),
                        hoa_don.getStatus());
                notificationService.notifyStudent(hoa_don.getStudent(), notifTitle, notifContent, null);
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo sau khi tự động thanh toán: {}", ex.getMessage());
            }
        }

        log.info("Tự động gạch nợ thành công cho hóa đơn: {}, số tiền: {}, trạng thái mới: {}",
                hoa_don.getInvoiceCode(), transferAmount, hoa_don.getStatus());

        return Map.of(
                "success", true,
                "message", "Thanh toán hóa đơn " + hoa_don.getInvoiceCode() + " thành công",
                "invoiceCode", hoa_don.getInvoiceCode(),
                "paidAmount", transferAmount,
                "invoiceStatus", hoa_don.getStatus()
        );
    }

    // Tìm hóa đơn phù hợp từ nội dung chuyển khoản
    private Invoice findMatchingInvoice(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return null;
        }

        // Cách 1: Tìm theo định dạng mã hóa đơn INV-YYYYMM-XXX hoặc INVYYYYMMXXX
        Pattern pattern = Pattern.compile("INV[-_ ]?([0-9]{6})[-_ ]?([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(rawContent);
        if (matcher.find()) {
            String candidateCode = matcher.group(0).replaceAll("[-_ ]", "");
            String formattedCode = "INV-" + matcher.group(1) + "-" + matcher.group(2).toUpperCase();
            Optional<Invoice> hoa_don_tuy_chon = invoiceRepository.findByInvoiceCode(formattedCode);
            if (hoa_don_tuy_chon.isPresent()) {
                return hoa_don_tuy_chon.get();
            }
            hoa_don_tuy_chon = invoiceRepository.findByInvoiceCode(candidateCode);
            if (hoa_don_tuy_chon.isPresent()) {
                return hoa_don_tuy_chon.get();
            }
        }

        // Cách 2: Nếu nội dung chứa SEVQR (mã test mặc định của SePay), tự động khớp với hóa đơn thử nghiệm đang mở
        if (rawContent.toUpperCase().contains("SEVQR")) {
            Optional<Invoice> hoa_don_thu_nghiem = invoiceRepository.findByInvoiceCode("INV-202610-SV001");
            if (hoa_don_thu_nghiem.isPresent() && !"PAID".equalsIgnoreCase(hoa_don_thu_nghiem.get().getStatus())) {
                log.info("Khớp hóa đơn {} qua mã mặc định SEVQR từ SePay", hoa_don_thu_nghiem.get().getInvoiceCode());
                return hoa_don_thu_nghiem.get();
            }
        }

        // Cách 2: Quét tất cả hóa đơn chưa thanh toán và kiểm tra xem mã hóa đơn có xuất hiện trong nội dung không
        List<Invoice> danh_sach_hoa_don_chua_tra = invoiceRepository.findAll().stream()
                .filter(inv -> !"PAID".equalsIgnoreCase(inv.getStatus()) && !"CANCELLED".equalsIgnoreCase(inv.getStatus()))
                .toList();

        for (Invoice hoa_don_cho : danh_sach_hoa_don_chua_tra) {
            if (hoa_don_cho.getInvoiceCode() != null) {
                String cleanInvCode = hoa_don_cho.getInvoiceCode().replaceAll("[-_ ]", "").toUpperCase();
                String cleanContent = rawContent.replaceAll("[-_ ]", "").toUpperCase();
                if (cleanContent.contains(cleanInvCode)) {
                    return hoa_don_cho;
                }
            }
        }

        return null;
    }
}
