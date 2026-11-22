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
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.QL_KyTucXa.DTO.Request.StudentSelfUpdate_Request;
import vn.QL_KyTucXa.DTO.Request.Student_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Student_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Student_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Student_ServiceTest {

    @Mock
    private Student_Repository studentRepository;

    @Mock
    private User_Repository userRepository;

    @Mock
    private Role_Repository roleRepository;

    @Mock
    private Contract_Repository contractRepository;

    @Mock
    private CurrentUser_Service currentUserService;

    @Mock
    private Student_Mapper studentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private Student_Service studentService;

    private User user;
    private Role studentRole;
    private Role staffRole;
    private Student student;
    private Student_Request request;
    private Student_Response response;

    @BeforeEach
    void setUp() {
        studentRole = Role.builder()
                .roleId("role-student")
                .roleCode("STUDENT")
                .roleName("Sinh viên")
                .build();

        staffRole = Role.builder()
                .roleId("role-staff")
                .roleCode("STAFF")
                .roleName("Nhân viên")
                .build();

        user = User.builder()
                .userId("u-01")
                .username("SV20210001")
                .role(studentRole)
                .status("ACTIVE")
                .build();

        student = Student.builder()
                .studentId("stu-01")
                .studentCode("SV20210001")
                .fullName("Nguyen Van A")
                .gender("MALE")
                .nationalId("001204000001")
                .user(user)
                .status("ACTIVE")
                .build();

        request = Student_Request.builder()
                .userId("u-01")
                .studentCode("SV20210001")
                .fullName("Nguyen Van A")
                .gender("MALE")
                .nationalId("001204000001")
                .build();

        response = Student_Response.builder()
                .studentId("stu-01")
                .studentCode("SV20210001")
                .fullName("Nguyen Van A")
                .gender("MALE")
                .nationalId("001204000001")
                .build();
    }

    // ==================== CREATE TESTS ====================

    @Test
    @DisplayName("Tạo hồ sơ sinh viên thành công khi có sẵn userId hợp lệ có vai trò STUDENT")
    void create_withExistingUserId_success() {
        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(false);
        when(studentRepository.existsByNationalId("001204000001")).thenReturn(false);
        when(userRepository.findById("u-01")).thenReturn(Optional.of(user));
        when(studentRepository.findByUser_UserId("u-01")).thenReturn(Optional.empty());
        when(studentMapper.toEntity(request)).thenReturn(student);
        when(studentRepository.save(any(Student.class))).thenReturn(student);
        when(studentMapper.toResponse(student)).thenReturn(response);

        Student_Response result = studentService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getStudentCode()).isEqualTo("SV20210001");
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("Tạo hồ sơ sinh viên thất bại khi tài khoản người dùng liên kết không có vai trò STUDENT")
    void create_failed_userIsNotStudentRole() {
        User staffUser = User.builder().userId("u-staff").role(staffRole).build();
        request.setUserId("u-staff");

        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(false);
        when(studentRepository.existsByNationalId("001204000001")).thenReturn(false);
        when(userRepository.findById("u-staff")).thenReturn(Optional.of(staffUser));

        AppException ex = assertThrows(AppException.class, () -> studentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Tài khoản người dùng phải có vai trò sinh viên");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo hồ sơ sinh viên thất bại khi số CCCD đã tồn tại trong CSDL")
    void create_failed_duplicateNationalId() {
        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(false);
        when(studentRepository.existsByNationalId("001204000001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Số CCCD/Định danh '001204000001' đã tồn tại");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo hồ sơ sinh viên thành công và tự động tạo tài khoản User khi userId để trống")
    void create_autoCreateUser_success() {
        Student_Request newReq = Student_Request.builder()
                .studentCode("SV20269999")
                .fullName("Tran Van B")
                .gender("MALE")
                .email("b@student.edu.vn")
                .phone("0987654321")
                .nationalId("001204000999")
                .build();

        when(studentRepository.existsByStudentCode("SV20269999")).thenReturn(false);
        when(studentRepository.existsByNationalId("001204000999")).thenReturn(false);
        when(userRepository.existsByUsername("SV20269999")).thenReturn(false);
        when(userRepository.existsByEmail("b@student.edu.vn")).thenReturn(false);
        when(roleRepository.findByRoleCode("STUDENT")).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode("SV20269999")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student createdStudent = Student.builder()
                .studentId("stu-new")
                .studentCode("SV20269999")
                .fullName("Tran Van B")
                .gender("MALE")
                .build();

        when(studentMapper.toEntity(newReq)).thenReturn(createdStudent);
        when(studentRepository.save(any(Student.class))).thenReturn(createdStudent);
        when(studentMapper.toResponse(createdStudent)).thenReturn(response);

        Student_Response res = studentService.create(newReq);

        assertThat(res).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(studentRepository).save(any(Student.class));
    }

    @Test
    @DisplayName("Tạo sinh viên đổi CCCD rỗng \"\" thành null để tránh vi phạm unique constraint của MySQL")
    void create_blankNationalIdConvertsToNull() {
        request.setNationalId("   ");
        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(false);
        when(userRepository.findById("u-01")).thenReturn(Optional.of(user));
        when(studentRepository.findByUser_UserId("u-01")).thenReturn(Optional.empty());
        when(studentMapper.toEntity(request)).thenReturn(student);
        when(studentRepository.save(any(Student.class))).thenReturn(student);
        when(studentMapper.toResponse(student)).thenReturn(response);

        studentService.create(request);

        assertThat(request.getNationalId()).isNull();
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("Tự động tạo tài khoản thất bại khi tên đăng nhập (MSSV) đã tồn tại trong bảng User")
    void create_autoCreateUser_duplicateUsername() {
        Student_Request newReq = Student_Request.builder()
                .studentCode("SV20269999")
                .fullName("Tran Van B")
                .gender("MALE")
                .build();

        when(studentRepository.existsByStudentCode("SV20269999")).thenReturn(false);
        when(userRepository.existsByUsername("SV20269999")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.create(newReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tự động tạo tài khoản thất bại khi email đã được sử dụng")
    void create_autoCreateUser_duplicateEmail() {
        Student_Request newReq = Student_Request.builder()
                .studentCode("SV20269999")
                .fullName("Tran Van B")
                .gender("MALE")
                .email("exist@student.edu.vn")
                .build();

        when(studentRepository.existsByStudentCode("SV20269999")).thenReturn(false);
        when(userRepository.existsByUsername("SV20269999")).thenReturn(false);
        when(userRepository.existsByEmail("exist@student.edu.vn")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.create(newReq));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo sinh viên thất bại khi không tìm thấy tài khoản người dùng chỉ định")
    void create_failed_userNotFound() {
        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(false);
        when(studentRepository.existsByNationalId("001204000001")).thenReturn(false);
        when(userRepository.findById("u-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> studentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXISTED);
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo sinh viên thất bại khi mã sinh viên đã tồn tại")
    void create_failed_duplicateStudentCode() {
        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo sinh viên thất bại khi User đã gắn với hồ sơ sinh viên khác")
    void create_failed_userAlreadyLinked() {
        when(studentRepository.existsByStudentCode("SV20210001")).thenReturn(false);
        when(studentRepository.existsByNationalId("001204000001")).thenReturn(false);
        when(userRepository.findById("u-01")).thenReturn(Optional.of(user));
        when(studentRepository.findByUser_UserId("u-01")).thenReturn(Optional.of(student));

        AppException ex = assertThrows(AppException.class, () -> studentService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    // ==================== VIEW / GET TESTS ====================

    @Test
    @DisplayName("getById thành công khi người gọi là ADMIN")
    void getById_success_asAdmin() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(studentMapper.toResponse(student)).thenReturn(response);

        Student_Response result = studentService.getById("stu-01");

        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isEqualTo("stu-01");
    }

    @Test
    @DisplayName("getById thành công khi người gọi là chính chủ sinh viên")
    void getById_success_asOwner() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("STAFF")).thenReturn(false);
        when(currentUserService.hasPermission("STUDENT_MANAGE")).thenReturn(false);
        when(currentUserService.userId()).thenReturn("u-01");

        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(studentMapper.toResponse(student)).thenReturn(response);

        Student_Response result = studentService.getById("stu-01");

        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isEqualTo("stu-01");
    }

    @Test
    @DisplayName("getById thất bại (UNAUTHORIZED) khi sinh viên khác cố tình xem hồ sơ không phải của mình")
    void getById_failed_unauthorizedStudent() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(false);
        when(currentUserService.hasRole("MANAGER")).thenReturn(false);
        when(currentUserService.hasRole("STAFF")).thenReturn(false);
        when(currentUserService.hasPermission("STUDENT_MANAGE")).thenReturn(false);
        when(currentUserService.userId()).thenReturn("u-another-student");

        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));

        AppException ex = assertThrows(AppException.class, () -> studentService.getById("stu-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(ex.getMessage()).contains("Bạn không có quyền xem thông tin hồ sơ của sinh viên khác");
    }

    @Test
    @DisplayName("getByUserId ném ngoại lệ khi User chưa có hồ sơ sinh viên")
    void getByUserId_orphanStudentUser_throwsException() {
        when(studentRepository.findByUser_UserId("u-orphan")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> studentService.getByUserId("u-orphan"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        assertThat(ex.getMessage()).contains("Hồ sơ sinh viên chưa hoàn tất");
        verify(studentRepository, never()).save(any());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    @DisplayName("update - Chặn thay đổi userId sang tài khoản khác")
    void update_failed_changeUserIdBlocked() {
        request.setUserId("u-different");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));

        AppException ex = assertThrows(AppException.class, () -> studentService.update("stu-01", request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không được phép thay đổi tài khoản người dùng");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Thất bại khi số CCCD bị trùng với sinh viên khác")
    void update_failed_duplicateNationalId() {
        request.setNationalId("001204000999");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(studentRepository.existsByNationalIdAndStudentIdNot("001204000999", "stu-01")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.update("stu-01", request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã tồn tại ở hồ sơ sinh viên khác");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMyProfile - Sinh viên tự cập nhật thông tin liên hệ và CCCD thành công")
    void updateMyProfile_success() {
        when(currentUserService.userId()).thenReturn("u-01");
        when(studentRepository.findByUser_UserId("u-01")).thenReturn(Optional.of(student));
        when(studentRepository.existsByNationalIdAndStudentIdNot("001204000888", "stu-01")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenReturn(student);
        when(studentMapper.toResponse(student)).thenReturn(response);

        StudentSelfUpdate_Request selfReq = StudentSelfUpdate_Request.builder()
                .nationalId("001204000888")
                .phone("0988776655")
                .email("newemail@student.edu.vn")
                .hometown("Nam Định")
                .build();

        Student_Response res = studentService.updateMyProfile(selfReq);

        assertThat(res).isNotNull();
        assertThat(student.getNationalId()).isEqualTo("001204000888");
        assertThat(student.getHometown()).isEqualTo("Nam Định");
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("update - Thất bại khi đổi trạng thái sang INACTIVE nhưng sinh viên còn hợp đồng lưu trú ACTIVE")
    void update_changeStatusToInactive_whenHasActiveContract_throwsException() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        request.setStatus("INACTIVE");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.update("stu-01", request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể chuyển trạng thái ngừng hoạt động (INACTIVE) cho sinh viên đang có hợp đồng lưu trú ACTIVE");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Đổi trạng thái sang INACTIVE thành công, khóa User và thu hồi refresh token")
    void update_changeStatusToInactive_success_locksUserAndRevokesTokens() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        request.setStatus("INACTIVE");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenReturn(student);
        when(studentMapper.toResponse(student)).thenReturn(response);

        studentService.update("stu-01", request);

        assertThat(student.getStatus()).isEqualTo("INACTIVE");
        assertThat(user.getStatus()).isEqualTo("INACTIVE");
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllActiveByUserId("u-01");
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("update - Đổi trạng thái sang ACTIVE thành công, mở khóa User nếu User đang INACTIVE")
    void update_changeStatusToActive_success_unlocksUser() {
        when(currentUserService.hasRole("ADMIN")).thenReturn(true);
        student.setStatus("INACTIVE");
        user.setStatus("INACTIVE");
        request.setStatus("ACTIVE");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenReturn(student);
        when(studentMapper.toResponse(student)).thenReturn(response);

        studentService.update("stu-01", request);

        assertThat(student.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(user);
        verify(studentRepository).save(student);
    }

    // ==================== DELETE TESTS ====================

    @Test
    @DisplayName("delete - Lần 1: Chuyển trạng thái sang INACTIVE và khóa User khi không còn hợp đồng ACTIVE")
    void delete_success_softDeleteAndLockUser() {
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);

        studentService.delete("stu-01");

        assertThat(student.getStatus()).isEqualTo("INACTIVE");
        assertThat(user.getStatus()).isEqualTo("INACTIVE");
        verify(studentRepository).save(student);
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllActiveByUserId("u-01");
        verify(studentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete - Lần 2 (khi đã INACTIVE): Xóa hoàn toàn Student và User khi không có hợp đồng hoạt động/chờ duyệt")
    void delete_whenAlreadyInactive_hardDeletesStudentAndUser() {
        student.setStatus("INACTIVE");
        user.setStatus("INACTIVE");
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(false);
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "PENDING")).thenReturn(false);
        when(contractRepository.findByStudent_StudentId("stu-01")).thenReturn(List.of());

        studentService.delete("stu-01");

        verify(studentRepository).delete(student);
        verify(refreshTokenService).deleteAllByUserId("u-01");
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("delete - Thất bại khi sinh viên đang có hợp đồng lưu trú ACTIVE")
    void delete_failed_studentHasActiveContract() {
        when(studentRepository.findById("stu-01")).thenReturn(Optional.of(student));
        when(contractRepository.existsByStudent_StudentIdAndStatus("stu-01", "ACTIVE")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> studentService.delete("stu-01"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Không thể xóa hoặc ngừng hoạt động sinh viên đang có hợp đồng lưu trú ACTIVE");
        verify(studentRepository, never()).save(any());
    }

    // ==================== SEARCH TESTS ====================

    @Test
    @DisplayName("getAll - Tìm kiếm phân trang với khoa, lớp, giới tính, trạng thái, đang ở/chưa có chỗ")
    void getAll_withSearchFilters_success() {
        Page<Student> page = new PageImpl<>(List.of(student));
        when(studentRepository.searchStudents(
                eq("SV001"), eq("CNTT"), eq("K68"), eq("MALE"), eq("ACTIVE"), eq(true), any(Pageable.class)
        )).thenReturn(page);
        when(studentMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<Student_Response> res = studentService.getAll(
                1, 10, "SV001", "CNTT", "K68", "MALE", "ACTIVE", true, "studentCode", "asc"
        );

        assertThat(res).isNotNull();
        assertThat(res.getData()).hasSize(1);
        verify(studentRepository).searchStudents(
                eq("SV001"), eq("CNTT"), eq("K68"), eq("MALE"), eq("ACTIVE"), eq(true), any(Pageable.class)
        );
    }
}
