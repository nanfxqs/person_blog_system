package com.blog.dto;

import jakarta.validation.constraints.NotBlank;

public class CreatePostRequest {

  @NotBlank
  private String title;

  private String summary;

  @NotBlank
  private String contentMd;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getContentMd() {
    return contentMd;
  }

  public void setContentMd(String contentMd) {
    this.contentMd = contentMd;
  }
}
