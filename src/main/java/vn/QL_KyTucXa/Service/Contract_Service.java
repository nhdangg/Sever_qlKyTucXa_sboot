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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.Contract_Request;
import vn.QL_KyTucXa.DTO.Request.StudentContractApply_Request;
import vn.QL_KyTucXa.DTO.Response.Contract_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.*;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Contract_Mapper;
import vn.QL_KyTucXa.Repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Contract_Service {

    Contract_Repository contractRepository;
    Student_Repository studentRepository;
    User_Repository userRepository;
    Bed_Repository bedRepository;
    Room_Repository roomRepository;
    RegistrationPeriod_Repository periodRepository;
    Staff_Repository staffRepository;
    Contract_Mapper contractMapper;
    SystemSetting_Service systemSettingService;
    Invoice_Repository invoiceRepository;
    VehicleRegistration_Repository vehicleRegistrationRepository;
    RoomTransfer_Repository roomTransferRepository;
    CurrentUser_Service currentUserService;
    AuditLog_Service auditLogService;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    static Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "contractId", "contractCode", "startDate", "endDate", "monthlyFee", "depositAmount", "status", "createdAt");

    // Tạo mới hợp đồng ở trạng thái chờ duyệt PENDING
    @Transactional
    public Contract_Response create(Contract_Request request) {
        log.info("Tạo mới hợp đồng: {}", request.getContractCode());

        // Kiểm tra trùng mã hợp đồng
        if (contractRepository.existsByContractCode(request.getContractCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã hợp đồng '" + request.getContractCode() + "' đã tồn tại");
        }

        // Kiểm tra thời hạn hợp đồng hợp lệ
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Ngày bắt đầu và ngày kết thúc không được để trống");
        }
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Kiểm tra sinh viên và chặn nếu đã có hợp đồng đang hoạt động
        Student student = resolveStudent(request.getStudentId());
        if (contractRepository.existsByStudent_StudentIdAndStatus(student.getStudentId(), "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên đã có hợp đồng đang hoạt động");
        }

        // Khóa giường bằng findByIdForUpdate để chống tranh chấp đồng thời (race
        // condition)
        Bed bed = resolveBedForUpdate(request.getBedId());
        validateGenderAndFacilityStatus(student, bed);
        if ("OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED, "Giường đã có người ở");
        }
        if ("RESERVED".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang được giữ chỗ chờ duyệt hồ sơ khác");
        }
        if ("MAINTENANCE".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang bảo trì, vui lòng chọn giường khác");
        }
        if (contractRepository.hasOverlappingActiveContract(bed.getBedId(), request.getStartDate(),
                request.getEndDate(), null)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đã có hợp đồng hoạt động trong khoảng thời gian này");
        }

        // Kiểm tra đợt đăng ký đang mở và còn hạn
        RegistrationPeriod period = resolvePeriod(request.getPeriodId());
        if (!"OPEN".equalsIgnoreCase(period.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đợt đăng ký hiện không mở");
        }
        if (period.getRegistrationDeadline() != null && LocalDate.now().isAfter(period.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đã hết hạn đăng ký cho đợt này");
        }

        // Lấy đơn giá phòng từ loại phòng nếu request không truyền
        if (request.getMonthlyFee() == null) {
            if (bed.getRoom() != null && bed.getRoom().getRoomType() != null
                    && bed.getRoom().getRoomType().getPricePerMonth() != null) {
                request.setMonthlyFee(bed.getRoom().getRoomType().getPricePerMonth());
            } else {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không tìm thấy đơn giá phòng cho giường đã chọn");
            }
        }

        // Thiết lập hợp đồng khởi tạo luôn ở trạng thái PENDING
        Contract contract = contractMapper.toEntity(request);
        contract.setStudent(student);
        contract.setBed(bed);
        contract.setPeriod(period);
        if (contract.getDepositAmount() == null || contract.getDepositAmount().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal defaultDeposit = systemSettingService != null
                    ? systemSettingService.getDecimalSetting("CONTRACT_DEFAULT_DEPOSIT",
                            contract.getMonthlyFee() != null ? contract.getMonthlyFee() : BigDecimal.ZERO)
                    : (contract.getMonthlyFee() != null ? contract.getMonthlyFee() : BigDecimal.ZERO);
            contract.setDepositAmount(defaultDeposit);
        }
        contract.setStatus("PENDING");
        contract.setSignedDate(null);
        contract.setApprover(null);

        // Tạm khóa giữ chỗ giường thành RESERVED
        bed.setStatus("RESERVED");
        bedRepository.save(bed);
        syncRoomOccupancyStatus(bed.getRoom());

        contract = contractRepository.save(contract);
        if (auditLogService != null) {
            auditLogService.recordAction("CREATE_CONTRACT", "hopDong", contract.getContractId(), null, "Created code: " + contract.getContractCode());
        }
        return contractMapper.toResponse(contract);
    }

    // Sinh viên nộp đơn đăng ký hợp đồng nội trú trực tuyến
    @Transactional
    public Contract_Response applyOnline(String studentId, StudentContractApply_Request request) {
        if (studentId == null || studentId.isBlank()) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND, "Không tìm thấy hồ sơ sinh viên để đăng ký");
        }

        Student student = resolveStudent(studentId);

        // Kiểm tra sinh viên đã có hợp đồng ACTIVE hoặc PENDING chưa
        if (contractRepository.existsByStudent_StudentIdAndStatus(student.getStudentId(), "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Bạn đã có hợp đồng lưu trú đang có hiệu lực");
        }
        if (contractRepository.existsByStudent_StudentIdAndStatus(student.getStudentId(), "PENDING")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Bạn đã có một đơn đăng ký hợp đồng đang chờ xét duyệt");
        }

        // Xác định đợt đăng ký
        RegistrationPeriod period;
        if (request.getPeriodId() != null && !request.getPeriodId().isBlank()) {
            period = resolvePeriod(request.getPeriodId());
        } else {
            period = periodRepository.findByStatus("OPEN").stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.OPERATION_FAILED,
                            "Hiện tại không có đợt đăng ký nào đang mở"));
        }

        if (!"OPEN".equalsIgnoreCase(period.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đợt đăng ký hiện không mở");
        }
        if (period.getRegistrationDeadline() != null && LocalDate.now().isAfter(period.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đã hết hạn đăng ký cho đợt này");
        }

        // Khóa giường bằng findByIdForUpdate để chống tranh chấp đồng thời (race
        // condition)
        Bed bed = resolveBedForUpdate(request.getBedId());
        validateGenderAndFacilityStatus(student, bed);
        if ("OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED, "Giường đã có người ở");
        }
        if ("RESERVED".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đang có người giữ chỗ chờ xét duyệt, vui lòng chọn giường khác");
        }
        if ("MAINTENANCE".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang bảo trì, vui lòng chọn giường khác");
        }

        LocalDate startDate = period.getStartDate() != null ? period.getStartDate() : LocalDate.now();
        LocalDate endDate = period.getEndDate() != null ? period.getEndDate() : startDate.plusMonths(5);

        if (contractRepository.hasOverlappingActiveContract(bed.getBedId(), startDate, endDate, null)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đã có người đăng ký trong khoảng thời gian này");
        }

        // Tạm khóa giữ chỗ giường thành RESERVED
        bed.setStatus("RESERVED");
        bedRepository.save(bed);

        // Xác định đơn giá phòng
        if (bed.getRoom() == null || bed.getRoom().getRoomType() == null
                || bed.getRoom().getRoomType().getPricePerMonth() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không tìm thấy đơn giá phòng cho giường đã chọn");
        }
        BigDecimal monthlyFee = bed.getRoom().getRoomType().getPricePerMonth();

        // Tạo mã hợp đồng tự động duy nhất
        String codePrefix = "HD-" + (student.getStudentCode() != null ? student.getStudentCode() : "ONL");
        long timestampSuffix = System.currentTimeMillis() % 100000;
        String contractCode = codePrefix + "-" + timestampSuffix;
        int attempts = 0;
        while (contractRepository.existsByContractCode(contractCode) && attempts < 10) {
            attempts++;
            contractCode = codePrefix + "-" + (timestampSuffix + attempts);
        }

        BigDecimal defaultDeposit = systemSettingService != null
                ? systemSettingService.getDecimalSetting("CONTRACT_DEFAULT_DEPOSIT", monthlyFee)
                : monthlyFee;

        Contract contract = Contract.builder()
                .contractCode(contractCode)
                .student(student)
                .bed(bed)
                .period(period)
                .startDate(startDate)
                .endDate(endDate)
                .monthlyFee(monthlyFee)
                .depositAmount(defaultDeposit)
                .status("PENDING")
                .signedDate(null)
                .approver(null)
                .build();

        contract = contractRepository.save(contract);
        log.info("Sinh viên {} nộp đơn đăng ký hợp đồng online: {}", student.getStudentCode(),
                contract.getContractCode());

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX
        if (notificationService != null) {
            String roomNum = (bed.getRoom() != null) ? bed.getRoom().getRoomNumber() : "";
            String bedNum = (bed.getBedNumber() != null) ? bed.getBedNumber() : "";
            String title = "Đơn đăng ký KTX mới từ SV " + student.getFullName();
            String content = String.format(
                    "Sinh viên %s (MSSV: %s) vừa nộp đơn đăng ký thuê phòng tại phòng %s, giường %s. Mã HĐ tạm: %s. Vui lòng vào duyệt hợp đồng.",
                    student.getFullName(),
                    student.getStudentCode(),
                    roomNum,
                    bedNum,
                    contract.getContractCode());
            notificationService.notifyStaffAndAdmins(title, content, student.getUser(), contract.getContractCode());
        }

        return contractMapper.toResponse(contract);
    }

    // Tra cứu sinh viên theo ID và báo lỗi nếu không tìm thấy
    private Student resolveStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
    }

    // Tra cứu giường theo ID và báo lỗi nếu không tìm thấy
    // private Bed resolveBed(String bedId) {
    // if (bedId == null || bedId.isBlank()) {
    // throw new AppException(ErrorCode.BED_NOT_FOUND);
    // }
    // return bedRepository.findById(bedId)
    // .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));
    // }

    // Tra cứu và khóa giường bằng PESSIMISTIC_WRITE để chống race condition
    private Bed resolveBedForUpdate(String bedId) {
        if (bedId == null || bedId.isBlank()) {
            throw new AppException(ErrorCode.BED_NOT_FOUND);
        }
        return bedRepository.findByIdForUpdate(bedId)
                .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));
    }

    // Tra cứu đợt đăng ký theo ID và báo lỗi nếu không tìm thấy
    private RegistrationPeriod resolvePeriod(String periodId) {
        if (periodId == null || periodId.isBlank()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký");
        }
        return periodRepository.findById(periodId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký"));
    }

    // Lấy thông tin chi tiết hợp đồng theo ID kèm kiểm tra quyền sở hữu sinh viên
    @Transactional(readOnly = true)
    public Contract_Response getById(String contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStudent() != null) {
            checkStudentAccessOwnership(contract.getStudent().getStudentId());
        }

        return contractMapper.toResponse(contract);
    }

    // Lấy danh sách hợp đồng của sinh viên kèm kiểm tra quyền sở hữu
    @Transactional(readOnly = true)
    public List<Contract_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Contract> list = contractRepository.findByStudent_StudentId(studentId);
        return contractMapper.toResponseList(list);
    }

    // Lấy toàn bộ danh sách hợp đồng không phân trang
    @Transactional(readOnly = true)
    public List<Contract_Response> getAll() {
        List<Contract> list = contractRepository.findAll();
        return contractMapper.toResponseList(list);
    }

    // Lấy danh sách hợp đồng có phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<Contract_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, null, null, sortBy, sortDir);
    }

    // Lấy danh sách hợp đồng có lọc theo từ khóa, trạng thái và đợt đăng ký
    @Transactional(readOnly = true)
    public Page_Response<Contract_Response> getAll(
            int page,
            int size,
            String keyword,
            String status,
            String periodId,
            String sortBy,
            String sortDir) {
        return getAll(page, size, keyword, status, periodId, null, null, sortBy, sortDir);
    }

    // Lấy danh sách hợp đồng có hỗ trợ lọc theo tòa nhà và phòng
    @Transactional(readOnly = true)
    public Page_Response<Contract_Response> getAll(
            int page,
            int size,
            String keyword,
            String status,
            String periodId,
            String buildingId,
            String roomId,
            String sortBy,
            String sortDir) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Sinh viên không có quyền truy cập danh sách toàn bộ hợp đồng");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String effectiveSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(effectiveSortBy).ascending()
                : Sort.by(effectiveSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String st = (status != null && !status.isBlank()) ? status.trim() : null;
        String pid = (periodId != null && !periodId.isBlank()) ? periodId.trim() : null;
        String bid = (buildingId != null && !buildingId.isBlank()) ? buildingId.trim() : null;
        String rid = (roomId != null && !roomId.isBlank()) ? roomId.trim() : null;

        Page<Contract> pageResult = contractRepository.searchContracts(kw, st, pid, bid, rid, pageable);
        List<Contract_Response> data = contractMapper.toResponseList(pageResult.getContent());

        return Page_Response.<Contract_Response>builder()
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

    // Phê duyệt hợp đồng sang trạng thái ACTIVE và chuyển giường sang OCCUPIED
    @Transactional
    public Contract_Response approve(String contractId, String staffId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản lý hoặc Quản trị viên mới có quyền phê duyệt hợp đồng");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Chỉ cho phép duyệt hợp đồng đang ở trạng thái PENDING
        if (!"PENDING".equalsIgnoreCase(contract.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể duyệt hợp đồng ở trạng thái PENDING");
        }

        // Kiểm tra sinh viên chưa có hợp đồng ACTIVE khác
        if (contract.getStudent() != null &&
                contractRepository.existsByStudent_StudentIdAndStatus(contract.getStudent().getStudentId(), "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên đã có hợp đồng đang hoạt động");
        }

        // Kiểm tra giường còn sẵn sàng và không chồng lịch hợp đồng ACTIVE khác
        if (contract.getBed() == null) {
            throw new AppException(ErrorCode.BED_NOT_FOUND);
        }
        // Khóa giường bằng findByIdForUpdate để chống race condition khi duyệt
        Bed bed = resolveBedForUpdate(contract.getBed().getBedId());
        validateGenderAndFacilityStatus(contract.getStudent(), bed);
        if ("OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED,
                    "Giường đã có người ở, không thể duyệt hợp đồng này");
        }
        if ("MAINTENANCE".equalsIgnoreCase(bed.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang bảo trì, không thể duyệt hợp đồng");
        }
        if ("RESERVED".equalsIgnoreCase(bed.getStatus())) {
            // Giường ở trạng thái RESERVED do đơn đăng ký online này giữ chỗ là hoàn toàn
            // hợp lệ.
            // Chỉ chặn nếu giường này đang bị giữ chỗ bởi một hợp đồng PENDING KHÁC.
            String currentContractId = contract.getContractId();
            boolean reservedByOther = contractRepository.findByBed_BedId(bed.getBedId()).stream()
                    .anyMatch(c -> !c.getContractId().equals(currentContractId)
                            && "PENDING".equalsIgnoreCase(c.getStatus()));
            if (reservedByOther) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Giường đang được giữ chỗ (RESERVED) cho hồ sơ khác, không thể duyệt");
            }
        }
        if (contractRepository.hasOverlappingActiveContract(bed.getBedId(), contract.getStartDate(),
                contract.getEndDate(), contract.getContractId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đã có hợp đồng hoạt động trong khoảng thời gian này");
        }

        // Xác thực nhân viên duyệt nếu có truyền staffId
        Staff staff = null;
        if (staffId != null && !staffId.isBlank()) {
            staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        }

        // Cập nhật trạng thái giường sang OCCUPIED
        bed.setStatus("OCCUPIED");
        bedRepository.save(bed);
        syncRoomOccupancyStatus(bed.getRoom());

        // Kích hoạt hợp đồng
        contract.setStatus("ACTIVE");
        contract.setApprover(staff);
        contract.setSignedDate(LocalDate.now());

        contract = contractRepository.save(contract);
        if (auditLogService != null) {
            auditLogService.recordAction("APPROVE_CONTRACT", "hopDong", contract.getContractId(), null, "ACTIVE");
        }

        // Tự động đồng bộ trạng thái hồ sơ sinh viên sang ACTIVE khi hợp đồng có hiệu lực
        if (contract.getStudent() != null) {
            Student student = contract.getStudent();
            if (!"ACTIVE".equalsIgnoreCase(student.getStatus())) {
                student.setStatus("ACTIVE");
                if (student.getUser() != null && "INACTIVE".equalsIgnoreCase(student.getUser().getStatus())) {
                    student.getUser().setStatus("ACTIVE");
                    userRepository.save(student.getUser());
                }
                studentRepository.save(student);
                log.info("Đã kích hoạt hồ sơ sinh viên {} ({}) sang ACTIVE khi duyệt hợp đồng {}",
                        student.getStudentCode(), student.getFullName(), contract.getContractCode());
            }
        }

        // Bắn thông báo cho sinh viên được duyệt hợp đồng
        if (notificationService != null && contract.getStudent() != null) {
            String roomNum = (bed.getRoom() != null) ? bed.getRoom().getRoomNumber() : "";
            String title = "Hợp đồng KTX của bạn đã được phê duyệt!";
            String content = String.format(
                    "Đơn đăng ký hợp đồng mã %s tại phòng %s (giường %s) đã được Ban Quản lý phê duyệt thành công. Chúc bạn có kỳ lưu trú thuận lợi!",
                    contract.getContractCode(), roomNum, bed.getBedNumber() != null ? bed.getBedNumber() : "");
            notificationService.notifyStudent(contract.getStudent(), title, content,
                    staff != null ? staff.getUser() : null);
        }

        return contractMapper.toResponse(contract);
    }

    // Thanh lý hợp đồng ACTIVE và hoàn trả giường về trạng thái AVAILABLE
    @Transactional
    public Contract_Response terminate(String contractId, String reason) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản lý hoặc Quản trị viên mới có quyền thanh lý hợp đồng");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Chỉ thanh lý hợp đồng đang hoạt động ACTIVE
        if (!"ACTIVE".equalsIgnoreCase(contract.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể thanh lý hợp đồng đang ở trạng thái ACTIVE");
        }

        // Kiểm tra sinh viên còn nợ hóa đơn chưa thanh toán hoàn tất
        if (invoiceRepository != null && contract.getStudent() != null
                && invoiceRepository.existsUnpaidInvoiceByStudentId(contract.getStudent().getStudentId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sinh viên còn hóa đơn chưa thanh toán hoàn tất. Vui lòng kiểm tra và thanh toán trước khi thanh lý hợp đồng");
        }

        contract.setStatus("TERMINATED");
        contract.setTerminatedDate(LocalDate.now());
        contract.setTerminationReason((reason != null && !reason.isBlank()) ? reason.trim() : "Kết thúc hợp đồng");

        // Trả giường về trạng thái AVAILABLE
        Bed bed = contract.getBed();
        if (bed != null) {
            bed.setStatus("AVAILABLE");
            bedRepository.save(bed);
            syncRoomOccupancyStatus(bed.getRoom());
        }

        // Tự động hết hạn các đăng ký xe đang hoạt động của sinh viên khi rời KTX
        if (vehicleRegistrationRepository != null && contract.getStudent() != null) {
            List<VehicleRegistration> vehicles = vehicleRegistrationRepository.findByStudent_StudentIdAndStatus(
                    contract.getStudent().getStudentId(), "ACTIVE");
            for (VehicleRegistration v : vehicles) {
                v.setStatus("EXPIRED");
                vehicleRegistrationRepository.save(v);
            }
        }

        // Tự động hủy các yêu cầu chuyển phòng PENDING liên quan đến hợp đồng này
        if (roomTransferRepository != null) {
            List<RoomTransfer> pendingTransfers = roomTransferRepository
                    .findByContract_ContractId(contract.getContractId());
            for (RoomTransfer rt : pendingTransfers) {
                if ("PENDING".equalsIgnoreCase(rt.getStatus())) {
                    rt.setStatus("CANCELLED");
                    rt.setReason("Hợp đồng lưu trú đã thanh lý");
                    roomTransferRepository.save(rt);
                }
            }
        }

        // Tự động chuyển trạng thái sinh viên sang INACTIVE nếu không còn hợp đồng ACTIVE nào khác
        if (contract.getStudent() != null) {
            Student student = contract.getStudent();
            final String currentContractId = contract.getContractId();
            boolean hasOtherActiveContract = contractRepository.findByStudent_StudentIdAndStatus(student.getStudentId(), "ACTIVE")
                    .stream()
                    .anyMatch(c -> !c.getContractId().equals(currentContractId));
            if (!hasOtherActiveContract && "ACTIVE".equalsIgnoreCase(student.getStatus())) {
                student.setStatus("INACTIVE");
                studentRepository.save(student);
                log.info("Sinh viên {} không còn hợp đồng lưu trú ACTIVE, đã chuyển trạng thái sang INACTIVE", student.getStudentCode());
            }
        }

        contract = contractRepository.save(contract);
        return contractMapper.toResponse(contract);
    }

    // Tác vụ tự động quét và chuyển trạng thái các hợp đồng quá hạn sang EXPIRED
    @Scheduled(cron = "0 30 1 * * ?")
    @Transactional
    public int expireCompletedContracts() {
        LocalDate today = LocalDate.now();
        List<Contract> expiredContracts = contractRepository.findByStatusAndEndDateBefore("ACTIVE", today);
        if (expiredContracts.isEmpty()) {
            return 0;
        }

        log.info("Phát hiện {} hợp đồng đã hết hạn cần chuyển sang EXPIRED", expiredContracts.size());
        int count = 0;
        for (Contract contract : expiredContracts) {
            contract.setStatus("EXPIRED");
            contract.setTerminatedDate(today);
            contract.setTerminationReason("Hết thời hạn hợp đồng");

            Bed bed = contract.getBed();
            if (bed != null) {
                bed.setStatus("AVAILABLE");
                bedRepository.save(bed);
                syncRoomOccupancyStatus(bed.getRoom());
            }

            if (vehicleRegistrationRepository != null && contract.getStudent() != null) {
                List<VehicleRegistration> vehicles = vehicleRegistrationRepository.findByStudent_StudentIdAndStatus(
                        contract.getStudent().getStudentId(), "ACTIVE");
                for (VehicleRegistration v : vehicles) {
                    v.setStatus("EXPIRED");
                    vehicleRegistrationRepository.save(v);
                }
            }

            // Tự động chuyển trạng thái sinh viên sang INACTIVE nếu không còn hợp đồng ACTIVE nào khác
            if (contract.getStudent() != null) {
                Student student = contract.getStudent();
                boolean hasOtherActiveContract = contractRepository.findByStudent_StudentIdAndStatus(student.getStudentId(), "ACTIVE")
                        .stream()
                        .anyMatch(c -> !c.getContractId().equals(contract.getContractId()));
                if (!hasOtherActiveContract && "ACTIVE".equalsIgnoreCase(student.getStatus())) {
                    student.setStatus("INACTIVE");
                    studentRepository.save(student);
                }
            }

            contractRepository.save(contract);
            count++;
        }
        log.info("Đã tự động hoàn tất hết hạn {} hợp đồng", count);
        return count;
    }

    // Từ chối đơn đăng ký hợp đồng PENDING và giải phóng giường RESERVED về
    // AVAILABLE
    @Transactional
    public Contract_Response reject(String contractId, String staffId, String reason) {
        if (currentUserService != null && !currentUserService.hasAnyRole("ADMIN", "MANAGER")
                && !currentUserService.hasPermission("CONTRACT_APPROVE")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ Quản lý hoặc Quản trị viên mới có quyền từ chối hợp đồng");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (!"PENDING".equalsIgnoreCase(contract.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể từ chối hợp đồng ở trạng thái PENDING");
        }

        // Khóa và giải phóng giường nếu đang ở trạng thái RESERVED
        if (contract.getBed() != null) {
            Bed bed = bedRepository.findByIdForUpdate(contract.getBed().getBedId())
                    .orElse(contract.getBed());
            if ("RESERVED".equalsIgnoreCase(bed.getStatus())) {
                bed.setStatus("AVAILABLE");
                bedRepository.save(bed);
                syncRoomOccupancyStatus(bed.getRoom());
            }
        }

        Staff staff = null;
        if (staffId != null && !staffId.isBlank()) {
            staff = staffRepository.findById(staffId)
                    .orElse(null);
        }

        contract.setStatus("REJECTED");
        contract.setApprover(staff);
        contract.setTerminatedDate(LocalDate.now());
        contract.setTerminationReason((reason != null && !reason.isBlank()) ? reason.trim() : "Từ chối duyệt đơn");

        contract = contractRepository.save(contract);

        // Bắn thông báo cho sinh viên khi bị từ chối duyệt
        if (notificationService != null && contract.getStudent() != null) {
            String title = "Hồ sơ đăng ký KTX không được phê duyệt";
            String content = String.format("Đơn đăng ký hợp đồng mã %s đã bị từ chối duyệt. Lý do: %s",
                    contract.getContractCode(),
                    contract.getTerminationReason() != null ? contract.getTerminationReason() : "Từ chối duyệt đơn");
            notificationService.notifyStudent(contract.getStudent(), title, content, staff != null ? staff.getUser() : null);
        }

        return contractMapper.toResponse(contract);
    }

    // Sinh viên tự hủy đơn đăng ký hợp đồng PENDING của chính mình
    @Transactional
    public Contract_Response cancel(String contractId, String studentId, String reason) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (!"PENDING".equalsIgnoreCase(contract.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể hủy đơn đăng ký khi đang ở trạng thái PENDING");
        }

        if (studentId != null && !studentId.isBlank()
                && (contract.getStudent() == null || !contract.getStudent().getStudentId().equals(studentId))) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy đơn đăng ký của sinh viên khác");
        }

        // Khóa và giải phóng giường nếu đang ở trạng thái RESERVED
        if (contract.getBed() != null) {
            Bed bed = bedRepository.findByIdForUpdate(contract.getBed().getBedId())
                    .orElse(contract.getBed());
            if ("RESERVED".equalsIgnoreCase(bed.getStatus())) {
                bed.setStatus("AVAILABLE");
                bedRepository.save(bed);
                syncRoomOccupancyStatus(bed.getRoom());
            }
        }

        contract.setStatus("CANCELLED");
        contract.setTerminatedDate(LocalDate.now());
        contract.setTerminationReason(
                (reason != null && !reason.isBlank()) ? reason.trim() : "Sinh viên tự hủy đơn đăng ký");

        contract = contractRepository.save(contract);
        return contractMapper.toResponse(contract);
    }

    // Cập nhật thông tin hợp đồng ngoại trừ trạng thái duyệt
    @Transactional
    public Contract_Response update(String contractId, Contract_Request request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Không cho phép chỉnh sửa hợp đồng đã thanh lý
        if ("TERMINATED".equalsIgnoreCase(contract.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa hợp đồng đã thanh lý");
        }

        // Kiểm tra trùng mã hợp đồng nếu thay đổi mã
        if (request.getContractCode() != null && !request.getContractCode().isBlank()
                && !contract.getContractCode().equalsIgnoreCase(request.getContractCode())
                && contractRepository.existsByContractCode(request.getContractCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã hợp đồng '" + request.getContractCode() + "' đã tồn tại");
        }

        // Kiểm tra khoảng thời gian bắt đầu và kết thúc
        LocalDate targetStart = request.getStartDate() != null ? request.getStartDate() : contract.getStartDate();
        LocalDate targetEnd = request.getEndDate() != null ? request.getEndDate() : contract.getEndDate();
        if (targetStart != null && targetEnd != null && !targetEnd.isAfter(targetStart)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Cập nhật sinh viên nếu thay đổi
        if (request.getStudentId() != null && !request.getStudentId().isBlank()
                && (contract.getStudent() == null
                        || !contract.getStudent().getStudentId().equals(request.getStudentId()))) {
            if ("ACTIVE".equalsIgnoreCase(contract.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Hợp đồng đang có hiệu lực (ACTIVE), không thể thay đổi sinh viên. Vui lòng thanh lý và tạo hợp đồng mới.");
            }
            Student newStudent = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
            contract.setStudent(newStudent);
        }

        // Cập nhật đợt đăng ký nếu thay đổi
        if (request.getPeriodId() != null && !request.getPeriodId().isBlank()
                && (contract.getPeriod() == null
                        || !contract.getPeriod().getPeriodId().equals(request.getPeriodId()))) {
            RegistrationPeriod newPeriod = periodRepository.findById(request.getPeriodId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký"));
            contract.setPeriod(newPeriod);
        }

        // Cập nhật giường và đồng bộ trạng thái giường
        if (request.getBedId() != null && !request.getBedId().isBlank()
                && (contract.getBed() == null || !contract.getBed().getBedId().equals(request.getBedId()))) {
            if ("ACTIVE".equalsIgnoreCase(contract.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Hợp đồng đang có hiệu lực (ACTIVE), không thể đổi giường qua hàm cập nhật. Vui lòng sử dụng tính năng Chuyển phòng (Room Transfer).");
            }
            Bed newBed = bedRepository.findById(request.getBedId())
                    .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));
            if ("OCCUPIED".equalsIgnoreCase(newBed.getStatus())) {
                throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED);
            }
            if (targetStart != null && targetEnd != null
                    && contractRepository.hasOverlappingActiveContract(newBed.getBedId(), targetStart, targetEnd,
                            contract.getContractId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Giường mới đã có hợp đồng hoạt động trong khoảng thời gian này");
            }
            contract.setBed(newBed);
        } else if ("ACTIVE".equalsIgnoreCase(contract.getStatus()) && contract.getBed() != null
                && targetStart != null && targetEnd != null
                && contractRepository.hasOverlappingActiveContract(contract.getBed().getBedId(), targetStart, targetEnd,
                        contract.getContractId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đã có hợp đồng hoạt động trong khoảng thời gian này");
        }

        // Ánh xạ các trường được phép cập nhật
        contractMapper.updateEntityFromRequest(contract, request);
        if (targetStart != null) {
            contract.setStartDate(targetStart);
        }
        if (targetEnd != null) {
            contract.setEndDate(targetEnd);
        }

        contract = contractRepository.save(contract);
        return contractMapper.toResponse(contract);
    }

    // Xóa hợp đồng và chỉ giải phóng giường nếu chính hợp đồng này đang giữ chỗ
    // (ACTIVE hoặc PENDING)
    @Transactional
    public void delete(String contractId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ Quản lý hoặc Quản trị viên mới có quyền xóa hợp đồng");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Chặn xóa nếu hợp đồng đã phát sinh dữ liệu liên quan để tránh lỗi ràng buộc
        // khóa ngoại FK
        if (invoiceRepository != null && invoiceRepository.existsByContract_ContractId(contractId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa hợp đồng đã phát sinh hóa đơn");
        }
        if (roomTransferRepository != null && roomTransferRepository.existsByContract_ContractId(contractId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa hợp đồng đã có yêu cầu chuyển phòng");
        }

        if (contract.getBed() != null) {
            boolean contractHoldsBed = "ACTIVE".equalsIgnoreCase(contract.getStatus())
                    || "PENDING".equalsIgnoreCase(contract.getStatus());
            if (contractHoldsBed) {
                Bed bed = bedRepository.findByIdForUpdate(contract.getBed().getBedId())
                        .orElse(contract.getBed());
                if ("OCCUPIED".equalsIgnoreCase(bed.getStatus()) || "RESERVED".equalsIgnoreCase(bed.getStatus())) {
                    bed.setStatus("AVAILABLE");
                    bedRepository.save(bed);
                    syncRoomOccupancyStatus(bed.getRoom());
                }
            }
        }

        contractRepository.delete(contract);
    }

    // Tự động đồng bộ trạng thái phòng (AVAILABLE / FULL) dựa trên số giường còn
    // trống
    private void syncRoomOccupancyStatus(Room room) {
        if (room == null || "MAINTENANCE".equalsIgnoreCase(room.getStatus())
                || "CLOSED".equalsIgnoreCase(room.getStatus())) {
            return;
        }
        List<Bed> beds = bedRepository.findByRoom_RoomId(room.getRoomId());
        if (beds == null || beds.isEmpty()) {
            return;
        }
        boolean hasAvailableBed = beds.stream().anyMatch(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus()));
        String newStatus = hasAvailableBed ? "AVAILABLE" : "FULL";
        if (!newStatus.equalsIgnoreCase(room.getStatus())) {
            room.setStatus(newStatus);
            if (roomRepository != null) {
                roomRepository.save(room);
            }
            log.info("Tự động đồng bộ trạng thái phòng {}: {}", room.getRoomNumber(), newStatus);
        }
    }

    // Kiểm tra tính tương thích giới tính giữa sinh viên và tòa nhà, kiểm tra trạng
    // thái phòng/tòa
    private void validateGenderAndFacilityStatus(Student student, Bed bed) {
        if (bed == null)
            return;

        Room room = bed.getRoom();
        if (room != null) {
            if ("MAINTENANCE".equalsIgnoreCase(room.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Phòng " + room.getRoomNumber() + " đang bảo trì, không thể xếp phòng cho sinh viên");
            }
            if ("CLOSED".equalsIgnoreCase(room.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Phòng " + room.getRoomNumber() + " đã đóng, không thể xếp phòng cho sinh viên");
            }

            if (room.getFloor() != null && room.getFloor().getBuilding() != null && student != null) {
                Building building = room.getFloor().getBuilding();
                String buildingGender = building.getGenderType() != null ? building.getGenderType().trim().toUpperCase()
                        : "MIXED";
                String studentGender = student.getGender() != null ? student.getGender().trim().toUpperCase() : "";

                if ("MALE".equals(buildingGender) && !("MALE".equals(studentGender) || "NAM".equals(studentGender))) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Tòa nhà " + building.getBuildingName() + " chỉ dành cho sinh viên Nam");
                }
                if ("FEMALE".equals(buildingGender)
                        && !("FEMALE".equals(studentGender) || "NU".equals(studentGender))) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Tòa nhà " + building.getBuildingName() + " chỉ dành cho sinh viên Nữ");
                }
            }
        }
    }

    // Kiểm tra quyền sở hữu đối với tài khoản sinh viên (Fail-closed)
    private void checkStudentAccessOwnership(String targetStudentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse(null);
            if (currentStudentId == null || !currentStudentId.equals(targetStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không có quyền truy cập hợp đồng của sinh viên khác");
            }
        }
    }
}
