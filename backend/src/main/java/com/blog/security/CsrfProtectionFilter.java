package com.blog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {

  private static final Set<String> CSRF_EXEMPT_PATHS = Set.of(
      "/api/auth/login",
      "/api/auth/register",
      "/api/auth/refresh");

  private final CsrfService csrfService;

  public CsrfProtectionFilter(CsrfService csrfService) {
    this.csrfService = csrfService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String method = request.getMethod();
    if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)) {
      return true;
    }

    String path = request.getRequestURI();
    if (CSRF_EXEMPT_PATHS.contains(path)) {
      return true;
    }

    // Only protect API endpoints
    return !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String cookieToken = getCookieValue(request, "csrf_token");
    String providedToken = request.getHeader("X-CSRF-Token");
    if (providedToken == null || providedToken.isBlank()) {
      providedToken = request.getParameter("csrf_token");
    }

    if (!csrfService.isValid(cookieToken, providedToken)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static String getCookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
