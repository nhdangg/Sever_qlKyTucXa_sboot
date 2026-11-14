package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.QL_KyTucXa.DTO.Request.MaintenanceRequest_Request;
import vn.QL_KyTucXa.DTO.Response.MaintenanceRequest_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.*;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.MaintenanceRequest_Mapper;
import vn.QL_KyTucXa.Repository.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceRequest_ServiceTest {

    @Mock
    private MaintenanceRequest_Repository maintenanceRepository;
    @Mock
    private Room_Repository roomRepository;
    @Mock
    private User_Repository userRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private MaintenanceRequest_Mapper maintenanceMapper;

    @InjectMocks
    private MaintenanceRequest_Service maintenanceService;

    private Room room;
    private User user;
    private Staff staff;
    private MaintenanceRequest maintenance;
    private MaintenanceRequest_Request request;
    private MaintenanceRequest_Response response;

    @BeforeEach
    void setUp() {
        room = Room.builder().roomId("room-01").roomNumber("101").build();
        user = User.builder().userId("user-01").username("student01").build();
        staff = Staff.builder().staffId("staff-01").fullName("Kỹ thuật viên").build();

        maintenance = MaintenanceRequest.builder()
                .requestId("req-01")
                .requestCode("YCSC-001")
                .room(room)
                .reportedBy(user)
                .description("Hỏng quạt trần")
                .priority("MEDIUM")
                .status("PENDING")
                .build();

        request = MaintenanceRequest_Request.builder()
                .requestCode("YCSC-001")
                .roomId("room-01")
                .reportedBy("user-01")
                .description("Hỏng quạt trần")
                .priority("MEDIUM")
                .status("PENDING")
                .build();

        response = MaintenanceRequest_Response.builder()
                .requestId("req-01")
                .requestCode("YCSC-001")
                .roomId("room-01")
                .roomNumber("101")
                .description("Hỏng quạt trần")
                .priority("MEDIUM")
                .status("PENDING")
                .build();
    }

    @Test
    @DisplayName("Tạo yêu cầu bảo trì thành công với mã yêu cầu và phòng cung cấp sẵn")
    void create_success() {
        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(maintenanceRepository.existsByRequestCode("YCSC-001")).thenReturn(false);
        when(maintenanceMapper.toEntity(request)).thenReturn(maintenance);
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getRequestCode()).isEqualTo("YCSC-001");
        verify(maintenanceRepository).save(maintenance);
    }

    @Test
    @DisplayName("Tự động sinh mã yêu cầu khi client không truyền mã")
    void create_autoGenerateRequestCode_whenCodeIsBlank() {
        request.setRequestCode(null);
        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(maintenanceRepository.existsByRequestCode(anyString())).thenReturn(false);
        when(maintenanceMapper.toEntity(request)).thenReturn(maintenance);
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.create(request);

        assertThat(result).isNotNull();
        verify(maintenanceRepository).save(any(MaintenanceRequest.class));
    }

    @Test
    @DisplayName("Tạo yêu cầu bảo trì thất bại khi mã yêu cầu đã tồn tại")
    void create_failed_duplicateRequestCode() {
        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(maintenanceRepository.existsByRequestCode("YCSC-001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tự động tìm phòng từ hợp đồng ACTIVE của sinh viên khi không truyền roomId")
    void create_resolveRoomFromActiveContract_success() {
        request.setRoomId(null);
        Student student = Student.builder().studentId("stu-01").build();
        Bed bed = Bed.builder().bedId("bed-01").room(room).build();
        Contract activeContract = Contract.builder().contractId("c-01").bed(bed).status("ACTIVE").build();

        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(studentRepository.findByUser_UserId("user-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentId("stu-01")).thenReturn(List.of(activeContract));
        when(maintenanceRepository.existsByRequestCode("YCSC-001")).thenReturn(false);
        when(maintenanceMapper.toEntity(request)).thenReturn(maintenance);
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.create(request);

        assertThat(result).isNotNull();
        verify(maintenanceRepository).save(maintenance);
    }

    @Test
    @DisplayName("Tạo yêu cầu bảo trì thất bại khi không truyền roomId và sinh viên không có hợp đồng ACTIVE")
    void create_failed_noActiveContract() {
        request.setRoomId(null);
        Student student = Student.builder().studentId("stu-01").build();
        Contract expiredContract = Contract.builder().contractId("c-01").status("TERMINATED").build();

        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(studentRepository.findByUser_UserId("user-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentId("stu-01")).thenReturn(List.of(expiredContract));

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo yêu cầu bảo trì thất bại khi mức ưu tiên không hợp lệ")
    void create_failed_invalidPriority() {
        request.setPriority("SUPER_HIGH");
        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(maintenanceRepository.existsByRequestCode("YCSC-001")).thenReturn(false);
        when(maintenanceMapper.toEntity(request)).thenReturn(maintenance);

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Mức ưu tiên NORMAL tự động chuyển đổi thành MEDIUM")
    void create_normalPriority_mapsToMedium() {
        request.setPriority("NORMAL");
        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(maintenanceRepository.existsByRequestCode("YCSC-001")).thenReturn(false);
        when(maintenanceMapper.toEntity(request)).thenReturn(maintenance);
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.create(request);

        assertThat(result).isNotNull();
        assertThat(maintenance.getPriority()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Tạo yêu cầu bảo trì thất bại khi phòng không tồn tại")
    void create_failed_roomNotFound() {
        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(roomRepository.findById("room-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Phân công nhân viên xử lý thành công")
    void assignStaff_success() {
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));
        when(staffRepository.findById("staff-01")).thenReturn(Optional.of(staff));
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.assignStaff("req-01", "staff-01");

        assertThat(result).isNotNull();
        assertThat(maintenance.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(maintenance.getAssignedTo()).isEqualTo(staff);
    }

    @Test
    @DisplayName("Phân công nhân viên thất bại khi yêu cầu đã hoàn thành")
    void assignStaff_failed_alreadyCompleted() {
        maintenance.setStatus("COMPLETED");
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.assignStaff("req-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Hoàn thành yêu cầu bảo trì thành công khi đã phân công nhân viên")
    void complete_success() {
        maintenance.setAssignedTo(staff);
        maintenance.setStatus("IN_PROGRESS");
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.complete("req-01", "Đã thay thế linh kiện mới");

        assertThat(result).isNotNull();
        assertThat(maintenance.getStatus()).isEqualTo("COMPLETED");
        assertThat(maintenance.getResolutionNote()).isEqualTo("Đã thay thế linh kiện mới");
        assertThat(maintenance.getCompletedDate()).isNotNull();
    }

    @Test
    @DisplayName("Hoàn thành yêu cầu thất bại khi chưa được phân công nhân sự")
    void complete_failed_notAssigned() {
        maintenance.setAssignedTo(null);
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.complete("req-01", "Ghi chú"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Hoàn thành yêu cầu thất bại khi yêu cầu đã hoàn thành trước đó")
    void complete_failed_alreadyCompleted() {
        maintenance.setAssignedTo(staff);
        maintenance.setStatus("COMPLETED");
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.complete("req-01", "Ghi chú"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Lấy danh sách yêu cầu theo ID người báo cáo")
    void getByReporterUserId_success() {
        when(maintenanceRepository.findByReportedBy_UserId("user-01")).thenReturn(List.of(maintenance));
        when(maintenanceMapper.toResponseList(List.of(maintenance))).thenReturn(List.of(response));

        List<MaintenanceRequest_Response> result = maintenanceService.getByReporterUserId("user-01");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Tìm kiếm yêu cầu bảo trì với các tiêu chí lọc")
    void getAll_withSearchCriteria() {
        Page<MaintenanceRequest> page = new PageImpl<>(List.of(maintenance));
        when(maintenanceRepository.searchMaintenanceRequests(eq("quạt"), eq("PENDING"), eq("MEDIUM"), eq("101"), eq("DIEN"), any(Pageable.class)))
                .thenReturn(page);
        when(maintenanceMapper.toResponseList(List.of(maintenance))).thenReturn(List.of(response));

        Page_Response<MaintenanceRequest_Response> result = maintenanceService.getAll(
                1, 10, "quạt", "PENDING", "MEDIUM", "101", "DIEN", "requestedDate", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Lấy yêu cầu bảo trì theo ID thành công")
    void getById_success() {
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response result = maintenanceService.getById("req-01");

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isEqualTo("req-01");
    }

    @Test
    @DisplayName("Xóa yêu cầu bảo trì thành công")
    void delete_success() {
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));

        maintenanceService.delete("req-01");

        verify(maintenanceRepository).delete(maintenance);
    }

    @Test
    @DisplayName("Sinh viên gửi yêu cầu sửa chữa phòng không thuộc hợp đồng của mình: bị chặn OPERATION_FAILED")
    void create_studentPickingDifferentRoom_throwsException() {
        request.setRoomId("room-99");
        Student student = Student.builder().studentId("stu-01").build();
        Bed myBed = Bed.builder().bedId("b-01").room(room).build();
        Contract myContract = Contract.builder().contractId("c-01").bed(myBed).status("ACTIVE").build();

        when(currentUserService.userId()).thenReturn("user-01");
        when(userRepository.findById("user-01")).thenReturn(Optional.of(user));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(studentRepository.findByUser_UserId("user-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentId("stu-01")).thenReturn(List.of(myContract));

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Sinh viên chỉ được yêu cầu sửa chữa cho đúng phòng mình đang ở");
    }

    @Test
    @DisplayName("Hủy yêu cầu sửa chữa thành công khi đang ở trạng thái PENDING")
    void cancel_success() {
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.userId()).thenReturn("user-01");
        when(maintenanceRepository.save(any(MaintenanceRequest.class))).thenReturn(maintenance);
        when(maintenanceMapper.toResponse(maintenance)).thenReturn(response);

        MaintenanceRequest_Response res = maintenanceService.cancel("req-01", "Tôi tự khắc phục được rồi");

        assertThat(res).isNotNull();
        assertThat(maintenance.getStatus()).isEqualTo("CANCELLED");
        assertThat(maintenance.getResolutionNote()).contains("Tôi tự khắc phục được rồi");
    }

    @Test
    @DisplayName("Hủy yêu cầu sửa chữa thất bại khi yêu cầu đã hoàn thành")
    void cancel_failed_alreadyCompleted() {
        maintenance.setStatus("COMPLETED");
        when(maintenanceRepository.findById("req-01")).thenReturn(Optional.of(maintenance));

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.cancel("req-01", "Lý do"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Sinh viên xem danh sách yêu cầu của người dùng khác: bị chặn UNAUTHORIZED")
    void getByReporterUserId_unauthorizedForOtherUser() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.userId()).thenReturn("user-01");

        AppException ex = assertThrows(AppException.class, () -> maintenanceService.getByReporterUserId("user-other"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
