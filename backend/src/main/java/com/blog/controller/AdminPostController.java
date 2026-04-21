package com.blog.controller;

import com.blog.dto.AdminPostDto;
import com.blog.dto.CreatePostRequest;
import com.blog.dto.UpdatePostRequest;
import com.blog.entity.Post;
import com.blog.entity.User;
import com.blog.repository.PostRepository;
import com.blog.repository.UserRepository;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public AdminPostController(PostRepository postRepository, UserRepository userRepository) {
    this.postRepository = postRepository;
    this.userRepository = userRepository;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Page<AdminPostDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status) {

    int safeSize = Math.min(Math.max(size, 1), 50);
    int safePage = Math.max(page, 0);

    Specification<Post> spec = Specification.where(hasStatus(status));
    var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));

    Page<AdminPostDto> result = postRepository.findAll(spec, pageable)
        .map(AdminPostController::toAdminDto);
    return ResponseEntity.ok(result);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AdminPostDto> create(@Valid @RequestBody CreatePostRequest request) {
    Long authorId = getCurrentUserId();
    if (authorId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Post post = new Post();
    post.setAuthorId(authorId);
    post.setTitle(request.getTitle());
    post.setSummary(request.getSummary());
    post.setContentMd(request.getContentMd());
    post.setStatus("draft");
    post.setPublishedAt(null);

    Post saved = postRepository.save(post);
    return ResponseEntity.status(HttpStatus.CREATED).body(toAdminDto(saved));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AdminPostDto> update(
      @PathVariable Long id,
      @Valid @RequestBody UpdatePostRequest request) {
    Post post = postRepository.findById(id).orElse(null);
    if (post == null) {
      return ResponseEntity.notFound().build();
    }

    post.setTitle(request.getTitle());
    post.setSummary(request.getSummary());
    post.setContentMd(request.getContentMd());
    Post saved = postRepository.save(post);

    return ResponseEntity.ok(toAdminDto(saved));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (!postRepository.existsById(id)) {
      return ResponseEntity.notFound().build();
    }
    postRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AdminPostDto> publish(@PathVariable Long id) {
    Post post = postRepository.findById(id).orElse(null);
    if (post == null) {
      return ResponseEntity.notFound().build();
    }

    post.setStatus("published");
    post.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    Post saved = postRepository.save(post);
    return ResponseEntity.ok(toAdminDto(saved));
  }

  @PostMapping("/{id}/unpublish")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AdminPostDto> unpublish(@PathVariable Long id) {
    Post post = postRepository.findById(id).orElse(null);
    if (post == null) {
      return ResponseEntity.notFound().build();
    }

    post.setStatus("draft");
    post.setPublishedAt(null);
    Post saved = postRepository.save(post);
    return ResponseEntity.ok(toAdminDto(saved));
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

  private static AdminPostDto toAdminDto(Post post) {
    return new AdminPostDto(
        post.getId(),
        post.getTitle(),
        post.getSummary(),
        post.getStatus(),
        post.getAuthorId(),
        post.getPublishedAt(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }

  private static Specification<Post> hasStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    String normalized = status.toLowerCase(Locale.ROOT);
    return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), normalized);
  }
}
