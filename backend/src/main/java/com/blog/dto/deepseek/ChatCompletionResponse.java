package com.blog.dto.deepseek;

import java.util.List;

public class ChatCompletionResponse {

  private List<Choice> choices;

  public List<Choice> getChoices() {
    return choices;
  }

  public void setChoices(List<Choice> choices) {
    this.choices = choices;
  }

  public static class Choice {

    private int index;
    private Message message;

    public int getIndex() {
      return index;
    }

    public void setIndex(int index) {
      this.index = index;
    }

    public Message getMessage() {
      return message;
    }

    public void setMessage(Message message) {
      this.message = message;
    }
  }

  public static class Message {

    private String role;
    private String content;

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }
}
