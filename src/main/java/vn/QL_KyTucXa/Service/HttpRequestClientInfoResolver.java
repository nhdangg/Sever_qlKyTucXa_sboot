package vn.QL_KyTucXa.Service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Phân giải thông tin Client từ HttpServletRequest context
// Tuân thủ Single Responsibility Principle (SRP) bằng cách cách ly toàn bộ logic HTTP Servlet khỏi Service nghiệp vụ
@Component
public class HttpRequestClientInfoResolver {

    private final String forwardHeadersStrategy;

    @Autowired
    public HttpRequestClientInfoResolver(@Value("${server.forward-headers-strategy:none}") String forwardHeadersStrategy) {
        this.forwardHeadersStrategy = forwardHeadersStrategy != null ? forwardHeadersStrategy.trim().toLowerCase() : "none";
    }

    // Lấy địa chỉ IP của Client từ request hiện tại
    public String getClientIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // Chỉ tin cậy header X-Forwarded-For khi hệ thống được cấu hình chạy sau proxy đã biết
            if (isForwardHeadersStrategyEnabled()) {
                String xfHeader = request.getHeader("X-Forwarded-For");
                if (xfHeader != null && !xfHeader.isBlank() && !"unknown".equalsIgnoreCase(xfHeader.trim())) {
                    return xfHeader.split(",")[0].trim();
                }
            }

            String remoteAddr = request.getRemoteAddr();
            if (remoteAddr != null && !remoteAddr.isBlank()) {
                return remoteAddr;
            }
        }
        return "127.0.0.1";
    }

    // Lấy User-Agent của Client từ request hiện tại
    public String getClientUserAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && !userAgent.isBlank()) {
                return userAgent;
            }
        }
        return "Web Browser";
    }

    // Kiểm tra cấu hình forward-headers-strategy có cho phép đọc thông tin từ proxy hay không
    private boolean isForwardHeadersStrategyEnabled() {
        return "framework".equalsIgnoreCase(forwardHeadersStrategy)
                || "native".equalsIgnoreCase(forwardHeadersStrategy);
    }
}
