package com.blog.exception;

public class DeepSeekException extends RuntimeException {

  public enum Type {
    CONFIG_MISSING,
    TIMEOUT,
    UPSTREAM_ERROR,
    CONTENT_TOO_LONG
  }

  private final Type type;
  private final String rawMessage;

  public DeepSeekException(Type type, String rawMessage) {
    super(rawMessage);
    this.type = type;
    this.rawMessage = rawMessage;
  }

  public DeepSeekException(Type type, String rawMessage, Throwable cause) {
    super(rawMessage, cause);
    this.type = type;
    this.rawMessage = rawMessage;
  }

  public Type getType() {
    return type;
  }

  public String getRawMessage() {
    return rawMessage;
  }
}
