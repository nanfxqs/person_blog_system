package com.blog.security;

import com.blog.entity.RefreshToken;
import com.blog.entity.User;
import com.blog.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  public record IssuedRefreshToken(String rawToken, OffsetDateTime expiresAt) {}

  private final RefreshTokenRepository refreshTokenRepository;
  private final Duration refreshTtl;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      @Value("${app.refresh.ttl-seconds:604800}") long refreshTtlSeconds) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
  }

  @Transactional
  public IssuedRefreshToken create(User user) {
    String raw = UUID.randomUUID().toString();
    String hash = sha256(raw);
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(refreshTtl.getSeconds());

    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setTokenHash(hash);
    token.setExpiresAt(expiresAt);
    refreshTokenRepository.save(token);

    return new IssuedRefreshToken(raw, expiresAt);
  }

  public Optional<RefreshToken> findValidByRaw(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    String hash = sha256(rawToken);
    return refreshTokenRepository.findByTokenHash(hash)
        .filter(rt -> rt.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
  }

  @Transactional
  public IssuedRefreshToken rotate(String rawToken) {
    RefreshToken existing = findValidByRaw(rawToken).orElseThrow(IllegalArgumentException::new);
    User user = existing.getUser();
    refreshTokenRepository.delete(existing);
    refreshTokenRepository.flush();
    return create(user);
  }

  @Transactional
  public void revokeByRaw(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    refreshTokenRepository.findByTokenHash(sha256(rawToken)).ifPresent(refreshTokenRepository::delete);
  }

  @Transactional
  public void revokeAllForUser(Long userId) {
    refreshTokenRepository.deleteByUser_Id(userId);
  }

  private static String sha256(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
