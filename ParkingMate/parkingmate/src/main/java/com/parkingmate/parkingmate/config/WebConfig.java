package com.parkingmate.parkingmate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // /api/ 로 시작하는 모든 요청을
                .allowedOrigins("http://localhost:5173") // http://localhost:5173 주소에서 오는 요청만
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH") // 허용되는 HTTP 메소드
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}