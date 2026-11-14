package vn.QL_KyTucXa.Service;

import org.springframework.stereotype.Component;
import vn.QL_KyTucXa.Entity.User;

// Chính sách xác thực hai yếu tố (2FA) dựa trên vai trò:
// Kích hoạt 2FA Telegram khi người dùng là Quản trị viên (ADMIN hoặc username "admin").

@Component
public class RoleBasedTwoFactorPolicy {

    public boolean requiresTwoFactorAuth(User user) {
        if (user == null)
            return false;
        return "admin".equalsIgnoreCase(user.getUsername())
                || (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getRoleCode()));
    }
}
