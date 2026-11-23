package com.cca.spring.service;

import com.cca.spring.domain.Thread;
import com.cca.spring.domain.ThreadMessage;
import com.cca.spring.mapper.ThreadMapper;
import com.cca.spring.mapper.ThreadMessageMapper;
import com.cca.spring.web.dto.ApiDtos.MessageResponse;
import com.cca.spring.web.dto.ApiDtos.PermissionResponse;
import com.cca.spring.web.dto.ApiDtos.ResolvePermissionRequest;
import com.cca.spring.web.dto.ApiDtos.SendMessageRequest;
import com.cca.spring.web.dto.ApiDtos.WsEvent;
import com.cca.spring.web.exception.BadRequestException;
import com.cca.spring.web.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  private final ThreadService threadService;
  private final ThreadMapper threadMapper;
  private final ThreadMessageMapper messageMapper;
  private final TimelineService timelineService;
  private final CopilotSessionManager sessionManager;
  private final TurnRegistry turnRegistry;
  private final EventBroadcaster broadcaster;
  private final PermissionService permissionService;
  private final com.cca.spring.config.AppProperties properties;

  public ChatService(ThreadService threadService,
                     ThreadMapper threadMapper,
                     ThreadMessageMapper messageMapper,
                     TimelineService timelineService,
                     CopilotSessionManager sessionManager,
                     TurnRegistry turnRegistry,
                     EventBroadcaster broadcaster,
                     PermissionService permissionService,
                     com.cca.spring.config.AppProperties properties) {
    this.threadService = threadService;
    this.threadMapper = threadMapper;
    this.messageMapper = messageMapper;
    this.timelineService = timelineService;
    this.sessionManager = sessionManager;
    this.turnRegistry = turnRegistry;
    this.broadcaster = broadcaster;
    this.permissionService = permissionService;
    this.properties = properties;
  }

  public MessageResponse send(String threadId, SendMessageRequest request) {
    if (request.content() == null || request.content().isBlank()) {
      throw new BadRequestException("消息内容不能为空");
    }
    Thread thread = threadService.getEntity(threadId);
    if (Boolean.TRUE.equals(thread.getArchived())) {
      throw new BadRequestException("会话已归档，无法继续发送消息");
    }
    if ("RUNNING".equals(thread.getStatus())) {
      throw new ConflictException("会话正在执行，请等待当前回合结束或先中止");
    }

    Instant now = Instant.now();
    String turnId = UUID.randomUUID().toString();
    ThreadMessage userMessage = new ThreadMessage();
    userMessage.setId(turnId);
    userMessage.setThreadId(threadId);
    userMessage.setRole("user");
    userMessage.setTurnId(turnId);
    userMessage.setContent(request.content());
    userMessage.setStatus("complete");
    userMessage.setInputTokens(0L);
    userMessage.setOutputTokens(0L);
    userMessage.setCreatedAt(now);
    messageMapper.insert(userMessage);

    if (request.model() != null && !request.model().isBlank()) {
      thread.setModel(request.model().trim());
    }
    if (request.reasoningEffort() != null) {
      thread.setReasoningEffort(request.reasoningEffort().isBlank() ? null : request.reasoningEffort().trim());
    }
    thread.setStatus("RUNNING");
    thread.setLastError(null);
    thread.setLastMessagePreview(truncate(request.content().strip(), 200));
    thread.setMessageCount((thread.getMessageCount() == null ? 0 : thread.getMessageCount()) + 1);
    thread.setLastMessageAt(now);
    thread.setUpdatedAt(now);
    threadMapper.updateByPrimaryKeySelective(thread);

    TurnRegistry.TurnState turn = turnRegistry.start(threadId, turnId);
    broadcaster.broadcast(threadId, WsEvent.of("turn.started", threadId, Map.of(
        "turnId", turn.getTurnId(), "startedAt", turn.getStartedAt())));
    broadcaster.broadcast(threadId, WsEvent.of("user.message", threadId,
        timelineService.toMessage(userMessage)));

    try {
      sessionManager.send(thread, request.content(), turnId);
      scheduleTimeout(threadId);
    } catch (Exception ex) {
      failTurn(threadId, ex);
    }
    return timelineService.toMessage(userMessage);
  }

  public void abort(String threadId) {
    Thread thread = threadService.getEntity(threadId);
    sessionManager.abort(threadId);
    TurnRegistry.TurnState turn = turnRegistry.finish(threadId);
    permissionService.cancelPending(threadId);
    thread.setStatus("IDLE");
    thread.setUpdatedAt(Instant.now());
    threadMapper.updateByPrimaryKeySelective(thread);
    broadcaster.broadcast(threadId, WsEvent.of("turn.completed", threadId, Map.of(
        "turnId", turn == null ? "" : turn.getTurnId(), "aborted", true)));
  }

  public PermissionResponse resolvePermission(String permissionId, ResolvePermissionRequest request) {
    return permissionService.resolve(permissionId, request);
  }

  public List<PermissionResponse> pendingPermissions(String threadId) {
    threadService.getEntity(threadId);
    return permissionService.pending(threadId);
  }

  private void scheduleTimeout(String threadId) {
    long timeoutMinutes = properties.getCopilot().getTurnTimeoutMinutes();
    if (timeoutMinutes <= 0) {
      return;
    }
    CompletableFuture.delayedExecutor(timeoutMinutes, TimeUnit.MINUTES).execute(() -> {
      if (!turnRegistry.isActive(threadId)) {
        return;
      }
      log.warn("Copilot 回合超时 thread={}", threadId);
      sessionManager.abort(threadId);
      failTurn(threadId, new IllegalStateException("Copilot 回合超时（" + timeoutMinutes + " 分钟）"));
    });
  }

  private void failTurn(String threadId, Throwable error) {
    TurnRegistry.TurnState turn = turnRegistry.finish(threadId);
    String message = rootMessage(error);
    permissionService.cancelPending(threadId);
    threadMapper.selectByPrimaryKey(threadId).ifPresent(thread -> {
      thread.setStatus("ERROR");
      thread.setLastError(message);
      thread.setUpdatedAt(Instant.now());
      threadMapper.updateByPrimaryKeySelective(thread);
    });
    if (turn != null) {
      broadcaster.broadcast(threadId, WsEvent.of("thread.error", threadId, Map.of(
          "message", message, "turnId", turn.getTurnId())));
      broadcaster.broadcast(threadId, WsEvent.of("turn.completed", threadId, Map.of(
          "turnId", turn.getTurnId(), "aborted", false)));
    }
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() > max ? value.substring(0, max) : value;
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
}
