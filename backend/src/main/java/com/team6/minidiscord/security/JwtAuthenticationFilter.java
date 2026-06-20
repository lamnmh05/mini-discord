package com.team6.minidiscord.security;

import com.team6.minidiscord.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                AuthenticatedUser user = jwtService.verify(authorization.substring("Bearer ".length()));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ApiException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(ex.code().status().value());
                response.setContentType("application/json");
                response.getWriter().write(errorBody(ex));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String errorBody(ApiException ex) {
        String details = ex.details().stream()
                .map(detail -> "\"" + json(detail) + "\"")
                .collect(Collectors.joining(","));
        String traceId = MDC.get("traceId");
        return """
                {"success":false,"data":null,"meta":{},"error":{"code":"%s","message":"%s","details":[%s],"traceId":%s}}\
                """.formatted(
                json(ex.code().name()),
                json(ex.getMessage()),
                details,
                traceId == null ? "null" : "\"" + json(traceId) + "\""
        );
    }

    private String json(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
