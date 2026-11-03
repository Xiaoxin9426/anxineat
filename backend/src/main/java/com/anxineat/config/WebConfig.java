package com.anxineat.config;

import com.anxineat.security.AdminAuthInterceptor;
import com.anxineat.security.UserAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：CORS + 鉴权拦截器挂载。
 *
 * <p>免鉴权：门店、商品、分类、登录、支付回调、公开配置、后台静态页。
 * 其余 /api/** 需用户 token；/api/admin/** 需管理员 token（登录接口除外）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    private static final String[] PUBLIC_PATTERNS = {
            "/api/store/**",
            "/api/product/**",
            "/api/category/**",
            "/api/auth/**",
            "/api/config/**",
            "/api/banner/**",
            "/api/pay/callback/**",
            "/api/pay/mock/**",
            "/api/pay/wechat/notify",
            "/api/health",
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATTERNS)
                .excludePathPatterns("/api/admin/**");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");
    }
}
