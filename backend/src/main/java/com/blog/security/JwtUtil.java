package com.blog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private final SecretKey signingKey;
  private final Duration accessTokenTtl;

  public JwtUtil(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
  }

  public String generateAccessToken(String email, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(email)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTokenTtl)))
        .claim("role", role)
        .signWith(signingKey)
        .compact();
  }

  public Claims parseAndValidate(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public boolean isValid(String token) {
    try {
      parseAndValidate(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
