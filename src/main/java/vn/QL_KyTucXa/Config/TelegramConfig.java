package vn.QL_KyTucXa.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class TelegramConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // Đặt read timeout 35 giây để lớn hơn timeout long-polling (25 giây) của Telegram
        factory.setReadTimeout(Duration.ofSeconds(35));
        return new RestTemplate(factory);
    }
}
