package com.blog.controller;

import com.blog.dto.AuthResponse;
import com.blog.dto.LoginRequest;
import com.blog.dto.RegisterRequest;
import com.blog.entity.User;
import com.blog.repository.UserRepository;
import com.blog.security.CsrfService;
import com.blog.security.JwtUtil;
import com.blog.security.RefreshTokenService;
import com.blog.security.RefreshTokenService.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final RefreshTokenService refreshTokenService;
  private final CsrfService csrfService;

  private final boolean cookieSecure;
  private final String sameSite;
  private final long accessCookieMaxAgeSeconds;
  private final long refreshCookieMaxAgeSeconds;

  public AuthController(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      RefreshTokenService refreshTokenService,
      CsrfService csrfService,
      @Value("${app.cookie.secure:true}") boolean cookieSecure,
      @Value("${app.cookie.same-site:Strict}") String sameSite,
      @Value("${app.jwt.access-token-ttl-seconds:900}") long accessCookieMaxAgeSeconds,
      @Value("${app.refresh.ttl-seconds:604800}") long refreshCookieMaxAgeSeconds) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.refreshTokenService = refreshTokenService;
    this.csrfService = csrfService;
    this.cookieSecure = cookieSecure;
    this.sameSite = sameSite;
    this.accessCookieMaxAgeSeconds = accessCookieMaxAgeSeconds;
    this.refreshCookieMaxAgeSeconds = refreshCookieMaxAgeSeconds;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    User user = new User();
    user.setEmail(request.getEmail());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setRole("user");
    userRepository.save(user);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new AuthResponse(user.getEmail(), user.getRole()));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail()).orElse(null);
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    String accessJwt = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());
    IssuedRefreshToken refresh = refreshTokenService.create(user);
    String csrf = csrfService.generateToken();

    ResponseCookie accessCookie = ResponseCookie.from("access_token", accessJwt)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/")
        .maxAge(Duration.ofSeconds(accessCookieMaxAgeSeconds))
        .build();

    ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refresh.rawToken())
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/api/auth/refresh")
        .maxAge(Duration.ofSeconds(refreshCookieMaxAgeSeconds))
        .build();

    ResponseCookie csrfCookie = ResponseCookie.from("csrf_token", csrf)
        .httpOnly(false)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/")
        .maxAge(Duration.ofSeconds(refreshCookieMaxAgeSeconds))
        .build();

    return ResponseEntity.ok()
        .header("Set-Cookie", accessCookie.toString())
        .header("Set-Cookie", refreshCookie.toString())
        .header("Set-Cookie", csrfCookie.toString())
        .body(new AuthResponse(user.getEmail(), user.getRole()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request) {
    String rawRefresh = getCookieValue(request, "refresh_token");
    if (rawRefresh == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    var existing = refreshTokenService.findValidByRaw(rawRefresh).orElse(null);
    if (existing == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    User user = existing.getUser();
    IssuedRefreshToken rotated;
    try {
      rotated = refreshTokenService.rotate(rawRefresh);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String accessJwt = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());

    ResponseCookie accessCookie = ResponseCookie.from("access_token", accessJwt)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/")
        .maxAge(Duration.ofSeconds(accessCookieMaxAgeSeconds))
        .build();

    ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", rotated.rawToken())
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/api/auth/refresh")
        .maxAge(Duration.ofSeconds(refreshCookieMaxAgeSeconds))
        .build();

    return ResponseEntity.ok()
        .header("Set-Cookie", accessCookie.toString())
        .header("Set-Cookie", refreshCookie.toString())
        .build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    String rawRefresh = getCookieValue(request, "refresh_token");
    if (rawRefresh != null) {
      refreshTokenService.revokeByRaw(rawRefresh);
    }

    ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/")
        .maxAge(Duration.ZERO)
        .build();

    ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/api/auth/refresh")
        .maxAge(Duration.ZERO)
        .build();

    ResponseCookie csrfCookie = ResponseCookie.from("csrf_token", "")
        .httpOnly(false)
        .secure(cookieSecure)
        .sameSite(sameSite)
        .path("/")
        .maxAge(Duration.ZERO)
        .build();

    return ResponseEntity.ok()
        .header("Set-Cookie", accessCookie.toString())
        .header("Set-Cookie", refreshCookie.toString())
        .header("Set-Cookie", csrfCookie.toString())
        .build();
  }

  @GetMapping("/me")
  public ResponseEntity<AuthResponse> me(org.springframework.security.core.Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String email = String.valueOf(authentication.getPrincipal());
    String role = authentication.getAuthorities().stream()
        .findFirst()
        .map(a -> a.getAuthority().replaceFirst("^ROLE_", "").toLowerCase())
        .orElse("user");
    return ResponseEntity.ok(new AuthResponse(email, role));
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
