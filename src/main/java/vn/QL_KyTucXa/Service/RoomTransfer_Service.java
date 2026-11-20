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
import vn.QL_KyTucXa.DTO.Request.RoomTransfer_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomTransfer_Response;
import vn.QL_KyTucXa.Entity.*;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RoomTransfer_Mapper;
import vn.QL_KyTucXa.Repository.Bed_Repository;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.RoomTransfer_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomTransfer_Service {

    RoomTransfer_Repository roomTransferRepository;
    Contract_Repository contractRepository;
    Bed_Repository bedRepository;
    Room_Repository roomRepository;
    Staff_Repository staffRepository;
    CurrentUser_Service currentUserService;
    RoomTransfer_Mapper roomTransferMapper;

    @Autowired(required = false)
    @lombok.experimental.NonFinal
    Notification_Service notificationService;

    static Set<String> ALLOWED_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");
    static Set<String> ALLOWED_SORT_FIELDS = Set.of("transferDate", "createdAt", "status", "transferId");

    // Tạo yêu cầu chuyển phòng dành cho quản trị viên/nhân viên với kiểm tra hợp đồng ACTIVE và giường hợp lệ
    @Transactional
    public RoomTransfer_Response create(RoomTransfer_Request request) {
        log.info("Tạo yêu cầu chuyển phòng cho hợp đồng ID: {}", request.getContractId());

        Contract hop_dong = resolveContract(request.getContractId(), null);

        if (roomTransferRepository.existsByContract_ContractIdAndStatus(hop_dong.getContractId(), "PENDING")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Hợp đồng này đang có yêu cầu chuyển phòng chờ xử lý (PENDING). Vui lòng đợi kết quả hoặc hủy yêu cầu cũ");
        }

        Bed giuong_cu = hop_dong.getBed();
        Bed giuong_moi = resolveNewBed(request.getNewBedId(), request.getTargetRoomId(), giuong_cu, hop_dong.getStudent());

        LocalDate transferDate = request.getTransferDate() != null ? request.getTransferDate() : LocalDate.now();
        if (transferDate.isBefore(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày chuyển phòng không được ở trong quá khứ");
        }
        request.setTransferDate(transferDate);

        RoomTransfer chuyen_phong = roomTransferMapper.toEntity(request);
        chuyen_phong.setContract(hop_dong);
        chuyen_phong.setOldBed(giuong_cu);
        chuyen_phong.setNewBed(giuong_moi);
        chuyen_phong.setTransferDate(transferDate);
        chuyen_phong.setStatus("PENDING");

        chuyen_phong = roomTransferRepository.save(chuyen_phong);
        return roomTransferMapper.toResponse(chuyen_phong);
    }

    // Tạo yêu cầu chuyển phòng cho sinh viên: xác minh quyền sở hữu hợp đồng chống IDOR và kiểm tra hợp đồng ACTIVE
    @Transactional
    public RoomTransfer_Response createForStudent(String studentId, RoomTransfer_Request request) {
        log.info("Sinh viên ID: {} gửi yêu cầu chuyển phòng", studentId);

        Contract hop_dong = resolveContract(request.getContractId(), studentId);

        if (roomTransferRepository.existsByContract_ContractIdAndStatus(hop_dong.getContractId(), "PENDING")) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Bạn đang có một yêu cầu chuyển phòng chờ duyệt (PENDING). Không thể tạo thêm yêu cầu mới");
        }

        Bed giuong_cu = hop_dong.getBed();
        Bed giuong_moi = resolveNewBed(request.getNewBedId(), request.getTargetRoomId(), giuong_cu, hop_dong.getStudent());

        LocalDate transferDate = request.getTransferDate() != null ? request.getTransferDate() : LocalDate.now();
        if (transferDate.isBefore(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày chuyển phòng không được ở trong quá khứ");
        }
        request.setTransferDate(transferDate);

        RoomTransfer chuyen_phong = roomTransferMapper.toEntity(request);
        chuyen_phong.setContract(hop_dong);
        chuyen_phong.setOldBed(giuong_cu);
        chuyen_phong.setNewBed(giuong_moi);
        chuyen_phong.setTransferDate(transferDate);
        chuyen_phong.setStatus("PENDING");

        chuyen_phong = roomTransferRepository.save(chuyen_phong);

        // Bắn thông báo tự động cho Ban Quản trị / Nhân viên KTX
        if (notificationService != null && hop_dong.getStudent() != null) {
            String fromRoom = (giuong_cu != null && giuong_cu.getRoom() != null) ? giuong_cu.getRoom().getRoomNumber() : "Phòng cũ";
            String toRoom = (giuong_moi != null && giuong_moi.getRoom() != null) ? giuong_moi.getRoom().getRoomNumber() : "Phòng mới";
            String title = "Đơn xin chuyển phòng từ SV " + hop_dong.getStudent().getFullName();
            String content = String.format("Sinh viên %s (MSSV: %s) vừa gửi đơn xin chuyển từ phòng %s sang phòng %s. Lý do: %s",
                    hop_dong.getStudent().getFullName(),
                    hop_dong.getStudent().getStudentCode(),
                    fromRoom,
                    toRoom,
                    request.getReason() != null ? request.getReason() : "Nguyện vọng cá nhân");
            notificationService.notifyStaffAndAdmins(title, content, hop_dong.getStudent().getUser(), chuyen_phong.getTransferId());
        }

        return roomTransferMapper.toResponse(chuyen_phong);
    }

    // Lấy danh sách yêu cầu chuyển phòng của sinh viên bằng một câu truy vấn trực tiếp tránh lỗi N+1
    @Transactional(readOnly = true)
    public List<RoomTransfer_Response> getByStudentId(String studentId) {
        checkStudentAccessOwnership(studentId);
        List<RoomTransfer> danh_sach_chuyen_phong = roomTransferRepository.findByContract_Student_StudentId(studentId);
        return roomTransferMapper.toResponseList(danh_sach_chuyen_phong);
    }

    // Lấy danh sách yêu cầu chuyển phòng của sinh viên có phân trang
    @Transactional(readOnly = true)
    public Page_Response<RoomTransfer_Response> getByStudentId(String studentId, int page, int size, String sortBy, String sortDir) {
        checkStudentAccessOwnership(studentId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<RoomTransfer> trang_chuyen_phong = roomTransferRepository.findByContract_Student_StudentId(studentId, pageable);
        List<RoomTransfer_Response> data = roomTransferMapper.toResponseList(trang_chuyen_phong.getContent());

        return Page_Response.<RoomTransfer_Response>builder()
                .currentPage(trang_chuyen_phong.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_chuyen_phong.getTotalPages())
                .totalElements(trang_chuyen_phong.getTotalElements())
                .hasPrevious(trang_chuyen_phong.hasPrevious())
                .hasNext(trang_chuyen_phong.hasNext())
                .isFirst(trang_chuyen_phong.isFirst())
                .isLast(trang_chuyen_phong.isLast())
                .data(data)
                .build();
    }

    // Xác thực hợp đồng chuyển phòng: kiểm tra bắt buộc hợp đồng ACTIVE và quyền sở hữu của sinh viên
    private Contract resolveContract(String contractId, String studentId) {
        Contract hop_dong;
        if (studentId != null && !studentId.isBlank()) {
            if (contractId != null && !contractId.isBlank()) {
                hop_dong = contractRepository.findById(contractId)
                        .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
                if (hop_dong.getStudent() == null || !hop_dong.getStudent().getStudentId().equals(studentId)) {
                    throw new AppException(ErrorCode.OPERATION_FAILED, "Hợp đồng không thuộc quyền sở hữu của sinh viên");
                }
            } else {
                List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findByStudent_StudentIdAndStatus(studentId, "ACTIVE");
                if (danh_sach_hop_dong_hieu_luc.isEmpty()) {
                    throw new AppException(ErrorCode.OPERATION_FAILED, "Sinh viên không có hợp đồng lưu trú đang hoạt động (ACTIVE)");
                }
                hop_dong = danh_sach_hop_dong_hieu_luc.get(0);
            }
        } else {
            if (contractId == null || contractId.isBlank()) {
                throw new AppException(ErrorCode.CONTRACT_NOT_FOUND, "Mã hợp đồng không được để trống");
            }
            hop_dong = contractRepository.findById(contractId)
                    .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        }

        if (!"ACTIVE".equalsIgnoreCase(hop_dong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể tạo yêu cầu chuyển phòng cho hợp đồng đang hoạt động (ACTIVE)");
        }

        if (hop_dong.getBed() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hợp đồng hiện chưa có giường lưu trú");
        }

        return hop_dong;
    }

    // Xác định giường mới: chỉ nhận giường AVAILABLE, chặn trùng giường cũ và kiểm tra giới tính tòa nhà
    private Bed resolveNewBed(String newBedId, String targetRoomId, Bed giuong_cu, Student student) {
        Bed giuong_moi;
        if (newBedId != null && !newBedId.isBlank()) {
            giuong_moi = bedRepository.findById(newBedId)
                    .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));
            if (!"AVAILABLE".equalsIgnoreCase(giuong_moi.getStatus())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Giường mới được chọn không khả dụng (trạng thái: " + giuong_moi.getStatus() + ")");
            }
        } else if (targetRoomId != null && !targetRoomId.isBlank()) {
            List<Bed> danh_sach_giuong_trong = bedRepository.findByRoom_RoomIdAndStatus(targetRoomId, "AVAILABLE");
            giuong_moi = danh_sach_giuong_trong.stream()
                    .filter(b -> giuong_cu == null || !b.getBedId().equals(giuong_cu.getBedId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.OPERATION_FAILED, "Phòng đích không còn giường trống (AVAILABLE)"));
        } else {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Vui lòng chọn giường mới hoặc phòng đích muốn chuyển đến");
        }

        if (giuong_cu != null && giuong_moi.getBedId().equals(giuong_cu.getBedId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Giường mới không được trùng với giường hiện tại");
        }

        // Kiểm tra phòng đích không ở trạng thái bảo trì hoặc tạm đóng
        if (giuong_moi.getRoom() != null && ("MAINTENANCE".equalsIgnoreCase(giuong_moi.getRoom().getStatus())
                || "CLOSED".equalsIgnoreCase(giuong_moi.getRoom().getStatus()))) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phòng đích đang ở trạng thái bảo trì hoặc tạm đóng, không thể chuyển phòng");
        }

        // Kiểm tra loại giới tính của tòa nhà có phù hợp với giới tính của sinh viên không
        if (student != null && giuong_moi.getRoom() != null && giuong_moi.getRoom().getFloor() != null
                && giuong_moi.getRoom().getFloor().getBuilding() != null) {
            Building toa_nha = giuong_moi.getRoom().getFloor().getBuilding();
            String buildingGender = toa_nha.getGenderType() != null ? toa_nha.getGenderType().toUpperCase() : "MIXED";
            String studentGender = student.getGender() != null ? student.getGender().toUpperCase() : "";

            if ("MALE".equals(buildingGender) && !("MALE".equals(studentGender) || "NAM".equals(studentGender))) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tòa nhà " + toa_nha.getBuildingName() + " chỉ dành cho sinh viên Nam");
            }
            if ("FEMALE".equals(buildingGender) && !("FEMALE".equals(studentGender) || "NU".equals(studentGender))) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Tòa nhà " + toa_nha.getBuildingName() + " chỉ dành cho sinh viên Nữ");
            }
        }

        return giuong_moi;
    }

    // Lấy thông tin chi tiết yêu cầu chuyển phòng theo ID
    @Transactional(readOnly = true)
    public RoomTransfer_Response getById(String transferId) {
        RoomTransfer chuyen_phong = roomTransferRepository.findById(transferId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu chuyển phòng"));

        if (chuyen_phong.getContract() != null && chuyen_phong.getContract().getStudent() != null) {
            checkStudentAccessOwnership(chuyen_phong.getContract().getStudent().getStudentId());
        }

        return roomTransferMapper.toResponse(chuyen_phong);
    }

    // Lấy danh sách yêu cầu chuyển phòng theo mã hợp đồng
    @Transactional(readOnly = true)
    public List<RoomTransfer_Response> getByContractId(String contractId) {
        Contract hop_dong = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (hop_dong.getStudent() != null) {
            checkStudentAccessOwnership(hop_dong.getStudent().getStudentId());
        }

        List<RoomTransfer> danh_sach_chuyen_phong = roomTransferRepository.findByContract_ContractId(contractId);
        return roomTransferMapper.toResponseList(danh_sach_chuyen_phong);
    }

    // Lấy danh sách yêu cầu chuyển phòng có phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<RoomTransfer_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, null, null, sortBy, sortDir);
    }

    // Tìm kiếm và lọc danh sách yêu cầu chuyển phòng theo trạng thái, sinh viên và khoảng ngày
    @Transactional(readOnly = true)
    public Page_Response<RoomTransfer_Response> getAll(
            int page,
            int size,
            String status,
            String studentKeyword,
            LocalDate fromDate,
            LocalDate toDate,
            String sortBy,
            String sortDir
    ) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Sinh viên không có quyền xem toàn bộ yêu cầu chuyển phòng");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanStudent = (studentKeyword != null && !studentKeyword.isBlank()) ? studentKeyword.trim() : null;

        Page<RoomTransfer> trang_chuyen_phong = roomTransferRepository.searchRoomTransfers(
                cleanStatus,
                cleanStudent,
                fromDate,
                toDate,
                pageable
        );
        List<RoomTransfer_Response> data = roomTransferMapper.toResponseList(trang_chuyen_phong.getContent());

        return Page_Response.<RoomTransfer_Response>builder()
                .currentPage(trang_chuyen_phong.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_chuyen_phong.getTotalPages())
                .totalElements(trang_chuyen_phong.getTotalElements())
                .hasPrevious(trang_chuyen_phong.hasPrevious())
                .hasNext(trang_chuyen_phong.hasNext())
                .isFirst(trang_chuyen_phong.isFirst())
                .isLast(trang_chuyen_phong.isLast())
                .data(data)
                .build();
    }

    // Phê duyệt chuyển phòng: khóa giường bi quan PESSIMISTIC_WRITE, kiểm tra ngày chuyển và cập nhật giá thuê
    @Transactional
    public RoomTransfer_Response approve(String transferId, String staffId) {
        RoomTransfer chuyen_phong = roomTransferRepository.findById(transferId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu chuyển phòng"));

        if (!"PENDING".equalsIgnoreCase(chuyen_phong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể phê duyệt yêu cầu chuyển phòng khi đang ở trạng thái PENDING (trạng thái hiện tại: " + chuyen_phong.getStatus() + ")");
        }

        if (chuyen_phong.getTransferDate().isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chưa đến ngày chuyển phòng (" + chuyen_phong.getTransferDate() + "). Chỉ có thể thực hiện chuyển giường từ ngày chuyển phòng");
        }

        // Kiểm tra tính toàn vẹn: giường cũ trên hợp đồng phải khớp với giường cũ trên đơn
        Contract hop_dong = chuyen_phong.getContract();
        Bed giuong_cu = chuyen_phong.getOldBed();
        if (hop_dong != null && hop_dong.getBed() != null && giuong_cu != null
                && !hop_dong.getBed().getBedId().equals(giuong_cu.getBedId())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Giường hiện tại của sinh viên không khớp với giường cũ trên đơn chuyển phòng");
        }

        // Khóa giường mới bằng PESSIMISTIC_WRITE để chống xung đột tranh chấp đồng thời
        Bed giuong_moi = bedRepository.findByIdForUpdate(chuyen_phong.getNewBed().getBedId())
                .orElseThrow(() -> new AppException(ErrorCode.BED_NOT_FOUND));

        if (!"AVAILABLE".equalsIgnoreCase(giuong_moi.getStatus())) {
            throw new AppException(ErrorCode.BED_ALREADY_OCCUPIED,
                    "Giường đích không còn khả dụng hoặc đã có người ở (trạng thái: " + giuong_moi.getStatus() + ")");
        }

        // Kiểm tra phòng đích không ở trạng thái bảo trì hoặc tạm đóng
        if (giuong_moi.getRoom() != null && ("MAINTENANCE".equalsIgnoreCase(giuong_moi.getRoom().getStatus())
                || "CLOSED".equalsIgnoreCase(giuong_moi.getRoom().getStatus()))) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Phòng đích đang ở trạng thái bảo trì hoặc tạm đóng, không thể chuyển phòng");
        }

        if (giuong_cu != null) {
            giuong_cu.setStatus("AVAILABLE");
            bedRepository.save(giuong_cu);
            syncRoomOccupancyStatus(giuong_cu.getRoom());
        }

        giuong_moi.setStatus("OCCUPIED");
        bedRepository.save(giuong_moi);
        syncRoomOccupancyStatus(giuong_moi.getRoom());

        // Cập nhật hợp đồng với giường mới và điều chỉnh tiền thuê theo loại phòng mới
        hop_dong.setBed(giuong_moi);
        RoomType loai_phong_moi = (giuong_moi.getRoom() != null) ? giuong_moi.getRoom().getRoomType() : null;
        if (loai_phong_moi != null && loai_phong_moi.getPricePerMonth() != null) {
            hop_dong.setMonthlyFee(loai_phong_moi.getPricePerMonth());
        }
        contractRepository.save(hop_dong);

        Staff nhan_vien = resolveStaff(staffId);
        if (nhan_vien != null) {
            chuyen_phong.setApprover(nhan_vien);
        }
        chuyen_phong.setStatus("APPROVED");
        chuyen_phong = roomTransferRepository.save(chuyen_phong);

        // Bắn thông báo cho sinh viên khi được duyệt chuyển phòng
        if (notificationService != null && hop_dong.getStudent() != null) {
            String fromRoom = (giuong_cu != null && giuong_cu.getRoom() != null) ? giuong_cu.getRoom().getRoomNumber() : "";
            String toRoom = (giuong_moi != null && giuong_moi.getRoom() != null) ? giuong_moi.getRoom().getRoomNumber() : "";
            String title = "Đơn xin chuyển phòng của bạn đã được duyệt!";
            String content = String.format("Yêu cầu chuyển từ phòng %s sang phòng %s đã được Ban Quản lý phê duyệt thành công. Vui lòng liên hệ BQL KTX để nhận phòng mới.",
                    fromRoom, toRoom);
            notificationService.notifyStudent(hop_dong.getStudent(), title, content, nhan_vien != null ? nhan_vien.getUser() : null);
        }

        return roomTransferMapper.toResponse(chuyen_phong);
    }

    // Tự động đồng bộ trạng thái phòng (AVAILABLE <-> FULL) theo số giường trống thực tế
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
        String targetStatus = hasAvailableBed ? "AVAILABLE" : "FULL";
        if (!targetStatus.equalsIgnoreCase(phong.getStatus())) {
            phong.setStatus(targetStatus);
            if (roomRepository != null) {
                roomRepository.save(phong);
            }
            log.info("Tự động đồng bộ trạng thái phòng {}: {}", phong.getRoomNumber(), targetStatus);
        }
    }

    // Từ chối chuyển phòng: chỉ cho phép khi PENDING và nối chuỗi lý do để bảo toàn lý do ban đầu của sinh viên
    @Transactional
    public RoomTransfer_Response reject(String transferId, String staffId, String reason) {
        RoomTransfer chuyen_phong = roomTransferRepository.findById(transferId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu chuyển phòng"));

        if (!"PENDING".equalsIgnoreCase(chuyen_phong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ có thể từ chối yêu cầu chuyển phòng khi đang ở trạng thái PENDING (trạng thái hiện tại: " + chuyen_phong.getStatus() + ")");
        }

        Staff nhan_vien = resolveStaff(staffId);
        if (nhan_vien != null) {
            chuyen_phong.setApprover(nhan_vien);
        }
        chuyen_phong.setStatus("REJECTED");

        String originalReason = chuyen_phong.getReason();
        String rejectNote = (reason != null && !reason.isBlank()) ? reason.trim() : "Không đáp ứng yêu cầu chuyển phòng";
        String combinedReason = (originalReason != null && !originalReason.isBlank())
                ? originalReason.trim() + " | Lý do từ chối: " + rejectNote
                : "Lý do từ chối: " + rejectNote;
        if (combinedReason.length() > 255) {
            combinedReason = combinedReason.substring(0, 255);
        }
        chuyen_phong.setReason(combinedReason);

        chuyen_phong = roomTransferRepository.save(chuyen_phong);

        // Bắn thông báo cho sinh viên khi bị từ chối chuyển phòng
        if (notificationService != null && chuyen_phong.getContract() != null && chuyen_phong.getContract().getStudent() != null) {
            String title = "Đơn xin chuyển phòng không được phê duyệt";
            String content = String.format("Yêu cầu chuyển phòng của bạn đã bị từ chối. Lý do: %s", rejectNote);
            notificationService.notifyStudent(chuyen_phong.getContract().getStudent(), title, content, nhan_vien != null ? nhan_vien.getUser() : null);
        }

        return roomTransferMapper.toResponse(chuyen_phong);
    }

    // Hủy yêu cầu chuyển phòng: sinh viên hủy đơn của chính mình khi đơn còn PENDING
    @Transactional
    public RoomTransfer_Response cancel(String transferId, String studentId) {
        RoomTransfer chuyen_phong = roomTransferRepository.findById(transferId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu chuyển phòng"));

        if (studentId != null && !studentId.isBlank()) {
            Contract hop_dong = chuyen_phong.getContract();
            if (hop_dong == null || hop_dong.getStudent() == null || !hop_dong.getStudent().getStudentId().equals(studentId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Bạn chỉ có thể hủy yêu cầu chuyển phòng của chính mình");
            }
        }

        if (!"PENDING".equalsIgnoreCase(chuyen_phong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể hủy yêu cầu chuyển phòng khi đơn đang ở trạng thái PENDING");
        }

        chuyen_phong.setStatus("CANCELLED");
        chuyen_phong = roomTransferRepository.save(chuyen_phong);
        return roomTransferMapper.toResponse(chuyen_phong);
    }

    // Xóa yêu cầu chuyển phòng: chỉ cho phép khi đơn còn PENDING hoặc CANCELLED
    @Transactional
    public void delete(String transferId) {
        RoomTransfer chuyen_phong = roomTransferRepository.findById(transferId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu chuyển phòng"));

        if (!"PENDING".equalsIgnoreCase(chuyen_phong.getStatus()) && !"CANCELLED".equalsIgnoreCase(chuyen_phong.getStatus())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Chỉ được phép xóa yêu cầu chuyển phòng khi đang ở trạng thái PENDING hoặc CANCELLED");
        }

        roomTransferRepository.delete(chuyen_phong);
    }

    // Xác định nhân viên duyệt: ưu tiên staffId truyền vào hoặc người dùng nhân viên hiện tại từ JWT
    private Staff resolveStaff(String staffId) {
        if (staffId != null && !staffId.isBlank()) {
            return staffRepository.findById(staffId).orElse(null);
        }
        if (currentUserService != null && currentUserService.userId() != null) {
            return staffRepository.findByUser_UserId(currentUserService.userId()).orElse(null);
        }
        return null;
    }

    // Kiểm tra quyền sở hữu đối với tài khoản sinh viên (Fail-closed)
    private void checkStudentAccessOwnership(String targetStudentId) {
        if (currentUserService != null && currentUserService.hasRole("STUDENT")) {
            String currentStudentId = currentUserService.findStudentId().orElse(null);
            if (currentStudentId == null || !currentStudentId.equals(targetStudentId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền truy cập yêu cầu chuyển phòng của sinh viên khác");
            }
        }
    }
}
