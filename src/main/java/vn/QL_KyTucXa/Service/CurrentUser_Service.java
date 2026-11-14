package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import vn.QL_KyTucXa.Entity.Staff;
import vn.QL_KyTucXa.Entity.Student;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CurrentUser_Service {

    Student_Repository studentRepository;
    Staff_Repository staffRepository;
    User_Repository userRepository;

    // Lấy ID người dùng hiện tại từ Security Context
    public String userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String userId = jwtAuthentication.getToken().getClaimAsString("userId");
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
            String sub = jwtAuthentication.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                return userRepository.findByUsername(sub)
                        .map(User::getUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
            }
        }
        if (authentication != null && authentication.getName() != null) {
            return userRepository.findByUsername(authentication.getName())
                    .map(User::getUserId)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    // Lấy ID sinh viên của người dùng hiện tại và ném lỗi STUDENT_NOT_FOUND nếu không tìm thấy hồ sơ
    public String studentId() {
        String uid = userId();
        return studentRepository.findByUser_UserId(uid)
                .map(Student::getStudentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
    }

    // Tìm ID sinh viên của người dùng hiện tại dưới dạng Optional
    public Optional<String> findStudentId() {
        try {
            String uid = userId();
            return studentRepository.findByUser_UserId(uid)
                    .map(Student::getStudentId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Lấy ID nhân viên của người dùng hiện tại và ném lỗi STAFF_NOT_FOUND nếu không tìm thấy hồ sơ
    public String staffId() {
        String uid = userId();
        return staffRepository.findByUser_UserId(uid)
                .map(Staff::getStaffId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
    }

    // Tìm ID nhân viên của người dùng hiện tại dưới dạng Optional cho phép null nếu là tài khoản admin không có hồ sơ nhân viên
    public Optional<String> findStaffId() {
        try {
            String uid = userId();
            return staffRepository.findByUser_UserId(uid)
                    .map(Staff::getStaffId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Kiểm tra người đăng nhập có vai trò không kèm tiền tố ROLE_ hay không
    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.trim()));
    }

    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        for (String r : roles) {
            if (hasRole(r)) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra người đăng nhập có quyền hạn cụ thể hay không ví dụ STUDENT_MANAGE CONTRACT_VIEW
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        String perm = permission.trim();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(perm) || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
