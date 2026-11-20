package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.UtilityReading_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.DTO.Response.UtilityReading_Response;
import vn.QL_KyTucXa.DTO.Response.UtilitySplit_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.ServiceType;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.UtilityReading;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Room_Mapper;
import vn.QL_KyTucXa.Mapper.UtilityReading_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;
import vn.QL_KyTucXa.Repository.ServiceType_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.UtilityReading_Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UtilityReading_Service {

    UtilityReading_Repository readingRepository;
    Room_Repository roomRepository;
    ServiceType_Repository serviceTypeRepository;
    Staff_Repository staffRepository;
    Contract_Repository contractRepository;
    Invoice_Repository invoiceRepository;
    CurrentUser_Service currentUserService;
    UtilityReading_Mapper readingMapper;
    Room_Mapper roomMapper;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "readingId", "readingMonth", "previousIndex", "currentIndex", "consumption", "createdAt"
    );

    // Chuẩn hóa ngày ghi chỉ số về ngày 1 đầu tháng
    private LocalDate normalizeReadingMonth(LocalDate date) {
        return (date != null ? date : LocalDate.now()).withDayOfMonth(1);
    }

    // Ghi mới chỉ số điện nước cho một phòng trong tháng
    @Transactional
    public UtilityReading_Response create(UtilityReading_Request request) {
        log.info("Ghi chỉ số điện nước cho phòng ID: {}, dịch vụ ID: {}", request.getRoomId(), request.getServiceTypeId());

        Room phong = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        ServiceType loai_dich_vu = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại dịch vụ"));

        if (!"METERED".equalsIgnoreCase(loai_dich_vu.getBillingType())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể ghi chỉ số cho loại dịch vụ tính theo đồng hồ (METERED)");
        }

        LocalDate readingMonth = normalizeReadingMonth(request.getReadingMonth());
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        if (readingMonth.isAfter(currentMonth)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể ghi nhận chỉ số cho kỳ tháng trong tương lai (" + readingMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + ")");
        }

        // Kiểm tra tránh trùng lặp bản ghi chỉ số trong cùng một tháng
        Optional<UtilityReading> existingOpt = readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
                phong.getRoomId(), loai_dich_vu.getServiceTypeId(), readingMonth);
        if (existingOpt.isPresent()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ số tháng " + readingMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " của phòng đã được ghi nhận");
        }

        // Xác định chỉ số đầu kỳ: ưu tiên lấy chỉ số cuối kỳ của bản ghi gần nhất trước tháng này
        BigDecimal previousIndex;
        if (Boolean.TRUE.equals(request.getIsMeterReset())) {
            previousIndex = request.getPreviousIndex() != null ? request.getPreviousIndex() : BigDecimal.ZERO;
        } else {
            Optional<UtilityReading> latestPriorReadingOpt = readingRepository
                    .findTopByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonthLessThanOrderByReadingMonthDesc(
                            phong.getRoomId(), loai_dich_vu.getServiceTypeId(), readingMonth);
            if (latestPriorReadingOpt.isPresent()) {
                previousIndex = latestPriorReadingOpt.get().getCurrentIndex();
            } else {
                previousIndex = request.getPreviousIndex() != null ? request.getPreviousIndex() : BigDecimal.ZERO;
            }
        }

        BigDecimal currentIndex = request.getCurrentIndex();
        if (currentIndex == null || currentIndex.compareTo(previousIndex) < 0) {
            throw new AppException(ErrorCode.INDEX_INVALID);
        }

        // Lấy nhân viên ghi số từ JWT (không dự phòng nhân viên đầu tiên trong CSDL)
        Staff nhan_vien_ghi_so = null;
        if (request.getRecordedBy() != null && !request.getRecordedBy().isBlank()) {
            nhan_vien_ghi_so = staffRepository.findById(request.getRecordedBy())
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        } else {
            nhan_vien_ghi_so = currentUserService.findStaffId()
                    .flatMap(staffRepository::findById)
                    .orElse(null);
        }

        UtilityReading chi_so = UtilityReading.builder()
                .room(phong)
                .serviceType(loai_dich_vu)
                .readingMonth(readingMonth)
                .previousIndex(previousIndex)
                .currentIndex(currentIndex)
                .consumption(currentIndex.subtract(previousIndex))
                .recordedBy(nhan_vien_ghi_so)
                .build();

        chi_so = readingRepository.save(chi_so);

        // Đồng bộ cập nhật chỉ số đầu kỳ của tháng kế tiếp nếu đã được tạo trước đó
        syncNextMonthPreviousIndex(phong.getRoomId(), loai_dich_vu.getServiceTypeId(), readingMonth, currentIndex);

        return readingMapper.toResponse(chi_so);
    }

    // Nhập chỉ số hàng loạt cho nhiều phòng (Tự động thêm mới hoặc cập nhật nếu đã có)
    @Transactional
    public UtilityReading_Response createOrUpdate(UtilityReading_Request request) {
        if (request.getRoomId() == null || request.getRoomId().isBlank()) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (request.getServiceTypeId() == null || request.getServiceTypeId().isBlank()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa chọn loại dịch vụ (Điện/Nước)");
        }

        Room phong = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        ServiceType loai_dich_vu = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại dịch vụ"));

        if (!"METERED".equalsIgnoreCase(loai_dich_vu.getBillingType())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể ghi chỉ số cho loại dịch vụ tính theo đồng hồ (METERED)");
        }

        LocalDate readingMonth = normalizeReadingMonth(request.getReadingMonth());
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        if (readingMonth.isAfter(currentMonth)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể ghi nhận chỉ số cho kỳ tháng trong tương lai (" + readingMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + ")");
        }

        Optional<UtilityReading> existingOpt = readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
                phong.getRoomId(), loai_dich_vu.getServiceTypeId(), readingMonth);

        BigDecimal previousIndex;
        if (Boolean.TRUE.equals(request.getIsMeterReset())) {
            previousIndex = request.getPreviousIndex() != null ? request.getPreviousIndex() : BigDecimal.ZERO;
        } else {
            Optional<UtilityReading> latestPriorReadingOpt = readingRepository
                    .findTopByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonthLessThanOrderByReadingMonthDesc(
                            phong.getRoomId(), loai_dich_vu.getServiceTypeId(), readingMonth);
            if (latestPriorReadingOpt.isPresent()) {
                previousIndex = latestPriorReadingOpt.get().getCurrentIndex();
            } else {
                previousIndex = request.getPreviousIndex() != null ? request.getPreviousIndex() : BigDecimal.ZERO;
            }
        }

        BigDecimal currentIndex = request.getCurrentIndex();
        if (currentIndex == null || currentIndex.compareTo(previousIndex) < 0) {
            throw new AppException(ErrorCode.INDEX_INVALID, "Chỉ số mới (" + currentIndex + ") không được nhỏ hơn chỉ số cũ (" + previousIndex + ")");
        }

        Staff nhan_vien_ghi_so = null;
        if (request.getRecordedBy() != null && !request.getRecordedBy().isBlank()) {
            nhan_vien_ghi_so = staffRepository.findById(request.getRecordedBy())
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        } else {
            nhan_vien_ghi_so = currentUserService.findStaffId()
                    .flatMap(staffRepository::findById)
                    .orElse(null);
        }

        UtilityReading chi_so;
        if (existingOpt.isPresent()) {
            chi_so = existingOpt.get();
            chi_so.setPreviousIndex(previousIndex);
            chi_so.setCurrentIndex(currentIndex);
            chi_so.setConsumption(currentIndex.subtract(previousIndex));
            if (nhan_vien_ghi_so != null) {
                chi_so.setRecordedBy(nhan_vien_ghi_so);
            }
        } else {
            chi_so = UtilityReading.builder()
                    .room(phong)
                    .serviceType(loai_dich_vu)
                    .readingMonth(readingMonth)
                    .previousIndex(previousIndex)
                    .currentIndex(currentIndex)
                    .consumption(currentIndex.subtract(previousIndex))
                    .recordedBy(nhan_vien_ghi_so)
                    .build();
        }

        chi_so = readingRepository.save(chi_so);
        syncNextMonthPreviousIndex(phong.getRoomId(), loai_dich_vu.getServiceTypeId(), readingMonth, currentIndex);

        return readingMapper.toResponse(chi_so);
    }

    @Transactional
    public List<UtilityReading_Response> createBatch(List<UtilityReading_Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<UtilityReading_Response> responses = new ArrayList<>();
        for (UtilityReading_Request req : requests) {
            responses.add(createOrUpdate(req));
        }
        return responses;
    }

    // Cập nhật chỉ số đầu kỳ của tháng sau nếu đã có bản ghi
    private void syncNextMonthPreviousIndex(String roomId, String serviceTypeId, LocalDate currentMonth, BigDecimal newCurrentIndex) {
        LocalDate nextMonth = currentMonth.plusMonths(1);
        readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(roomId, serviceTypeId, nextMonth)
                .ifPresent(nextReading -> {
                    nextReading.setPreviousIndex(newCurrentIndex);
                    if (nextReading.getCurrentIndex().compareTo(newCurrentIndex) >= 0) {
                        nextReading.setConsumption(nextReading.getCurrentIndex().subtract(newCurrentIndex));
                    }
                    readingRepository.save(nextReading);
                });
    }

    @Transactional(readOnly = true)
    public UtilityReading_Response getById(String readingId) {
        UtilityReading chi_so = readingRepository.findById(readingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chỉ số điện nước"));

        return readingMapper.toResponse(chi_so);
    }

    @Transactional(readOnly = true)
    public List<UtilityReading_Response> getByRoomId(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        List<UtilityReading> danh_sach_chi_so = readingRepository.findByRoom_RoomId(roomId);
        return readingMapper.toResponseList(danh_sach_chi_so);
    }

    // Tìm kiếm chỉ số phân trang theo tòa nhà, phòng, dịch vụ và kỳ tháng
    @Transactional(readOnly = true)
    public Page_Response<UtilityReading_Response> search(
            String buildingId,
            String roomId,
            String serviceTypeId,
            LocalDate readingMonth,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "readingMonth";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);
        LocalDate normalizedMonth = readingMonth != null ? normalizeReadingMonth(readingMonth) : null;

        Page<UtilityReading> trang_chi_so = readingRepository.searchUtilityReadings(
                (buildingId != null && !buildingId.isBlank()) ? buildingId.trim() : null,
                (roomId != null && !roomId.isBlank()) ? roomId.trim() : null,
                (serviceTypeId != null && !serviceTypeId.isBlank()) ? serviceTypeId.trim() : null,
                normalizedMonth,
                pageable
        );

        List<UtilityReading_Response> data = readingMapper.toResponseList(trang_chi_so.getContent());

        return Page_Response.<UtilityReading_Response>builder()
                .currentPage(trang_chi_so.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_chi_so.getTotalPages())
                .totalElements(trang_chi_so.getTotalElements())
                .hasPrevious(trang_chi_so.hasPrevious())
                .hasNext(trang_chi_so.hasNext())
                .isFirst(trang_chi_so.isFirst())
                .isLast(trang_chi_so.isLast())
                .data(data)
                .build();
    }

    @Transactional(readOnly = true)
    public Page_Response<UtilityReading_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return search(null, null, null, null, page, size, sortBy, sortDir);
    }

    // Lấy danh sách các phòng chưa ghi nhận chỉ số trong tháng này (loại bỏ phòng CLOSED và phòng không có người ở)
    @Transactional(readOnly = true)
    public List<Room_Response> getUnrecordedRooms(LocalDate readingMonth, String serviceTypeId, String buildingId) {
        LocalDate normalizedMonth = normalizeReadingMonth(readingMonth);
        List<Room> danh_sach_phong = roomRepository.findAll();

        if (buildingId != null && !buildingId.isBlank()) {
            danh_sach_phong = danh_sach_phong.stream()
                    .filter(r -> r.getFloor() != null && r.getFloor().getBuilding() != null
                            && buildingId.equals(r.getFloor().getBuilding().getBuildingId()))
                    .toList();
        }

        // Bỏ qua các phòng đã đóng (CLOSED)
        danh_sach_phong = danh_sach_phong.stream()
                .filter(r -> !"CLOSED".equalsIgnoreCase(r.getStatus()))
                .toList();

        // Bỏ qua các phòng không có người ở (không có hợp đồng ACTIVE nào)
        if (!danh_sach_phong.isEmpty() && contractRepository != null) {
            List<String> roomIds = danh_sach_phong.stream().map(Room::getRoomId).toList();
            List<Object[]> activeCounts = contractRepository.countActiveByRoomIds(roomIds);
            Map<String, Long> countMap = activeCounts.stream()
                    .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
            danh_sach_phong = danh_sach_phong.stream()
                    .filter(r -> countMap.getOrDefault(r.getRoomId(), 0L) > 0)
                    .toList();
        }

        // Lấy danh sách ID phòng đã được ghi chỉ số trong tháng
        List<UtilityReading> danh_sach_chi_so = (serviceTypeId != null && !serviceTypeId.isBlank())
                ? readingRepository.findByReadingMonth(normalizedMonth).stream()
                        .filter(u -> serviceTypeId.equals(u.getServiceType().getServiceTypeId()))
                        .toList()
                : readingRepository.findByReadingMonth(normalizedMonth);

        Set<String> recordedRoomIds = danh_sach_chi_so.stream()
                .map(u -> u.getRoom().getRoomId())
                .collect(Collectors.toSet());

        List<Room> danh_sach_phong_chua_ghi = danh_sach_phong.stream()
                .filter(r -> !recordedRoomIds.contains(r.getRoomId()))
                .toList();

        return roomMapper.toResponseList(danh_sach_phong_chua_ghi);
    }

    // Thuật toán tính toán và phân bổ chi phí điện nước theo số sinh viên có hợp đồng ACTIVE trong phòng
    @Transactional(readOnly = true)
    public UtilitySplit_Response calculateRoomUtilitySplit(String roomId, String serviceTypeId, LocalDate readingMonth) {
        Room phong = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        LocalDate normalizedMonth = normalizeReadingMonth(readingMonth);

        UtilityReading chi_so = readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
                roomId, serviceTypeId, normalizedMonth)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa ghi nhận chỉ số điện nước cho phòng trong tháng " + normalizedMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))));

        ServiceType loai_dich_vu = chi_so.getServiceType();
        BigDecimal unitPrice = loai_dich_vu.getUnitPrice() != null ? loai_dich_vu.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal consumption = chi_so.getConsumption() != null ? chi_so.getConsumption() : BigDecimal.ZERO;
        BigDecimal totalAmount = consumption.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        // Lấy các hợp đồng có hiệu lực trong tháng ghi chỉ số của phòng (ưu tiên theo tháng, dự phòng theo ACTIVE hiện tại)
        LocalDate monthStart = normalizedMonth.withDayOfMonth(1);
        LocalDate monthEnd = normalizedMonth.withDayOfMonth(normalizedMonth.lengthOfMonth());
        List<Contract> danh_sach_hop_dong_hieu_luc = contractRepository.findEffectiveContractsByRoomIdAndMonth(roomId, monthStart, monthEnd);
        if (danh_sach_hop_dong_hieu_luc.isEmpty()) {
            danh_sach_hop_dong_hieu_luc = contractRepository.findActiveContractsByRoomId(roomId);
        }
        int activeCount = danh_sach_hop_dong_hieu_luc.size();

        BigDecimal amountPerResident = BigDecimal.ZERO;
        BigDecimal remainder = BigDecimal.ZERO;
        if (activeCount > 0) {
            // Chia đều làm tròn xuống phần nguyên/2 số thập phân, dồn toàn bộ phần dư lẻ cho người đầu tiên
            amountPerResident = totalAmount.divide(BigDecimal.valueOf(activeCount), 2, RoundingMode.FLOOR);
            remainder = totalAmount.subtract(amountPerResident.multiply(BigDecimal.valueOf(activeCount)));
        }

        List<UtilitySplit_Response.ResidentShare> shares = new ArrayList<>();
        boolean isFirst = true;
        for (Contract hop_dong : danh_sach_hop_dong_hieu_luc) {
            if (hop_dong.getStudent() != null) {
                BigDecimal share = amountPerResident;
                if (isFirst) {
                    share = share.add(remainder);
                    isFirst = false;
                }
                shares.add(UtilitySplit_Response.ResidentShare.builder()
                        .studentId(hop_dong.getStudent().getStudentId())
                        .studentCode(hop_dong.getStudent().getStudentCode())
                        .fullName(hop_dong.getStudent().getFullName())
                        .contractId(hop_dong.getContractId())
                        .contractCode(hop_dong.getContractCode())
                        .shareAmount(share)
                        .build());
            }
        }

        return UtilitySplit_Response.builder()
                .roomId(phong.getRoomId())
                .roomNumber(phong.getRoomNumber())
                .serviceTypeId(loai_dich_vu.getServiceTypeId())
                .serviceName(loai_dich_vu.getServiceName())
                .readingMonth(normalizedMonth)
                .previousIndex(chi_so.getPreviousIndex())
                .currentIndex(chi_so.getCurrentIndex())
                .consumption(consumption)
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .activeResidentCount(activeCount)
                .amountPerResident(amountPerResident)
                .residentShares(shares)
                .build();
    }

    // Cập nhật thông tin chỉ số điện nước
    @Transactional
    public UtilityReading_Response update(String readingId, UtilityReading_Request request) {
        UtilityReading chi_so = readingRepository.findById(readingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chỉ số điện nước"));

        Room phong_dich = chi_so.getRoom();
        if (request.getRoomId() != null && !request.getRoomId().isBlank()) {
            phong_dich = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        }

        ServiceType loai_dich_vu_dich = chi_so.getServiceType();
        if (request.getServiceTypeId() != null && !request.getServiceTypeId().isBlank()) {
            loai_dich_vu_dich = serviceTypeRepository.findById(request.getServiceTypeId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại dịch vụ"));
        }

        if (!"METERED".equalsIgnoreCase(loai_dich_vu_dich.getBillingType())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Chỉ có thể ghi chỉ số cho loại dịch vụ tính theo đồng hồ (METERED)");
        }

        LocalDate targetMonth = request.getReadingMonth() != null ? normalizeReadingMonth(request.getReadingMonth()) : chi_so.getReadingMonth();

        // Kiểm tra tránh trùng lặp nếu đổi phòng, loại dịch vụ hoặc kỳ tháng
        final String currentReadingId = chi_so.getReadingId();
        readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
                phong_dich.getRoomId(), loai_dich_vu_dich.getServiceTypeId(), targetMonth)
                .filter(existing -> !existing.getReadingId().equals(currentReadingId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.OPERATION_FAILED, "Đã tồn tại bản ghi chỉ số cho phòng và dịch vụ này trong tháng");
                });

        // Chặn sửa chỉ số khi hóa đơn tháng đó của phòng đã được phát hành hoặc đã thanh toán
        if (invoiceRepository.existsIssuedInvoiceForRoomAndMonth(phong_dich.getRoomId(), targetMonth)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể chỉnh sửa chỉ số vì hóa đơn tháng " + targetMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " của phòng đã được phát hành");
        }

        // Xác định chỉ số đầu kỳ
        BigDecimal previousIndex;
        if (Boolean.TRUE.equals(request.getIsMeterReset())) {
            previousIndex = request.getPreviousIndex() != null ? request.getPreviousIndex() : BigDecimal.ZERO;
        } else {
            Optional<UtilityReading> latestPriorReadingOpt = readingRepository
                    .findTopByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonthLessThanOrderByReadingMonthDesc(
                            phong_dich.getRoomId(), loai_dich_vu_dich.getServiceTypeId(), targetMonth);
            if (latestPriorReadingOpt.isPresent()) {
                previousIndex = latestPriorReadingOpt.get().getCurrentIndex();
            } else {
                previousIndex = request.getPreviousIndex() != null ? request.getPreviousIndex() : chi_so.getPreviousIndex();
            }
        }

        BigDecimal currentIndex = request.getCurrentIndex() != null ? request.getCurrentIndex() : chi_so.getCurrentIndex();
        if (currentIndex.compareTo(previousIndex) < 0) {
            throw new AppException(ErrorCode.INDEX_INVALID);
        }

        chi_so.setRoom(phong_dich);
        chi_so.setServiceType(loai_dich_vu_dich);
        chi_so.setReadingMonth(targetMonth);
        chi_so.setPreviousIndex(previousIndex);
        chi_so.setCurrentIndex(currentIndex);
        chi_so.setConsumption(currentIndex.subtract(previousIndex));

        chi_so = readingRepository.save(chi_so);

        // Đồng bộ cập nhật sang tháng tiếp theo nếu có
        syncNextMonthPreviousIndex(phong_dich.getRoomId(), loai_dich_vu_dich.getServiceTypeId(), targetMonth, currentIndex);

        return readingMapper.toResponse(chi_so);
    }

    // Xóa chỉ số điện nước (chỉ cho phép khi chưa phát hành hóa đơn)
    @Transactional
    public void delete(String readingId) {
        UtilityReading chi_so = readingRepository.findById(readingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chỉ số điện nước"));

        if (invoiceRepository.existsIssuedInvoiceForRoomAndMonth(chi_so.getRoom().getRoomId(), chi_so.getReadingMonth())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa chỉ số vì hóa đơn tháng " + chi_so.getReadingMonth().format(DateTimeFormatter.ofPattern("MM/yyyy")) + " của phòng đã được phát hành");
        }

        readingRepository.delete(chi_so);
    }
}
