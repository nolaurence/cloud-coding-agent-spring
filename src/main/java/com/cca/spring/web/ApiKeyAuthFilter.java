package com.cca.spring.web;

import com.cca.spring.config.AppProperties;
import com.cca.spring.web.dto.ApiDtos.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private static final List<String> PUBLIC_PATHS = List.of("/api/health", "/api/config");

  private final AppProperties properties;
  private final ObjectMapper objectMapper;

  public ApiKeyAuthFilter(AppProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.getSecurity().isEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    return !path.startsWith("/api/") || PUBLIC_PATHS.contains(path);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    String expected = properties.getSecurity().getApiKey();
    if (expected == null || expected.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }
    String provided = request.getHeader("X-API-Key");
    if (provided == null || provided.isBlank()) {
      String authorization = request.getHeader("Authorization");
      if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
        provided = authorization.substring(7).trim();
      }
    }
    if (provided == null || !provided.equals(expected)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write(objectMapper.writeValueAsString(
          new ApiError("unauthorized", "API Key 无效或缺失", Instant.now())));
      return;
    }
    filterChain.doFilter(request, response);
  }
}
