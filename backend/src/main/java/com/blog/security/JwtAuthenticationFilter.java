package com.blog.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  public JwtAuthenticationFilter(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = getCookieValue(request, "access_token");
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        Claims claims = jwtUtil.parseAndValidate(token);
        String email = claims.getSubject();
        String role = claims.get("role", String.class);
        if (email != null && role != null) {
          var auth = new UsernamePasswordAuthenticationToken(
              email,
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
          auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (Exception ignored) {
        // Invalid JWT - treat as unauthenticated
      }
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
