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
        Student sinh_vien = resolveStudent(request.getStudentId());
        if (contractRepository.existsByStudent_StudentIdAndStatus(sinh_vien.getStudentId(), "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên đã có hợp đồng đang hoạt động");
        }

        // Khóa giường bằng findByIdForUpdate để chống tranh chấp đồng thời (race
        // condition)
        Bed giuong = resolveBedForUpdate(request.getBedId());
        validateGenderAndFacilityStatus(sinh_vien, giuong);
        if ("OCCUPIED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED, "Giường đã có người ở");
        }
        if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang được giữ chỗ chờ duyệt hồ sơ khác");
        }
        if ("MAINTENANCE".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang bảo trì, vui lòng chọn giường khác");
        }
        if (contractRepository.hasOverlappingActiveContract(giuong.getBedId(), request.getStartDate(),
                request.getEndDate(), null)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đã có hợp đồng hoạt động trong khoảng thời gian này");
        }

        // Kiểm tra đợt đăng ký đang mở và còn hạn
        RegistrationPeriod dot_dang_ky = resolvePeriod(request.getPeriodId());
        if (!"OPEN".equalsIgnoreCase(dot_dang_ky.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đợt đăng ký hiện không mở");
        }
        if (dot_dang_ky.getRegistrationDeadline() != null
                && LocalDate.now().isAfter(dot_dang_ky.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đã hết hạn đăng ký cho đợt này");
        }

        // Lấy đơn giá phòng từ loại phòng nếu request không truyền
        if (request.getMonthlyFee() == null) {
            if (giuong.getRoom() != null && giuong.getRoom().getRoomType() != null
                    && giuong.getRoom().getRoomType().getPricePerMonth() != null) {
                request.setMonthlyFee(giuong.getRoom().getRoomType().getPricePerMonth());
            } else {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Không tìm thấy đơn giá phòng cho giường đã chọn");
            }
        }

        // Thiết lập hợp đồng khởi tạo luôn ở trạng thái PENDING
        Contract hop_dong = contractMapper.toEntity(request);
        hop_dong.setStudent(sinh_vien);
        hop_dong.setBed(giuong);
        hop_dong.setPeriod(dot_dang_ky);
        if (hop_dong.getDepositAmount() == null || hop_dong.getDepositAmount().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal defaultDeposit = systemSettingService != null
                    ? systemSettingService.getDecimalSetting("CONTRACT_DEFAULT_DEPOSIT",
                            hop_dong.getMonthlyFee() != null ? hop_dong.getMonthlyFee() : BigDecimal.ZERO)
                    : (hop_dong.getMonthlyFee() != null ? hop_dong.getMonthlyFee() : BigDecimal.ZERO);
            hop_dong.setDepositAmount(defaultDeposit);
        }
        hop_dong.setStatus("PENDING");
        hop_dong.setSignedDate(null);
        hop_dong.setApprover(null);

        // Tạm khóa giữ chỗ giường thành RESERVED
        giuong.setStatus("RESERVED");
        bedRepository.save(giuong);
        syncRoomOccupancyStatus(giuong.getRoom());

        hop_dong = contractRepository.save(hop_dong);
        if (auditLogService != null) {
            auditLogService.recordAction("CREATE_CONTRACT", "hopDong", hop_dong.getContractId(), null,
                    "Created code: " + hop_dong.getContractCode());
        }
        return contractMapper.toResponse(hop_dong);
    }

    // Sinh viên nộp đơn đăng ký hợp đồng nội trú trực tuyến
    @Transactional
    public Contract_Response applyOnline(String studentId, StudentContractApply_Request request) {
        if (studentId == null || studentId.isBlank()) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND, "Không tìm thấy hồ sơ sinh viên để đăng ký");
        }

        Student sinh_vien = resolveStudent(studentId);

        // Kiểm tra sinh viên đã có hợp đồng ACTIVE hoặc PENDING chưa
        if (contractRepository.existsByStudent_StudentIdAndStatus(sinh_vien.getStudentId(), "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Bạn đã có hợp đồng lưu trú đang có hiệu lực");
        }
        if (contractRepository.existsByStudent_StudentIdAndStatus(sinh_vien.getStudentId(), "PENDING")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Bạn đã có một đơn đăng ký hợp đồng đang chờ xét duyệt");
        }

        // Xác định đợt đăng ký
        RegistrationPeriod dot_dang_ky;
        if (request.getPeriodId() != null && !request.getPeriodId().isBlank()) {
            dot_dang_ky = resolvePeriod(request.getPeriodId());
        } else {
            dot_dang_ky = periodRepository.findByStatus("OPEN").stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.OPERATION_FAILED,
                            "Hiện tại không có đợt đăng ký nào đang mở"));
        }

        if (!"OPEN".equalsIgnoreCase(dot_dang_ky.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đợt đăng ký hiện không mở");
        }
        if (dot_dang_ky.getRegistrationDeadline() != null
                && LocalDate.now().isAfter(dot_dang_ky.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Đã hết hạn đăng ký cho đợt này");
        }

        // Khóa giường bằng findByIdForUpdate để chống tranh chấp đồng thời (race
        // condition)
        Bed giuong = resolveBedForUpdate(request.getBedId());
        validateGenderAndFacilityStatus(sinh_vien, giuong);
        if ("OCCUPIED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED, "Giường đã có người ở");
        }
        if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đang có người giữ chỗ chờ xét duyệt, vui lòng chọn giường khác");
        }
        if ("MAINTENANCE".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang bảo trì, vui lòng chọn giường khác");
        }

        LocalDate startDate = dot_dang_ky.getStartDate() != null ? dot_dang_ky.getStartDate() : LocalDate.now();
        LocalDate endDate = dot_dang_ky.getEndDate() != null ? dot_dang_ky.getEndDate() : startDate.plusMonths(5);

        if (contractRepository.hasOverlappingActiveContract(giuong.getBedId(), startDate, endDate, null)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đã có người đăng ký trong khoảng thời gian này");
        }

        // Tạm khóa giữ chỗ giường thành RESERVED
        giuong.setStatus("RESERVED");
        bedRepository.save(giuong);

        // Xác định đơn giá phòng
        if (giuong.getRoom() == null || giuong.getRoom().getRoomType() == null
                || giuong.getRoom().getRoomType().getPricePerMonth() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không tìm thấy đơn giá phòng cho giường đã chọn");
        }
        BigDecimal monthlyFee = giuong.getRoom().getRoomType().getPricePerMonth();

        // Tạo mã hợp đồng tự động duy nhất
        String codePrefix = "HD-" + (sinh_vien.getStudentCode() != null ? sinh_vien.getStudentCode() : "ONL");
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

        Contract hop_dong = Contract.builder()
                .contractCode(contractCode)
                .student(sinh_vien)
                .bed(giuong)
                .period(dot_dang_ky)
                .startDate(startDate)
                .endDate(endDate)
                .monthlyFee(monthlyFee)
                .depositAmount(defaultDeposit)
                .status("PENDING")
                .signedDate(null)
                .approver(null)
                .build();

        hop_dong = contractRepository.save(hop_dong);
        log.info("Sinh viên {} nộp đơn đăng ký hợp đồng online: {}", sinh_vien.getStudentCode(),
                hop_dong.getContractCode());

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX
        if (notificationService != null) {
            try {
                String roomNum = (giuong.getRoom() != null) ? giuong.getRoom().getRoomNumber() : "";
                String bedNum = (giuong.getBedNumber() != null) ? giuong.getBedNumber() : "";
                String title = "Đơn đăng ký KTX mới từ SV " + sinh_vien.getFullName();
                String content = String.format(
                        "Sinh viên %s (MSSV: %s) vừa nộp đơn đăng ký thuê phòng tại phòng %s, giường %s. Mã HĐ tạm: %s. Vui lòng vào duyệt hợp đồng.",
                        sinh_vien.getFullName(),
                        sinh_vien.getStudentCode(),
                        roomNum,
                        bedNum,
                        hop_dong.getContractCode());
                notificationService.notifyStaffAndAdmins(title, content, sinh_vien.getUser(), null);
            } catch (Exception e) {
                log.warn("Không thể gửi thông báo nộp đơn đăng ký online: {}", e.getMessage());
            }
        }

        return contractMapper.toResponse(hop_dong);
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
        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (hop_dong.getStudent() != null) {
            checkStudentAccessOwnership(hop_dong.getStudent().getStudentId());
        }

        return contractMapper.toResponse(hop_dong);
    }

    // Lấy danh sách hợp đồng của sinh viên kèm kiểm tra quyền sở hữu
    @Transactional(readOnly = true)
    public List<Contract_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        if (!studentRepository.existsById(studentId)) {
            throw new AppException(ErrorCode.STUDENT_NOT_FOUND);
        }

        List<Contract> danh_sach_hop_dong = contractRepository.findByStudent_StudentId(studentId);
        return contractMapper.toResponseList(danh_sach_hop_dong);
    }

    // Lấy toàn bộ danh sách hợp đồng không phân trang
    @Transactional(readOnly = true)
    public List<Contract_Response> getAll() {
        List<Contract> danh_sach_hop_dong = contractRepository.findAll();
        return contractMapper.toResponseList(danh_sach_hop_dong);
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

        Page<Contract> trang_hop_dong = contractRepository.searchContracts(kw, st, pid, bid, rid, pageable);
        List<Contract_Response> data = contractMapper.toResponseList(trang_hop_dong.getContent());

        return Page_Response.<Contract_Response>builder()
                .currentPage(trang_hop_dong.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_hop_dong.getTotalPages())
                .totalElements(trang_hop_dong.getTotalElements())
                .hasPrevious(trang_hop_dong.hasPrevious())
                .hasNext(trang_hop_dong.hasNext())
                .isFirst(trang_hop_dong.isFirst())
                .isLast(trang_hop_dong.isLast())
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

        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Chỉ cho phép duyệt hợp đồng đang ở trạng thái PENDING
        if (!"PENDING".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể duyệt hợp đồng ở trạng thái PENDING");
        }

        // Kiểm tra sinh viên chưa có hợp đồng ACTIVE khác
        if (hop_dong.getStudent() != null &&
                contractRepository.existsByStudent_StudentIdAndStatus(hop_dong.getStudent().getStudentId(), "ACTIVE")) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên đã có hợp đồng đang hoạt động");
        }

        // Kiểm tra giường còn sẵn sàng và không chồng lịch hợp đồng ACTIVE khác
        if (hop_dong.getBed() == null) {
            throw new AppException(ErrorCode.BED_NOT_FOUND);
        }
        // Khóa giường bằng findByIdForUpdate để chống race condition khi duyệt
        Bed giuong = resolveBedForUpdate(hop_dong.getBed().getBedId());
        validateGenderAndFacilityStatus(hop_dong.getStudent(), giuong);
        if ("OCCUPIED".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED,
                    "Giường đã có người ở, không thể duyệt hợp đồng này");
        }
        if ("MAINTENANCE".equalsIgnoreCase(giuong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường đang bảo trì, không thể duyệt hợp đồng");
        }
        if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
            // Giường ở trạng thái RESERVED do đơn đăng ký online này giữ chỗ là hoàn toàn
            // hợp lệ.
            // Chỉ chặn nếu giường này đang bị giữ chỗ bởi một hợp đồng PENDING KHÁC.
            String currentContractId = hop_dong.getContractId();
            boolean reservedByOther = contractRepository.findByBed_BedId(giuong.getBedId()).stream()
                    .anyMatch(c -> !c.getContractId().equals(currentContractId)
                            && "PENDING".equalsIgnoreCase(c.getStatus()));
            if (reservedByOther) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Giường đang được giữ chỗ (RESERVED) cho hồ sơ khác, không thể duyệt");
            }
        }
        if (contractRepository.hasOverlappingActiveContract(giuong.getBedId(), hop_dong.getStartDate(),
                hop_dong.getEndDate(), hop_dong.getContractId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đã có hợp đồng hoạt động trong khoảng thời gian này");
        }

        // Xác thực nhân viên duyệt nếu có truyền staffId
        Staff nhan_vien = null;
        if (staffId != null && !staffId.isBlank()) {
            nhan_vien = staffRepository.findById(staffId)
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        }

        // Cập nhật trạng thái giường sang OCCUPIED
        giuong.setStatus("OCCUPIED");
        bedRepository.save(giuong);
        syncRoomOccupancyStatus(giuong.getRoom());

        // Kích hoạt hợp đồng
        hop_dong.setStatus("ACTIVE");
        hop_dong.setApprover(nhan_vien);
        hop_dong.setSignedDate(LocalDate.now());

        hop_dong = contractRepository.save(hop_dong);
        if (auditLogService != null) {
            try {
                auditLogService.recordAction("APPROVE_CONTRACT", "hopDong", hop_dong.getContractId(), null, "ACTIVE");
            } catch (Exception e) {
                log.warn("Không thể ghi log phê duyệt hợp đồng: {}", e.getMessage());
            }
        }

        // Tự động đồng bộ trạng thái hồ sơ sinh viên sang ACTIVE khi hợp đồng có hiệu
        // lực
        if (hop_dong.getStudent() != null) {
            Student sinh_vien = hop_dong.getStudent();
            if (!"ACTIVE".equalsIgnoreCase(sinh_vien.getStatus())) {
                sinh_vien.setStatus("ACTIVE");
                if (sinh_vien.getUser() != null && "INACTIVE".equalsIgnoreCase(sinh_vien.getUser().getStatus())) {
                    sinh_vien.getUser().setStatus("ACTIVE");
                    userRepository.save(sinh_vien.getUser());
                }
                studentRepository.save(sinh_vien);
                log.info("Đã kích hoạt hồ sơ sinh viên {} ({}) sang ACTIVE khi duyệt hợp đồng {}",
                        sinh_vien.getStudentCode(), sinh_vien.getFullName(), hop_dong.getContractCode());
            }
        }

        // Bắn thông báo cho sinh viên được duyệt hợp đồng
        if (notificationService != null && hop_dong.getStudent() != null) {
            try {
                String roomNum = (giuong.getRoom() != null) ? giuong.getRoom().getRoomNumber() : "";
                String title = "Hợp đồng KTX của bạn đã được phê duyệt!";
                String content = String.format(
                        "Đơn đăng ký hợp đồng mã %s tại phòng %s (giường %s) đã được Ban Quản lý phê duyệt thành công. Chúc bạn có kỳ lưu trú thuận lợi!",
                        hop_dong.getContractCode(), roomNum,
                        giuong.getBedNumber() != null ? giuong.getBedNumber() : "");
                notificationService.notifyStudent(hop_dong.getStudent(), title, content,
                        nhan_vien != null ? nhan_vien.getUser() : null);
            } catch (Exception e) {
                log.warn("Không thể gửi thông báo phê duyệt hợp đồng tới sinh viên: {}", e.getMessage());
            }
        }

        return contractMapper.toResponse(hop_dong);
    }

    // Thanh lý hợp đồng ACTIVE và hoàn trả giường về trạng thái AVAILABLE
    @Transactional
    public Contract_Response terminate(String contractId, String reason) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản lý hoặc Quản trị viên mới có quyền thanh lý hợp đồng");
        }

        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Chỉ thanh lý hợp đồng đang hoạt động ACTIVE
        if (!"ACTIVE".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể thanh lý hợp đồng đang ở trạng thái ACTIVE");
        }

        // Kiểm tra sinh viên còn nợ hóa đơn chưa thanh toán hoàn tất
        if (invoiceRepository != null && hop_dong.getStudent() != null
                && invoiceRepository.existsUnpaidInvoiceByStudentId(hop_dong.getStudent().getStudentId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Sinh viên còn hóa đơn chưa thanh toán hoàn tất. Vui lòng kiểm tra và thanh toán trước khi thanh lý hợp đồng");
        }

        hop_dong.setStatus("TERMINATED");
        hop_dong.setTerminatedDate(LocalDate.now());
        hop_dong.setTerminationReason((reason != null && !reason.isBlank()) ? reason.trim() : "Kết thúc hợp đồng");

        // Trả giường về trạng thái AVAILABLE
        Bed giuong = hop_dong.getBed();
        if (giuong != null) {
            giuong.setStatus("AVAILABLE");
            bedRepository.save(giuong);
            syncRoomOccupancyStatus(giuong.getRoom());
        }

        // Tự động hết hạn các đăng ký xe đang hoạt động của sinh viên khi rời KTX
        if (vehicleRegistrationRepository != null && hop_dong.getStudent() != null) {
            List<VehicleRegistration> danh_sach_xe = vehicleRegistrationRepository.findByStudent_StudentIdAndStatus(
                    hop_dong.getStudent().getStudentId(), "ACTIVE");
            for (VehicleRegistration v : danh_sach_xe) {
                v.setStatus("EXPIRED");
                vehicleRegistrationRepository.save(v);
            }
        }

        // Tự động hủy các yêu cầu chuyển phòng PENDING liên quan đến hợp đồng này
        if (roomTransferRepository != null) {
            List<RoomTransfer> danh_sach_chuyen_phong = roomTransferRepository
                    .findByContract_ContractId(hop_dong.getContractId());
            for (RoomTransfer rt : danh_sach_chuyen_phong) {
                if ("PENDING".equalsIgnoreCase(rt.getStatus())) {
                    rt.setStatus("CANCELLED");
                    rt.setReason("Hợp đồng lưu trú đã thanh lý");
                    roomTransferRepository.save(rt);
                }
            }
        }

        // Tự động chuyển trạng thái sinh viên sang INACTIVE nếu không còn hợp đồng
        // ACTIVE nào khác
        if (hop_dong.getStudent() != null) {
            Student sinh_vien = hop_dong.getStudent();
            final String currentContractId = hop_dong.getContractId();
            boolean hasOtherActiveContract = contractRepository
                    .findByStudent_StudentIdAndStatus(sinh_vien.getStudentId(), "ACTIVE")
                    .stream()
                    .anyMatch(c -> !c.getContractId().equals(currentContractId));
            if (!hasOtherActiveContract && "ACTIVE".equalsIgnoreCase(sinh_vien.getStatus())) {
                sinh_vien.setStatus("INACTIVE");
                studentRepository.save(sinh_vien);
                log.info("Sinh viên {} không còn hợp đồng lưu trú ACTIVE, đã chuyển trạng thái sang INACTIVE",
                        sinh_vien.getStudentCode());
            }
        }

        hop_dong = contractRepository.save(hop_dong);
        return contractMapper.toResponse(hop_dong);
    }

    // Tác vụ tự động quét và chuyển trạng thái các hợp đồng quá hạn sang EXPIRED
    @Scheduled(cron = "0 30 1 * * ?")
    @Transactional
    public int expireCompletedContracts() {
        LocalDate today = LocalDate.now();
        List<Contract> danh_sach_hop_dong_het_han = contractRepository.findByStatusAndEndDateBefore("ACTIVE", today);
        if (danh_sach_hop_dong_het_han.isEmpty()) {
            return 0;
        }

        log.info("Phát hiện {} hợp đồng đã hết hạn cần chuyển sang EXPIRED", danh_sach_hop_dong_het_han.size());
        int count = 0;
        for (Contract hop_dong : danh_sach_hop_dong_het_han) {
            hop_dong.setStatus("EXPIRED");
            hop_dong.setTerminatedDate(today);
            hop_dong.setTerminationReason("Hết thời hạn hợp đồng");

            Bed giuong = hop_dong.getBed();
            if (giuong != null) {
                giuong.setStatus("AVAILABLE");
                bedRepository.save(giuong);
                syncRoomOccupancyStatus(giuong.getRoom());
            }

            if (vehicleRegistrationRepository != null && hop_dong.getStudent() != null) {
                List<VehicleRegistration> danh_sach_xe = vehicleRegistrationRepository.findByStudent_StudentIdAndStatus(
                        hop_dong.getStudent().getStudentId(), "ACTIVE");
                for (VehicleRegistration v : danh_sach_xe) {
                    v.setStatus("EXPIRED");
                    vehicleRegistrationRepository.save(v);
                }
            }

            // Tự động chuyển trạng thái sinh viên sang INACTIVE nếu không còn hợp đồng
            // ACTIVE nào khác
            if (hop_dong.getStudent() != null) {
                Student sinh_vien = hop_dong.getStudent();
                boolean hasOtherActiveContract = contractRepository
                        .findByStudent_StudentIdAndStatus(sinh_vien.getStudentId(), "ACTIVE")
                        .stream()
                        .anyMatch(c -> !c.getContractId().equals(hop_dong.getContractId()));
                if (!hasOtherActiveContract && "ACTIVE".equalsIgnoreCase(sinh_vien.getStatus())) {
                    sinh_vien.setStatus("INACTIVE");
                    studentRepository.save(sinh_vien);
                }
            }

            contractRepository.save(hop_dong);
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

        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (!"PENDING".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể từ chối hợp đồng ở trạng thái PENDING");
        }

        // Khóa và giải phóng giường nếu đang ở trạng thái RESERVED
        if (hop_dong.getBed() != null) {
            Bed giuong = bedRepository.findByIdForUpdate(hop_dong.getBed().getBedId())
                    .orElse(hop_dong.getBed());
            if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
                giuong.setStatus("AVAILABLE");
                bedRepository.save(giuong);
                syncRoomOccupancyStatus(giuong.getRoom());
            }
        }

        Staff nhan_vien = null;
        if (staffId != null && !staffId.isBlank()) {
            nhan_vien = staffRepository.findById(staffId)
                    .orElse(null);
        }

        hop_dong.setStatus("REJECTED");
        hop_dong.setApprover(nhan_vien);
        hop_dong.setTerminatedDate(LocalDate.now());
        hop_dong.setTerminationReason((reason != null && !reason.isBlank()) ? reason.trim() : "Từ chối duyệt đơn");

        hop_dong = contractRepository.save(hop_dong);

        // Bắn thông báo cho sinh viên khi bị từ chối duyệt
        if (notificationService != null && hop_dong.getStudent() != null) {
            try {
                String title = "Hồ sơ đăng ký KTX không được phê duyệt";
                String content = String.format("Đơn đăng ký hợp đồng mã %s đã bị từ chối duyệt. Lý do: %s",
                        hop_dong.getContractCode(),
                        hop_dong.getTerminationReason() != null ? hop_dong.getTerminationReason() : "Từ chối duyệt đơn");
                notificationService.notifyStudent(hop_dong.getStudent(), title, content, nhan_vien != null ? nhan_vien.getUser() : null);
            } catch (Exception e) {
                log.warn("Không thể gửi thông báo từ chối hợp đồng: {}", e.getMessage());
            }
        }

        return contractMapper.toResponse(hop_dong);
    }

    // Sinh viên tự hủy đơn đăng ký hợp đồng PENDING của chính mình
    @Transactional
    public Contract_Response cancel(String contractId, String studentId, String reason) {
        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (!"PENDING".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể hủy đơn đăng ký khi đang ở trạng thái PENDING");
        }

        if (studentId != null && !studentId.isBlank()
                && (hop_dong.getStudent() == null || !hop_dong.getStudent().getStudentId().equals(studentId))) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy đơn đăng ký của sinh viên khác");
        }

        // Khóa và giải phóng giường nếu đang ở trạng thái RESERVED
        if (hop_dong.getBed() != null) {
            Bed giuong = bedRepository.findByIdForUpdate(hop_dong.getBed().getBedId())
                    .orElse(hop_dong.getBed());
            if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
                giuong.setStatus("AVAILABLE");
                bedRepository.save(giuong);
                syncRoomOccupancyStatus(giuong.getRoom());
            }
        }

        hop_dong.setStatus("CANCELLED");
        hop_dong.setTerminatedDate(LocalDate.now());
        hop_dong.setTerminationReason(
                (reason != null && !reason.isBlank()) ? reason.trim() : "Sinh viên tự hủy đơn đăng ký");

        hop_dong = contractRepository.save(hop_dong);
        return contractMapper.toResponse(hop_dong);
    }

    // Cập nhật thông tin hợp đồng ngoại trừ trạng thái duyệt
    @Transactional
    public Contract_Response update(String contractId, Contract_Request request) {
        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Không cho phép chỉnh sửa hợp đồng đã thanh lý
        if ("TERMINATED".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa hợp đồng đã thanh lý");
        }

        // Kiểm tra trùng mã hợp đồng nếu thay đổi mã
        if (request.getContractCode() != null && !request.getContractCode().isBlank()
                && !hop_dong.getContractCode().equalsIgnoreCase(request.getContractCode())
                && contractRepository.existsByContractCode(request.getContractCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã hợp đồng '" + request.getContractCode() + "' đã tồn tại");
        }

        // Kiểm tra khoảng thời gian bắt đầu và kết thúc
        LocalDate targetStart = request.getStartDate() != null ? request.getStartDate() : hop_dong.getStartDate();
        LocalDate targetEnd = request.getEndDate() != null ? request.getEndDate() : hop_dong.getEndDate();
        if (targetStart != null && targetEnd != null && !targetEnd.isAfter(targetStart)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Cập nhật sinh viên nếu thay đổi
        if (request.getStudentId() != null && !request.getStudentId().isBlank()
                && (hop_dong.getStudent() == null
                        || !hop_dong.getStudent().getStudentId().equals(request.getStudentId()))) {
            if ("ACTIVE".equalsIgnoreCase(hop_dong.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Hợp đồng đang có hiệu lực (ACTIVE), không thể thay đổi sinh viên. Vui lòng thanh lý và tạo hợp đồng mới.");
            }
            Student sinh_vien_moi = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
            hop_dong.setStudent(sinh_vien_moi);
        }

        // Cập nhật đợt đăng ký nếu thay đổi
        if (request.getPeriodId() != null && !request.getPeriodId().isBlank()
                && (hop_dong.getPeriod() == null
                        || !hop_dong.getPeriod().getPeriodId().equals(request.getPeriodId()))) {
            RegistrationPeriod dot_dang_ky_moi = periodRepository.findById(request.getPeriodId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký"));
            hop_dong.setPeriod(dot_dang_ky_moi);
        }

        // Cập nhật giường và đồng bộ trạng thái giường
        if (request.getBedId() != null && !request.getBedId().isBlank()
                && (hop_dong.getBed() == null || !hop_dong.getBed().getBedId().equals(request.getBedId()))) {
            if ("ACTIVE".equalsIgnoreCase(hop_dong.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Hợp đồng đang có hiệu lực (ACTIVE), không thể đổi giường qua hàm cập nhật. Vui lòng sử dụng tính năng Chuyển phòng (Room Transfer).");
            }
            Bed giuong_moi = bedRepository.findById(request.getBedId())
                    .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));
            if ("OCCUPIED".equalsIgnoreCase(giuong_moi.getStatus())) {
                throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED);
            }
            if (targetStart != null && targetEnd != null
                    && contractRepository.hasOverlappingActiveContract(giuong_moi.getBedId(), targetStart, targetEnd,
                            hop_dong.getContractId())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Giường mới đã có hợp đồng hoạt động trong khoảng thời gian này");
            }
            hop_dong.setBed(giuong_moi);
        } else if ("ACTIVE".equalsIgnoreCase(hop_dong.getStatus()) && hop_dong.getBed() != null
                && targetStart != null && targetEnd != null
                && contractRepository.hasOverlappingActiveContract(hop_dong.getBed().getBedId(), targetStart, targetEnd,
                        hop_dong.getContractId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường đã có hợp đồng hoạt động trong khoảng thời gian này");
        }

        // Ánh xạ các trường được phép cập nhật
        contractMapper.updateEntityFromRequest(hop_dong, request);
        if (targetStart != null) {
            hop_dong.setStartDate(targetStart);
        }
        if (targetEnd != null) {
            hop_dong.setEndDate(targetEnd);
        }

        hop_dong = contractRepository.save(hop_dong);
        return contractMapper.toResponse(hop_dong);
    }

    // Xóa hợp đồng và chỉ giải phóng giường nếu chính hợp đồng này đang giữ chỗ
    // (ACTIVE hoặc PENDING)
    @Transactional
    public void delete(String contractId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ Quản lý hoặc Quản trị viên mới có quyền xóa hợp đồng");
        }

        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Chặn xóa nếu hợp đồng đã phát sinh dữ liệu liên quan để tránh lỗi ràng buộc
        // khóa ngoại FK
        if (invoiceRepository != null && invoiceRepository.existsByContract_ContractId(contractId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa hợp đồng đã phát sinh hóa đơn");
        }
        if (roomTransferRepository != null && roomTransferRepository.existsByContract_ContractId(contractId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa hợp đồng đã có yêu cầu chuyển phòng");
        }

        if (hop_dong.getBed() != null) {
            boolean contractHoldsBed = "ACTIVE".equalsIgnoreCase(hop_dong.getStatus())
                    || "PENDING".equalsIgnoreCase(hop_dong.getStatus());
            if (contractHoldsBed) {
                Bed giuong = bedRepository.findByIdForUpdate(hop_dong.getBed().getBedId())
                        .orElse(hop_dong.getBed());
                if ("OCCUPIED".equalsIgnoreCase(giuong.getStatus())) {
                    giuong.setStatus("AVAILABLE");
                    bedRepository.save(giuong);
                    syncRoomOccupancyStatus(giuong.getRoom());
                } else if ("RESERVED".equalsIgnoreCase(giuong.getStatus())) {
                    giuong.setStatus("AVAILABLE");
                    bedRepository.save(giuong);
                    syncRoomOccupancyStatus(giuong.getRoom());
                }
            }
        }

        contractRepository.delete(hop_dong);
    }

    // Tự động đồng bộ trạng thái phòng (AVAILABLE / FULL) dựa trên số giường còn
    // trống
    private void syncRoomOccupancyStatus(Room phong) {
        if (phong == null || "MAINTENANCE".equalsIgnoreCase(phong.getStatus())
                || "CLOSED".equalsIgnoreCase(phong.getStatus())) {
            return;
        }
        List<Bed> danh_sach_giuong = bedRepository.findByRoom_RoomId(phong.getRoomId());
        if (danh_sach_giuong == null || danh_sach_giuong.isEmpty()) {
            return;
        }
        boolean hasAvailableBed = danh_sach_giuong.stream().anyMatch(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus()));
        String newStatus = hasAvailableBed ? "AVAILABLE" : "FULL";
        if (!newStatus.equalsIgnoreCase(phong.getStatus())) {
            phong.setStatus(newStatus);
            if (roomRepository != null) {
                roomRepository.save(phong);
            }
            log.info("Tự động đồng bộ trạng thái phòng {}: {}", phong.getRoomNumber(), newStatus);
        }
    }

    // Kiểm tra tính tương thích giới tính giữa sinh viên và tòa nhà, kiểm tra trạng
    // thái phòng/tòa
    private void validateGenderAndFacilityStatus(Student sinh_vien, Bed giuong) {
        if (giuong == null)
            return;

        Room phong = giuong.getRoom();
        if (phong != null) {
            if ("MAINTENANCE".equalsIgnoreCase(phong.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Phòng " + phong.getRoomNumber() + " đang bảo trì, không thể xếp phòng cho sinh viên");
            }
            if ("CLOSED".equalsIgnoreCase(phong.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Phòng " + phong.getRoomNumber() + " đã đóng, không thể xếp phòng cho sinh viên");
            }

            if (phong.getFloor() != null && phong.getFloor().getBuilding() != null && sinh_vien != null) {
                Building toa_nha = phong.getFloor().getBuilding();
                String buildingGender = toa_nha.getGenderType() != null ? toa_nha.getGenderType().trim().toUpperCase()
                        : "MIXED";
                String studentGender = sinh_vien.getGender() != null ? sinh_vien.getGender().trim().toUpperCase() : "";

                if ("MALE".equals(buildingGender) && !("MALE".equals(studentGender) || "NAM".equals(studentGender))) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Tòa nhà " + toa_nha.getBuildingName() + " chỉ dành cho sinh viên Nam");
                }
                if ("FEMALE".equals(buildingGender)
                        && !("FEMALE".equals(studentGender) || "NU".equals(studentGender))) {
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Tòa nhà " + toa_nha.getBuildingName() + " chỉ dành cho sinh viên Nữ");
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
