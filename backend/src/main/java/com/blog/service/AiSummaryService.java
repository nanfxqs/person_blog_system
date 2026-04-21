package com.blog.service;

import com.blog.client.DeepSeekClient;
import com.blog.config.DeepSeekConfig.DeepSeekProperties;
import com.blog.entity.AiSummaryLog;
import com.blog.entity.Post;
import com.blog.exception.DeepSeekException;
import com.blog.repository.AiSummaryLogRepository;
import com.blog.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiSummaryService {

  private final PostRepository postRepository;
  private final AiSummaryLogRepository aiSummaryLogRepository;
  private final DeepSeekClient deepSeekClient;
  private final DeepSeekProperties deepSeekProperties;
  private final TransactionTemplate requiresNewTx;

  public AiSummaryService(
      PostRepository postRepository,
      AiSummaryLogRepository aiSummaryLogRepository,
      DeepSeekClient deepSeekClient,
      DeepSeekProperties deepSeekProperties,
      PlatformTransactionManager transactionManager) {
    this.postRepository = postRepository;
    this.aiSummaryLogRepository = aiSummaryLogRepository;
    this.deepSeekClient = deepSeekClient;
    this.deepSeekProperties = deepSeekProperties;

    TransactionTemplate tpl = new TransactionTemplate(transactionManager);
    tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.requiresNewTx = tpl;
  }

  @Transactional
  public String generateSummary(Long postId, Long adminUserId) {
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

    long startMs = System.currentTimeMillis();
    try {
      String summary = deepSeekClient.generateSummary(post.getContentMd());
      int latencyMs = safeLatencyMs(startMs);

      post.setSummary(summary);
      postRepository.save(post);

      AiSummaryLog log = new AiSummaryLog();
      log.setPostId(postId);
      log.setAdminUserId(adminUserId);
      log.setModel(deepSeekProperties.getModel());
      log.setLatencyMs(latencyMs);
      log.setStatus("success");
      log.setErrorMessage(null);
      aiSummaryLogRepository.save(log);

      return summary;
    } catch (DeepSeekException e) {
      int latencyMs = safeLatencyMs(startMs);
      saveFailLogRequiresNew(postId, adminUserId, latencyMs, e.getRawMessage());
      throw e;
    } catch (RuntimeException e) {
      int latencyMs = safeLatencyMs(startMs);
      saveFailLogRequiresNew(postId, adminUserId, latencyMs, e.getMessage());
      throw e;
    }
  }

  private void saveFailLogRequiresNew(Long postId, Long adminUserId, int latencyMs, String errorMessage) {
    requiresNewTx.executeWithoutResult(status -> {
      AiSummaryLog log = new AiSummaryLog();
      log.setPostId(postId);
      log.setAdminUserId(adminUserId);
      log.setModel(deepSeekProperties.getModel());
      log.setLatencyMs(latencyMs);
      log.setStatus("fail");
      log.setErrorMessage(errorMessage);
      aiSummaryLogRepository.save(log);
    });
  }

  private static int safeLatencyMs(long startMs) {
    long ms = System.currentTimeMillis() - startMs;
    if (ms < 0) {
      return 0;
    }
    if (ms > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) ms;
  }
}
