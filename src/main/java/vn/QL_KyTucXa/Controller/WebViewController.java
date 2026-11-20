package vn.QL_KyTucXa.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebViewController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/student-portal")
    public String studentPortal() {
        return "student-portal";
    }

    @GetMapping("/buildings")
    public String buildings() {
        return "buildings";
    }

    @GetMapping("/rooms")
    public String rooms() {
        return "rooms";
    }

    @GetMapping("/students")
    public String students() {
        return "students";
    }

    @GetMapping("/contracts")
    public String contracts() {
        return "contracts";
    }

    @GetMapping("/invoices")
    public String invoices() {
        return "invoices";
    }

    @GetMapping("/maintenance")
    public String maintenance() {
        return "maintenance";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @GetMapping("/admin/database")
    public String adminDatabase() {
        return "admin/database";
    }

    @GetMapping("/admin/users")
    public String adminUsers() {
        return "admin/users";
    }

    @GetMapping("/admin/staff")
    public String adminStaff() {
        return "admin/staff";
    }

    @GetMapping("/admin/assets")
    public String adminAssets() {
        return "admin/assets";
    }

    @GetMapping("/admin/security")
    public String adminSecurity() {
        return "admin/security";
    }

    // Điều hướng tới trang Nhật ký hệ thống (Audit Logs) dành cho Quản trị viên
    @GetMapping("/admin/audit-logs")
    public String adminAuditLogs() {
        return "admin/audit-logs";
    }
}
