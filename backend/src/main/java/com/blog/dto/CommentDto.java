package com.blog.dto;

import java.time.OffsetDateTime;

public class CommentDto {

  private final Long id;
  private final String content;
  private final Long userId;
  private final OffsetDateTime createdAt;
  private final OffsetDateTime updatedAt;

  public CommentDto(
      Long id,
      String content,
      Long userId,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.content = content;
    this.userId = userId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getContent() {
    return content;
  }

  public Long getUserId() {
    return userId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
