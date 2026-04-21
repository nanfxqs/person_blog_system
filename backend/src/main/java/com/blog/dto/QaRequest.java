package com.blog.dto;

import jakarta.validation.constraints.NotBlank;

public class QaRequest {

  @NotBlank
  private String question;

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }
}
