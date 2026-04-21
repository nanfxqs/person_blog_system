package com.blog.controller;

import com.blog.dto.QaRequest;
import com.blog.dto.QaResponse;
import com.blog.entity.User;
import com.blog.exception.DeepSeekException;
import com.blog.exception.DeepSeekException.Type;
import com.blog.repository.UserRepository;
import com.blog.service.AiQaService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class AiQaController {

  private final AiQaService aiQaService;
  private final UserRepository userRepository;

  public AiQaController(AiQaService aiQaService, UserRepository userRepository) {
    this.aiQaService = aiQaService;
    this.userRepository = userRepository;
  }

  @PostMapping("/{id}/qa")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> answer(@PathVariable Long id, @Valid @RequestBody QaRequest request) {
    Long userId = getCurrentUserId();
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", "Unauthorized"));
    }

    try {
      String answer = aiQaService.answerQuestion(id, userId, request.getQuestion());
      return ResponseEntity.ok(new QaResponse(answer));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("message", "Post not found"));
    } catch (DeepSeekException e) {
      if (e.getType() == Type.CONTENT_TOO_LONG) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("message", "Question too long"));
      }
      if (e.getType() == Type.CONFIG_MISSING) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "DeepSeek API key not configured"));
      }
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("message", "AI question answering failed"));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("message", "AI question answering failed"));
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
