package vn.QL_KyTucXa.Config;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vn.QL_KyTucXa.Exception.ErrorCode;

import java.util.Locale;

/**
 * Cấu hình Validator xử lý tự động dịch message từ enum ErrorCode trong package Exception.
 * Ví dụ: @NotBlank(message = "USER_REQUEST") sẽ tự động lấy thông tin từ ErrorCode.USER_REQUEST.getMessage().
 */
@Configuration
public class ValidationConfig {

    public static class ErrorCodeMessageInterpolator implements MessageInterpolator {
        private final MessageInterpolator defaultInterpolator;

        public ErrorCodeMessageInterpolator(MessageInterpolator defaultInterpolator) {
            this.defaultInterpolator = defaultInterpolator;
        }

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return resolveErrorCodeMessage(messageTemplate, context, Locale.getDefault());
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return resolveErrorCodeMessage(messageTemplate, context, locale);
        }

        private String resolveErrorCodeMessage(String messageTemplate, Context context, Locale locale) {
            if (messageTemplate != null) {
                String key = messageTemplate.trim();
                // Bỏ ngoặc nhọn nếu có (ví dụ "{USER_REQUEST}" -> "USER_REQUEST")
                if (key.startsWith("{") && key.endsWith("}")) {
                    key = key.substring(1, key.length() - 1).trim();
                }
                try {
                    ErrorCode errorCode = ErrorCode.valueOf(key);
                    return errorCode.getMessage();
                } catch (IllegalArgumentException ignored) {
                    // Nếu không phải là một ErrorCode thì giữ nguyên interpolation mặc định
                }
            }
            return defaultInterpolator != null 
                    ? defaultInterpolator.interpolate(messageTemplate, context, locale) 
                    : messageTemplate;
        }
    }

    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        MessageInterpolator defaultInterpolator = Validation.byDefaultProvider()
                .configure()
                .getDefaultMessageInterpolator();
        factoryBean.setMessageInterpolator(new ErrorCodeMessageInterpolator(defaultInterpolator));
        return factoryBean;
    }
}