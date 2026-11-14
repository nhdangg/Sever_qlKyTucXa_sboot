package vn.QL_KyTucXa.Service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class HttpRequestClientInfoResolverTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Khi không có HTTP Request Context thì fallback về IP mặc định")
    void getClientIpAddress_noContext_returnsDefault() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("none");
        String ip = resolver.getClientIpAddress();
        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Khi không có HTTP Request Context thì fallback về User-Agent mặc định")
    void getClientUserAgent_noContext_returnsDefault() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("none");
        String userAgent = resolver.getClientUserAgent();
        assertThat(userAgent).isEqualTo("Web Browser");
    }

    @Test
    @DisplayName("Khi strategy là none thì bỏ qua header X-Forwarded-For giả mạo và lấy remoteAddr")
    void getClientIpAddress_strategyNone_ignoresSpoofedXForwardedFor() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("none");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String ip = resolver.getClientIpAddress();
        assertThat(ip).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("Khi strategy là framework thì tin cậy header X-Forwarded-For từ proxy")
    void getClientIpAddress_strategyFramework_trustsForwardedFor() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("framework");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 198.51.100.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String ip = resolver.getClientIpAddress();
        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Khi strategy là native thì tin cậy header X-Forwarded-For từ proxy")
    void getClientIpAddress_strategyNative_trustsForwardedFor() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("native");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.200");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String ip = resolver.getClientIpAddress();
        assertThat(ip).isEqualTo("203.0.113.200");
    }

    @Test
    @DisplayName("Khi strategy là framework nhưng header X-Forwarded-For rỗng hoặc unknown thì fallback remoteAddr")
    void getClientIpAddress_strategyFramework_blankOrUnknownForwarded_fallsBackToRemoteAddr() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("framework");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String ip = resolver.getClientIpAddress();
        assertThat(ip).isEqualTo("10.0.0.8");
    }

    @Test
    @DisplayName("Khi có header User-Agent hợp lệ thì lấy đúng giá trị User-Agent")
    void getClientUserAgent_withValidHeader_returnsUserAgent() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("none");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String userAgent = resolver.getClientUserAgent();
        assertThat(userAgent).isEqualTo("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
    }

    @Test
    @DisplayName("Khi header User-Agent rỗng thì fallback về Web Browser mặc định")
    void getClientUserAgent_blankHeader_returnsDefault() {
        HttpRequestClientInfoResolver resolver = new HttpRequestClientInfoResolver("none");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String userAgent = resolver.getClientUserAgent();
        assertThat(userAgent).isEqualTo("Web Browser");
    }
}
