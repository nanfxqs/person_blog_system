package com.blog.controller;

import com.blog.dto.PostDetailDto;
import com.blog.dto.PostSummaryDto;
import com.blog.entity.Post;
import com.blog.repository.PostRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

  private final PostRepository postRepository;

  public PostController(PostRepository postRepository) {
    this.postRepository = postRepository;
  }

  @GetMapping
  public ResponseEntity<Page<PostSummaryDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishedFrom,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishedTo) {

    int safeSize = Math.min(Math.max(size, 1), 50);
    int safePage = Math.max(page, 0);

    Specification<Post> spec = Specification
        .where(hasStatus("published"))
        .and(titleContainsIgnoreCase(keyword))
        .and(publishedAtFrom(publishedFrom))
        .and(publishedAtTo(publishedTo));

    var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "publishedAt"));
    Page<PostSummaryDto> result = postRepository.findAll(spec, pageable)
        .map(PostController::toSummaryDto);

    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PostDetailDto> getById(@PathVariable Long id) {
    Post post = postRepository.findById(id).orElse(null);
    if (post == null) {
      return ResponseEntity.notFound().build();
    }

    if ("draft".equalsIgnoreCase(post.getStatus()) && !isAdmin()) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(toDetailDto(post));
  }

  private static PostSummaryDto toSummaryDto(Post post) {
    return new PostSummaryDto(
        post.getId(),
        post.getTitle(),
        post.getSummary(),
        post.getStatus(),
        post.getPublishedAt(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }

  private static PostDetailDto toDetailDto(Post post) {
    return new PostDetailDto(
        post.getId(),
        post.getTitle(),
        post.getSummary(),
        post.getContentMd(),
        post.getStatus(),
        post.getPublishedAt(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }

  private static Specification<Post> hasStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    return (root, query, cb) -> cb.equal(
        cb.lower(root.get("status")),
        status.toLowerCase(Locale.ROOT));
  }

  private static Specification<Post> titleContainsIgnoreCase(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("title")), like);
  }

  private static Specification<Post> publishedAtFrom(LocalDateTime from) {
    if (from == null) {
      return null;
    }
    OffsetDateTime fromTs = from.atOffset(ZoneOffset.UTC);
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("publishedAt"), fromTs);
  }

  private static Specification<Post> publishedAtTo(LocalDateTime to) {
    if (to == null) {
      return null;
    }
    OffsetDateTime toTs = to.atOffset(ZoneOffset.UTC);
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("publishedAt"), toTs);
  }

  private static boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    for (GrantedAuthority authority : auth.getAuthorities()) {
      if ("ROLE_ADMIN".equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
