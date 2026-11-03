package com.anxineat.security;

import com.anxineat.common.BizException;
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
 * 小程序端用户鉴权拦截器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER)) {
            writeUnauthorized(response);
            return false;
        }
        String token = header.substring(BEARER.length());
        try {
            Claims claims = jwtUtil.parse(token);
            if (!JwtUtil.SubjectType.USER.name().equals(claims.get("type", String.class))) {
                writeUnauthorized(response);
                return false;
            }
            AuthContext.setUserId(Long.parseLong(claims.getSubject()));
            return true;
        } catch (Exception e) {
            log.debug("用户 token 解析失败: {}", e.getMessage());
            writeUnauthorized(response);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            ResultCode rc = ResultCode.UNAUTHORIZED;
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(rc)));
        } catch (IOException ignored) {
            // ignore
        }
    }

    /** 供业务层抛出统一异常 */
    public static BizException unauthorized() {
        return new BizException(ResultCode.UNAUTHORIZED);
    }
}
