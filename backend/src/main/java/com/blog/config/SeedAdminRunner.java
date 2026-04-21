package com.blog.config;

import com.blog.entity.User;
import com.blog.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SeedAdminRunner implements CommandLineRunner {

  private final UserRepository userRepository;
  private final String adminEmail;
  private final String adminPassword;

  public SeedAdminRunner(
      UserRepository userRepository,
      @Value("${app.admin.email:}") String adminEmail,
      @Value("${app.admin.password:}") String adminPassword) {
    this.userRepository = userRepository;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
  }

  @Override
  public void run(String... args) {
    if (adminEmail == null || adminEmail.isBlank()) {
      return;
    }
    if (adminPassword == null || adminPassword.isBlank()) {
      return;
    }

    boolean hasAdmin = userRepository.findAll().stream()
        .anyMatch(u -> "admin".equalsIgnoreCase(u.getRole()));
    if (hasAdmin) {
      return;
    }

    User admin = userRepository.findByEmail(adminEmail).orElseGet(User::new);
    admin.setEmail(adminEmail);
    admin.setRole("admin");
    admin.setPasswordHash(new BCryptPasswordEncoder().encode(adminPassword));
    userRepository.save(admin);
  }
}
