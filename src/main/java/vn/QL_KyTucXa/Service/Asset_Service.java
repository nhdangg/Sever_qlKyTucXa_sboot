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
import vn.QL_KyTucXa.DTO.Request.Asset_Request;
import vn.QL_KyTucXa.DTO.Response.Asset_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Asset;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Asset_Mapper;
import vn.QL_KyTucXa.Repository.Asset_Repository;
import vn.QL_KyTucXa.Repository.RoomAsset_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Asset_Service {

    Asset_Repository assetRepository;
    RoomAsset_Repository roomAssetRepository;
    Asset_Mapper assetMapper;

    static Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "IN_STOCK", "MAINTENANCE", "DISCONTINUED", "LIQUIDATED");
    static Set<String> ALLOWED_SORT_FIELDS = Set.of("assetId", "assetCode", "assetName", "category", "unitPrice",
            "purchaseDate", "warrantyUntil", "status", "createdAt");

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "ACTIVE";
        }
        String s = rawStatus.trim().toUpperCase();
        if ("IN_USE".equals(s) || "GOOD".equals(s)) {
            s = "ACTIVE";
        } else if ("BROKEN".equals(s)) {
            s = "MAINTENANCE";
        }
        if (!ALLOWED_STATUSES.contains(s)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Trạng thái tài sản không hợp lệ. Cho phép: " + String.join(", ", ALLOWED_STATUSES));
        }
        return s;
    }

    @Transactional
    public Asset_Response create(Asset_Request request) {
        log.info("Thêm tài sản mới: {}", request.getAssetCode());

        if (assetRepository.existsByAssetCode(request.getAssetCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã tài sản '" + request.getAssetCode() + "' đã tồn tại");
        }

        if (request.getWarrantyUntil() != null && request.getPurchaseDate() != null
                && request.getWarrantyUntil().isBefore(request.getPurchaseDate())) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn bảo hành không được trước ngày mua");
        }

        String normalizedStatus = normalizeStatus(request.getStatus());

        Asset tai_san = assetMapper.toEntity(request);
        tai_san.setStatus(normalizedStatus);
        tai_san = assetRepository.save(tai_san);

        return assetMapper.toResponse(tai_san);
    }

    @Transactional(readOnly = true)
    public Asset_Response getById(String assetId) {
        Asset tai_san = assetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));

        return assetMapper.toResponse(tai_san);
    }

    @Transactional(readOnly = true)
    public List<Asset_Response> getByName(String assetName) {
        if (assetName == null || assetName.trim().isEmpty()) {
            return getAll();
        }
        List<Asset> danh_sach_tai_san = assetRepository.findByAssetNameContainingIgnoreCase(assetName.trim());
        return assetMapper.toResponseList(danh_sach_tai_san);
    }

    @Transactional(readOnly = true)
    public List<Asset_Response> getByStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return getAll();
        }
        String normalizedStatus = normalizeStatus(status);
        List<Asset> danh_sach_tai_san = assetRepository.findByStatus(normalizedStatus);
        return assetMapper.toResponseList(danh_sach_tai_san);
    }

    @Transactional(readOnly = true)
    public List<Asset_Response> getByCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return getAll();
        }
        List<Asset> danh_sach_tai_san = assetRepository.findByCategoryContainingIgnoreCase(category.trim());
        return assetMapper.toResponseList(danh_sach_tai_san);
    }

    @Transactional(readOnly = true)
    public List<Asset_Response> getByAssetCode(String assetCode) {
        if (assetCode == null || assetCode.trim().isEmpty()) {
            return getAll();
        }
        List<Asset> danh_sach_tai_san = assetRepository.findByAssetCodeContainingIgnoreCase(assetCode.trim());
        return assetMapper.toResponseList(danh_sach_tai_san);
    }

    @Transactional(readOnly = true)
    public List<Asset_Response> getAll() {
        List<Asset> danh_sach_tai_san = assetRepository.findAll();
        return assetMapper.toResponseList(danh_sach_tai_san);
    }

    @Transactional(readOnly = true)
    public Page_Response<Asset_Response> getAll(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int pageIndex = Math.max(page - 1, 0);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(pageIndex, safeSize, sort);

        Page<Asset> trang_tai_san = assetRepository.findAll(pageable);
        List<Asset_Response> data = assetMapper.toResponseList(trang_tai_san.getContent());

        return Page_Response.<Asset_Response>builder()
                .currentPage(trang_tai_san.getNumber() + 1)
                .pageSize(safeSize)
                .totalPages(trang_tai_san.getTotalPages())
                .totalElements(trang_tai_san.getTotalElements())
                .hasPrevious(trang_tai_san.hasPrevious())
                .hasNext(trang_tai_san.hasNext())
                .isFirst(trang_tai_san.isFirst())
                .isLast(trang_tai_san.isLast())
                .data(data)
                .build();
    }

    @Transactional
    public Asset_Response update(String assetId, Asset_Request request) {
        Asset tai_san = assetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));

        if (!tai_san.getAssetCode().equalsIgnoreCase(request.getAssetCode()) &&
                assetRepository.existsByAssetCode(request.getAssetCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã tài sản '" + request.getAssetCode() + "' đã tồn tại");
        }

        LocalDate targetPurchaseDate = request.getPurchaseDate() != null ? request.getPurchaseDate()
                : tai_san.getPurchaseDate();
        LocalDate targetWarrantyUntil = request.getWarrantyUntil() != null ? request.getWarrantyUntil()
                : tai_san.getWarrantyUntil();
        if (targetWarrantyUntil != null && targetPurchaseDate != null
                && targetWarrantyUntil.isBefore(targetPurchaseDate)) {
            throw new AppException(ErrorCode.OPERATION_FAILED, "Hạn bảo hành không được trước ngày mua");
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String normalizedStatus = normalizeStatus(request.getStatus());
            request.setStatus(normalizedStatus);
            tai_san.setStatus(normalizedStatus);
        }

        assetMapper.updateEntityFromRequest(tai_san, request);
        tai_san = assetRepository.save(tai_san);

        return assetMapper.toResponse(tai_san);
    }

    // Xóa tài sản: kiểm tra tồn tại và chặn xóa nếu tài sản đã được phân bổ cho
    // phòng
    @Transactional
    public void delete(String assetId) {
        Asset tai_san = assetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài sản"));

        if (roomAssetRepository.existsByAsset_AssetId(assetId)) {
            throw new AppException(ErrorCode.ASSET_IN_USE);
        }

        assetRepository.delete(tai_san);
    }
}
