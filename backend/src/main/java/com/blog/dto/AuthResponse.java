package com.blog.dto;

public class AuthResponse {

  private final String email;
  private final String role;

  public AuthResponse(String email, String role) {
    this.email = email;
    this.role = role;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }
}
