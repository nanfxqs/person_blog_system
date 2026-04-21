package com.blog.client;

import com.blog.config.DeepSeekConfig.DeepSeekProperties;
import com.blog.dto.deepseek.ChatCompletionRequest;
import com.blog.dto.deepseek.ChatCompletionRequest.Message;
import com.blog.dto.deepseek.ChatCompletionResponse;
import com.blog.exception.DeepSeekException;
import com.blog.exception.DeepSeekException.Type;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class DeepSeekClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

  private final RestTemplate restTemplate;
  private final DeepSeekProperties properties;

  public DeepSeekClient(
      @Qualifier("deepSeekRestTemplate") RestTemplate deepSeekRestTemplate,
      DeepSeekProperties properties) {
    this.restTemplate = deepSeekRestTemplate;
    this.properties = properties;
  }

  public String generateSummary(String content) {
    ensureConfigured();
    String safeContent = truncate(content, properties.getMaxContentLength());
    String prompt = "请为以下文章生成一段简短摘要（100字以内）：\n\n" + safeContent;
    return chat(prompt);
  }

  public String answerQuestion(String content, String question) {
    ensureConfigured();

    String q = question == null ? "" : question;
    if (q.length() > properties.getMaxQuestionLength()) {
      throw new DeepSeekException(Type.CONTENT_TOO_LONG,
          "Question length exceeds max-question-length: " + q.length());
    }

    String safeContent = truncate(content, properties.getMaxContentLength());
    String prompt = "基于以下文章内容回答问题。如果问题与文章无关，请说明。\n\n"
        + "文章：\n" + safeContent + "\n\n"
        + "问题：" + q + "\n\n"
        + "请用中文回答：";

    return chat(prompt);
  }

  private void ensureConfigured() {
    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      throw new DeepSeekException(Type.CONFIG_MISSING, "DeepSeek API key not configured");
    }
  }

  private String chat(String prompt) {
    String baseUrl = properties.getBaseUrl();
    String url = joinUrl(baseUrl != null ? baseUrl : "https://api.deepseek.com", "/chat/completions");

    ChatCompletionRequest request = new ChatCompletionRequest(
        properties.getModel(),
        List.of(new Message("user", prompt)));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(properties.getApiKey());

    HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);

    long startNs = System.nanoTime();
    try {
      ResponseEntity<ChatCompletionResponse> resp = restTemplate.exchange(
          url,
          HttpMethod.POST,
          entity,
          ChatCompletionResponse.class);
      long ms = Duration.ofNanos(System.nanoTime() - startNs).toMillis();

      String extracted = extractContent(resp.getBody());
      log.info("DeepSeek chat/completions ok in {}ms (promptLen={}, answerLen={})",
          ms, prompt.length(), extracted.length());
      return extracted;
    } catch (HttpStatusCodeException e) {
      long ms = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
      String body = safeBody(e.getResponseBodyAsString());
      log.warn("DeepSeek upstream error status={} in {}ms body={}", e.getStatusCode(), ms, body);
      throw new DeepSeekException(Type.UPSTREAM_ERROR,
          "DeepSeek upstream error: " + e.getStatusCode(), e);
    } catch (ResourceAccessException e) {
      long ms = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
      if (isTimeout(e)) {
        log.warn("DeepSeek timeout in {}ms", ms);
        throw new DeepSeekException(Type.TIMEOUT, "DeepSeek request timed out", e);
      }
      log.warn("DeepSeek resource access error in {}ms: {}", ms, e.getMessage());
      throw new DeepSeekException(Type.UPSTREAM_ERROR, "DeepSeek request failed", e);
    } catch (RuntimeException e) {
      long ms = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
      log.warn("DeepSeek unexpected error in {}ms: {}", ms, e.getMessage());
      throw new DeepSeekException(Type.UPSTREAM_ERROR, "DeepSeek unexpected error", e);
    }
  }

  private static String extractContent(ChatCompletionResponse response) {
    if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
      throw new DeepSeekException(Type.UPSTREAM_ERROR, "DeepSeek response missing choices");
    }
    ChatCompletionResponse.Choice first = response.getChoices().get(0);
    if (first == null || first.getMessage() == null || first.getMessage().getContent() == null) {
      throw new DeepSeekException(Type.UPSTREAM_ERROR, "DeepSeek response missing message content");
    }
    return first.getMessage().getContent().trim();
  }

  private static boolean isTimeout(ResourceAccessException e) {
    Throwable cause = e.getCause();
    if (cause instanceof SocketTimeoutException) {
      return true;
    }
    String msg = e.getMessage();
    return msg != null && msg.toLowerCase().contains("timed out");
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) {
      return "";
    }
    if (maxLen <= 0 || s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen);
  }

  private static String safeBody(String body) {
    if (body == null) {
      return "";
    }
    String b = body.replaceAll("\r\n|\n|\r", " ").trim();
    if (b.length() > 500) {
      return b.substring(0, 500);
    }
    return b;
  }

  private static String joinUrl(String baseUrl, String path) {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.deepseek.com";
    }
    String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String p = (path != null && path.startsWith("/")) ? path : "/" + (path == null ? "" : path);
    return b + p;
  }
}
