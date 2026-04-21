package com.blog.controller;

import com.blog.dto.CommentDto;
import com.blog.dto.CreateCommentRequest;
import com.blog.entity.Comment;
import com.blog.entity.Post;
import com.blog.entity.User;
import com.blog.repository.CommentRepository;
import com.blog.repository.PostRepository;
import com.blog.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CommentController {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public CommentController(
      CommentRepository commentRepository,
      PostRepository postRepository,
      UserRepository userRepository) {
    this.commentRepository = commentRepository;
    this.postRepository = postRepository;
    this.userRepository = userRepository;
  }

  @GetMapping("/posts/{postId}/comments")
  public ResponseEntity<List<CommentDto>> listByPost(@PathVariable Long postId) {
    Post post = requireVisiblePost(postId);
    if (post == null) {
      return ResponseEntity.notFound().build();
    }

    List<CommentDto> result = commentRepository.findByPostIdOrderByCreatedAtDesc(postId)
        .stream()
        .map(CommentController::toDto)
        .toList();
    return ResponseEntity.ok(result);
  }

  @PostMapping("/posts/{postId}/comments")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<CommentDto> create(
      @PathVariable Long postId,
      @Valid @RequestBody CreateCommentRequest request) {

    Post post = requireVisiblePost(postId);
    if (post == null) {
      return ResponseEntity.notFound().build();
    }

    Long userId = getCurrentUserId();
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Comment comment = new Comment();
    comment.setPostId(postId);
    comment.setUserId(userId);
    comment.setContent(request.getContent());
    Comment saved = commentRepository.save(comment);

    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
  }

  @GetMapping("/admin/comments")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<CommentDto>> listAll() {
    List<CommentDto> result = commentRepository.findAllByOrderByCreatedAtDesc()
        .stream()
        .map(CommentController::toDto)
        .toList();
    return ResponseEntity.ok(result);
  }

  @DeleteMapping("/admin/comments/{commentId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteByAdmin(@PathVariable Long commentId) {
    if (!commentRepository.existsById(commentId)) {
      return ResponseEntity.notFound().build();
    }
    commentRepository.deleteById(commentId);
    return ResponseEntity.noContent().build();
  }

  private Post requireVisiblePost(Long postId) {
    Post post = postRepository.findById(postId).orElse(null);
    if (post == null) {
      return null;
    }
    if ("draft".equalsIgnoreCase(post.getStatus()) && !isAdmin()) {
      return null;
    }
    return post;
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

  private static CommentDto toDto(Comment comment) {
    return new CommentDto(
        comment.getId(),
        comment.getContent(),
        comment.getUserId(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
