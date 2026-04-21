package com.blog.dto;

public class QaResponse {

  private final String answer;

  public QaResponse(String answer) {
    this.answer = answer;
  }

  public String getAnswer() {
    return answer;
  }
}
