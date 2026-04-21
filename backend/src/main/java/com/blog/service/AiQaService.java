package com.blog.service;

import com.blog.client.DeepSeekClient;
import com.blog.config.DeepSeekConfig.DeepSeekProperties;
import com.blog.entity.AiQaLog;
import com.blog.entity.Post;
import com.blog.exception.DeepSeekException;
import com.blog.repository.AiQaLogRepository;
import com.blog.repository.PostRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiQaService {

  private static final Logger log = LoggerFactory.getLogger(AiQaService.class);

  private final PostRepository postRepository;
  private final AiQaLogRepository aiQaLogRepository;
  private final DeepSeekClient deepSeekClient;
  private final DeepSeekProperties deepSeekProperties;
  private final TransactionTemplate requiresNewTx;

  public AiQaService(
      PostRepository postRepository,
      AiQaLogRepository aiQaLogRepository,
      DeepSeekClient deepSeekClient,
      DeepSeekProperties deepSeekProperties,
      PlatformTransactionManager transactionManager) {
    this.postRepository = postRepository;
    this.aiQaLogRepository = aiQaLogRepository;
    this.deepSeekClient = deepSeekClient;
    this.deepSeekProperties = deepSeekProperties;

    TransactionTemplate tpl = new TransactionTemplate(transactionManager);
    tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.requiresNewTx = tpl;
  }

  @Transactional
  public String answerQuestion(Long postId, Long userId, String question) {
    String q = question == null ? "" : question;

    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

    if ("draft".equalsIgnoreCase(post.getStatus()) && !isAdmin()) {
      saveFailLogRequiresNew(postId, userId, q, 0, "Post not visible");
      throw new IllegalArgumentException("Post not visible: " + postId);
    }

    long startNs = System.nanoTime();
    try {
      String answer = deepSeekClient.answerQuestion(post.getContentMd(), q);
      int latencyMs = safeLatencyMs(startNs);

      AiQaLog qaLog = new AiQaLog();
      qaLog.setPostId(postId);
      qaLog.setUserId(userId);
      qaLog.setQuestion(q);
      qaLog.setAnswer(answer);
      qaLog.setModel(deepSeekProperties.getModel());
      qaLog.setLatencyMs(latencyMs);
      qaLog.setStatus("success");
      qaLog.setErrorMessage(null);
      aiQaLogRepository.save(qaLog);

      log.info("AI QA success postId={} userId={} latencyMs={} qLen={} aLen={}",
          postId,
          userId,
          latencyMs,
          q.length(),
          answer == null ? 0 : answer.length());

      return answer;
    } catch (DeepSeekException e) {
      int latencyMs = safeLatencyMs(startNs);
      saveFailLogRequiresNew(postId, userId, q, latencyMs, e.getRawMessage());
      log.warn("AI QA failed postId={} userId={} latencyMs={} type={} msg={}",
          postId, userId, latencyMs, e.getType(), e.getRawMessage());
      throw e;
    } catch (RuntimeException e) {
      int latencyMs = safeLatencyMs(startNs);
      saveFailLogRequiresNew(postId, userId, q, latencyMs, e.getMessage());
      log.warn("AI QA failed postId={} userId={} latencyMs={} msg={}",
          postId, userId, latencyMs, e.getMessage());
      throw e;
    }
  }

  private void saveFailLogRequiresNew(
      Long postId,
      Long userId,
      String question,
      int latencyMs,
      String errorMessage) {
    requiresNewTx.executeWithoutResult(status -> {
      AiQaLog qaLog = new AiQaLog();
      qaLog.setPostId(postId);
      qaLog.setUserId(userId);
      qaLog.setQuestion(question == null ? "" : question);
      qaLog.setAnswer("");
      qaLog.setModel(deepSeekProperties.getModel());
      qaLog.setLatencyMs(latencyMs);
      qaLog.setStatus("fail");
      qaLog.setErrorMessage(truncate(errorMessage, 1000));
      aiQaLogRepository.save(qaLog);
    });
  }

  private static int safeLatencyMs(long startNs) {
    long ms = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
    if (ms < 0) {
      return 0;
    }
    if (ms > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) ms;
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) {
      return null;
    }
    if (maxLen <= 0 || s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen);
  }

  private static boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    for (GrantedAuthority authority : auth.getAuthorities()) {
      if ("ROLE_ADMIN".equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
