package com.blog.dto;

import java.time.OffsetDateTime;

public class AdminPostDto {

  private final Long id;
  private final String title;
  private final String summary;
  private final String status;
  private final Long authorId;
  private final OffsetDateTime publishedAt;
  private final OffsetDateTime createdAt;
  private final OffsetDateTime updatedAt;

  public AdminPostDto(
      Long id,
      String title,
      String summary,
      String status,
      Long authorId,
      OffsetDateTime publishedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.title = title;
    this.summary = summary;
    this.status = status;
    this.authorId = authorId;
    this.publishedAt = publishedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getStatus() {
    return status;
  }

  public Long getAuthorId() {
    return authorId;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
