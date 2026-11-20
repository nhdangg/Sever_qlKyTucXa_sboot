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
import vn.QL_KyTucXa.DTO.Request.RegistrationPeriod_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RegistrationPeriod_Response;
import vn.QL_KyTucXa.Entity.RegistrationPeriod;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RegistrationPeriod_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.RegistrationPeriod_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegistrationPeriod_Service {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "UPCOMING", "OPEN", "CLOSED", "CANCELLED"
    );
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "periodId", "periodName", "academicYear", "startDate", "endDate", "registrationDeadline", "status", "createdAt"
    );

    RegistrationPeriod_Repository periodRepository;
    Contract_Repository contractRepository;
    RegistrationPeriod_Mapper periodMapper;

    // Tạo mới đợt đăng ký có kiểm tra tính hợp lệ ngày tháng, hạn nộp và chống trùng lặp thời gian
    @Transactional
    public RegistrationPeriod_Response create(RegistrationPeriod_Request request) {
        log.info("Tạo mới đợt đăng ký: {}", request.getPeriodName());

        validatePeriodRequest(request, null, null);

        RegistrationPeriod dot_dang_ky = periodMapper.toEntity(request);
        dot_dang_ky = periodRepository.save(dot_dang_ky);

        return periodMapper.toResponse(dot_dang_ky);
    }

    // Lấy chi tiết đợt đăng ký theo ID
    @Transactional(readOnly = true)
    public RegistrationPeriod_Response getById(String periodId) {
        RegistrationPeriod dot_dang_ky = periodRepository.findById(periodId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký"));

        return periodMapper.toResponse(dot_dang_ky);
    }

    // Lấy danh sách đợt đăng ký không phân trang có hỗ trợ lọc theo năm học và trạng thái
    @Transactional(readOnly = true)
    public List<RegistrationPeriod_Response> getAll(String academicYear, String status) {
        String yearFilter = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : null;
        String statusFilter = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        List<RegistrationPeriod> danh_sach_dot_dang_ky;
        if (yearFilter != null && statusFilter != null) {
            danh_sach_dot_dang_ky = periodRepository.findByAcademicYearAndStatus(yearFilter, statusFilter);
        } else if (yearFilter != null) {
            danh_sach_dot_dang_ky = periodRepository.findByAcademicYear(yearFilter);
        } else if (statusFilter != null) {
            danh_sach_dot_dang_ky = periodRepository.findByStatus(statusFilter);
        } else {
            danh_sach_dot_dang_ky = periodRepository.findAll();
        }

        return periodMapper.toResponseList(danh_sach_dot_dang_ky);
    }

    // Lấy toàn bộ danh sách đợt đăng ký không phân trang
    @Transactional(readOnly = true)
    public List<RegistrationPeriod_Response> getAll() {
        return getAll(null, null);
    }

    // Lấy danh sách đợt đăng ký có phân trang và lọc theo năm học, trạng thái
    @Transactional(readOnly = true)
    public Page_Response<RegistrationPeriod_Response> getAll(
            int page,
            int size,
            String academicYear,
            String status,
            String sortBy,
            String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "startDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        String yearFilter = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : null;
        String statusFilter = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<RegistrationPeriod> trang_dot_dang_ky = periodRepository.searchPeriods(yearFilter, statusFilter, pageable);
        List<RegistrationPeriod_Response> data = periodMapper.toResponseList(trang_dot_dang_ky.getContent());

        return Page_Response.<RegistrationPeriod_Response>builder()
                .currentPage(trang_dot_dang_ky.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_dot_dang_ky.getTotalPages())
                .totalElements(trang_dot_dang_ky.getTotalElements())
                .hasPrevious(trang_dot_dang_ky.hasPrevious())
                .hasNext(trang_dot_dang_ky.hasNext())
                .isFirst(trang_dot_dang_ky.isFirst())
                .isLast(trang_dot_dang_ky.isLast())
                .data(data)
                .build();
    }

    // Lấy danh sách đợt đăng ký có phân trang cơ bản
    @Transactional(readOnly = true)
    public Page_Response<RegistrationPeriod_Response> getAll(int page, int size, String sortBy, String sortDir) {
        return getAll(page, size, null, null, sortBy, sortDir);
    }

    // Cập nhật thông tin đợt đăng ký
    @Transactional
    public RegistrationPeriod_Response update(String periodId, RegistrationPeriod_Request request) {
        RegistrationPeriod dot_dang_ky = periodRepository.findById(periodId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký"));

        validatePeriodRequest(request, periodId, dot_dang_ky.getStatus());

        periodMapper.updateEntityFromRequest(dot_dang_ky, request);
        dot_dang_ky = periodRepository.save(dot_dang_ky);

        return periodMapper.toResponse(dot_dang_ky);
    }

    // Xóa đợt đăng ký có kiểm tra ràng buộc hợp đồng để tránh lỗi khóa ngoại FK
    @Transactional
    public void delete(String periodId) {
        RegistrationPeriod dot_dang_ky = periodRepository.findById(periodId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt đăng ký"));

        // Kiểm tra ràng buộc hợp đồng đã phát sinh để tránh lỗi khóa ngoại FK
        if (contractRepository.existsByPeriod_PeriodId(periodId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa đợt đăng ký đã phát sinh hợp đồng sinh viên");
        }

        periodRepository.delete(dot_dang_ky);
    }

    // Tự động quét và cập nhật trạng thái đợt đăng ký theo ngày (UPCOMING, OPEN, CLOSED)
    @Transactional
    public int autoUpdatePeriodStatuses() {
        log.info("Bắt đầu tác vụ tự động quét và cập nhật trạng thái các đợt đăng ký");
        List<RegistrationPeriod> danh_sach_dot_dang_ky = periodRepository.findAll();
        int updatedCount = 0;

        for (RegistrationPeriod dot_dang_ky : danh_sach_dot_dang_ky) {
            // Không tự động đổi trạng thái nếu đợt đăng ký đã bị CANCELLED
            if ("CANCELLED".equalsIgnoreCase(dot_dang_ky.getStatus())) {
                continue;
            }

            String currentStatus = dot_dang_ky.getStatus();
            String expectedStatus = computeStatusByDate(
                    dot_dang_ky.getStartDate(), dot_dang_ky.getEndDate(), dot_dang_ky.getRegistrationDeadline());

            if (!expectedStatus.equalsIgnoreCase(currentStatus)) {
                log.info("Chuyển trạng thái đợt đăng ký {}: {} -> {}",
                        dot_dang_ky.getPeriodName(), currentStatus, expectedStatus);
                dot_dang_ky.setStatus(expectedStatus);
                periodRepository.save(dot_dang_ky);
                updatedCount++;
            }
        }

        log.info("Hoàn tất quét đợt đăng ký, đã cập nhật {} đợt", updatedCount);
        return updatedCount;
    }

    // Kiểm tra tính hợp lệ về ngày tháng, hạn nộp, trùng lặp và trạng thái đợt đăng ký
    private void validatePeriodRequest(RegistrationPeriod_Request request, String excludePeriodId, String currentStatus) {
        if (request.getStartDate() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày bắt đầu không được để trống");
        }
        if (request.getEndDate() == null) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày kết thúc không được để trống");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }

        // Kiểm tra hạn nộp hồ sơ đăng ký phải nằm trong khoảng thời gian của đợt
        if (request.getRegistrationDeadline() != null) {
            if (request.getRegistrationDeadline().isBefore(request.getStartDate())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn nộp hồ sơ đăng ký không thể trước ngày bắt đầu đợt");
            }
            if (request.getRegistrationDeadline().isAfter(request.getEndDate())) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn nộp hồ sơ đăng ký không thể sau ngày kết thúc đợt");
            }
        }

        // Kiểm tra đợt đăng ký bị trùng lặp thời gian trong cùng năm học
        String academicYear = (request.getAcademicYear() != null && !request.getAcademicYear().isBlank())
                ? request.getAcademicYear().trim()
                : null;
        if (periodRepository.hasOverlappingPeriod(request.getStartDate(), request.getEndDate(), academicYear, excludePeriodId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Thời gian đợt đăng ký bị trùng lặp với đợt đăng ký khác trong năm học " + (academicYear != null ? academicYear : ""));
        }

        // Kiểm tra và chuẩn hóa trạng thái hợp lệ
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String normalizedStatus = request.getStatus().trim().toUpperCase();
            if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Trạng thái đợt đăng ký '" + request.getStatus() + "' không hợp lệ. Cho phép: " + ALLOWED_STATUSES);
            }
            request.setStatus(normalizedStatus);
        } else if (currentStatus != null && !currentStatus.isBlank()) {
            // Giữ nguyên trạng thái hiện tại của đợt đăng ký nếu request không truyền (tránh tự mở lại đợt CANCELLED)
            request.setStatus(currentStatus);
        } else {
            // Đợt tạo mới không truyền trạng thái: tự động tính theo ngày
            request.setStatus(computeStatusByDate(request.getStartDate(), request.getEndDate(), request.getRegistrationDeadline()));
        }
    }

    // Tự động xác định trạng thái đợt đăng ký dựa trên ngày hiện tại
    private String computeStatusByDate(LocalDate startDate, LocalDate endDate, LocalDate deadline) {
        LocalDate today = LocalDate.now();
        // 1. Quá hạn chót nộp đơn đăng ký HOẶC đã qua ngày kết thúc kỳ ở -> CLOSED
        if (today.isAfter(endDate) || (deadline != null && today.isAfter(deadline))) {
            return "CLOSED";
        }
        // 2. Chưa tới ngày bắt đầu (và chưa hết hạn đăng ký) -> UPCOMING
        if (today.isBefore(startDate)) {
            return "UPCOMING";
        }
        return "OPEN";
    }
}

