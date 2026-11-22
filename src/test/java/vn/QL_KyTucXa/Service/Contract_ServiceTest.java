package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.Contract_Request;
import vn.QL_KyTucXa.DTO.Response.Contract_Response;
import vn.QL_KyTucXa.Entity.Bed;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.RegistrationPeriod;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Contract_Mapper;
import vn.QL_KyTucXa.Repository.Bed_Repository;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.RegistrationPeriod_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Contract_ServiceTest {

    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private Bed_Repository bedRepository;
    @Mock
    private vn.QL_KyTucXa.Repository.Room_Repository roomRepository;
    @Mock
    private RegistrationPeriod_Repository periodRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private Contract_Mapper contractMapper;
    @Mock
    private SystemSetting_Service systemSettingService;
    @Mock
    private vn.QL_KyTucXa.Repository.Invoice_Repository invoiceRepository;
    @Mock
    private vn.QL_KyTucXa.Repository.VehicleRegistration_Repository vehicleRegistrationRepository;
    @Mock
    private vn.QL_KyTucXa.Repository.RoomTransfer_Repository roomTransferRepository;
    @Mock
    private CurrentUser_Service currentUserService;

    @InjectMocks
    private Contract_Service contractService;

    private Student student;
    private Bed bed;
    private RegistrationPeriod period;
    private Staff staff;
    private Contract contract;
    private Contract_Request request;
    private Contract_Response response;

    @BeforeEach
    void setUp() {
        student = Student.builder().studentId("stu-01").studentCode("SV01").build();
        bed = Bed.builder().bedId("bed-01").bedNumber("G01").status("AVAILABLE").build();
        period = RegistrationPeriod.builder().periodId("period-01").periodName("Học kỳ 1").status("OPEN").build();
        staff = Staff.builder().staffId("staff-01").staffCode("NV01").build();

        contract = Contract.builder()
                .contractId("hd-01")
                .contractCode("HD2026-001")
                .student(student)
                .bed(bed)
                .period(period)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .monthlyFee(BigDecimal.valueOf(1500000))
                .status("PENDING")
                .build();

        request = Contract_Request.builder()
                .contractCode("HD2026-001")
                .studentId("stu-01")
                .bedId("bed-01")
                .periodId("period-01")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .monthlyFee(BigDecimal.valueOf(1500000))
                .build();

        response = Contract_Response.builder()
                .contractId("hd-01")
                .contractCode("HD2026-001")
                .status("PENDING")
                .build();

        lenient().when(bedRepository.findByIdForUpdate(anyString())).thenReturn(Optional.of(bed));
    }

    @Test
    @DisplayName("Tạo hợp đồng thành công với trạng thái PENDING và không đổi trạng thái giường")
    void create_success() {
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(contractRepository.hasOverlappingActiveContract("bed-01", request.getStartDate(), request.getEndDate(), null)).thenReturn(false);
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(contractMapper.toEntity(request)).thenReturn(contract);
        when(contractRepository.save(any(Contract.class))).thenReturn(contract);
        when(contractMapper.toResponse(contract)).thenReturn(response);

        Contract_Response result = contractService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getContractCode()).isEqualTo("HD2026-001");
        assertThat(contract.getStatus()).isEqualTo("PENDING");
        verify(bedRepository).save(bed);
        assertThat(bed.getStatus()).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi mã hợp đồng đã tồn tại")
    void create_failed_duplicateContractCode() {
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi ngày kết thúc không sau ngày bắt đầu")
    void create_failed_invalidDates() {
        request.setEndDate(request.getStartDate());
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi không tìm thấy sinh viên")
    void create_failed_studentNotFound() {
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi sinh viên đã có hợp đồng ACTIVE")
    void create_failed_studentAlreadyHasActiveContract() {
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi giường đã có người ở (OCCUPIED)")
    void create_failed_bedAlreadyOccupied() {
        bed.setStatus("OCCUPIED");
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BED_ALREADY_OCCUPIED);
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi giới tính sinh viên không phù hợp với tòa nhà")
    void create_failed_genderMismatch() {
        student.setGender("MALE");
        vn.QL_KyTucXa.Entity.Building femaleBuilding = vn.QL_KyTucXa.Entity.Building.builder()
                .buildingName("Tòa Nữ B")
                .genderType("FEMALE")
                .build();
        vn.QL_KyTucXa.Entity.Floor floor = vn.QL_KyTucXa.Entity.Floor.builder().building(femaleBuilding).build();
        vn.QL_KyTucXa.Entity.Room room = vn.QL_KyTucXa.Entity.Room.builder().floor(floor).status("AVAILABLE").build();
        bed.setRoom(room);

        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("chỉ dành cho sinh viên Nữ");
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi phòng đang bảo trì (MAINTENANCE)")
    void create_failed_roomInMaintenance() {
        vn.QL_KyTucXa.Entity.Room room = vn.QL_KyTucXa.Entity.Room.builder().roomNumber("P101").status("MAINTENANCE").build();
        bed.setRoom(room);

        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đang bảo trì");
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi giường đã có hợp đồng ACTIVE trùng thời gian")
    void create_failed_overlappingContractOnBed() {
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(contractRepository.hasOverlappingActiveContract("bed-01", request.getStartDate(), request.getEndDate(), null)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tạo hợp đồng thất bại khi đợt đăng ký không ở trạng thái OPEN")
    void create_failed_periodNotOpen() {
        period.setStatus("CLOSED");
        when(contractRepository.existsByContractCode("HD2026-001")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(contractRepository.hasOverlappingActiveContract("bed-01", request.getStartDate(), request.getEndDate(), null)).thenReturn(false);
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));

        AppException ex = assertThrows(AppException.class, () -> contractService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Phê duyệt hợp đồng thành công: chuyển contract sang ACTIVE và giường sang OCCUPIED")
    void approve_success() {
        contract.setStatus("PENDING");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(contractRepository.hasOverlappingActiveContract("bed-01", contract.getStartDate(), contract.getEndDate(), "hd-01")).thenReturn(false);
        when(staffRepository.findById("staff-01")).thenReturn(Optional.of(staff));
        when(contractRepository.save(contract)).thenReturn(contract);
        when(contractMapper.toResponse(contract)).thenReturn(response);

        Contract_Response result = contractService.approve("hd-01", "staff-01");

        assertThat(result).isNotNull();
        assertThat(contract.getStatus()).isEqualTo("ACTIVE");
        assertThat(contract.getApprover()).isEqualTo(staff);
        assertThat(contract.getSignedDate()).isEqualTo(LocalDate.now());
        assertThat(bed.getStatus()).isEqualTo("OCCUPIED");
        verify(bedRepository).save(bed);
    }

    @Test
    @DisplayName("Phê duyệt thất bại khi hợp đồng không ở trạng thái PENDING")
    void approve_failed_whenNotPending() {
        contract.setStatus("ACTIVE");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));

        AppException ex = assertThrows(AppException.class, () -> contractService.approve("hd-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Phê duyệt thất bại khi sinh viên đã có hợp đồng ACTIVE khác")
    void approve_failed_studentAlreadyHasActiveContract() {
        contract.setStatus("PENDING");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.approve("hd-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Phê duyệt thất bại khi giường đã bị OCCUPIED")
    void approve_failed_bedAlreadyOccupied() {
        contract.setStatus("PENDING");
        bed.setStatus("OCCUPIED");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> contractService.approve("hd-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BED_ALREADY_OCCUPIED);
    }

    @Test
    @DisplayName("Thanh lý hợp đồng thành công: chuyển contract sang TERMINATED và giường sang AVAILABLE")
    void terminate_success() {
        contract.setStatus("ACTIVE");
        bed.setStatus("OCCUPIED");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(contractRepository.save(contract)).thenReturn(contract);
        when(contractMapper.toResponse(contract)).thenReturn(response);

        Contract_Response result = contractService.terminate("hd-01", "Kết thúc thời hạn thuê");

        assertThat(result).isNotNull();
        assertThat(contract.getStatus()).isEqualTo("TERMINATED");
        assertThat(contract.getTerminationReason()).isEqualTo("Kết thúc thời hạn thuê");
        assertThat(contract.getTerminatedDate()).isEqualTo(LocalDate.now());
        assertThat(bed.getStatus()).isEqualTo("AVAILABLE");
        verify(bedRepository).save(bed);
    }

    @Test
    @DisplayName("Thanh lý hợp đồng thất bại khi hợp đồng không ở trạng thái ACTIVE")
    void terminate_failed_whenNotActive() {
        contract.setStatus("PENDING");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));

        AppException ex = assertThrows(AppException.class, () -> contractService.terminate("hd-01", "Lý do"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Thanh lý hợp đồng thất bại khi sinh viên còn hóa đơn chưa thanh toán hoàn tất")
    void terminate_failed_whenHasUnpaidInvoices() {
        contract.setStatus("ACTIVE");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsUnpaidInvoiceByStudentId("stu-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.terminate("hd-01", "Thanh lý"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("chưa thanh toán hoàn tất");
    }

    @Test
    @DisplayName("Tự động chuyển các hợp đồng hết hạn sang EXPIRED và trả giường AVAILABLE")
    void expireCompletedContracts_success() {
        contract.setStatus("ACTIVE");
        bed.setStatus("OCCUPIED");
        when(contractRepository.findByStatusAndEndDateBefore(eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of(contract));

        int expiredCount = contractService.expireCompletedContracts();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(contract.getStatus()).isEqualTo("EXPIRED");
        assertThat(bed.getStatus()).isEqualTo("AVAILABLE");
        verify(bedRepository).save(bed);
        verify(contractRepository).save(contract);
    }

    @Test
    @DisplayName("Lấy hợp đồng theo ID thành công")
    void getById_success() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(contractMapper.toResponse(contract)).thenReturn(response);

        Contract_Response result = contractService.getById("hd-01");

        assertThat(result).isNotNull();
        assertThat(result.getContractId()).isEqualTo("hd-01");
    }

    @Test
    @DisplayName("Lấy hợp đồng theo ID thất bại khi không tìm thấy")
    void getById_notFound() {
        when(contractRepository.findById("unknown-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> contractService.getById("unknown-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("Cập nhật hợp đồng thất bại khi hợp đồng đã thanh lý")
    void update_failed_whenTerminated() {
        contract.setStatus("TERMINATED");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));

        AppException ex = assertThrows(AppException.class, () -> contractService.update("hd-01", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa hợp đồng thành công và trả giường nếu đang ACTIVE")
    void delete_success() {
        contract.setStatus("ACTIVE");
        bed.setStatus("OCCUPIED");
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractId("hd-01")).thenReturn(false);
        when(roomTransferRepository.existsByContract_ContractId("hd-01")).thenReturn(false);

        contractService.delete("hd-01");

        assertThat(bed.getStatus()).isEqualTo("AVAILABLE");
        verify(bedRepository).save(bed);
        verify(contractRepository).delete(contract);
    }

    @Test
    @DisplayName("Xóa hợp đồng thất bại khi đã phát sinh hóa đơn")
    void delete_failed_whenInvoiceExists() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractId("hd-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.delete("hd-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Xóa hợp đồng thất bại khi đã có yêu cầu chuyển phòng")
    void delete_failed_whenRoomTransferExists() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByContract_ContractId("hd-01")).thenReturn(false);
        when(roomTransferRepository.existsByContract_ContractId("hd-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> contractService.delete("hd-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Lấy hợp đồng theo ID thất bại khi sinh viên xem hợp đồng của người khác")
    void getById_failed_unauthorizedForOtherStudent() {
        when(contractRepository.findById("hd-01")).thenReturn(Optional.of(contract));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> contractService.getById("hd-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lấy danh sách hợp đồng theo sinh viên thất bại khi sinh viên xem của người khác")
    void getByStudentId_failed_unauthorizedForOtherStudent() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("my-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> contractService.getByStudentId("other-stu-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Nộp đơn online thất bại khi phòng chưa được cấu hình đơn giá")
    void applyOnline_failed_missingRoomPrice() {
        bed.setRoom(null);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "PENDING")).thenReturn(false);
        when(periodRepository.findById("period-01")).thenReturn(Optional.of(period));
        when(bedRepository.findByIdForUpdate("bed-01")).thenReturn(Optional.of(bed));

        vn.QL_KyTucXa.DTO.Request.StudentContractApply_Request applyReq = vn.QL_KyTucXa.DTO.Request.StudentContractApply_Request.builder()
                .bedId("bed-01")
                .periodId("period-01")
                .build();

        AppException ex = assertThrows(AppException.class, () -> contractService.applyOnline("stu-01", applyReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }
}
