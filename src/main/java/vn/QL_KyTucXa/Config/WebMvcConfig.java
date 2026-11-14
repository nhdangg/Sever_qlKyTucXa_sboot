package vn.QL_KyTucXa.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDirConfig;

    /**
     * Lấy đường dẫn thư mục upload thực tế:
     * Hỗ trợ cấu hình động qua biến môi trường UPLOAD_DIR khi deploy lên Linux Server / Docker
     */
    public Path getUploadDir() {
        return Paths.get(uploadDirConfig).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path baseUploadDir = getUploadDir();
        try {
            Files.createDirectories(baseUploadDir.resolve("avatars"));
        } catch (IOException ignored) {}

        String pathUri = baseUploadDir.toUri().toString();
        if (!pathUri.endsWith("/")) {
            pathUri += "/";
        }

        // Tự động phục vụ các tệp trong thư mục uploads qua URL /uploads/**
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(pathUri);
    }
}
