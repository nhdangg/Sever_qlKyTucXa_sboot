package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.ViolationType_Request;
import vn.QL_KyTucXa.DTO.Response.ViolationType_Response;
import vn.QL_KyTucXa.Entity.ViolationType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.ViolationType_Mapper;
import vn.QL_KyTucXa.Repository.ViolationType_Repository;
import vn.QL_KyTucXa.Repository.Violation_Repository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViolationType_Service {

    ViolationType_Repository violationTypeRepository;
    Violation_Repository violationRepository;
    ViolationType_Mapper violationTypeMapper;
    CurrentUser_Service currentUserService;

    // Kiểm tra quyền ghi danh mục chỉ dành cho ADMIN hoặc MANAGER
    private void checkAdminOrManager() {
        if (currentUserService != null && !currentUserService.hasRole("ADMIN") && !currentUserService.hasRole("MANAGER")) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Chỉ Quản trị viên hoặc Quản lý KTX mới có quyền thao tác danh mục loại vi phạm");
        }
    }

    // Chuẩn hóa và kiểm tra mức độ nghiêm trọng thuộc tập cố định (MINOR, MEDIUM, SERIOUS)
    private String normalizeSeverityLevel(String level) {
        if (level == null || level.isBlank()) {
            return "MINOR";
        }
        String upper = level.trim().toUpperCase();
        return switch (upper) {
            case "SERIOUS", "NGHIEM_TRONG" -> "SERIOUS";
            case "MEDIUM", "TRUNG_BINH" -> "MEDIUM";
            case "MINOR", "NHE" -> "MINOR";
            default -> throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mức độ nghiêm trọng không hợp lệ. Chỉ chấp nhận: MINOR, MEDIUM, SERIOUS");
        };
    }

    @Transactional
    public ViolationType_Response create(ViolationType_Request request) {
        checkAdminOrManager();

        if (request.getViolationName() == null || request.getViolationName().isBlank()) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Tên loại vi phạm không được để trống");
        }
        String name = request.getViolationName().trim();
        log.info("Tạo mới loại vi phạm: {}", name);

        // Kiểm tra tránh trùng lặp tên loại vi phạm
        if (violationTypeRepository.existsByViolationName(name)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Tên loại vi phạm '" + name + "' đã tồn tại");
        }

        // Chặn tiền phạt và điểm trừ âm
        if (request.getFineAmount() != null && request.getFineAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.PRICE_INVALID, "Tiền phạt không thể âm");
        }
        if (request.getPenaltyPoints() != null && request.getPenaltyPoints() < 0) {
            throw new AppException(ErrorCode.PENALTY_POINTS_INVALID, "Điểm trừ không thể âm");
        }

        ViolationType loai_vi_pham = violationTypeMapper.toEntity(request);
        loai_vi_pham.setViolationName(name);
        loai_vi_pham.setSeverityLevel(normalizeSeverityLevel(request.getSeverityLevel()));

        loai_vi_pham = violationTypeRepository.save(loai_vi_pham);
        return violationTypeMapper.toResponse(loai_vi_pham);
    }

    @Transactional(readOnly = true)
    public ViolationType_Response getById(String violationTypeId) {
        ViolationType loai_vi_pham = violationTypeRepository.findById(violationTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại vi phạm"));

        return violationTypeMapper.toResponse(loai_vi_pham);
    }

    @Transactional(readOnly = true)
    public List<ViolationType_Response> getAll() {
        List<ViolationType> danh_sach_loai_vi_pham = violationTypeRepository.findAll();
        return violationTypeMapper.toResponseList(danh_sach_loai_vi_pham);
    }

    @Transactional(readOnly = true)
    public List<ViolationType_Response> getBySeverityLevel(String severityLevel) {
        // Nếu severityLevel là null hoặc rỗng, trả về tất cả danh mục thay vì ngầm định MINOR
        if (severityLevel == null || severityLevel.isBlank()) {
            return getAll();
        }
        String normalized = normalizeSeverityLevel(severityLevel);
        List<ViolationType> danh_sach_loai_vi_pham = violationTypeRepository.findBySeverityLevel(normalized);
        return violationTypeMapper.toResponseList(danh_sach_loai_vi_pham);
    }

    @Transactional
    public ViolationType_Response update(String violationTypeId, ViolationType_Request request) {
        checkAdminOrManager();

        ViolationType loai_vi_pham = violationTypeRepository.findById(violationTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại vi phạm"));

        if (request.getViolationName() != null && !request.getViolationName().isBlank()) {
            String name = request.getViolationName().trim();
            if (violationTypeRepository.existsByViolationNameAndViolationTypeIdNot(name, violationTypeId)) {
                throw new AppException(ErrorCode.OPERATION_FAILED, "Tên loại vi phạm '" + name + "' đã tồn tại");
            }
            loai_vi_pham.setViolationName(name);
        }

        if (request.getSeverityLevel() != null && !request.getSeverityLevel().isBlank()) {
            loai_vi_pham.setSeverityLevel(normalizeSeverityLevel(request.getSeverityLevel()));
        }

        // Chặn tiền phạt và điểm trừ âm
        if (request.getPenaltyPoints() != null) {
            if (request.getPenaltyPoints() < 0) {
                throw new AppException(ErrorCode.PENALTY_POINTS_INVALID, "Điểm trừ không thể âm");
            }
            loai_vi_pham.setPenaltyPoints(request.getPenaltyPoints());
        }
        if (request.getFineAmount() != null) {
            if (request.getFineAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new AppException(ErrorCode.PRICE_INVALID, "Tiền phạt không thể âm");
            }
            loai_vi_pham.setFineAmount(request.getFineAmount());
        }
        if (request.getDescription() != null) {
            loai_vi_pham.setDescription(request.getDescription());
        }

        loai_vi_pham = violationTypeRepository.save(loai_vi_pham);
        return violationTypeMapper.toResponse(loai_vi_pham);
    }

    @Transactional
    public void delete(String violationTypeId) {
        checkAdminOrManager();

        ViolationType loai_vi_pham = violationTypeRepository.findById(violationTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại vi phạm"));

        // Chặn xóa nếu loại vi phạm đã được sử dụng trong các biên bản kỷ luật để tránh lỗi ràng buộc khóa ngoại
        if (violationRepository.existsByViolationType_ViolationTypeId(violationTypeId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể xóa loại vi phạm vì đã có biên bản kỷ luật áp dụng loại này");
        }

        violationTypeRepository.delete(loai_vi_pham);
    }
}
