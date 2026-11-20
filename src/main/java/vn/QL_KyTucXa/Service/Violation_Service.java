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

        Student sinh_vien = resolveStudent(request.getStudentId());
        ViolationType loai_vi_pham = resolveViolationType(request.getViolationTypeId());

        LocalDate violationDate = request.getViolationDate() != null ? request.getViolationDate() : LocalDate.now();
        if (violationDate.isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày vi phạm không thể ở tương lai");
        }

        // Điểm trừ và tiền phạt: chỉ lấy mặc định từ loại vi phạm khi request không truyền (null)
        Integer penaltyPoints = request.getPenaltyPointsApplied() != null
                ? request.getPenaltyPointsApplied()
                : (loai_vi_pham.getPenaltyPoints() != null ? loai_vi_pham.getPenaltyPoints() : 0);
        if (penaltyPoints < 0) {
            throw new AppException(ErrorCode.PENALTY_POINTS_INVALID, "Điểm trừ áp dụng không thể âm");
        }

        BigDecimal fineAmount = request.getFineAmount() != null
                ? request.getFineAmount()
                : (loai_vi_pham.getFineAmount() != null ? loai_vi_pham.getFineAmount() : BigDecimal.ZERO);
        if (fineAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.PRICE_INVALID, "Tiền phạt không thể âm");
        }

        // Người xử lý: ưu tiên lấy từ JWT cán bộ đăng nhập, chỉ ADMIN mới được chỉ định người khác
        Staff nguoi_xu_ly = null;
        if (currentUserService != null && currentUserService.hasRole("ADMIN")
                && request.getHandledBy() != null && !request.getHandledBy().isBlank()) {
            nguoi_xu_ly = staffRepository.findById(request.getHandledBy().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        } else if (currentUserService != null) {
            nguoi_xu_ly = currentUserService.findStaffId()
                    .flatMap(staffRepository::findById)
                    .orElse(null);
        }

        Violation vi_pham = Violation.builder()
                .student(sinh_vien)
                .violationType(loai_vi_pham)
                .violationDate(violationDate)
                .description(request.getDescription())
                .actionTaken(request.getActionTaken())
                .fineAmount(fineAmount)
                .penaltyPointsApplied(penaltyPoints)
                .handledBy(nguoi_xu_ly)
                .status("RECORDED")
                .build();

        vi_pham = violationRepository.save(vi_pham);

        // Kết nối khoản thu tiền phạt vào hóa đơn DRAFT tháng này nếu có tiền phạt
        attachFineToInvoiceIfApplicable(vi_pham, sinh_vien, violationDate, fineAmount,
                loai_vi_pham.getViolationName());

        // Gửi thông báo cho sinh viên khi bị lập biên bản kỷ luật
        if (notificationService != null && sinh_vien.getUser() != null) {
            try {
                String title = "Biên bản vi phạm kỷ luật mới: " + loai_vi_pham.getViolationName();
                String content = String.format("Ban Quản lý KTX đã lập biên bản vi phạm ngày %s. Điểm trừ: -%d điểm, Tiền phạt: %,.0f VNĐ. Lý do: %s",
                        violationDate,
                        penaltyPoints,
                        fineAmount,
                        vi_pham.getDescription() != null ? vi_pham.getDescription() : loai_vi_pham.getViolationName());
                notificationService.notifyStudent(sinh_vien, title, content, nguoi_xu_ly != null ? nguoi_xu_ly.getUser() : null);
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo lập biên bản vi phạm cho sinh viên: {}", ex.getMessage());
            }
        }

        // Kiểm tra ngưỡng tổng điểm trừ để cảnh báo xử lý kỷ luật (chỉ cảnh báo khi mới vượt ngưỡng)
        int totalPenaltyPoints = violationRepository.sumPenaltyPointsByStudent(sinh_vien.getStudentId());
        int previousPoints = totalPenaltyPoints - penaltyPoints;
        int threshold = systemSettingService != null ? systemSettingService.getIntSetting("VIOLATION_PENALTY_THRESHOLD", 10) : 10;
        if (previousPoints < threshold && totalPenaltyPoints >= threshold && notificationService != null) {
            try {
                String warnTitle = "Cảnh báo kỷ luật: SV " + sinh_vien.getFullName() + " tích lũy " + totalPenaltyPoints + " điểm trừ";
                String warnContent = String.format("Sinh viên %s (MSSV: %s) đã tích lũy %d điểm trừ vi phạm, đạt ngưỡng xử lý kỷ luật quy định.",
                        sinh_vien.getFullName(), sinh_vien.getStudentCode(), totalPenaltyPoints);
                notificationService.notifyStaffAndAdmins(warnTitle, warnContent, sinh_vien.getUser(), vi_pham.getViolationId());
            } catch (Exception ex) {
                log.warn("Không thể gửi cảnh báo ngưỡng điểm vi phạm cho cán bộ: {}", ex.getMessage());
            }
        }

        return violationMapper.toResponse(vi_pham);
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

        List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStudent_StudentIdAndStatus(student.getStudentId(),
                "ACTIVE");
        if (danh_sach_hop_dong_hieu_luc.isEmpty()) {
            return;
        }

        Contract hop_dong = danh_sach_hop_dong_hieu_luc.get(0);
        LocalDate invoiceMonth = violationDate.withDayOfMonth(1);
        List<Invoice> danh_sach_hoa_don = invoiceRepository.findByContract_ContractIdAndInvoiceMonth(hop_dong.getContractId(), invoiceMonth);

        // Nếu tháng vi phạm chưa có hóa đơn mở, tìm hóa đơn chưa thanh toán gần nhất của hợp đồng
        Invoice hoa_don_dich = null;
        for (Invoice hoa_don : danh_sach_hoa_don) {
            if (isInvoiceOpenForFine(hoa_don)) {
                hoa_don_dich = hoa_don;
                break;
            }
        }
        if (hoa_don_dich == null) {
            List<Invoice> tat_ca_hoa_don_hop_dong = invoiceRepository.findByContract_ContractId(hop_dong.getContractId());
            for (Invoice hoa_don : tat_ca_hoa_don_hop_dong) {
                if (isInvoiceOpenForFine(hoa_don)) {
                    hoa_don_dich = hoa_don;
                    break;
                }
            }
        }

        if (hoa_don_dich != null) {
            InvoiceItem khoan_thu_tien_phat = InvoiceItem.builder()
                    .invoice(hoa_don_dich)
                    .itemType("FINE")
                    .description("Tiền phạt vi phạm: " + violationName)
                    .quantity(BigDecimal.ONE)
                    .unitPrice(fineAmount)
                    .amount(fineAmount)
                    .referenceId(violation.getViolationId())
                    .build();

            hoa_don_dich.getInvoiceItems().add(khoan_thu_tien_phat);
            BigDecimal newSubtotal = (hoa_don_dich.getSubtotalAmount() != null ? hoa_don_dich.getSubtotalAmount() : BigDecimal.ZERO)
                    .add(fineAmount);
            hoa_don_dich.setSubtotalAmount(newSubtotal);
            hoa_don_dich.calculateTotalAmount();
            invoiceRepository.save(hoa_don_dich);
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
        Violation vi_pham = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        if (vi_pham.getStudent() != null) {
            checkStudentAccessOwnership(vi_pham.getStudent().getStudentId());
        }

        return violationMapper.toResponse(vi_pham);
    }

    @Transactional(readOnly = true)
    public List<Violation_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Violation> danh_sach_vi_pham = violationRepository.findByStudent_StudentId(studentId);
        return violationMapper.toResponseList(danh_sach_vi_pham);
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

        Page<Violation> trang_vi_pham = violationRepository.searchViolations(
                (studentId != null && !studentId.isBlank()) ? studentId.trim() : null,
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (violationTypeId != null && !violationTypeId.isBlank()) ? violationTypeId.trim() : null,
                (severityLevel != null && !severityLevel.isBlank()) ? severityLevel.trim() : null,
                (status != null && !status.isBlank()) ? status.trim() : null,
                fromDate,
                toDate,
                pageable);

        List<Violation_Response> danh_sach_ket_qua = violationMapper.toResponseList(trang_vi_pham.getContent());

        return Page_Response.<Violation_Response>builder()
                .currentPage(trang_vi_pham.getNumber() + 1)
                .pageSize(trang_vi_pham.getSize())
                .totalPages(trang_vi_pham.getTotalPages())
                .totalElements(trang_vi_pham.getTotalElements())
                .hasPrevious(trang_vi_pham.hasPrevious())
                .hasNext(trang_vi_pham.hasNext())
                .isFirst(trang_vi_pham.isFirst())
                .isLast(trang_vi_pham.isLast())
                .data(danh_sach_ket_qua)
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

        Violation vi_pham = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        if ("CANCELLED".equalsIgnoreCase(vi_pham.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Biên bản vi phạm đã bị hủy, không thể chỉnh sửa");
        }
        if ("PAID".equalsIgnoreCase(vi_pham.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Biên bản vi phạm đã thanh toán hoàn tất (PAID), không thể chỉnh sửa");
        }

        // Nếu dòng FINE đã vào hóa đơn: chặn sửa số tiền phạt, sinh viên và loại vi phạm
        boolean fineInInvoice = (invoiceItemRepository != null)
                && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", violationId);

        if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
            if (fineInInvoice && !request.getStudentId().equals(vi_pham.getStudent().getStudentId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Khoản phạt đã được đưa vào hóa đơn, không thể thay đổi sinh viên vi phạm");
            }
            vi_pham.setStudent(resolveStudent(request.getStudentId()));
        }

        if (request.getViolationTypeId() != null && !request.getViolationTypeId().isBlank()) {
            if (fineInInvoice && !request.getViolationTypeId().equals(vi_pham.getViolationType().getViolationTypeId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Khoản phạt đã được đưa vào hóa đơn, không thể thay đổi loại vi phạm");
            }
            vi_pham.setViolationType(resolveViolationType(request.getViolationTypeId()));
        }

        if (request.getViolationDate() != null) {
            if (request.getViolationDate().isAfter(LocalDate.now())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày vi phạm không thể ở tương lai");
            }
            vi_pham.setViolationDate(request.getViolationDate());
        }

        if (request.getDescription() != null) {
            vi_pham.setDescription(request.getDescription());
        }

        if (request.getActionTaken() != null) {
            vi_pham.setActionTaken(request.getActionTaken());
        }

        if (request.getFineAmount() != null) {
            if (request.getFineAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(ErrorCode.PRICE_INVALID, "Tiền phạt không thể âm");
            }
            if (fineInInvoice && request.getFineAmount().compareTo(vi_pham.getFineAmount()) != 0) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Khoản phạt đã được đưa vào hóa đơn, không thể thay đổi số tiền phạt");
            }
            vi_pham.setFineAmount(request.getFineAmount());
        }

        if (request.getPenaltyPointsApplied() != null) {
            if (request.getPenaltyPointsApplied() < 0) {
                throw new AppException(ErrorCode.PENALTY_POINTS_INVALID, "Điểm trừ áp dụng không thể âm");
            }
            vi_pham.setPenaltyPointsApplied(request.getPenaltyPointsApplied());
        }

        // Giới hạn status trong RECORDED, PROCESSED, RESOLVED (chặn tự ý đặt PAID hoặc CANCELLED qua update)
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String updatedStatus = request.getStatus().trim().toUpperCase();
            if (!Set.of("RECORDED", "PROCESSED", "RESOLVED").contains(updatedStatus)) {
                throw new AppException(ErrorCode.STATUS_INVALID,
                        "Trạng thái cập nhật chỉ được là RECORDED, PROCESSED hoặc RESOLVED. Hủy biên bản vui lòng thực hiện qua chức năng xóa, thanh toán thực hiện qua hóa đơn.");
            }
            vi_pham.setStatus(updatedStatus);
        }

        vi_pham = violationRepository.save(vi_pham);
        return violationMapper.toResponse(vi_pham);
    }

    // Đổi trạng thái biên bản khi tiền phạt đã được thanh toán (chỉ ADMIN hoặc MANAGER, dùng cho thu tiền mặt ngoài hóa đơn)
    @Transactional
    public Violation_Response markAsPaid(String violationId) {
        checkAdminOrManagerRole("Chỉ Quản trị viên hoặc Quản lý KTX mới có quyền xác nhận thu tiền phạt trực tiếp");

        Violation vi_pham = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        if ("CANCELLED".equalsIgnoreCase(vi_pham.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Biên bản vi phạm đã bị hủy, không thể đánh dấu thanh toán");
        }

        if ("PAID".equalsIgnoreCase(vi_pham.getStatus())) {
            return violationMapper.toResponse(vi_pham);
        }

        // Chặn khi dòng FINE đang nằm trong hóa đơn chưa thanh toán
        if (invoiceItemRepository != null) {
            List<InvoiceItem> danh_sach_khoan_phat = invoiceItemRepository.findByItemTypeAndReferenceId("FINE", violationId);
            for (InvoiceItem muc_phat : danh_sach_khoan_phat) {
                Invoice hoa_don = muc_phat.getInvoice();
                if (hoa_don != null && !"PAID".equalsIgnoreCase(hoa_don.getStatus())) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Khoản phạt đang nằm trong hóa đơn " + hoa_don.getInvoiceCode() + " (" + hoa_don.getStatus()
                                    + "). Vui lòng thanh toán qua hóa đơn hoặc gỡ khỏi hóa đơn trước khi thu tiền mặt trực tiếp.");
                }
            }
        }

        vi_pham.setStatus("PAID");
        vi_pham = violationRepository.save(vi_pham);
        return violationMapper.toResponse(vi_pham);
    }

    // Xóa mềm biên bản: chuyển trạng thái thành CANCELLED và gỡ khoản phạt khỏi hóa đơn chưa thanh toán
    @Transactional
    public void delete(String violationId) {
        checkAdminOrManagerRole("Chỉ Quản trị viên hoặc Quản lý KTX mới có quyền hủy biên bản vi phạm");

        Violation vi_pham = violationRepository.findById(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy biên bản vi phạm"));

        // Kiểm tra xem biên bản đã được đưa vào hóa đơn nào chưa
        if (invoiceItemRepository != null) {
            List<InvoiceItem> danh_sach_khoan_phat = invoiceItemRepository.findByItemTypeAndReferenceId("FINE", violationId);
            for (InvoiceItem muc_phat : danh_sach_khoan_phat) {
                Invoice hoa_don = muc_phat.getInvoice();
                if (hoa_don != null) {
                    if ("PAID".equalsIgnoreCase(hoa_don.getStatus())
                            || "PARTIALLY_PAID".equalsIgnoreCase(hoa_don.getStatus())
                            || (hoa_don.getPaidAmount() != null && hoa_don.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
                        throw new AppException(ErrorCode.OPERATION_FAILED,
                                "Không thể xóa biên bản vi phạm đã thanh toán hoặc đang thanh toán trong hóa đơn " + hoa_don.getInvoiceCode());
                    }
                    if (hoa_don.getInvoiceItems() != null) {
                        hoa_don.getInvoiceItems().remove(muc_phat);
                        BigDecimal newSubtotal = (hoa_don.getSubtotalAmount() != null ? hoa_don.getSubtotalAmount() : BigDecimal.ZERO)
                                .subtract(muc_phat.getAmount() != null ? muc_phat.getAmount() : BigDecimal.ZERO);
                        hoa_don.setSubtotalAmount(newSubtotal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newSubtotal);
                        hoa_don.calculateTotalAmount();
                        invoiceRepository.save(hoa_don);
                    }
                }
            }
        }

        vi_pham.setStatus("CANCELLED");
        violationRepository.save(vi_pham);
    }

    // Lấy tổng hợp điểm trừ, số tiền phạt và cảnh báo kỷ luật theo sinh viên
    @Transactional(readOnly = true)
    public StudentViolationSummary_Response getStudentViolationSummary(String studentId) {
        checkStudentAccessOwnership(studentId);

        Student sinh_vien = resolveStudent(studentId);
        List<Violation> danh_sach_vi_pham = violationRepository.findByStudent_StudentId(studentId);

        int totalPenaltyPoints = 0;
        BigDecimal totalFineAmount = BigDecimal.ZERO;
        BigDecimal unpaidFineAmount = BigDecimal.ZERO;
        int violationCount = 0;

        for (Violation vi_pham : danh_sach_vi_pham) {
            if ("CANCELLED".equalsIgnoreCase(vi_pham.getStatus())) {
                continue;
            }
            violationCount++;
            if (vi_pham.getPenaltyPointsApplied() != null) {
                totalPenaltyPoints += vi_pham.getPenaltyPointsApplied();
            }
            if (vi_pham.getFineAmount() != null) {
                totalFineAmount = totalFineAmount.add(vi_pham.getFineAmount());
                if (!"PAID".equalsIgnoreCase(vi_pham.getStatus())) {
                    unpaidFineAmount = unpaidFineAmount.add(vi_pham.getFineAmount());
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
        List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStudent_StudentIdAndStatus(studentId, "ACTIVE");
        if (!danh_sach_hop_dong_hieu_luc.isEmpty() && danh_sach_hop_dong_hieu_luc.get(0).getBed() != null && danh_sach_hop_dong_hieu_luc.get(0).getBed().getRoom() != null) {
            roomNumber = danh_sach_hop_dong_hieu_luc.get(0).getBed().getRoom().getRoomNumber();
        }

        return StudentViolationSummary_Response.builder()
                .studentId(sinh_vien.getStudentId())
                .studentCode(sinh_vien.getStudentCode())
                .fullName(sinh_vien.getFullName())
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
