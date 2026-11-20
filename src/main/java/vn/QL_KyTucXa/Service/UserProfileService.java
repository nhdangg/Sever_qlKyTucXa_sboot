package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.Student_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

/**
 * Service lấy thông tin chi tiết hồ sơ đại diện cho từng loại vai trò.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {

    Staff_Repository staffRepository;
    Student_Repository studentRepository;
    User_Repository userRepository;

    public UserProfileDTO resolveUserProfile(User user) {
        String roleCode = (user.getRole() != null) ? user.getRole().getRoleCode() : "USER";
        String roleName = (user.getRole() != null) ? user.getRole().getRoleName() : "Người dùng";
        String fullName = user.getUsername();
        String userCode = "";

        if ("STUDENT".equalsIgnoreCase(roleCode)) {
            var ho_so_sinh_vien = studentRepository.findByUser_UserId(user.getUserId());
            if (ho_so_sinh_vien.isPresent()) {
                fullName = ho_so_sinh_vien.get().getFullName();
                userCode = ho_so_sinh_vien.get().getStudentCode();
            }
        } else {
            // Tra hồ sơ nhân viên cho mọi vai trò khác STUDENT (STAFF, MANAGER, ADMIN...)
            var ho_so_nhan_vien = staffRepository.findByUser_UserId(user.getUserId());
            if (ho_so_nhan_vien.isPresent()) {
                fullName = ho_so_nhan_vien.get().getFullName();
                userCode = ho_so_nhan_vien.get().getStaffCode();
            } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
                fullName = "Quản trị viên";
            }
        }

        return UserProfileDTO.builder()
                .roleCode(roleCode)
                .roleName(roleName)
                .fullName(fullName)
                .userCode(userCode)
                .build();
    }

    public UserProfileDTO resolveUserProfileByUserId(String userId) {
        User nguoi_dung = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return resolveUserProfile(nguoi_dung);
    }
}
