package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.VehicleRegistration_Request;
import vn.QL_KyTucXa.DTO.Response.VehicleRegistration_Response;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.VehicleRegistration;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.VehicleRegistration_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.VehicleRegistration_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleRegistration_ServiceTest {

    @Mock
    private VehicleRegistration_Repository vehicleRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private SystemSetting_Service systemSettingService;
    @Mock
    private VehicleRegistration_Mapper vehicleMapper;

    @InjectMocks
    private VehicleRegistration_Service vehicleService;

    private Student student;
    private VehicleRegistration vehicle;
    private VehicleRegistration_Request request;
    private VehicleRegistration_Response response;

    @BeforeEach
    void setUp() {
        student = Student.builder().studentId("st-01").studentCode("SV001").fullName("Nguyễn Văn A").build();
        vehicle = VehicleRegistration.builder()
                .vehicleId("veh-01")
                .student(student)
                .vehicleType("MOTORBIKE")
                .licensePlate("29A112345")
                .brandModel("Honda Wave")
                .monthlyFee(new BigDecimal("100000"))
                .status("PENDING")
                .registrationDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusMonths(6))
                .build();

        request = VehicleRegistration_Request.builder()
                .studentId("st-01")
                .vehicleType("MOTORBIKE")
                .licensePlate("29-A1 123.45")
                .brandModel("Honda Wave")
                .build();

        response = VehicleRegistration_Response.builder()
                .vehicleId("veh-01")
                .studentId("st-01")
                .licensePlate("29A112345")
                .status("PENDING")
                .build();
    }

    @Test
    @DisplayName("Đăng ký xe thành công")
    void create_success() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(true);
        when(systemSettingService.getIntSetting(eq("MAX_VEHICLES_PER_STUDENT"), anyInt())).thenReturn(1);
        when(vehicleRepository.countByStudent_StudentIdAndStatusIn(eq("st-01"), any())).thenReturn(0L);
        when(vehicleRepository.findByLicensePlate("29A112345")).thenReturn(List.of());
        when(systemSettingService.getDecimalSetting(eq("PARKING_FEE_MOTORBIKE"), any(BigDecimal.class))).thenReturn(new BigDecimal("100000"));
        when(contractRepository.findByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(List.of(
                Contract.builder().endDate(LocalDate.now().plusMonths(5)).build()
        ));
        when(vehicleRepository.save(any(VehicleRegistration.class))).thenReturn(vehicle);
        when(vehicleMapper.toResponse(any(VehicleRegistration.class))).thenReturn(response);

        VehicleRegistration_Response res = vehicleService.create(request);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo("PENDING");
        verify(vehicleRepository).save(any(VehicleRegistration.class));
    }

    @Test
    @DisplayName("Đăng ký xe thất bại khi sinh viên không có hợp đồng ACTIVE")
    void create_failed_noActiveContract() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Đăng ký xe thất bại khi biển số trùng lặp đang hoạt động")
    void create_failed_duplicatePlate() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(true);
        when(systemSettingService.getIntSetting(eq("MAX_VEHICLES_PER_STUDENT"), anyInt())).thenReturn(1);
        when(vehicleRepository.countByStudent_StudentIdAndStatusIn(eq("st-01"), any())).thenReturn(0L);
        when(vehicleRepository.findByLicensePlate("29A112345")).thenReturn(List.of(
                VehicleRegistration.builder().status("ACTIVE").build()
        ));

        AppException ex = assertThrows(AppException.class, () -> vehicleService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Duyệt đăng ký xe thành công")
    void approve_success() {
        when(vehicleRepository.findById("veh-01")).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(VehicleRegistration.class))).thenReturn(vehicle);
        when(vehicleMapper.toResponse(any(VehicleRegistration.class))).thenReturn(response);

        VehicleRegistration_Response res = vehicleService.approve("veh-01", true);

        assertThat(res).isNotNull();
        assertThat(vehicle.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Xóa xe chuyển trạng thái CANCELLED")
    void delete_softDelete() {
        when(vehicleRepository.findById("veh-01")).thenReturn(Optional.of(vehicle));
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);

        vehicleService.delete("veh-01");

        assertThat(vehicle.getStatus()).isEqualTo("CANCELLED");
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    @DisplayName("Cập nhật trạng thái xe thất bại khi truyền trạng thái không hợp lệ")
    void updateStatus_invalidStatus_throwsException() {
        VehicleRegistration_Request updateReq = VehicleRegistration_Request.builder()
                .status("INVALID_STATUS")
                .build();
        when(vehicleRepository.findById("veh-01")).thenReturn(Optional.of(vehicle));
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.update("veh-01", updateReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tự động quét và đánh dấu EXPIRED cho các xe ACTIVE đã quá hạn")
    void autoExpireVehicles_success() {
        VehicleRegistration expiredVehicle = VehicleRegistration.builder()
                .vehicleId("v-exp")
                .status("ACTIVE")
                .expiryDate(LocalDate.now().minusDays(1))
                .build();

        when(vehicleRepository.findByStatusAndExpiryDateBefore(eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of(expiredVehicle));

        vehicleService.autoExpireVehicles();

        assertThat(expiredVehicle.getStatus()).isEqualTo("EXPIRED");
        verify(vehicleRepository).saveAll(List.of(expiredVehicle));
    }
}
