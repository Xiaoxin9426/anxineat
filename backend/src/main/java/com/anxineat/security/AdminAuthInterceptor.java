package com.anxineat.security;

import com.anxineat.common.R;
import com.anxineat.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 后台管理员鉴权拦截器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER)) {
            writeFail(response, ResultCode.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(BEARER.length()));
            if (!JwtUtil.SubjectType.ADMIN.name().equals(claims.get("type", String.class))) {
                writeFail(response, ResultCode.FORBIDDEN);
                return false;
            }
            Long storeId = claims.get("storeId") == null ? null : claims.get("storeId", Long.class);
            AuthContext.setAdmin(new AdminPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get("role", String.class),
                    storeId));
            return true;
        } catch (Exception e) {
            log.debug("管理员 token 解析失败: {}", e.getMessage());
            writeFail(response, ResultCode.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private void writeFail(HttpServletResponse response, ResultCode rc) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(rc)));
        } catch (IOException ignored) {
            // ignore
        }
    }
}
