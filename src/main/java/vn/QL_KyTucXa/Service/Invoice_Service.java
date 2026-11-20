package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.InvoiceGenerate_Request;
import vn.QL_KyTucXa.DTO.Request.Invoice_Request;
import vn.QL_KyTucXa.DTO.Response.Invoice_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.UtilitySplit_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Invoice;
import vn.QL_KyTucXa.Entity.InvoiceItem;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.ServiceType;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.UtilityReading;
import vn.QL_KyTucXa.Entity.VehicleRegistration;
import vn.QL_KyTucXa.Entity.Violation;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Invoice_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.InvoiceItem_Repository;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Payment_Repository;
import vn.QL_KyTucXa.Repository.ServiceType_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.UtilityReading_Repository;
import vn.QL_KyTucXa.Repository.VehicleRegistration_Repository;
import vn.QL_KyTucXa.Repository.Violation_Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Invoice_Service {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "invoiceId", "invoiceCode", "invoiceMonth", "issueDate", "dueDate",
            "subtotalAmount", "discountAmount", "lateFeeAmount", "totalAmount",
            "paidAmount", "status", "createdAt");

    Invoice_Repository invoiceRepository;
    Contract_Repository contractRepository;
    Student_Repository studentRepository;
    Payment_Repository paymentRepository;
    CurrentUser_Service currentUserService;
    Invoice_Mapper invoiceMapper;
    SystemSetting_Service systemSettingService;
    UtilityReading_Repository utilityReadingRepository;
    UtilityReading_Service utilityReadingService;
    VehicleRegistration_Repository vehicleRegistrationRepository;
    Violation_Repository violationRepository;
    InvoiceItem_Repository invoiceItemRepository;
    ServiceType_Repository serviceTypeRepository;

    // Tạo hóa đơn mới
    @Transactional
    public Invoice_Response create(Invoice_Request request) {
        if (request.getContractId() == null || request.getContractId().isBlank()) {
            throw new AppException(ErrorCode.CONTRACT_NOT_FOUND);
        }

        Contract hop_dong = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ được phép lập hóa đơn cho hợp đồng đang có hiệu lực (ACTIVE)");
        }

        // Lấy thông tin sinh viên trực tiếp từ hợp đồng
        Student sinh_vien = hop_dong.getStudent();
        if (sinh_vien == null) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        // Kiểm tra tính nhất quán nếu client truyền kèm studentId
        if (request.getStudentId() != null && !request.getStudentId().isBlank()
                && !request.getStudentId().equals(sinh_vien.getStudentId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã sinh viên không khớp với sinh viên trên hợp đồng");
        }

        // Chuẩn hóa kỳ hóa đơn về ngày đầu tháng
        LocalDate invoiceMonth = (request.getInvoiceMonth() != null ? request.getInvoiceMonth() : LocalDate.now())
                .withDayOfMonth(1);

        // Kiểm tra trùng lặp kỳ hóa đơn trên cùng một hợp đồng
        if (invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(hop_dong.getContractId(), invoiceMonth)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn cho hợp đồng '" + hop_dong.getContractCode()
                    + "' trong tháng " + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " đã tồn tại");
        }

        // Tạo mã hóa đơn theo quy chuẩn nếu client không truyền vào
        String invoiceCode = request.getInvoiceCode();
        if (invoiceCode == null || invoiceCode.isBlank()) {
            invoiceCode = generateInvoiceCode(sinh_vien, invoiceMonth);
            request.setInvoiceCode(invoiceCode);
        } else if (invoiceRepository.existsByInvoiceCode(invoiceCode)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã hóa đơn '" + invoiceCode + "' đã tồn tại");
        }

        log.info("Tạo hóa đơn mới: {} cho sinh viên: {}", invoiceCode, sinh_vien.getStudentCode());

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();
        int deadlineDays = systemSettingService != null
                ? systemSettingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 10)
                : 10;
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : issueDate.plusDays(deadlineDays);
        if (dueDate.isBefore(issueDate)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn thanh toán không thể trước ngày phát hành");
        }

        Invoice hoa_don = invoiceMapper.toEntity(request);
        hoa_don.setInvoiceCode(invoiceCode);
        hoa_don.setContract(hop_dong);
        hoa_don.setStudent(sinh_vien);
        hoa_don.setInvoiceMonth(invoiceMonth);
        hoa_don.setIssueDate(issueDate);
        hoa_don.setDueDate(dueDate);

        // Trạng thái ban đầu: ưu tiên theo request (ISSUED / DRAFT), số tiền đã trả
        // luôn là 0
        String initialStatus = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().toUpperCase()
                : "DRAFT";
        hoa_don.setStatus(initialStatus);
        hoa_don.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        // Khởi tạo danh sách chi tiết hóa đơn nếu chưa có
        if (hoa_don.getInvoiceItems() == null) {
            hoa_don.setInvoiceItems(new ArrayList<>());
        }

        // 1. Trường hợp client gửi danh sách chi tiết khoản thu (invoiceItems)
        if (request.getInvoiceItems() != null && !request.getInvoiceItems().isEmpty()) {
            for (vn.QL_KyTucXa.DTO.Request.InvoiceItem_Request itemReq : request.getInvoiceItems()) {
                BigDecimal qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : BigDecimal.ONE;
                BigDecimal price = itemReq.getUnitPrice() != null
                        ? itemReq.getUnitPrice().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal amt = itemReq.getAmount() != null ? itemReq.getAmount().setScale(2, RoundingMode.HALF_UP)
                        : qty.multiply(price).setScale(2, RoundingMode.HALF_UP);

                InvoiceItem item = InvoiceItem.builder()
                        .invoice(hoa_don)
                        .itemType(itemReq.getItemType() != null ? itemReq.getItemType() : "SERVICE")
                        .description(itemReq.getDescription())
                        .quantity(qty)
                        .unitPrice(price)
                        .amount(amt)
                        .referenceId(itemReq.getReferenceId())
                        .build();
                hoa_don.getInvoiceItems().add(item);
            }
        } else {
            // 2. Tự động bóc tách các dòng chi tiết: RENT, UTILITY, SERVICE (PER_PERSON &
            // FLAT)
            BigDecimal roomRent;
            if (request.getSubtotalAmount() != null && request.getSubtotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                roomRent = request.getSubtotalAmount().setScale(2, RoundingMode.HALF_UP);
            } else {
                roomRent = resolveRoomMonthlyFee(hop_dong);
            }
            if (roomRent.compareTo(BigDecimal.ZERO) > 0) {
                InvoiceItem rentItem = InvoiceItem.builder()
                        .invoice(hoa_don)
                        .itemType("RENT")
                        .description("Tiền thuê phòng lưu trú kỳ tháng "
                                + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                        .quantity(BigDecimal.ONE)
                        .unitPrice(roomRent)
                        .amount(roomRent)
                        .referenceId(hop_dong.getContractId())
                        .build();
                hoa_don.getInvoiceItems().add(rentItem);
            }

            // Gắn tiền điện nước phòng (UTILITY) phân bổ cho sinh viên
            if (utilityReadingRepository != null && utilityReadingService != null
                    && hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
                String roomId = hop_dong.getBed().getRoom().getRoomId();
                LocalDate utilityMonth = invoiceMonth.minusMonths(1);
                List<vn.QL_KyTucXa.Entity.UtilityReading> danh_sach_chi_so = utilityReadingRepository
                        .findByRoom_RoomIdAndReadingMonth(roomId, utilityMonth);
                if (danh_sach_chi_so.isEmpty()) {
                    danh_sach_chi_so = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId, invoiceMonth);
                    if (!danh_sach_chi_so.isEmpty()) {
                        utilityMonth = invoiceMonth;
                    }
                }
                final Invoice targetInvoice = hoa_don;
                final LocalDate finalInvoiceMonth = invoiceMonth;
                for (vn.QL_KyTucXa.Entity.UtilityReading reading : danh_sach_chi_so) {
                    try {
                        vn.QL_KyTucXa.DTO.Response.UtilitySplit_Response split = utilityReadingService
                                .calculateRoomUtilitySplit(
                                        roomId, reading.getServiceType().getServiceTypeId(), utilityMonth);
                        if (split != null && split.getResidentShares() != null) {
                            split.getResidentShares().stream()
                                    .filter(rs -> rs.getStudentId() != null
                                            && rs.getStudentId().equals(sinh_vien.getStudentId()))
                                    .findFirst()
                                    .ifPresent(rs -> {
                                        if (rs.getShareAmount() != null
                                                && rs.getShareAmount().compareTo(BigDecimal.ZERO) > 0) {
                                             String srvName = (reading.getServiceType() != null
                                                    && reading.getServiceType().getServiceName() != null)
                                                             ? reading.getServiceType().getServiceName()
                                                             : "Điện nước";
                                            InvoiceItem utilityItem = InvoiceItem.builder()
                                                    .invoice(targetInvoice)
                                                    .itemType("UTILITY")
                                                    .description("Tiền " + srvName + " tháng "
                                                            + finalInvoiceMonth
                                                                    .format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                                    .quantity(BigDecimal.ONE)
                                                    .unitPrice(rs.getShareAmount().setScale(2, RoundingMode.HALF_UP))
                                                    .amount(rs.getShareAmount().setScale(2, RoundingMode.HALF_UP))
                                                    .referenceId(reading.getReadingId())
                                                    .build();
                                            targetInvoice.getInvoiceItems().add(utilityItem);
                                        }
                                    });
                        }
                    } catch (Exception ex) {
                        log.warn("Không thể tính chia điện nước phòng {} khi lập HĐ lẻ: {}", roomId, ex.getMessage());
                    }
                }
            }

            // Tự động gắn các dịch vụ KTX theo tháng (PER_PERSON: Rác, An ninh & FLAT: Wifi
            // phòng)
            if (serviceTypeRepository != null) {
                List<ServiceType> danh_sach_loai_dich_vu = serviceTypeRepository.findAll();
                String monthLabel = invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"));
                for (ServiceType st : danh_sach_loai_dich_vu) {
                    if ("PER_PERSON".equalsIgnoreCase(st.getBillingType())
                            && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                        InvoiceItem serviceItem = InvoiceItem.builder()
                                .invoice(hoa_don)
                                .itemType("SERVICE")
                                .description(st.getServiceName() + " tháng " + monthLabel)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .amount(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(st.getServiceTypeId())
                                .build();
                        hoa_don.getInvoiceItems().add(serviceItem);
                    }
                }
                if (hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
                    String roomId = hop_dong.getBed().getRoom().getRoomId();
                    String roomNumber = hop_dong.getBed().getRoom().getRoomNumber();
                    List<Contract> activeContractsInRoom = contractRepository.findActiveContractsByRoomId(roomId);
                    int activeCountInRoom = activeContractsInRoom.isEmpty() ? 1 : activeContractsInRoom.size();
                    for (ServiceType st : danh_sach_loai_dich_vu) {
                        if ("FLAT".equalsIgnoreCase(st.getBillingType())
                                && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal flatShare = st.getUnitPrice().divide(
                                    BigDecimal.valueOf(activeCountInRoom), 2, RoundingMode.HALF_UP);
                            InvoiceItem flatItem = InvoiceItem.builder()
                                    .invoice(hoa_don)
                                    .itemType("SERVICE")
                                    .description(
                                            st.getServiceName() + " (Phòng " + roomNumber + ") tháng " + monthLabel)
                                    .quantity(BigDecimal.ONE)
                                    .unitPrice(flatShare)
                                    .amount(flatShare)
                                    .referenceId(st.getServiceTypeId())
                                    .build();
                            hoa_don.getInvoiceItems().add(flatItem);
                        }
                    }
                }
            }

            LocalDate monthEnd = invoiceMonth.withDayOfMonth(invoiceMonth.lengthOfMonth());

            // Tự động gắn phí gửi xe (VEHICLE) đang hoạt động trong kỳ
            if (vehicleRegistrationRepository != null) {
                List<VehicleRegistration> danh_sach_xe = vehicleRegistrationRepository.findActiveVehiclesForStudentInMonth(
                        sinh_vien.getStudentId(), invoiceMonth, monthEnd);
                for (VehicleRegistration vehicle : danh_sach_xe) {
                    if (vehicle.getMonthlyFee() != null && vehicle.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0) {
                        String plateInfo = (vehicle.getLicensePlate() != null && !vehicle.getLicensePlate().isBlank())
                                ? " (" + vehicle.getLicensePlate() + ")"
                                : "";
                        InvoiceItem vehicleItem = InvoiceItem.builder()
                                .invoice(hoa_don)
                                .itemType("VEHICLE")
                                .description("Phí gửi xe " + vehicle.getVehicleType() + plateInfo + " tháng "
                                        + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                .quantity(BigDecimal.ONE)
                                .unitPrice(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .amount(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(vehicle.getVehicleId())
                                .build();
                        hoa_don.getInvoiceItems().add(vehicleItem);
                    }
                }
            }

            // Tự động gắn tiền phạt vi phạm kỷ luật chưa tính vào hóa đơn (FINE)
            if (violationRepository != null) {
                List<Violation> danh_sach_vi_pham = violationRepository.findUnbilledFinesByStudent(sinh_vien.getStudentId(),
                        monthEnd);
                for (Violation v : danh_sach_vi_pham) {
                    boolean alreadyBilled = invoiceItemRepository != null
                            && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", v.getViolationId());
                    if (!alreadyBilled && v.getFineAmount() != null
                            && v.getFineAmount().compareTo(BigDecimal.ZERO) > 0) {
                        String vName = (v.getViolationType() != null && v.getViolationType().getViolationName() != null)
                                ? v.getViolationType().getViolationName()
                                : "Kỷ luật";
                        InvoiceItem fineItem = InvoiceItem.builder()
                                .invoice(hoa_don)
                                .itemType("FINE")
                                .description("Tiền phạt vi phạm: " + vName)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .amount(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(v.getViolationId())
                                .build();
                        hoa_don.getInvoiceItems().add(fineItem);
                    }
                }
            }
        }

        hoa_don.setDiscountAmount(
                request.getDiscountAmount() != null ? request.getDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        hoa_don.setLateFeeAmount(
                request.getLateFeeAmount() != null ? request.getLateFeeAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        recalculateInvoiceAmounts(hoa_don);

        hoa_don = invoiceRepository.save(hoa_don);
        return invoiceMapper.toResponse(hoa_don);
    }

    // Tính toán lại tổng tiền các dòng chi tiết và các khoản chiết khấu, phạt trễ
    // hạn
    private void recalculateInvoiceAmounts(Invoice invoice) {
        if (invoice.getInvoiceItems() != null && !invoice.getInvoiceItems().isEmpty()) {
            BigDecimal itemsSum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            for (InvoiceItem item : invoice.getInvoiceItems()) {
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
                BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal itemAmount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
                item.setQuantity(qty);
                item.setUnitPrice(price);
                item.setAmount(itemAmount);
                item.setInvoice(invoice);
                itemsSum = itemsSum.add(itemAmount);
            }
            invoice.setSubtotalAmount(itemsSum);
        }

        BigDecimal subtotal = invoice.getSubtotalAmount() != null
                ? invoice.getSubtotalAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = invoice.getDiscountAmount() != null
                ? invoice.getDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lateFee = invoice.getLateFeeAmount() != null
                ? invoice.getLateFeeAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        invoice.setSubtotalAmount(subtotal);
        invoice.setDiscountAmount(discount);
        invoice.setLateFeeAmount(lateFee);

        BigDecimal total = subtotal.subtract(discount).add(lateFee).setScale(2, RoundingMode.HALF_UP);
        invoice.setTotalAmount(
                total.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : total);
    }

    // Sinh mã hóa đơn theo quy chuẩn INV-yyyyMM-studentCode
    private String generateInvoiceCode(Student student, LocalDate invoiceMonth) {
        String studentCode = (student.getStudentCode() != null && !student.getStudentCode().isBlank())
                ? student.getStudentCode()
                : student.getStudentId();
        String baseCode = "INV-" + invoiceMonth.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + studentCode;
        String candidateCode = baseCode;
        int counter = 1;
        while (invoiceRepository.existsByInvoiceCode(candidateCode)) {
            candidateCode = String.format("%s-%02d", baseCode, counter++);
        }
        return candidateCode;
    }

    // Xác định đơn giá phòng hàng tháng theo thứ tự ưu tiên: Hợp đồng -> Bảng Loại
    // phòng trong CSDL
    private BigDecimal resolveRoomMonthlyFee(Contract hop_dong) {
        if (hop_dong != null && hop_dong.getMonthlyFee() != null
                && hop_dong.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0) {
            return hop_dong.getMonthlyFee().setScale(2, RoundingMode.HALF_UP);
        }
        if (hop_dong != null && hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
            Room phong = hop_dong.getBed().getRoom();
            if (phong.getRoomType() != null && phong.getRoomType().getPricePerMonth() != null
                    && phong.getRoomType().getPricePerMonth().compareTo(BigDecimal.ZERO) > 0) {
                return phong.getRoomType().getPricePerMonth().setScale(2, RoundingMode.HALF_UP);
            }
        }
        throw new AppException(ErrorCode.OPERATION_FAILED,
                "Không thể xác định đơn giá phòng cho hợp đồng '" + (hop_dong != null ? hop_dong.getContractCode() : "")
                        + "'. Vui lòng cấu hình đơn giá cho loại phòng hoặc ghi rõ trên hợp đồng.");
    }

    // Lấy thông tin hóa đơn theo ID kèm kiểm tra quyền sở hữu sinh viên
    @Transactional(readOnly = true)
    public Invoice_Response getById(String invoiceId) {
        Invoice hoa_don = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        checkStudentAccessOwnership(hoa_don.getStudent().getStudentId());
        return invoiceMapper.toResponse(hoa_don);
    }

    // Lấy danh sách hóa đơn theo sinh viên kèm kiểm tra quyền sở hữu
    @Transactional(readOnly = true)
    public List<Invoice_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Invoice> danh_sach_hoa_don = invoiceRepository.findByStudent_StudentId(studentId);
        return invoiceMapper.toResponseList(danh_sach_hoa_don);
    }

    // Lấy danh sách hóa đơn theo hợp đồng
    @Transactional(readOnly = true)
    public List<Invoice_Response> getByContractId(String contractId) {
        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (hop_dong.getStudent() != null) {
            checkStudentAccessOwnership(hop_dong.getStudent().getStudentId());
        }

        List<Invoice> danh_sach_hoa_don = invoiceRepository.findByContract_ContractId(contractId);
        return invoiceMapper.toResponseList(danh_sach_hoa_don);
    }

    // Tìm kiếm hóa đơn phân trang theo từ khóa, trạng thái và kỳ hóa đơn
    @Transactional(readOnly = true)
    public Page_Response<Invoice_Response> search(
            String keyword,
            String status,
            LocalDate invoiceMonth,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không được truy cập danh sách toàn bộ hóa đơn");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "dueDate";
        LocalDate normalizedMonth = invoiceMonth != null ? invoiceMonth.withDayOfMonth(1) : null;
        int pageIndex = Math.max(page - 1, 0);
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Invoice> trang_hoa_don = invoiceRepository.searchInvoices(
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (status != null && !status.isBlank()) ? status.trim() : null,
                normalizedMonth,
                pageable);

        List<Invoice_Response> data = invoiceMapper.toResponseList(trang_hoa_don.getContent());

        return Page_Response.<Invoice_Response>builder()
                .currentPage(trang_hoa_don.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_hoa_don.getTotalPages())
                .totalElements(trang_hoa_don.getTotalElements())
                .hasPrevious(trang_hoa_don.hasPrevious())
                .hasNext(trang_hoa_don.hasNext())
                .isFirst(trang_hoa_don.isFirst())
                .isLast(trang_hoa_don.isLast())
                .data(data)
                .build();
    }

    // Lấy toàn bộ hóa đơn có phân trang
    @Transactional(readOnly = true)
    public Page_Response<Invoice_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return search(null, null, null, page, size, sortBy, sortDir);
    }

    // Phát hành hóa đơn (chuyển trạng thái từ DRAFT sang ISSUED)
    @Transactional
    public Invoice_Response issue(String invoiceId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ nhân viên hoặc quản trị viên mới có quyền phát hành hóa đơn");
        }

        Invoice hoa_don = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if (!"DRAFT".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể phát hành hóa đơn khi đang ở trạng thái DRAFT (Trạng thái hiện tại: "
                            + hoa_don.getStatus() + ")");
        }

        hoa_don.setStatus("ISSUED");
        hoa_don.setIssueDate(LocalDate.now());
        if (hoa_don.getDueDate() == null || hoa_don.getDueDate().isBefore(hoa_don.getIssueDate())) {
            int deadlineDays = systemSettingService != null
                    ? systemSettingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 10)
                    : 10;
            hoa_don.setDueDate(hoa_don.getIssueDate().plusDays(deadlineDays));
        }

        recalculateInvoiceAmounts(hoa_don);
        hoa_don = invoiceRepository.save(hoa_don);
        log.info("Đã phát hành hóa đơn: {}", hoa_don.getInvoiceCode());

        return invoiceMapper.toResponse(hoa_don);
    }

    // Cập nhật thông tin hóa đơn khi chưa thanh toán hoàn tất
    @Transactional
    public Invoice_Response update(String invoiceId, Invoice_Request request) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ quản lý hoặc quản trị viên mới có quyền chỉnh sửa hóa đơn");
        }

        Invoice hoa_don = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa hóa đơn đã thanh toán hoàn tất");
        }
        if ("CANCELLED".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa hóa đơn đã bị hủy (CANCELLED)");
        }

        if (request.getInvoiceCode() != null && !request.getInvoiceCode().isBlank() &&
                !hoa_don.getInvoiceCode().equalsIgnoreCase(request.getInvoiceCode()) &&
                invoiceRepository.existsByInvoiceCode(request.getInvoiceCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã hóa đơn '" + request.getInvoiceCode() + "' đã tồn tại");
        }

        if (request.getInvoiceMonth() != null) {
            LocalDate newMonth = request.getInvoiceMonth().withDayOfMonth(1);
            if (!newMonth.equals(hoa_don.getInvoiceMonth()) &&
                    invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(hoa_don.getContract().getContractId(),
                            newMonth)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Hóa đơn cho kỳ " + newMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " đã tồn tại");
            }
            hoa_don.setInvoiceMonth(newMonth);
        }

        invoiceMapper.updateEntityFromRequest(hoa_don, request);

        if (request.getSubtotalAmount() != null && request.getSubtotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            hoa_don.setSubtotalAmount(request.getSubtotalAmount().setScale(2, RoundingMode.HALF_UP));
        }

        recalculateInvoiceAmounts(hoa_don);

        // Kiểm tra tổng tiền sau cập nhật không được nhỏ hơn số tiền sinh viên đã thanh
        // toán thực tế
        if (hoa_don.getPaidAmount() != null && hoa_don.getTotalAmount().compareTo(hoa_don.getPaidAmount()) < 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Tổng tiền phải trả sau khi cập nhật (" + hoa_don.getTotalAmount()
                            + ") không được nhỏ hơn số tiền đã thanh toán (" + hoa_don.getPaidAmount() + ")");
        }

        hoa_don = invoiceRepository.save(hoa_don);
        return invoiceMapper.toResponse(hoa_don);
    }

    // Hủy hóa đơn (chuyển sang CANCELLED nếu chưa thanh toán và chưa có khoản thanh
    // toán thực tế)
    @Transactional
    public Invoice_Response cancel(String invoiceId, String reason) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ quản lý hoặc quản trị viên mới có quyền hủy hóa đơn");
        }

        Invoice hoa_don = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể hủy hóa đơn đã thanh toán hoàn tất");
        }
        if ("CANCELLED".equalsIgnoreCase(hoa_don.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn này đã ở trạng thái CANCELLED trước đó");
        }
        if (hoa_don.getPaidAmount() != null && hoa_don.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể hủy hóa đơn đã phát sinh tiền thanh toán thực tế (" + hoa_don.getPaidAmount()
                            + " VNĐ). Vui lòng hoàn tiền trước");
        }

        // Giải phóng các biên bản vi phạm FINE khỏi hóa đơn hủy để không bị kẹt
        // referenceId
        if (hoa_don.getInvoiceItems() != null) {
            for (InvoiceItem item : hoa_don.getInvoiceItems()) {
                if ("FINE".equalsIgnoreCase(item.getItemType())) {
                    item.setReferenceId(null);
                }
            }
        }

        hoa_don.setStatus("CANCELLED");
        hoa_don = invoiceRepository.save(hoa_don);
        log.info("Đã hủy hóa đơn: {} với lý do: {}", hoa_don.getInvoiceCode(), reason);
        return invoiceMapper.toResponse(hoa_don);
    }

    // Xóa hóa đơn (chỉ cho phép xóa khi chưa thanh toán và chưa có giao dịch thu
    // tiền)
    @Transactional
    public void delete(String invoiceId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ quản lý hoặc quản trị viên mới có quyền xóa hóa đơn");
        }

        Invoice hoa_don = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(hoa_don.getStatus())
                || "PARTIALLY_PAID".equalsIgnoreCase(hoa_don.getStatus())
                || (hoa_don.getPaidAmount() != null && hoa_don.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa hóa đơn đã thanh toán hoặc đã thu tiền một phần");
        }

        if (paymentRepository.existsByInvoice_InvoiceId(invoiceId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa hóa đơn đã phát sinh giao dịch thanh toán");
        }

        invoiceRepository.delete(hoa_don);
        log.info("Đã xóa hóa đơn: {}", hoa_don.getInvoiceCode());
    }

    // Lập hóa đơn hàng loạt cho các hợp đồng đang hoạt động trong kỳ tháng
    @Transactional
    public List<Invoice_Response> generateBatch(InvoiceGenerate_Request request) {
        if (request.getInvoiceMonth() == null) {
            throw new AppException(ErrorCode.INVOICE_MONTH_BLANK);
        }
        LocalDate invoiceMonth = request.getInvoiceMonth().withDayOfMonth(1);
        LocalDate issueDate = LocalDate.now();
        int defaultDeadlineDays = systemSettingService != null
                ? systemSettingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 15)
                : 15;
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate()
                : issueDate.plusDays(defaultDeadlineDays);

        if (dueDate.isBefore(issueDate)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn thanh toán không thể trước ngày hiện tại");
        }

        log.info("Bắt đầu lập hóa đơn hàng loạt cho kỳ tháng: {}", invoiceMonth);

        List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStatus("ACTIVE");
        List<Invoice> danh_sach_hoa_don_da_tao = new ArrayList<>();
        LocalDate monthEnd = invoiceMonth.withDayOfMonth(invoiceMonth.lengthOfMonth());

        for (Contract hop_dong : danh_sach_hop_dong_hieu_luc) {
            if (hop_dong.getStartDate().isAfter(monthEnd) || hop_dong.getEndDate().isBefore(invoiceMonth)) {
                continue;
            }

            // Lọc theo Tòa nhà nếu có yêu cầu phạm vi
            if (request.getBuildingId() != null && !request.getBuildingId().isBlank()) {
                String contractBuildingId = null;
                if (hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null
                        && hop_dong.getBed().getRoom().getFloor() != null
                        && hop_dong.getBed().getRoom().getFloor().getBuilding() != null) {
                    contractBuildingId = hop_dong.getBed().getRoom().getFloor().getBuilding().getBuildingId();
                }
                if (!request.getBuildingId().equals(contractBuildingId)) {
                    continue;
                }
            }

            // Lọc theo Tầng nếu có yêu cầu phạm vi
            if (request.getFloorId() != null && !request.getFloorId().isBlank()) {
                String contractFloorId = null;
                if (hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null
                        && hop_dong.getBed().getRoom().getFloor() != null) {
                    contractFloorId = hop_dong.getBed().getRoom().getFloor().getFloorId();
                }
                if (!request.getFloorId().equals(contractFloorId)) {
                    continue;
                }
            }

            // Bỏ qua nếu đã có hóa đơn đang hoạt động (không tính hóa đơn đã bị hủy
            // CANCELLED)
            List<Invoice> danh_sach_hoa_don_da_ton_tai = invoiceRepository
                    .findByContract_ContractIdAndInvoiceMonth(hop_dong.getContractId(), invoiceMonth);
            if (danh_sach_hoa_don_da_ton_tai != null
                    && danh_sach_hoa_don_da_ton_tai.stream().anyMatch(inv -> !"CANCELLED".equalsIgnoreCase(inv.getStatus()))) {
                continue;
            }

            Student sinh_vien = hop_dong.getStudent();
            if (sinh_vien == null) {
                continue;
            }

            BigDecimal monthlyFee;
            try {
                monthlyFee = resolveRoomMonthlyFee(hop_dong);
            } catch (Exception ex) {
                log.warn("Bỏ qua hợp đồng {} khi lập hóa đơn hàng loạt: {}", hop_dong.getContractCode(),
                        ex.getMessage());
                continue;
            }

            String invoiceCode = generateInvoiceCode(sinh_vien, invoiceMonth);

            Invoice hoa_don = Invoice.builder()
                    .invoiceCode(invoiceCode)
                    .contract(hop_dong)
                    .student(sinh_vien)
                    .invoiceMonth(invoiceMonth)
                    .issueDate(issueDate)
                    .dueDate(dueDate)
                    .subtotalAmount(monthlyFee)
                    .discountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .lateFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .totalAmount(monthlyFee)
                    .paidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .status("ISSUED")
                    .invoiceItems(new ArrayList<>())
                    .build();

            if (monthlyFee.compareTo(BigDecimal.ZERO) > 0) {
                InvoiceItem rentItem = InvoiceItem.builder()
                        .invoice(hoa_don)
                        .itemType("RENT")
                        .description("Tiền thuê phòng lưu trú kỳ tháng "
                                + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                        .quantity(BigDecimal.ONE)
                        .unitPrice(monthlyFee)
                        .amount(monthlyFee)
                        .referenceId(hop_dong.getContractId())
                        .build();
                hoa_don.getInvoiceItems().add(rentItem);
            }

            // Gắn tiền điện nước phòng (UTILITY) phân bổ cho sinh viên (ưu tiên chỉ số chốt
            // tháng trước invoiceMonth.minusMonths(1))
            if (utilityReadingRepository != null && utilityReadingService != null
                    && hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
                String roomId = hop_dong.getBed().getRoom().getRoomId();
                LocalDate utilityMonth = invoiceMonth.minusMonths(1);
                List<UtilityReading> danh_sach_chi_so = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId,
                        utilityMonth);
                if (danh_sach_chi_so.isEmpty()) {
                    danh_sach_chi_so = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId, invoiceMonth);
                    if (!danh_sach_chi_so.isEmpty()) {
                        utilityMonth = invoiceMonth;
                    }
                }
                for (UtilityReading reading : danh_sach_chi_so) {
                    try {
                        UtilitySplit_Response split = utilityReadingService.calculateRoomUtilitySplit(
                                roomId, reading.getServiceType().getServiceTypeId(), utilityMonth);
                        if (split != null && split.getResidentShares() != null) {
                            split.getResidentShares().stream()
                                    .filter(rs -> rs.getStudentId() != null
                                            && rs.getStudentId().equals(sinh_vien.getStudentId()))
                                    .findFirst()
                                    .ifPresent(rs -> {
                                        if (rs.getShareAmount() != null
                                                && rs.getShareAmount().compareTo(BigDecimal.ZERO) > 0) {
                                            String srvName = (reading.getServiceType() != null
                                                    && reading.getServiceType().getServiceName() != null)
                                                            ? reading.getServiceType().getServiceName()
                                                            : "Điện nước";
                                            InvoiceItem utilityItem = InvoiceItem.builder()
                                                    .invoice(hoa_don)
                                                    .itemType("UTILITY")
                                                    .description("Tiền " + srvName + " tháng "
                                                            + invoiceMonth
                                                                    .format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                                    .quantity(BigDecimal.ONE)
                                                    .unitPrice(rs.getShareAmount().setScale(2, RoundingMode.HALF_UP))
                                                    .amount(rs.getShareAmount().setScale(2, RoundingMode.HALF_UP))
                                                    .referenceId(reading.getReadingId())
                                                    .build();
                                            hoa_don.getInvoiceItems().add(utilityItem);
                                        }
                                    });
                        }
                    } catch (Exception ex) {
                        log.warn("Không thể tính chia điện nước phòng {} cho hợp đồng {}: {}", roomId,
                                hop_dong.getContractCode(), ex.getMessage());
                    }
                }
            }

            // Gắn các dịch vụ KTX theo tháng (SERVICE):
            // 1. Phí theo đầu người (PER_PERSON): Vệ sinh thu gom rác, Điện an ninh chiếu
            // sáng chung
            // 2. Phí cố định theo phòng (FLAT) chia đều số SV trong phòng của kỳ tháng:
            // Internet Wifi
            if (serviceTypeRepository != null) {
                List<ServiceType> danh_sach_loai_dich_vu = serviceTypeRepository.findAll();
                String monthLabel = invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"));

                for (ServiceType st : danh_sach_loai_dich_vu) {
                    if ("PER_PERSON".equalsIgnoreCase(st.getBillingType())
                            && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                        InvoiceItem serviceItem = InvoiceItem.builder()
                                .invoice(hoa_don)
                                .itemType("SERVICE")
                                .description(st.getServiceName() + " tháng " + monthLabel)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .amount(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(st.getServiceTypeId())
                                .build();
                        hoa_don.getInvoiceItems().add(serviceItem);
                    }
                }

                if (hop_dong.getBed() != null && hop_dong.getBed().getRoom() != null) {
                    String roomId = hop_dong.getBed().getRoom().getRoomId();
                    String roomNumber = hop_dong.getBed().getRoom().getRoomNumber();
                    long activeCountInRoom = danh_sach_hop_dong_hieu_luc.stream()
                            .filter(c -> c.getBed() != null && c.getBed().getRoom() != null
                                     && roomId.equals(c.getBed().getRoom().getRoomId())
                                    && !c.getStartDate().isAfter(monthEnd) && !c.getEndDate().isBefore(invoiceMonth))
                            .count();
                    if (activeCountInRoom == 0) {
                        activeCountInRoom = 1;
                    }

                    for (ServiceType st : danh_sach_loai_dich_vu) {
                        if ("FLAT".equalsIgnoreCase(st.getBillingType())
                                && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal flatShare = st.getUnitPrice().divide(
                                    BigDecimal.valueOf(activeCountInRoom), 2, RoundingMode.HALF_UP);
                            InvoiceItem flatItem = InvoiceItem.builder()
                                    .invoice(hoa_don)
                                    .itemType("SERVICE")
                                    .description(
                                            st.getServiceName() + " (Phòng " + roomNumber + ") tháng " + monthLabel)
                                    .quantity(BigDecimal.ONE)
                                    .unitPrice(flatShare)
                                    .amount(flatShare)
                                    .referenceId(st.getServiceTypeId())
                                    .build();
                            hoa_don.getInvoiceItems().add(flatItem);
                        }
                    }
                }
            }

            // Gắn phí gửi xe (VEHICLE) đang hoạt động trong kỳ
            if (vehicleRegistrationRepository != null) {
                List<VehicleRegistration> danh_sach_xe = vehicleRegistrationRepository.findActiveVehiclesForStudentInMonth(
                        sinh_vien.getStudentId(), invoiceMonth, monthEnd);
                for (VehicleRegistration vehicle : danh_sach_xe) {
                    if (vehicle.getMonthlyFee() != null && vehicle.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0) {
                        String plateInfo = (vehicle.getLicensePlate() != null && !vehicle.getLicensePlate().isBlank())
                                ? " (" + vehicle.getLicensePlate() + ")"
                                : "";
                        InvoiceItem vehicleItem = InvoiceItem.builder()
                                .invoice(hoa_don)
                                .itemType("VEHICLE")
                                .description("Phí gửi xe " + vehicle.getVehicleType() + plateInfo + " tháng "
                                        + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                .quantity(BigDecimal.ONE)
                                .unitPrice(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .amount(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(vehicle.getVehicleId())
                                .build();
                        hoa_don.getInvoiceItems().add(vehicleItem);
                    }
                }
            }

            // Gắn tiền phạt vi phạm kỷ luật chưa tính vào hóa đơn (FINE)
            if (violationRepository != null) {
                List<Violation> danh_sach_vi_pham = violationRepository.findUnbilledFinesByStudent(sinh_vien.getStudentId(),
                        monthEnd);
                for (Violation v : danh_sach_vi_pham) {
                    boolean alreadyBilled = invoiceItemRepository != null
                            && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", v.getViolationId());
                    if (!alreadyBilled && v.getFineAmount() != null
                            && v.getFineAmount().compareTo(BigDecimal.ZERO) > 0) {
                        String vName = (v.getViolationType() != null && v.getViolationType().getViolationName() != null)
                                ? v.getViolationType().getViolationName()
                                : "Kỷ luật";
                        InvoiceItem fineItem = InvoiceItem.builder()
                                .invoice(hoa_don)
                                .itemType("FINE")
                                .description("Tiền phạt vi phạm: " + vName)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .amount(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(v.getViolationId())
                                .build();
                        hoa_don.getInvoiceItems().add(fineItem);
                    }
                }
            }

            // Tự động tính lại tổng tiền từ toàn bộ các dòng chi tiết RENT, UTILITY,
            // VEHICLE, FINE
            recalculateInvoiceAmounts(hoa_don);

            danh_sach_hoa_don_da_tao.add(hoa_don);
        }

        if (!danh_sach_hoa_don_da_tao.isEmpty()) {
            danh_sach_hoa_don_da_tao = invoiceRepository.saveAll(danh_sach_hoa_don_da_tao);
            log.info("Lập hóa đơn hàng loạt hoàn tất: đã tạo {} hóa đơn cho kỳ {}", danh_sach_hoa_don_da_tao.size(),
                    invoiceMonth);
        }

        return invoiceMapper.toResponseList(danh_sach_hoa_don_da_tao);
    }

    // Tác vụ tự động lập hóa đơn hàng loạt vào lúc 23:00 ngày 28 hàng tháng
    @Scheduled(cron = "0 0 23 28 * ?")
    @Transactional
    public void scheduleMonthlyBatchInvoicing() {
        LocalDate nextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        log.info("Bắt đầu tác vụ tự động lập hóa đơn hàng loạt cho kỳ tới: {}", nextMonth);
        try {
            InvoiceGenerate_Request req = InvoiceGenerate_Request.builder()
                    .invoiceMonth(nextMonth)
                    .build();
            generateBatch(req);
        } catch (Exception ex) {
            log.error("Lỗi khi chạy tự động lập hóa đơn hàng loạt: {}", ex.getMessage(), ex);
        }
    }

    // Tác vụ quét và cập nhật trạng thái hóa đơn quá hạn kèm phí phạt trễ hạn
    @Transactional
    public int processOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> danh_sach_hoa_don_qua_han = invoiceRepository.findOverdueInvoices(today);

        if (danh_sach_hoa_don_qua_han.isEmpty()) {
            log.info("Không có hóa đơn nào quá hạn cần xử lý hôm nay: {}", today);
            return 0;
        }

        log.info("Phát hiện {} hóa đơn quá hạn chưa thanh toán, tiến hành cập nhật trạng thái và phí phạt trễ hạn",
                danh_sach_hoa_don_qua_han.size());

        int count = 0;
        for (Invoice hoa_don : danh_sach_hoa_don_qua_han) {
            // Chỉ cập nhật trạng thái OVERDUE cho hóa đơn ISSUED (chưa thanh toán đồng
            // nào).
            // Hóa đơn PARTIALLY_PAID giữ nguyên trạng thái để tránh bị đảo trạng thái qua
            // lại hằng đêm.
            if ("ISSUED".equalsIgnoreCase(hoa_don.getStatus())) {
                hoa_don.setStatus("OVERDUE");
            }

            // Áp dụng phí phạt trễ hạn đọc từ cấu hình hệ thống (mặc định 5%) trên số tiền
            // chưa thanh toán nếu chưa bị tính phí phạt
            if (hoa_don.getLateFeeAmount() == null || hoa_don.getLateFeeAmount().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal finePercent = systemSettingService != null
                        ? systemSettingService.getDecimalSetting("LATE_PAYMENT_FINE_PERCENT", new BigDecimal("5.0"))
                        : new BigDecimal("5.0");
                BigDecimal lateFeeRate = finePercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                BigDecimal totalBeforeLate = (hoa_don.getSubtotalAmount() != null ? hoa_don.getSubtotalAmount()
                        : BigDecimal.ZERO)
                        .subtract(hoa_don.getDiscountAmount() != null ? hoa_don.getDiscountAmount() : BigDecimal.ZERO);
                BigDecimal paid = hoa_don.getPaidAmount() != null ? hoa_don.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal unpaid = totalBeforeLate.subtract(paid);

                if (unpaid.compareTo(BigDecimal.ZERO) > 0 && lateFeeRate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal lateFee = unpaid.multiply(lateFeeRate).setScale(2, RoundingMode.HALF_UP);
                    hoa_don.setLateFeeAmount(lateFee);
                }
            }

            recalculateInvoiceAmounts(hoa_don);
            count++;
        }

        invoiceRepository.saveAll(danh_sach_hoa_don_qua_han);
        log.info("Đã xử lý {} hóa đơn quá hạn", count);
        return count;
    }

    // Kiểm tra quyền sở hữu đối với tài khoản sinh viên (Fail-closed)
    private void checkStudentAccessOwnership(String targetStudentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse(null);
            if (currentStudentId == null || !currentStudentId.equals(targetStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không có quyền truy cập hóa đơn của sinh viên khác");
            }
        }
    }
}
