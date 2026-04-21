package com.blog.repository;

import com.blog.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
  List<Comment> findByPostIdOrderByCreatedAtDesc(Long postId);
  List<Comment> findAllByOrderByCreatedAtDesc();
}
