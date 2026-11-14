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

        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Lấy thông tin sinh viên trực tiếp từ hợp đồng
        Student student = contract.getStudent();
        if (student == null) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        // Kiểm tra tính nhất quán nếu client truyền kèm studentId
        if (request.getStudentId() != null && !request.getStudentId().isBlank()
                && !request.getStudentId().equals(student.getStudentId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã sinh viên không khớp với sinh viên trên hợp đồng");
        }

        // Chuẩn hóa kỳ hóa đơn về ngày đầu tháng
        LocalDate invoiceMonth = (request.getInvoiceMonth() != null ? request.getInvoiceMonth() : LocalDate.now())
                .withDayOfMonth(1);

        // Kiểm tra trùng lặp kỳ hóa đơn trên cùng một hợp đồng
        if (invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(contract.getContractId(), invoiceMonth)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn cho hợp đồng '" + contract.getContractCode()
                    + "' trong tháng " + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " đã tồn tại");
        }

        // Tạo mã hóa đơn theo quy chuẩn nếu client không truyền vào
        String invoiceCode = request.getInvoiceCode();
        if (invoiceCode == null || invoiceCode.isBlank()) {
            invoiceCode = generateInvoiceCode(student, invoiceMonth);
            request.setInvoiceCode(invoiceCode);
        } else if (invoiceRepository.existsByInvoiceCode(invoiceCode)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Mã hóa đơn '" + invoiceCode + "' đã tồn tại");
        }

        log.info("Tạo hóa đơn mới: {} cho sinh viên: {}", invoiceCode, student.getStudentCode());

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();
        int deadlineDays = systemSettingService != null
                ? systemSettingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 10)
                : 10;
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : issueDate.plusDays(deadlineDays);
        if (dueDate.isBefore(issueDate)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn thanh toán không thể trước ngày phát hành");
        }

        Invoice invoice = invoiceMapper.toEntity(request);
        invoice.setInvoiceCode(invoiceCode);
        invoice.setContract(contract);
        invoice.setStudent(student);
        invoice.setInvoiceMonth(invoiceMonth);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);

        // Trạng thái ban đầu: ưu tiên theo request (ISSUED / DRAFT), số tiền đã trả luôn là 0
        String initialStatus = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().toUpperCase()
                : "DRAFT";
        invoice.setStatus(initialStatus);
        invoice.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        // Khởi tạo danh sách chi tiết hóa đơn nếu chưa có
        if (invoice.getInvoiceItems() == null) {
            invoice.setInvoiceItems(new ArrayList<>());
        }

        // 1. Trường hợp client gửi danh sách chi tiết khoản thu (invoiceItems)
        if (request.getInvoiceItems() != null && !request.getInvoiceItems().isEmpty()) {
            for (vn.QL_KyTucXa.DTO.Request.InvoiceItem_Request itemReq : request.getInvoiceItems()) {
                BigDecimal qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : BigDecimal.ONE;
                BigDecimal price = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal amt = itemReq.getAmount() != null ? itemReq.getAmount().setScale(2, RoundingMode.HALF_UP)
                        : qty.multiply(price).setScale(2, RoundingMode.HALF_UP);

                InvoiceItem item = InvoiceItem.builder()
                        .invoice(invoice)
                        .itemType(itemReq.getItemType() != null ? itemReq.getItemType() : "SERVICE")
                        .description(itemReq.getDescription())
                        .quantity(qty)
                        .unitPrice(price)
                        .amount(amt)
                        .referenceId(itemReq.getReferenceId())
                        .build();
                invoice.getInvoiceItems().add(item);
            }
        } else {
            // 2. Tự động bóc tách các dòng chi tiết: RENT, UTILITY, SERVICE (PER_PERSON & FLAT)
            BigDecimal roomRent;
            if (request.getSubtotalAmount() != null && request.getSubtotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                roomRent = request.getSubtotalAmount().setScale(2, RoundingMode.HALF_UP);
            } else {
                roomRent = resolveRoomMonthlyFee(contract);
            }
            if (roomRent.compareTo(BigDecimal.ZERO) > 0) {
                InvoiceItem rentItem = InvoiceItem.builder()
                        .invoice(invoice)
                        .itemType("RENT")
                        .description("Tiền thuê phòng lưu trú kỳ tháng "
                                + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                        .quantity(BigDecimal.ONE)
                        .unitPrice(roomRent)
                        .amount(roomRent)
                        .referenceId(contract.getContractId())
                        .build();
                invoice.getInvoiceItems().add(rentItem);
            }

            // Gắn tiền điện nước phòng (UTILITY) phân bổ cho sinh viên
            if (utilityReadingRepository != null && utilityReadingService != null
                    && contract.getBed() != null && contract.getBed().getRoom() != null) {
                String roomId = contract.getBed().getRoom().getRoomId();
                LocalDate utilityMonth = invoiceMonth.minusMonths(1);
                List<vn.QL_KyTucXa.Entity.UtilityReading> readings = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId, utilityMonth);
                if (readings.isEmpty()) {
                    readings = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId, invoiceMonth);
                    if (!readings.isEmpty()) {
                        utilityMonth = invoiceMonth;
                    }
                }
                final Invoice targetInvoice = invoice;
                final LocalDate finalInvoiceMonth = invoiceMonth;
                for (vn.QL_KyTucXa.Entity.UtilityReading reading : readings) {
                    try {
                        vn.QL_KyTucXa.DTO.Response.UtilitySplit_Response split = utilityReadingService.calculateRoomUtilitySplit(
                                roomId, reading.getServiceType().getServiceTypeId(), utilityMonth);
                        if (split != null && split.getResidentShares() != null) {
                            split.getResidentShares().stream()
                                    .filter(rs -> rs.getStudentId() != null
                                            && rs.getStudentId().equals(student.getStudentId()))
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
                                                            + finalInvoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
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

            // Tự động gắn các dịch vụ KTX theo tháng (PER_PERSON: Rác, An ninh & FLAT: Wifi phòng)
            if (serviceTypeRepository != null) {
                List<ServiceType> serviceTypes = serviceTypeRepository.findAll();
                String monthLabel = invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"));
                for (ServiceType st : serviceTypes) {
                    if ("PER_PERSON".equalsIgnoreCase(st.getBillingType())
                            && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                        InvoiceItem serviceItem = InvoiceItem.builder()
                                .invoice(invoice)
                                .itemType("SERVICE")
                                .description(st.getServiceName() + " tháng " + monthLabel)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .amount(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(st.getServiceTypeId())
                                .build();
                        invoice.getInvoiceItems().add(serviceItem);
                    }
                }
                if (contract.getBed() != null && contract.getBed().getRoom() != null) {
                    String roomId = contract.getBed().getRoom().getRoomId();
                    String roomNumber = contract.getBed().getRoom().getRoomNumber();
                    List<Contract> activeContractsInRoom = contractRepository.findActiveContractsByRoomId(roomId);
                    int activeCountInRoom = activeContractsInRoom.isEmpty() ? 1 : activeContractsInRoom.size();
                    for (ServiceType st : serviceTypes) {
                        if ("FLAT".equalsIgnoreCase(st.getBillingType())
                                && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal flatShare = st.getUnitPrice().divide(
                                    BigDecimal.valueOf(activeCountInRoom), 2, RoundingMode.HALF_UP);
                            InvoiceItem flatItem = InvoiceItem.builder()
                                    .invoice(invoice)
                                    .itemType("SERVICE")
                                    .description(st.getServiceName() + " (Phòng " + roomNumber + ") tháng " + monthLabel)
                                    .quantity(BigDecimal.ONE)
                                    .unitPrice(flatShare)
                                    .amount(flatShare)
                                    .referenceId(st.getServiceTypeId())
                                    .build();
                            invoice.getInvoiceItems().add(flatItem);
                        }
                    }
                }
            }

            LocalDate monthEnd = invoiceMonth.withDayOfMonth(invoiceMonth.lengthOfMonth());

            // Tự động gắn phí gửi xe (VEHICLE) đang hoạt động trong kỳ
            if (vehicleRegistrationRepository != null) {
                List<VehicleRegistration> vehicles = vehicleRegistrationRepository.findActiveVehiclesForStudentInMonth(
                        student.getStudentId(), invoiceMonth, monthEnd);
                for (VehicleRegistration vehicle : vehicles) {
                    if (vehicle.getMonthlyFee() != null && vehicle.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0) {
                        String plateInfo = (vehicle.getLicensePlate() != null && !vehicle.getLicensePlate().isBlank())
                                ? " (" + vehicle.getLicensePlate() + ")"
                                : "";
                        InvoiceItem vehicleItem = InvoiceItem.builder()
                                .invoice(invoice)
                                .itemType("VEHICLE")
                                .description("Phí gửi xe " + vehicle.getVehicleType() + plateInfo + " tháng "
                                        + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                .quantity(BigDecimal.ONE)
                                .unitPrice(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .amount(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(vehicle.getVehicleId())
                                .build();
                        invoice.getInvoiceItems().add(vehicleItem);
                    }
                }
            }

            // Tự động gắn tiền phạt vi phạm kỷ luật chưa tính vào hóa đơn (FINE)
            if (violationRepository != null) {
                List<Violation> violations = violationRepository.findUnbilledFinesByStudent(student.getStudentId(),
                        monthEnd);
                for (Violation v : violations) {
                    boolean alreadyBilled = invoiceItemRepository != null
                            && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", v.getViolationId());
                    if (!alreadyBilled && v.getFineAmount() != null
                            && v.getFineAmount().compareTo(BigDecimal.ZERO) > 0) {
                        String vName = (v.getViolationType() != null && v.getViolationType().getViolationName() != null)
                                ? v.getViolationType().getViolationName()
                                : "Kỷ luật";
                        InvoiceItem fineItem = InvoiceItem.builder()
                                .invoice(invoice)
                                .itemType("FINE")
                                .description("Tiền phạt vi phạm: " + vName)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .amount(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(v.getViolationId())
                                .build();
                        invoice.getInvoiceItems().add(fineItem);
                    }
                }
            }
        }

        invoice.setDiscountAmount(
                request.getDiscountAmount() != null ? request.getDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setLateFeeAmount(
                request.getLateFeeAmount() != null ? request.getLateFeeAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        recalculateInvoiceAmounts(invoice);

        invoice = invoiceRepository.save(invoice);
        return invoiceMapper.toResponse(invoice);
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
    private BigDecimal resolveRoomMonthlyFee(Contract contract) {
        if (contract != null && contract.getMonthlyFee() != null
                && contract.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0) {
            return contract.getMonthlyFee().setScale(2, RoundingMode.HALF_UP);
        }
        if (contract != null && contract.getBed() != null && contract.getBed().getRoom() != null) {
            Room room = contract.getBed().getRoom();
            if (room.getRoomType() != null && room.getRoomType().getPricePerMonth() != null
                    && room.getRoomType().getPricePerMonth().compareTo(BigDecimal.ZERO) > 0) {
                return room.getRoomType().getPricePerMonth().setScale(2, RoundingMode.HALF_UP);
            }
        }
        throw new AppException(ErrorCode.OPERATION_FAILED,
                "Không thể xác định đơn giá phòng cho hợp đồng '" + (contract != null ? contract.getContractCode() : "")
                        + "'. Vui lòng cấu hình đơn giá cho loại phòng hoặc ghi rõ trên hợp đồng.");
    }

    // Lấy thông tin hóa đơn theo ID kèm kiểm tra quyền sở hữu sinh viên
    @Transactional(readOnly = true)
    public Invoice_Response getById(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        checkStudentAccessOwnership(invoice.getStudent().getStudentId());
        return invoiceMapper.toResponse(invoice);
    }

    // Lấy danh sách hóa đơn theo sinh viên kèm kiểm tra quyền sở hữu
    @Transactional(readOnly = true)
    public List<Invoice_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Invoice> list = invoiceRepository.findByStudent_StudentId(studentId);
        return invoiceMapper.toResponseList(list);
    }

    // Lấy danh sách hóa đơn theo hợp đồng
    @Transactional(readOnly = true)
    public List<Invoice_Response> getByContractId(String contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStudent() != null) {
            checkStudentAccessOwnership(contract.getStudent().getStudentId());
        }

        List<Invoice> list = invoiceRepository.findByContract_ContractId(contractId);
        return invoiceMapper.toResponseList(list);
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

        Page<Invoice> pageResult = invoiceRepository.searchInvoices(
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                (status != null && !status.isBlank()) ? status.trim() : null,
                normalizedMonth,
                pageable);

        List<Invoice_Response> data = invoiceMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Invoice_Response>builder()
                .currentPage(pageResult.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasPrevious(pageResult.hasPrevious())
                .hasNext(pageResult.hasNext())
                .isFirst(pageResult.isFirst())
                .isLast(pageResult.isLast())
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
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ nhân viên hoặc quản trị viên mới có quyền phát hành hóa đơn");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if (!"DRAFT".equalsIgnoreCase(invoice.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể phát hành hóa đơn khi đang ở trạng thái DRAFT (Trạng thái hiện tại: "
                            + invoice.getStatus() + ")");
        }

        invoice.setStatus("ISSUED");
        invoice.setIssueDate(LocalDate.now());
        if (invoice.getDueDate() == null || invoice.getDueDate().isBefore(invoice.getIssueDate())) {
            int deadlineDays = systemSettingService != null
                    ? systemSettingService.getIntSetting("INVOICE_PAYMENT_DEADLINE_DAYS", 10)
                    : 10;
            invoice.setDueDate(invoice.getIssueDate().plusDays(deadlineDays));
        }

        recalculateInvoiceAmounts(invoice);
        invoice = invoiceRepository.save(invoice);
        log.info("Đã phát hành hóa đơn: {}", invoice.getInvoiceCode());

        return invoiceMapper.toResponse(invoice);
    }

    // Cập nhật thông tin hóa đơn khi chưa thanh toán hoàn tất
    @Transactional
    public Invoice_Response update(String invoiceId, Invoice_Request request) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ quản lý hoặc quản trị viên mới có quyền chỉnh sửa hóa đơn");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa hóa đơn đã thanh toán hoàn tất");
        }
        if ("CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa hóa đơn đã bị hủy (CANCELLED)");
        }

        if (request.getInvoiceCode() != null && !request.getInvoiceCode().isBlank() &&
                !invoice.getInvoiceCode().equalsIgnoreCase(request.getInvoiceCode()) &&
                invoiceRepository.existsByInvoiceCode(request.getInvoiceCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã hóa đơn '" + request.getInvoiceCode() + "' đã tồn tại");
        }

        if (request.getInvoiceMonth() != null) {
            LocalDate newMonth = request.getInvoiceMonth().withDayOfMonth(1);
            if (!newMonth.equals(invoice.getInvoiceMonth()) &&
                    invoiceRepository.existsByContract_ContractIdAndInvoiceMonth(invoice.getContract().getContractId(),
                            newMonth)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Hóa đơn cho kỳ " + newMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " đã tồn tại");
            }
            invoice.setInvoiceMonth(newMonth);
        }

        invoiceMapper.updateEntityFromRequest(invoice, request);

        if (request.getSubtotalAmount() != null && request.getSubtotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setSubtotalAmount(request.getSubtotalAmount().setScale(2, RoundingMode.HALF_UP));
        }

        recalculateInvoiceAmounts(invoice);

        // Kiểm tra tổng tiền sau cập nhật không được nhỏ hơn số tiền sinh viên đã thanh
        // toán thực tế
        if (invoice.getPaidAmount() != null && invoice.getTotalAmount().compareTo(invoice.getPaidAmount()) < 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Tổng tiền phải trả sau khi cập nhật (" + invoice.getTotalAmount()
                            + ") không được nhỏ hơn số tiền đã thanh toán (" + invoice.getPaidAmount() + ")");
        }

        invoice = invoiceRepository.save(invoice);
        return invoiceMapper.toResponse(invoice);
    }

    // Hủy hóa đơn (chuyển sang CANCELLED nếu chưa thanh toán và chưa có khoản thanh
    // toán thực tế)
    @Transactional
    public Invoice_Response cancel(String invoiceId, String reason) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ quản lý hoặc quản trị viên mới có quyền hủy hóa đơn");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể hủy hóa đơn đã thanh toán hoàn tất");
        }
        if ("CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hóa đơn này đã ở trạng thái CANCELLED trước đó");
        }
        if (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể hủy hóa đơn đã phát sinh tiền thanh toán thực tế (" + invoice.getPaidAmount()
                            + " VNĐ). Vui lòng hoàn tiền trước");
        }

        // Giải phóng các biên bản vi phạm FINE khỏi hóa đơn hủy để không bị kẹt referenceId
        if (invoice.getInvoiceItems() != null) {
            for (InvoiceItem item : invoice.getInvoiceItems()) {
                if ("FINE".equalsIgnoreCase(item.getItemType())) {
                    item.setReferenceId(null);
                }
            }
        }

        invoice.setStatus("CANCELLED");
        invoice = invoiceRepository.save(invoice);
        log.info("Đã hủy hóa đơn: {} với lý do: {}", invoice.getInvoiceCode(), reason);
        return invoiceMapper.toResponse(invoice);
    }

    // Xóa hóa đơn (chỉ cho phép xóa khi chưa thanh toán và chưa có giao dịch thu
    // tiền)
    @Transactional
    public void delete(String invoiceId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ quản lý hoặc quản trị viên mới có quyền xóa hóa đơn");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(invoice.getStatus())
                || "PARTIALLY_PAID".equalsIgnoreCase(invoice.getStatus())
                || (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa hóa đơn đã thanh toán hoặc đã thu tiền một phần");
        }

        if (paymentRepository.existsByInvoice_InvoiceId(invoiceId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa hóa đơn đã phát sinh giao dịch thanh toán");
        }

        invoiceRepository.delete(invoice);
        log.info("Đã xóa hóa đơn: {}", invoice.getInvoiceCode());
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

        List<Contract> activeContracts = contractRepository.findByStatus("ACTIVE");
        List<Invoice> createdInvoices = new ArrayList<>();
        LocalDate monthEnd = invoiceMonth.withDayOfMonth(invoiceMonth.lengthOfMonth());

        for (Contract contract : activeContracts) {
            if (contract.getStartDate().isAfter(monthEnd) || contract.getEndDate().isBefore(invoiceMonth)) {
                continue;
            }

            // Lọc theo Tòa nhà nếu có yêu cầu phạm vi
            if (request.getBuildingId() != null && !request.getBuildingId().isBlank()) {
                String contractBuildingId = null;
                if (contract.getBed() != null && contract.getBed().getRoom() != null
                        && contract.getBed().getRoom().getFloor() != null
                        && contract.getBed().getRoom().getFloor().getBuilding() != null) {
                    contractBuildingId = contract.getBed().getRoom().getFloor().getBuilding().getBuildingId();
                }
                if (!request.getBuildingId().equals(contractBuildingId)) {
                    continue;
                }
            }

            // Lọc theo Tầng nếu có yêu cầu phạm vi
            if (request.getFloorId() != null && !request.getFloorId().isBlank()) {
                String contractFloorId = null;
                if (contract.getBed() != null && contract.getBed().getRoom() != null
                        && contract.getBed().getRoom().getFloor() != null) {
                    contractFloorId = contract.getBed().getRoom().getFloor().getFloorId();
                }
                if (!request.getFloorId().equals(contractFloorId)) {
                    continue;
                }
            }

            // Bỏ qua nếu đã có hóa đơn đang hoạt động (không tính hóa đơn đã bị hủy CANCELLED)
            List<Invoice> existingInvoices = invoiceRepository.findByContract_ContractIdAndInvoiceMonth(contract.getContractId(), invoiceMonth);
            if (existingInvoices != null && existingInvoices.stream().anyMatch(inv -> !"CANCELLED".equalsIgnoreCase(inv.getStatus()))) {
                continue;
            }

            Student student = contract.getStudent();
            if (student == null) {
                continue;
            }

            BigDecimal monthlyFee;
            try {
                monthlyFee = resolveRoomMonthlyFee(contract);
            } catch (Exception ex) {
                log.warn("Bỏ qua hợp đồng {} khi lập hóa đơn hàng loạt: {}", contract.getContractCode(),
                        ex.getMessage());
                continue;
            }

            String invoiceCode = generateInvoiceCode(student, invoiceMonth);

            Invoice invoice = Invoice.builder()
                    .invoiceCode(invoiceCode)
                    .contract(contract)
                    .student(student)
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
                        .invoice(invoice)
                        .itemType("RENT")
                        .description("Tiền thuê phòng lưu trú kỳ tháng "
                                + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                        .quantity(BigDecimal.ONE)
                        .unitPrice(monthlyFee)
                        .amount(monthlyFee)
                        .referenceId(contract.getContractId())
                        .build();
                invoice.getInvoiceItems().add(rentItem);
            }

            // Gắn tiền điện nước phòng (UTILITY) phân bổ cho sinh viên (ưu tiên chỉ số chốt tháng trước invoiceMonth.minusMonths(1))
            if (utilityReadingRepository != null && utilityReadingService != null
                    && contract.getBed() != null && contract.getBed().getRoom() != null) {
                String roomId = contract.getBed().getRoom().getRoomId();
                LocalDate utilityMonth = invoiceMonth.minusMonths(1);
                List<UtilityReading> readings = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId, utilityMonth);
                if (readings.isEmpty()) {
                    readings = utilityReadingRepository.findByRoom_RoomIdAndReadingMonth(roomId, invoiceMonth);
                    if (!readings.isEmpty()) {
                        utilityMonth = invoiceMonth;
                    }
                }
                for (UtilityReading reading : readings) {
                    try {
                        UtilitySplit_Response split = utilityReadingService.calculateRoomUtilitySplit(
                                roomId, reading.getServiceType().getServiceTypeId(), utilityMonth);
                        if (split != null && split.getResidentShares() != null) {
                            split.getResidentShares().stream()
                                    .filter(rs -> rs.getStudentId() != null
                                            && rs.getStudentId().equals(student.getStudentId()))
                                    .findFirst()
                                    .ifPresent(rs -> {
                                        if (rs.getShareAmount() != null
                                                && rs.getShareAmount().compareTo(BigDecimal.ZERO) > 0) {
                                            String srvName = (reading.getServiceType() != null
                                                    && reading.getServiceType().getServiceName() != null)
                                                            ? reading.getServiceType().getServiceName()
                                                            : "Điện nước";
                                            InvoiceItem utilityItem = InvoiceItem.builder()
                                                    .invoice(invoice)
                                                    .itemType("UTILITY")
                                                    .description("Tiền " + srvName + " tháng "
                                                            + invoiceMonth
                                                                    .format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                                    .quantity(BigDecimal.ONE)
                                                    .unitPrice(rs.getShareAmount().setScale(2, RoundingMode.HALF_UP))
                                                    .amount(rs.getShareAmount().setScale(2, RoundingMode.HALF_UP))
                                                    .referenceId(reading.getReadingId())
                                                    .build();
                                            invoice.getInvoiceItems().add(utilityItem);
                                        }
                                    });
                        }
                    } catch (Exception ex) {
                        log.warn("Không thể tính chia điện nước phòng {} cho hợp đồng {}: {}", roomId,
                                contract.getContractCode(), ex.getMessage());
                    }
                }
            }

            // Gắn các dịch vụ KTX theo tháng (SERVICE):
            // 1. Phí theo đầu người (PER_PERSON): Vệ sinh thu gom rác, Điện an ninh chiếu sáng chung
            // 2. Phí cố định theo phòng (FLAT) chia đều số SV trong phòng của kỳ tháng: Internet Wifi
            if (serviceTypeRepository != null) {
                List<ServiceType> serviceTypes = serviceTypeRepository.findAll();
                String monthLabel = invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"));

                for (ServiceType st : serviceTypes) {
                    if ("PER_PERSON".equalsIgnoreCase(st.getBillingType())
                            && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                        InvoiceItem serviceItem = InvoiceItem.builder()
                                .invoice(invoice)
                                .itemType("SERVICE")
                                .description(st.getServiceName() + " tháng " + monthLabel)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .amount(st.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(st.getServiceTypeId())
                                .build();
                        invoice.getInvoiceItems().add(serviceItem);
                    }
                }

                if (contract.getBed() != null && contract.getBed().getRoom() != null) {
                    String roomId = contract.getBed().getRoom().getRoomId();
                    String roomNumber = contract.getBed().getRoom().getRoomNumber();
                    long activeCountInRoom = activeContracts.stream()
                            .filter(c -> c.getBed() != null && c.getBed().getRoom() != null
                                    && roomId.equals(c.getBed().getRoom().getRoomId())
                                    && !c.getStartDate().isAfter(monthEnd) && !c.getEndDate().isBefore(invoiceMonth))
                            .count();
                    if (activeCountInRoom == 0) {
                        activeCountInRoom = 1;
                    }

                    for (ServiceType st : serviceTypes) {
                        if ("FLAT".equalsIgnoreCase(st.getBillingType())
                                && st.getUnitPrice() != null && st.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal flatShare = st.getUnitPrice().divide(
                                    BigDecimal.valueOf(activeCountInRoom), 2, RoundingMode.HALF_UP);
                            InvoiceItem flatItem = InvoiceItem.builder()
                                    .invoice(invoice)
                                    .itemType("SERVICE")
                                    .description(st.getServiceName() + " (Phòng " + roomNumber + ") tháng " + monthLabel)
                                    .quantity(BigDecimal.ONE)
                                    .unitPrice(flatShare)
                                    .amount(flatShare)
                                    .referenceId(st.getServiceTypeId())
                                    .build();
                            invoice.getInvoiceItems().add(flatItem);
                        }
                    }
                }
            }

            // Gắn phí gửi xe (VEHICLE) đang hoạt động trong kỳ
            if (vehicleRegistrationRepository != null) {
                List<VehicleRegistration> vehicles = vehicleRegistrationRepository.findActiveVehiclesForStudentInMonth(
                        student.getStudentId(), invoiceMonth, monthEnd);
                for (VehicleRegistration vehicle : vehicles) {
                    if (vehicle.getMonthlyFee() != null && vehicle.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0) {
                        String plateInfo = (vehicle.getLicensePlate() != null && !vehicle.getLicensePlate().isBlank())
                                ? " (" + vehicle.getLicensePlate() + ")"
                                : "";
                        InvoiceItem vehicleItem = InvoiceItem.builder()
                                .invoice(invoice)
                                .itemType("VEHICLE")
                                .description("Phí gửi xe " + vehicle.getVehicleType() + plateInfo + " tháng "
                                        + invoiceMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                                .quantity(BigDecimal.ONE)
                                .unitPrice(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .amount(vehicle.getMonthlyFee().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(vehicle.getVehicleId())
                                .build();
                        invoice.getInvoiceItems().add(vehicleItem);
                    }
                }
            }

            // Gắn tiền phạt vi phạm kỷ luật chưa tính vào hóa đơn (FINE)
            if (violationRepository != null) {
                List<Violation> violations = violationRepository.findUnbilledFinesByStudent(student.getStudentId(),
                        monthEnd);
                for (Violation v : violations) {
                    boolean alreadyBilled = invoiceItemRepository != null
                            && invoiceItemRepository.existsByItemTypeAndReferenceId("FINE", v.getViolationId());
                    if (!alreadyBilled && v.getFineAmount() != null
                            && v.getFineAmount().compareTo(BigDecimal.ZERO) > 0) {
                        String vName = (v.getViolationType() != null && v.getViolationType().getViolationName() != null)
                                ? v.getViolationType().getViolationName()
                                : "Kỷ luật";
                        InvoiceItem fineItem = InvoiceItem.builder()
                                .invoice(invoice)
                                .itemType("FINE")
                                .description("Tiền phạt vi phạm: " + vName)
                                .quantity(BigDecimal.ONE)
                                .unitPrice(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .amount(v.getFineAmount().setScale(2, RoundingMode.HALF_UP))
                                .referenceId(v.getViolationId())
                                .build();
                        invoice.getInvoiceItems().add(fineItem);
                    }
                }
            }

            // Tự động tính lại tổng tiền từ toàn bộ các dòng chi tiết RENT, UTILITY,
            // VEHICLE, FINE
            recalculateInvoiceAmounts(invoice);

            createdInvoices.add(invoice);
        }

        if (!createdInvoices.isEmpty()) {
            createdInvoices = invoiceRepository.saveAll(createdInvoices);
            log.info("Lập hóa đơn hàng loạt hoàn tất: đã tạo {} hóa đơn cho kỳ {}", createdInvoices.size(),
                    invoiceMonth);
        }

        return invoiceMapper.toResponseList(createdInvoices);
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
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(today);

        if (overdueInvoices.isEmpty()) {
            log.info("Không có hóa đơn nào quá hạn cần xử lý hôm nay: {}", today);
            return 0;
        }

        log.info("Phát hiện {} hóa đơn quá hạn chưa thanh toán, tiến hành cập nhật trạng thái và phí phạt trễ hạn",
                overdueInvoices.size());

        int count = 0;
        for (Invoice invoice : overdueInvoices) {
            // Chỉ cập nhật trạng thái OVERDUE cho hóa đơn ISSUED (chưa thanh toán đồng
            // nào).
            // Hóa đơn PARTIALLY_PAID giữ nguyên trạng thái để tránh bị đảo trạng thái qua
            // lại hằng đêm.
            if ("ISSUED".equalsIgnoreCase(invoice.getStatus())) {
                invoice.setStatus("OVERDUE");
            }

            // Áp dụng phí phạt trễ hạn đọc từ cấu hình hệ thống (mặc định 5%) trên số tiền
            // chưa thanh toán nếu chưa bị tính phí phạt
            if (invoice.getLateFeeAmount() == null || invoice.getLateFeeAmount().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal finePercent = systemSettingService != null
                        ? systemSettingService.getDecimalSetting("LATE_PAYMENT_FINE_PERCENT", new BigDecimal("5.0"))
                        : new BigDecimal("5.0");
                BigDecimal lateFeeRate = finePercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                BigDecimal totalBeforeLate = (invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount()
                        : BigDecimal.ZERO)
                        .subtract(invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO);
                BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal unpaid = totalBeforeLate.subtract(paid);

                if (unpaid.compareTo(BigDecimal.ZERO) > 0 && lateFeeRate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal lateFee = unpaid.multiply(lateFeeRate).setScale(2, RoundingMode.HALF_UP);
                    invoice.setLateFeeAmount(lateFee);
                }
            }

            recalculateInvoiceAmounts(invoice);
            count++;
        }

        invoiceRepository.saveAll(overdueInvoices);
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
