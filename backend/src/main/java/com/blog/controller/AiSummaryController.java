package com.blog.controller;

import com.blog.dto.GenerateSummaryResponse;
import com.blog.entity.User;
import com.blog.exception.DeepSeekException;
import com.blog.exception.DeepSeekException.Type;
import com.blog.repository.UserRepository;
import com.blog.service.AiSummaryService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/posts")
public class AiSummaryController {

  private final AiSummaryService aiSummaryService;
  private final UserRepository userRepository;

  public AiSummaryController(AiSummaryService aiSummaryService, UserRepository userRepository) {
    this.aiSummaryService = aiSummaryService;
    this.userRepository = userRepository;
  }

  @PostMapping("/{id}/summary:generate")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> generate(@PathVariable Long id) {
    Long adminUserId = getCurrentUserId();
    if (adminUserId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", "Unauthorized"));
    }

    try {
      String summary = aiSummaryService.generateSummary(id, adminUserId);
      return ResponseEntity.ok(new GenerateSummaryResponse(summary));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("message", "Post not found"));
    } catch (DeepSeekException e) {
      if (e.getType() == Type.CONFIG_MISSING) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "DeepSeek API key not configured"));
      }
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("message", "AI summary generation failed"));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("message", "AI summary generation failed"));
    }
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return null;
    }
    String email = String.valueOf(auth.getPrincipal());
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
      return null;
    }
    return user.getId();
  }
}
