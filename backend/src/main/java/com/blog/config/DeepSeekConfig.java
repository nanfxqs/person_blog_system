package com.blog.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(DeepSeekConfig.DeepSeekProperties.class)
public class DeepSeekConfig {

  @Bean
  public RestTemplate deepSeekRestTemplate(DeepSeekProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
    factory.setReadTimeout((int) Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis());
    return new RestTemplate(factory);
  }

  @ConfigurationProperties(prefix = "app.deepseek")
  public static class DeepSeekProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private long timeoutSeconds = 15;
    private int maxContentLength = 20000;
    private int maxQuestionLength = 500;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public long getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxContentLength() {
      return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
      this.maxContentLength = maxContentLength;
    }

    public int getMaxQuestionLength() {
      return maxQuestionLength;
    }

    public void setMaxQuestionLength(int maxQuestionLength) {
      this.maxQuestionLength = maxQuestionLength;
    }
  }
}
