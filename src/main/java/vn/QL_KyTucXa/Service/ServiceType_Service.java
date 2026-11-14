package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Request.ServiceType_Request;
import vn.QL_KyTucXa.DTO.Response.ServiceType_Response;
import vn.QL_KyTucXa.Entity.ServiceType;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.ServiceType_Mapper;
import vn.QL_KyTucXa.Repository.ServiceType_Repository;
import vn.QL_KyTucXa.Repository.UtilityReading_Repository;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceType_Service {

    ServiceType_Repository serviceTypeRepository;
    ServiceType_Mapper serviceTypeMapper;
    UtilityReading_Repository utilityReadingRepository;

    public static final Set<String> BillingType = Set.of("METERED", "FLAT", "PER_PERSON");

    @Transactional
    public ServiceType_Response create(ServiceType_Request request) {
        log.info("Tạo mới loại dịch vụ: {}", request.getServiceCode());

        if (serviceTypeRepository.existsByServiceCode(request.getServiceCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã dịch vụ '" + request.getServiceCode() + "' đã tồn tại");
        }
        if (request.getBillingType() == null || request.getBillingType().isBlank()) {
            request.setBillingType("METERED");
        } else {
            String normalizedBillingType = request.getBillingType().trim().toUpperCase();
            if (!BillingType.contains(normalizedBillingType)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không tìm thấy phương thức tính phí '" + request.getBillingType() + "'");
            }
            request.setBillingType(normalizedBillingType);
        }

        ServiceType serviceType = serviceTypeMapper.toEntity(request);
        serviceType = serviceTypeRepository.save(serviceType);
        return serviceTypeMapper.toResponse(serviceType);
    }

    @Transactional(readOnly = true)
    public ServiceType_Response getById(String serviceTypeId) {
        ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại dịch vụ"));

        return serviceTypeMapper.toResponse(serviceType);
    }

    @Transactional(readOnly = true)
    public List<ServiceType_Response> getAll() {
        List<ServiceType> list = serviceTypeRepository.findAll();
        return serviceTypeMapper.toResponseList(list);
    }

    @Transactional
    public ServiceType_Response update(String serviceTypeId, ServiceType_Request request) {
        ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại dịch vụ"));

        if (!serviceType.getServiceCode().equalsIgnoreCase(request.getServiceCode()) &&
                serviceTypeRepository.existsByServiceCode(request.getServiceCode())) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Mã dịch vụ '" + request.getServiceCode() + "' đã tồn tại");
        }

        if (request.getBillingType() != null && !request.getBillingType().isBlank()) {
            String normalizedBillingType = request.getBillingType().trim().toUpperCase();
            if (!BillingType.contains(normalizedBillingType)) {
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không tìm thấy phương thức tính phí '" + request.getBillingType() + "'");
            }
            request.setBillingType(normalizedBillingType);
        } else {
            request.setBillingType(null);
        }

        serviceTypeMapper.updateEntityFromRequest(serviceType, request);
        serviceType = serviceTypeRepository.save(serviceType);
        return serviceTypeMapper.toResponse(serviceType);
    }

    @Transactional
    public void delete(String serviceTypeId) {
        ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại dịch vụ"));

        if (utilityReadingRepository.existsByServiceType_ServiceTypeId(serviceTypeId)) {
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Không thể xóa loại dịch vụ này vì đã có dữ liệu ghi chỉ số điện nước liên quan");
        }

        serviceTypeRepository.delete(serviceType);
    }
}
