package com.blog.dto;

import java.time.OffsetDateTime;

public class PostDetailDto {

  private final Long id;
  private final String title;
  private final String summary;
  private final String contentMd;
  private final String status;
  private final OffsetDateTime publishedAt;
  private final OffsetDateTime createdAt;
  private final OffsetDateTime updatedAt;

  public PostDetailDto(
      Long id,
      String title,
      String summary,
      String contentMd,
      String status,
      OffsetDateTime publishedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.title = title;
    this.summary = summary;
    this.contentMd = contentMd;
    this.status = status;
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

  public String getContentMd() {
    return contentMd;
  }

  public String getStatus() {
    return status;
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
