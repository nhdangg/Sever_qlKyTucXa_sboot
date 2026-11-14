package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Violation_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.StudentViolationSummary_Response;
import vn.QL_KyTucXa.DTO.Response.Violation_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Invoice;
import vn.QL_KyTucXa.Entity.InvoiceItem;
import vn.QL_KyTucXa.Entity.Staff;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Violation_Service {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "violationId", "violationDate", "fineAmount", "penaltyPointsApplied", "status", "createdAt"
    );

    Violation_Repository violationRepository;
    Student_Repository studentRepository;
    ViolationType_Repository violationTypeRepository;
    Staff_Repository staffRepository;
    Contract_Repository contractRepository;
    Invoice_Repository invoiceRepository;
    InvoiceItem_Repository invoiceItemRepository;
    CurrentUser_Service currentUserService;
    Violation_Mapper violationMapper;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    SystemSetting_Service systemSettingService;

    // Kiểm tra quyền cán bộ/quản trị viên đối với các thao tác lập/chỉnh sửa biên bản
    private void checkStaffOrAdminRole() {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền lập hoặc chỉnh sửa biên bản vi phạm");
        }
    }

    // Kiểm tra quyền quản trị viên/quản lý đối với các thao tác xóa hoặc thu tiền mặt
    private void checkAdminOrManagerRole(String actionMessage) {
        boolean isAdminOrManager = currentUserService != null
                && (currentUserService.hasRole("ADMIN") || currentUserService.hasRole("MANAGER"));
        if (!isAdminOrManager) {
            throw new AppException(ErrorCode.UNAUTHORIZED, actionMessage);
        }
    }

    // Lập biên bản vi phạm kỷ luật mới
    @Transactional
    public Violation_Response create(Violation_Request request) {
        checkStaffOrAdminRole();

        log.info("Lập biên bản vi phạm cho sinh viên ID: {}, Loại vi phạm ID: {}", request.getStudentId(),
                request.getViolationTypeId());

        Student student = resolveStudent(request.getStudentId());
        ViolationType violationType = resolveViolationType(request.getViolationTypeId());

        LocalDate violationDate = request.getViolationDate() != null ? request.getViolationDate() : LocalDate.now();
        if (violationDate.isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày vi phạm không thể ở tương lai");
        }

        // Điểm trừ và tiền phạt: chỉ lấy mặc định từ loại vi phạm khi request không truyền (null)
        Integer penaltyPoints = request.getPenaltyPointsApplied() != null
                ? request.getPenaltyPointsApplied()
                : (violationType.getPenaltyPoints() != null ? violationType.getPenaltyPoints() : 0);
        if (penaltyPoints < 0) {
            throw new AppException(ErrorCode.PENALTY_POINTS_INVALID, "Điểm trừ áp dụng không thể âm");
        }

        BigDecimal fineAmount = request.getFineAmount() != null
                ? request.getFineAmount()
                : (violationType.getFineAmount() != null ? violationType.getFineAmount() : BigDecimal.ZERO);
        if (fineAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.PRICE_INVALID, "Tiền phạt không thể âm");
        }

        // Người xử lý: ưu tiên lấy từ JWT cán bộ đăng nhập, chỉ ADMIN mới được chỉ định người khác
        Staff handler = null;
        if (currentUserService != null && currentUserService.hasRole("ADMIN")
                && request.getHandledBy() != null && !request.getHandledBy().isBlank()) {
            handler = staffRepository.findById(request.getHandledBy().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        } else if (currentUserService != null) {
            handler = currentUserService.findStaffId()
                    .flatMap(staffRepository::findById)
                    .orElse(null);
        }

        Violation violation = Violation.builder()
                .student(student)
                .violationType(violationType)
                .violationDate(violationDate)
                .description(request.getDescription())
                .actionTaken(request.getActionTaken())
                .fineAmount(fineAmount)
                .penaltyPointsApplied(penaltyPoints)
                .handledBy(handler)
                .status("RECORDED")
                .build();

        violation = violationRepository.save(violation);

        // Kết nối khoản thu tiền phạt vào hóa đơn DRAFT tháng này nếu có tiền phạt
        attachFineToInvoiceIfApplicable(violation, student, violationDate, fineAmount,
                violationType.getViolationName());

        // Gửi thông báo cho sinh viên khi bị lập biên bản kỷ luật
        if (notificationService != null && student.getUser() != null) {
            try {
                String title = "Biên bản vi phạm kỷ luật mới: " + violationType.getViolationName();
                String content = String.format("Ban Quản lý KTX đã lập biên bản vi phạm ngày %s. Điểm trừ: -%d điểm, Tiền phạt: %,.0f VNĐ. Lý do: %s",
                        violationDate,
                        penaltyPoints,
                        fineAmount,
                        violation.getDescription() != null ? violation.getDescription() : violationType.getViolationName());
                notificationService.notifyStudent(student, title, content, handler != null ? handler.getUser() : null);
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo lập biên bản vi phạm cho sinh viên: {}", ex.getMessage());
            }
        }

        // Kiểm tra ngưỡng tổng điểm trừ để cảnh báo xử lý kỷ luật (chỉ cảnh báo khi mới vượt ngưỡng)
        int totalPenaltyPoints = violationRepository.sumPenaltyPointsByStudent(student.getStudentId());
        int previousPoints = totalPenaltyPoints - penaltyPoints;
        int threshold = systemSettingService != null ? systemSettingService.getIntSetting("VIOLATION_PENALTY_THRESHOLD", 10) : 10;
        if (previousPoints < threshold && totalPenaltyPoints >= threshold && notificationService != null) {
            try {
                String warnTitle = "Cảnh báo kỷ luật: SV " + student.getFullName() + " tích lũy " + totalPenaltyPoints + " điểm trừ";
                String warnContent = String.format("Sinh viên %s (MSSV: %s) đã tích lũy %d điểm trừ vi phạm, đạt ngưỡng xử lý kỷ luật quy định.",
                        student.getFullName(), student.getStudentCode(), totalPenaltyPoints);
                notificationService.notifyStaffAndAdmins(warnTitle, warnContent, student.getUser(), violation.getViolationId());
            } catch (Exception ex) {
                log.warn("Không thể gửi cảnh báo ngưỡng điểm vi phạm cho cán bộ: {}", ex.getMessage());
            }
        }

        return violationMapper.toResponse(violation);
    }

    // Tự động gắn tiền phạt vào hóa đơn chưa thanh toán (DRAFT / ISSUED / OVERDUE) của sinh viên
    private void attachFineToInvoiceIfApplicable(Violation violation, Student student, LocalDate violationDate,
            BigDecimal fineAmount, String violationName) {
        if (fineAmount == null || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (invoiceItemRepository != null
                && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", violation.getViolationId())) {
            return;
        }

        List<Contract> activeContracts = contractRepository.findByStudent_StudentIdAndStatus(student.getStudentId(),
                "ACTIVE");
        if (activeContracts.isEmpty()) {
            return;
        }

        Contract contract = activeContracts.get(0);
        LocalDate invoiceMonth = violationDate.withDayOfMonth(1);
        List<Invoice> invoices = invoiceRepository.findByContract_ContractIdAndInvoiceMonth(contract.getContractId(), invoiceMonth);

        // Nếu tháng vi phạm chưa có hóa đơn mở, tìm hóa đơn chưa thanh toán gần nhất của hợp đồng
        Invoice targetInv = null;
        for (Invoice inv : invoices) {
            if (isInvoiceOpenForFine(inv)) {
                targetInv = inv;
                break;
            }
        }
        if (targetInv == null) {
            List<Invoice> allContractInvoices = invoiceRepository.findByContract_ContractId(contract.getContractId());
            for (Invoice inv : allContractInvoices) {
                if (isInvoiceOpenForFine(inv)) {
                    targetInv = inv;
                    break;
                }
            }
        }

        if (targetInv != null) {
            InvoiceItem fineItem = InvoiceItem.builder()
                    .invoice(targetInv)
                    .itemType("FINE")
                    .description("Tiền phạt vi phạm: " + violationName)
                    .quantity(BigDecimal.ONE)
                    .unitPrice(fineAmount)
                    .amount(fineAmount)
                    .referenceId(violation.getViolationId())
                    .build();

            targetInv.getInvoiceItems().add(fineItem);
            BigDecimal newSubtotal = (targetInv.getSubtotalAmount() != null ? targetInv.getSubtotalAmount() : BigDecimal.ZERO)
                    .add(fineAmount);
            targetInv.setSubtotalAmount(newSubtotal);
            targetInv.calculateTotalAmount();
            invoiceRepository.save(targetInv);
        }
    }

    private boolean isInvoiceOpenForFine(Invoice inv) {
        if (inv == null || inv.getStatus() == null) return false;
        String st = inv.getStatus().toUpperCase();
        return "DRAFT".equals(st) || "ISSUED".equals(st) || "OVERDUE".equals(st);
    }

    // Tra cứu sinh viên theo mã ID, ném lỗi rõ ràng nếu không tìm thấy
    private Student resolveStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
    }

    // Tra cứu loại vi phạm theo mã ID, ném lỗi rõ ràng nếu không tìm thấy
    private ViolationType resolveViolationType(String violationTypeId) {
        if (violationTypeId == null || violationTypeId.isBlank()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa chọn loại vi phạm");
        }
        return violationTypeRepository.findById(violationTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại vi phạm"));
    }

    @Transactional(readOnly = true)
    public Violation_Response getById(String violationId) {
        Violation violation = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        if (violation.getStudent() != null) {
            checkStudentAccessOwnership(violation.getStudent().getStudentId());
        }

        return violationMapper.toResponse(violation);
    }

    @Transactional(readOnly = true)
    public List<Violation_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Violation> list = violationRepository.findByStudent_StudentId(studentId);
        return violationMapper.toResponseList(list);
    }

    // Tìm kiếm biên bản vi phạm có phân trang theo sinh viên, loại vi phạm, mức độ nghiêm trọng, trạng thái và khoảng thời gian
    @Transactional(readOnly = true)
    public Page_Response<Violation_Response> search(
            String studentId,
            String keyword,
            String violationTypeId,
            String severityLevel,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        // Sinh viên gọi search thì bắt buộc ép về studentId của chính mình (chống lộ dữ liệu)
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            studentId = currentUserService.studentId();
        }

        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "violationDate";
        int safeSize = (size <= 0) ? 10 : Math.min(size, 100);
        int safePage = Math.max(page, 1);
        int pageIndex = safePage - 1;

        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Violation> pageResult = violationRepository.searchViolations(
                (studentId != null && !studentId.isBlank()) ? studentId.trim() : null,
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (violationTypeId != null && !violationTypeId.isBlank()) ? violationTypeId.trim() : null,
                (severityLevel != null && !severityLevel.isBlank()) ? severityLevel.trim() : null,
                (status != null && !status.isBlank()) ? status.trim() : null,
                fromDate,
                toDate,
                pageable);

        List<Violation_Response> data = violationMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Violation_Response>builder()
                .currentPage(pageResult.getNumber() + 1)
                .pageSize(pageResult.getSize())
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasPrevious(pageResult.hasPrevious())
                .hasNext(pageResult.hasNext())
                .isFirst(pageResult.isFirst())
                .isLast(pageResult.isLast())
                .data(data)
                .build();
    }

    @Transactional(readOnly = true)
    public Page_Response<Violation_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return search(null, null, null, null, null, null, null, page, size, sortBy, sortDir);
    }

    // Cập nhật thông tin biên bản kỷ luật
    @Transactional
    public Violation_Response update(String violationId, Violation_Request request) {
        checkStaffOrAdminRole();

        Violation violation = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        if ("CANCELLED".equalsIgnoreCase(violation.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Biên bản vi phạm đã bị hủy, không thể chỉnh sửa");
        }
        if ("PAID".equalsIgnoreCase(violation.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Biên bản vi phạm đã thanh toán hoàn tất (PAID), không thể chỉnh sửa");
        }

        // Nếu dòng FINE đã vào hóa đơn: chặn sửa số tiền phạt, sinh viên và loại vi phạm
        boolean fineInInvoice = (invoiceItemRepository != null)
                && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", violationId);

        if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
            if (fineInInvoice && !request.getStudentId().equals(violation.getStudent().getStudentId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Khoản phạt đã được đưa vào hóa đơn, không thể thay đổi sinh viên vi phạm");
            }
            violation.setStudent(resolveStudent(request.getStudentId()));
        }

        if (request.getViolationTypeId() != null && !request.getViolationTypeId().isBlank()) {
            if (fineInInvoice && !request.getViolationTypeId().equals(violation.getViolationType().getViolationTypeId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Khoản phạt đã được đưa vào hóa đơn, không thể thay đổi loại vi phạm");
            }
            violation.setViolationType(resolveViolationType(request.getViolationTypeId()));
        }

        if (request.getViolationDate() != null) {
            if (request.getViolationDate().isAfter(LocalDate.now())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày vi phạm không thể ở tương lai");
            }
            violation.setViolationDate(request.getViolationDate());
        }

        if (request.getDescription() != null) {
            violation.setDescription(request.getDescription());
        }

        if (request.getActionTaken() != null) {
            violation.setActionTaken(request.getActionTaken());
        }

        if (request.getFineAmount() != null) {
            if (request.getFineAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(ErrorCode.PRICE_INVALID, "Tiền phạt không thể âm");
            }
            if (fineInInvoice && request.getFineAmount().compareTo(violation.getFineAmount()) != 0) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Khoản phạt đã được đưa vào hóa đơn, không thể thay đổi số tiền phạt");
            }
            violation.setFineAmount(request.getFineAmount());
        }

        if (request.getPenaltyPointsApplied() != null) {
            if (request.getPenaltyPointsApplied() < 0) {
                throw new AppException(ErrorCode.PENALTY_POINTS_INVALID, "Điểm trừ áp dụng không thể âm");
            }
            violation.setPenaltyPointsApplied(request.getPenaltyPointsApplied());
        }

        // Giới hạn status trong RECORDED, PROCESSED, RESOLVED (chặn tự ý đặt PAID hoặc CANCELLED qua update)
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String updatedStatus = request.getStatus().trim().toUpperCase();
            if (!Set.of("RECORDED", "PROCESSED", "RESOLVED").contains(updatedStatus)) {
                throw new AppException(ErrorCode.STATUS_INVALID,
                        "Trạng thái cập nhật chỉ được là RECORDED, PROCESSED hoặc RESOLVED. Hủy biên bản vui lòng thực hiện qua chức năng xóa, thanh toán thực hiện qua hóa đơn.");
            }
            violation.setStatus(updatedStatus);
        }

        violation = violationRepository.save(violation);
        return violationMapper.toResponse(violation);
    }

    // Đổi trạng thái biên bản khi tiền phạt đã được thanh toán (chỉ ADMIN hoặc MANAGER, dùng cho thu tiền mặt ngoài hóa đơn)
    @Transactional
    public Violation_Response markAsPaid(String violationId) {
        checkAdminOrManagerRole("Chỉ Quản trị viên hoặc Quản lý KTX mới có quyền xác nhận thu tiền phạt trực tiếp");

        Violation violation = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        if ("CANCELLED".equalsIgnoreCase(violation.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Biên bản vi phạm đã bị hủy, không thể đánh dấu thanh toán");
        }

        if ("PAID".equalsIgnoreCase(violation.getStatus())) {
            return violationMapper.toResponse(violation);
        }

        // Chặn khi dòng FINE đang nằm trong hóa đơn chưa thanh toán
        if (invoiceItemRepository != null) {
            List<InvoiceItem> fineItems = invoiceItemRepository.findByItemTypeAndReferenceId("FINE", violationId);
            for (InvoiceItem item : fineItems) {
                Invoice inv = item.getInvoice();
                if (inv != null && !"PAID".equalsIgnoreCase(inv.getStatus())) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Khoản phạt đang nằm trong hóa đơn " + inv.getInvoiceCode() + " (" + inv.getStatus()
                                    + "). Vui lòng thanh toán qua hóa đơn hoặc gỡ khỏi hóa đơn trước khi thu tiền mặt trực tiếp.");
                }
            }
        }

        violation.setStatus("PAID");
        violation = violationRepository.save(violation);
        return violationMapper.toResponse(violation);
    }

    // Xóa mềm biên bản: chuyển trạng thái thành CANCELLED và gỡ khoản phạt khỏi hóa đơn chưa thanh toán
    @Transactional
    public void delete(String violationId) {
        checkAdminOrManagerRole("Chỉ Quản trị viên hoặc Quản lý KTX mới có quyền hủy biên bản vi phạm");

        Violation violation = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        // Kiểm tra xem biên bản đã được đưa vào hóa đơn nào chưa
        if (invoiceItemRepository != null) {
            List<InvoiceItem> fineItems = invoiceItemRepository.findByItemTypeAndReferenceId("FINE", violationId);
            for (InvoiceItem item : fineItems) {
                Invoice invoice = item.getInvoice();
                if (invoice != null) {
                    if ("PAID".equalsIgnoreCase(invoice.getStatus())
                            || "PARTIALLY_PAID".equalsIgnoreCase(invoice.getStatus())
                            || (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể xóa biên bản vi phạm đã thanh toán hoặc đang thanh toán trong hóa đơn " + invoice.getInvoiceCode());
                    }
                    if (invoice.getInvoiceItems() != null) {
                        invoice.getInvoiceItems().remove(item);
                        BigDecimal newSubtotal = (invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : BigDecimal.ZERO)
                                .subtract(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
                        invoice.setSubtotalAmount(newSubtotal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newSubtotal);
                        invoice.calculateTotalAmount();
                        invoiceRepository.save(invoice);
                    }
                }
            }
        }

        violation.setStatus("CANCELLED");
        violationRepository.save(violation);
    }

    // Lấy tổng hợp điểm trừ, số tiền phạt và cảnh báo kỷ luật theo sinh viên
    @Transactional(readOnly = true)
    public StudentViolationSummary_Response getStudentViolationSummary(String studentId) {
        checkStudentAccessOwnership(studentId);

        Student student = resolveStudent(studentId);
        List<Violation> violations = violationRepository.findByStudent_StudentId(studentId);

        int totalPenaltyPoints = 0;
        BigDecimal totalFineAmount = BigDecimal.ZERO;
        BigDecimal unpaidFineAmount = BigDecimal.ZERO;
        int violationCount = 0;

        for (Violation v : violations) {
            if ("CANCELLED".equalsIgnoreCase(v.getStatus())) {
                continue;
            }
            violationCount++;
            if (v.getPenaltyPointsApplied() != null) {
                totalPenaltyPoints += v.getPenaltyPointsApplied();
            }
            if (v.getFineAmount() != null) {
                totalFineAmount = totalFineAmount.add(v.getFineAmount());
                if (!"PAID".equalsIgnoreCase(v.getStatus())) {
                    unpaidFineAmount = unpaidFineAmount.add(v.getFineAmount());
                }
            }
        }

        String warningLevel;
        String warningMessage;
        if (totalPenaltyPoints >= 20) {
            warningLevel = "LEVEL_3";
            warningMessage = "CẢNH CÁO MỨC 3: Đề xuất chấm dứt hợp đồng nội trú và buộc rời khỏi KTX";
        } else if (totalPenaltyPoints >= 10) {
            warningLevel = "LEVEL_2";
            warningMessage = "CẢNH CÁO MỨC 2: Khiển trách toàn ký túc xá và gửi thông báo về khoa/phụ huynh";
        } else if (totalPenaltyPoints >= 5) {
            warningLevel = "LEVEL_1";
            warningMessage = "CẢNH CÁO MỨC 1: Nhắc nhở và ký cam kết không tái phạm nội quy KTX";
        } else {
            warningLevel = "NORMAL";
            warningMessage = "Bình thường (chưa vượt ngưỡng cảnh cáo kỷ luật)";
        }

        // Lấy phòng ở hiện tại nếu có
        String roomNumber = "";
        List<Contract> activeContracts = contractRepository.findByStudent_StudentIdAndStatus(studentId, "ACTIVE");
        if (!activeContracts.isEmpty() && activeContracts.get(0).getBed() != null && activeContracts.get(0).getBed().getRoom() != null) {
            roomNumber = activeContracts.get(0).getBed().getRoom().getRoomNumber();
        }

        return StudentViolationSummary_Response.builder()
                .studentId(student.getStudentId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .roomNumber(roomNumber)
                .totalPenaltyPoints(totalPenaltyPoints)
                .totalFineAmount(totalFineAmount)
                .unpaidFineAmount(unpaidFineAmount)
                .violationCount(violationCount)
                .warningLevel(warningLevel)
                .warningMessage(warningMessage)
                .build();
    }

    // Kiểm tra quyền sở hữu đối với tài khoản sinh viên (Fail-closed)
    private void checkStudentAccessOwnership(String targetStudentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse(null);
            if (currentStudentId == null || !currentStudentId.equals(targetStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền truy cập biên bản vi phạm của sinh viên khác");
            }
        }
    }
}
