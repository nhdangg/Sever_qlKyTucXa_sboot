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
import vn.QL_KyTucXa.DTO.Request.Feedback_Request;
import vn.QL_KyTucXa.DTO.Response.Feedback_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.Feedback;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Feedback_Mapper;
import vn.QL_KyTucXa.Repository.Feedback_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Feedback_ServiceTest {

    @Mock
    private Feedback_Repository feedbackRepository;

    @Mock
    private Student_Repository studentRepository;

    @Mock
    private Staff_Repository staffRepository;

    @Mock
    private CurrentUser_Service currentUserService;

    @Mock
    private Feedback_Mapper feedbackMapper;

    @InjectMocks
    private Feedback_Service feedbackService;

    private Student student;
    private Staff staff;
    private Feedback feedback;
    private Feedback_Request request;
    private Feedback_Response response;

    @BeforeEach
    void setUp() {
        student = Student.builder().studentId("stu-01").studentCode("SV01").fullName("Nguyễn Văn A").build();
        staff = Staff.builder().staffId("staff-01").staffCode("NV01").fullName("Trần Quản Lý").build();

        feedback = Feedback.builder()
                .feedbackId("fb-01")
                .student(student)
                .category("Cơ sở vật chất")
                .content("Bóng đèn phòng bị hỏng")
                .rating(5)
                .status("SUBMITTED")
                .build();

        request = Feedback_Request.builder()
                .studentId("stu-01")
                .category("Cơ sở vật chất")
                .content("Bóng đèn phòng bị hỏng")
                .rating(5)
                .response("Đã sửa")
                .status("RESOLVED")
                .build();

        response = Feedback_Response.builder()
                .feedbackId("fb-01")
                .studentId("stu-01")
                .category("Cơ sở vật chất")
                .content("Bóng đèn phòng bị hỏng")
                .status("SUBMITTED")
                .build();
    }

    @Test
    @DisplayName("Tạo phản hồi thành công từ sinh viên JWT, xóa response và status trong request")
    void create_success_fromStudentRole() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("stu-01");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(feedbackMapper.toEntity(request)).thenReturn(feedback);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(feedback);
        when(feedbackMapper.toResponse(feedback)).thenReturn(response);

        Feedback_Response result = feedbackService.create(request);

        assertThat(result).isNotNull();
        assertThat(feedback.getStatus()).isEqualTo("SUBMITTED");
        assertThat(feedback.getResponse()).isNull();
        assertThat(feedback.getRespondedBy()).isNull();
        verify(feedbackRepository).save(feedback);
    }

    @Test
    @DisplayName("Tạo phản hồi thất bại khi không tìm thấy sinh viên")
    void create_failed_studentNotFound() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(false);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> feedbackService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        verify(studentRepository, never()).findAll();
    }

    @Test
    @DisplayName("Lấy chi tiết phản hồi thành công")
    void getById_success() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));
        when(currentUserService.hasRole("STUDENT")).thenReturn(false);
        when(feedbackMapper.toResponse(feedback)).thenReturn(response);

        Feedback_Response result = feedbackService.getById("fb-01");

        assertThat(result).isNotNull();
        assertThat(result.getFeedbackId()).isEqualTo("fb-01");
    }

    @Test
    @DisplayName("Lấy chi tiết phản hồi thất bại khi sinh viên xem phản hồi của người khác")
    void getById_failed_unauthorizedForOtherStudent() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("stu-other");

        AppException ex = assertThrows(AppException.class, () -> feedbackService.getById("fb-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lấy phản hồi theo sinh viên thành công khi cùng sinh viên hoặc quản lý")
    void getByStudentId_success() {
        when(studentRepository.existsById("stu-01")).thenReturn(true);
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("stu-01");
        when(feedbackRepository.findByStudent_StudentId("stu-01")).thenReturn(List.of(feedback));
        when(feedbackMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<Feedback_Response> list = feedbackService.getByStudentId("stu-01");

        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("Lấy phản hồi theo sinh viên thất bại khi sinh viên xem của sinh viên khác")
    void getByStudentId_failed_unauthorizedForOtherStudent() {
        when(studentRepository.existsById("stu-01")).thenReturn(true);
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("stu-other");

        AppException ex = assertThrows(AppException.class, () -> feedbackService.getByStudentId("stu-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Trả lời phản hồi thành công và chuyển trạng thái sang RESOLVED")
    void respond_success_withJwtStaff() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));
        when(currentUserService.findStaffId()).thenReturn(Optional.of("staff-01"));
        when(staffRepository.findById("staff-01")).thenReturn(Optional.of(staff));
        when(feedbackRepository.save(feedback)).thenReturn(feedback);
        when(feedbackMapper.toResponse(feedback)).thenReturn(response);

        Feedback_Response result = feedbackService.respond("fb-01", null, "Đã tiếp nhận và sửa xong");

        assertThat(result).isNotNull();
        assertThat(feedback.getStatus()).isEqualTo("RESOLVED");
        assertThat(feedback.getResponse()).isEqualTo("Đã tiếp nhận và sửa xong");
        assertThat(feedback.getRespondedBy()).isEqualTo(staff);
        verify(staffRepository, never()).findAll();
    }

    @Test
    @DisplayName("Trả lời phản hồi thất bại khi phản hồi đã được trả lời trước đó")
    void respond_failed_alreadyResolved() {
        feedback.setStatus("RESOLVED");
        feedback.setResponse("Đã trả lời");
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));

        AppException ex = assertThrows(AppException.class, () -> feedbackService.respond("fb-01", "staff-01", "Nội dung mới"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Trả lời phản hồi thất bại khi nội dung trả lời để trống")
    void respond_failed_blankResponse() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));

        AppException ex = assertThrows(AppException.class, () -> feedbackService.respond("fb-01", "staff-01", "   "));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Tìm kiếm phản hồi có phân trang theo từ khóa, trạng thái và danh mục")
    void getAll_withSearch() {
        Page<Feedback> page = new PageImpl<>(List.of(feedback));
        when(feedbackRepository.searchFeedbacks(eq("hỏng"), eq("SUBMITTED"), eq("Cơ sở vật chất"), any(Pageable.class)))
                .thenReturn(page);
        when(feedbackMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Feedback_Response> result = feedbackService.getAll(1, 10, "hỏng", "SUBMITTED", "Cơ sở vật chất", "createdAt", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Xóa phản hồi thành công")
    void delete_success() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));

        feedbackService.delete("fb-01");

        verify(feedbackRepository).delete(feedback);
    }

    @Test
    @DisplayName("Người dùng không phải ADMIN và không có hồ sơ nhân viên cố trả lời phản hồi: bị chặn UNAUTHORIZED")
    void respond_failed_nonAdminCannotSpoofStaffId() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> feedbackService.respond("fb-01", "staff-fake", "Trả lời"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Sinh viên xóa phản hồi của người khác: bị chặn UNAUTHORIZED")
    void delete_failed_studentCannotDeleteOtherStudentFeedback() {
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("STAFF")).thenReturn(false);
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("stu-other");

        AppException ex = assertThrows(AppException.class, () -> feedbackService.delete("fb-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Sinh viên xóa phản hồi đã được xử lý (RESOLVED): bị chặn OPERATION_FAILED")
    void delete_failed_studentCannotDeleteResolvedFeedback() {
        feedback.setStatus("RESOLVED");
        when(feedbackRepository.findById("fb-01")).thenReturn(Optional.of(feedback));
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("STAFF")).thenReturn(false);
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("stu-01");

        AppException ex = assertThrows(AppException.class, () -> feedbackService.delete("fb-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }
}

