package com.blog.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CsrfService {

  public String generateToken() {
    return UUID.randomUUID().toString();
  }

  public boolean isValid(String cookieToken, String providedToken) {
    if (cookieToken == null || cookieToken.isBlank()) {
      return false;
    }
    if (providedToken == null || providedToken.isBlank()) {
      return false;
    }
    return Objects.equals(cookieToken, providedToken);
  }
}
