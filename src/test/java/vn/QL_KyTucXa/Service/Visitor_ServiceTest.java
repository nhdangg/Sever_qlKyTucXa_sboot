package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.Visitor_Request;
import vn.QL_KyTucXa.DTO.Response.Visitor_Response;
import vn.QL_KyTucXa.Entity.Bed;
import vn.QL_KyTucXa.Entity.Contract;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.Visitor;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Visitor_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.Visitor_Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Visitor_ServiceTest {

    @Mock
    private Visitor_Repository visitorRepository;
    @Mock
    private Student_Repository studentRepository;
    @Mock
    private Room_Repository roomRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private SystemSetting_Service systemSettingService;
    @Mock
    private Visitor_Mapper visitorMapper;

    @InjectMocks
    private Visitor_Service visitorService;

    private Student student;
    private Room room;
    private Contract contract;
    private Visitor visitor;
    private Visitor_Request request;
    private Visitor_Response response;

    @BeforeEach
    void setUp() {
        student = Student.builder().studentId("st-01").studentCode("SV001").fullName("Sinh Viên A").build();
        room = Room.builder().roomId("r-101").roomNumber("101").build();
        Bed bed = Bed.builder().bedId("bed-01").room(room).build();
        contract = Contract.builder().contractId("c-01").student(student).bed(bed).status("ACTIVE").build();

        visitor = Visitor.builder()
                .visitorId("vis-01")
                .visitorName("Nguyễn Phụ Huynh")
                .nationalId("001099123456")
                .phone("0912345678")
                .hostStudent(student)
                .room(room)
                .checkInTime(LocalDateTime.now())
                .build();

        request = Visitor_Request.builder()
                .visitorName("Nguyễn Phụ Huynh")
                .nationalId("001099123456")
                .phone("0912345678")
                .hostStudentId("st-01")
                .roomId("r-101")
                .build();

        response = Visitor_Response.builder()
                .visitorId("vis-01")
                .visitorName("Nguyễn Phụ Huynh")
                .nationalId("001099123456")
                .build();
    }

    @Test
    @DisplayName("Khách check-in thành công")
    void checkIn_success() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(List.of(contract));
        when(visitorRepository.existsByNationalIdAndCheckOutTimeIsNull("001099123456")).thenReturn(false);
        when(systemSettingService.getIntSetting(eq("MAX_CONCURRENT_VISITORS_PER_STUDENT"), anyInt())).thenReturn(2);
        when(systemSettingService.getIntSetting(eq("MAX_CONCURRENT_VISITORS_PER_ROOM"), anyInt())).thenReturn(4);
        when(visitorRepository.countActiveVisitorsByStudentId("st-01")).thenReturn(0L);
        when(visitorRepository.countActiveVisitorsByRoomId("r-101")).thenReturn(0L);
        when(systemSettingService.getSettingValue(eq("MAX_VISIT_HOUR"), anyString())).thenReturn("23:59");
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());
        when(visitorMapper.toEntity(request)).thenReturn(visitor);
        when(visitorRepository.save(any(Visitor.class))).thenReturn(visitor);
        when(visitorMapper.toResponse(any(Visitor.class))).thenReturn(response);

        Visitor_Response res = visitorService.checkIn(request);

        assertThat(res).isNotNull();
        assertThat(res.getVisitorName()).isEqualTo("Nguyễn Phụ Huynh");
        verify(visitorRepository).save(any(Visitor.class));
    }

    @Test
    @DisplayName("Check-in thất bại khi khách cùng CCCD đang ở trong KTX chưa check-out")
    void checkIn_failed_alreadyInside() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(List.of(contract));
        when(visitorRepository.existsByNationalIdAndCheckOutTimeIsNull("001099123456")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> visitorService.checkIn(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Check-in thất bại khi sinh viên không có hợp đồng ACTIVE")
    void checkIn_failed_noActiveContract() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(List.of());

        AppException ex = assertThrows(AppException.class, () -> visitorService.checkIn(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Check-out thành công")
    void checkOut_success() {
        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(visitorRepository.save(any(Visitor.class))).thenReturn(visitor);
        when(visitorMapper.toResponse(any(Visitor.class))).thenReturn(response);

        Visitor_Response res = visitorService.checkOut("vis-01");

        assertThat(res).isNotNull();
        assertThat(visitor.getCheckOutTime()).isNotNull();
    }

    @Test
    @DisplayName("Check-out thất bại khi khách đã check-out trước đó")
    void checkOut_failed_alreadyCheckedOut() {
        visitor.setCheckOutTime(LocalDateTime.now().minusHours(1));
        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));

        AppException ex = assertThrows(AppException.class, () -> visitorService.checkOut("vis-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Cán bộ/bảo vệ nhập bù khách ngoài giờ thăm: cho phép ghi nhận và không chặn lỗi")
    void checkIn_staff_outsideVisitingHours_allowed() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("st-01")).thenReturn(Optional.of(student));
        when(contractRepository.findByStudent_StudentIdAndStatus("st-01", "ACTIVE")).thenReturn(List.of(contract));
        when(visitorRepository.existsByNationalIdAndCheckOutTimeIsNull("001099123456")).thenReturn(false);
        when(systemSettingService.getIntSetting(eq("MAX_CONCURRENT_VISITORS_PER_STUDENT"), anyInt())).thenReturn(2);
        when(systemSettingService.getIntSetting(eq("MAX_CONCURRENT_VISITORS_PER_ROOM"), anyInt())).thenReturn(4);
        when(visitorRepository.countActiveVisitorsByStudentId("st-01")).thenReturn(0L);
        when(visitorRepository.countActiveVisitorsByRoomId("r-101")).thenReturn(0L);
        // Giờ thăm kết thúc lúc 01:00 sáng, giờ hiện tại chắc chắn ngoài khung này (trừ khi chạy đúng 0-1h)
        when(systemSettingService.getSettingValue(eq("MAX_VISIT_HOUR"), anyString())).thenReturn("00:01");
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());
        when(visitorMapper.toEntity(request)).thenReturn(visitor);
        when(visitorRepository.save(any(Visitor.class))).thenReturn(visitor);
        when(visitorMapper.toResponse(any(Visitor.class))).thenReturn(response);

        Visitor_Response res = visitorService.checkIn(request);

        assertThat(res).isNotNull();
        verify(visitorRepository).save(any(Visitor.class));
    }

    @Test
    @DisplayName("Duyệt khách vào cổng: cập nhật thời điểm vào thực tế và người duyệt")
    void approve_success_stampsActualCheckInTime() {
        Staff approver = Staff.builder().staffId("staff-sec").build();
        visitor.setApprover(null);
        visitor.setPurpose("[PENDING] Tham ban");

        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));
        when(currentUserService.findStaffId()).thenReturn(Optional.of("staff-sec"));
        when(staffRepository.findById("staff-sec")).thenReturn(Optional.of(approver));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(i -> i.getArgument(0));
        when(visitorMapper.toResponse(any(Visitor.class))).thenReturn(response);

        Visitor_Response res = visitorService.approve("vis-01");

        assertThat(res).isNotNull();
        assertThat(visitor.getApprover()).isEqualTo(approver);
        assertThat(visitor.getCheckInTime()).isNotNull();
        verify(visitorRepository).save(visitor);
    }

    @Test
    @DisplayName("Sinh viên xem thông tin khách của sinh viên khác: bị chặn UNAUTHORIZED")
    void getById_studentUnauthorized_whenAccessingOtherStudentVisitor() {
        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("st-other"));

        AppException ex = assertThrows(AppException.class, () -> visitorService.getById("vis-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Check-out thất bại khi khách chưa được duyệt vào cổng (PENDING)")
    void checkOut_fails_whenVisitorPending() {
        visitor.setPurpose("[PENDING] Tham ban");
        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));

        AppException ex = assertThrows(AppException.class, () -> visitorService.checkOut("vis-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Từ chối khách thăm thành công")
    void reject_success() {
        visitor.setPurpose("[PENDING] Tham ban");
        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(i -> i.getArgument(0));
        when(visitorMapper.toResponse(any(Visitor.class))).thenReturn(response);

        Visitor_Response res = visitorService.reject("vis-01", "Sai thong tin");

        assertThat(res).isNotNull();
        assertThat(visitor.getCheckOutTime()).isNotNull();
        assertThat(visitor.getPurpose()).startsWith("[REJECTED]");
    }

    @Test
    @DisplayName("Sinh viên hủy đăng ký khách thăm của chính mình thành công")
    void cancel_success() {
        visitor.setPurpose("[PENDING] Tham ban");
        when(visitorRepository.findById("vis-01")).thenReturn(Optional.of(visitor));
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("STAFF")).thenReturn(false);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("st-01"));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(i -> i.getArgument(0));
        when(visitorMapper.toResponse(any(Visitor.class))).thenReturn(response);

        Visitor_Response res = visitorService.cancel("vis-01");

        assertThat(res).isNotNull();
        assertThat(visitor.getCheckOutTime()).isNotNull();
        assertThat(visitor.getPurpose()).startsWith("[CANCELLED]");
    }
}
