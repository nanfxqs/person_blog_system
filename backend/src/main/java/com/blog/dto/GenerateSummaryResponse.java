package com.blog.dto;

public class GenerateSummaryResponse {

  private final String summary;

  public GenerateSummaryResponse(String summary) {
    this.summary = summary;
  }

  public String getSummary() {
    return summary;
  }
}
