package com.example.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final int limitPerMinute;

    public RateLimitFilter(StringRedisTemplate redis, int limitPerMinute) {
        this.redis = redis;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                && request.getRequestURI().startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long minute = System.currentTimeMillis() / 60_000;
        String key = "rl:" + request.getRemoteAddr() + ":" + minute;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(120));
            }
            if (count != null && count > limitPerMinute) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write(
                        "{\"title\":\"Too many requests\",\"status\":429,\"detail\":\"Rate limit exceeded\"}");
                return;
            }
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}