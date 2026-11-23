package com.cca.spring.service;

import com.cca.spring.domain.Thread;
import com.cca.spring.domain.ThreadEvent;
import com.cca.spring.domain.ThreadMessage;
import com.cca.spring.mapper.ThreadEventMapper;
import com.cca.spring.mapper.ThreadMapper;
import com.cca.spring.mapper.ThreadMessageMapper;
import com.cca.spring.web.dto.ApiDtos.ActivityResponse;
import com.cca.spring.web.dto.ApiDtos.MessageResponse;
import com.cca.spring.web.dto.ApiDtos.WsEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.copilot.generated.AssistantMessageDeltaEvent;
import com.microsoft.copilot.generated.AssistantMessageEvent;
import com.microsoft.copilot.generated.AssistantMessageStartEvent;
import com.microsoft.copilot.generated.AssistantReasoningDeltaEvent;
import com.microsoft.copilot.generated.AssistantReasoningEvent;
import com.microsoft.copilot.generated.AssistantTurnEndEvent;
import com.microsoft.copilot.generated.AssistantTurnStartEvent;
import com.microsoft.copilot.generated.SessionErrorEvent;
import com.microsoft.copilot.generated.SessionEvent;
import com.microsoft.copilot.generated.SessionIdleEvent;
import com.microsoft.copilot.generated.SessionResumeEvent;
import com.microsoft.copilot.generated.SessionStartEvent;
import com.microsoft.copilot.generated.SessionTitleChangedEvent;
import com.microsoft.copilot.generated.SessionUsageInfoEvent;
import com.microsoft.copilot.generated.SessionWarningEvent;
import com.microsoft.copilot.generated.ToolExecutionCompleteEvent;
import com.microsoft.copilot.generated.ToolExecutionProgressEvent;
import com.microsoft.copilot.generated.ToolExecutionStartEvent;
import com.microsoft.copilot.generated.UserMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CopilotEventDispatcher {

  private static final Logger log = LoggerFactory.getLogger(CopilotEventDispatcher.class);
  private static final int MAX_TOOL_RESULT_CHARS = 32_000;

  private final ThreadMapper threadMapper;
  private final ThreadMessageMapper messageMapper;
  private final ThreadEventMapper eventMapper;
  private final EventBroadcaster broadcaster;
  private final TurnRegistry turnRegistry;
  private final ObjectMapper objectMapper;
  private final ThreadService threadService;
  private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

  public CopilotEventDispatcher(ThreadMapper threadMapper,
                                ThreadMessageMapper messageMapper,
                                ThreadEventMapper eventMapper,
                                EventBroadcaster broadcaster,
                                TurnRegistry turnRegistry,
                                ObjectMapper objectMapper,
                                ThreadService threadService) {
    this.threadMapper = threadMapper;
    this.messageMapper = messageMapper;
    this.eventMapper = eventMapper;
    this.broadcaster = broadcaster;
    this.turnRegistry = turnRegistry;
    this.objectMapper = objectMapper;
    this.threadService = threadService;
  }

  public void dispatch(String threadId, SessionEvent event) {
    Object lock = locks.computeIfAbsent(threadId, key -> new Object());
    synchronized (lock) {
      try {
        handle(threadId, event);
      } catch (Exception ex) {
        log.error("处理 Copilot 事件失败 thread={} type={}", threadId, event.getType(), ex);
      }
    }
  }

  private void handle(String threadId, SessionEvent event) {
    if (event instanceof AssistantMessageStartEvent start) {
      String messageId = start.getData() == null ? null : start.getData().messageId();
      ensureAssistantMessage(threadId, messageId, "streaming");
    } else if (event instanceof AssistantMessageDeltaEvent delta) {
      String messageId = ensureAssistantMessage(threadId,
          delta.getData() == null ? null : delta.getData().messageId(), "streaming");
      String text = delta.getData() == null ? "" : delta.getData().deltaContent();
      TurnRegistry.TurnState turn = turnRegistry.get(threadId);
      if (turn != null) {
        turn.appendText(text);
      }
      broadcaster.broadcast(threadId, WsEvent.of("message.delta", threadId, Map.of(
          "messageId", messageId,
          "turnId", turn == null ? "" : turn.getTurnId(),
          "delta", text == null ? "" : text)));
    } else if (event instanceof AssistantReasoningDeltaEvent delta) {
      String text = delta.getData() == null ? "" : delta.getData().deltaContent();
      TurnRegistry.TurnState turn = turnRegistry.get(threadId);
      if (turn != null) {
        turn.appendReasoning(text);
      }
      broadcaster.broadcast(threadId, WsEvent.of("message.reasoning_delta", threadId, Map.of(
          "turnId", turn == null ? "" : turn.getTurnId(),
          "delta", text == null ? "" : text)));
    } else if (event instanceof AssistantMessageEvent message) {
      var data = message.getData();
      if (data != null) {
        upsertFinalAssistant(threadId, data.messageId(), data.content(), data.reasoningText(),
            data.model(), data.outputTokens());
        broadcaster.broadcast(threadId, WsEvent.of("message.completed", threadId,
            messageResponse(data.messageId())));
      }
    } else if (event instanceof AssistantReasoningEvent reasoning) {
      var data = reasoning.getData();
      if (data != null && data.content() != null) {
        broadcaster.broadcast(threadId, WsEvent.of("message.reasoning", threadId, Map.of(
            "turnId", turnId(threadId),
            "content", data.content())));
      }
    } else if (event instanceof AssistantTurnStartEvent) {
      // The application already emits turn.started when the user sends a message.
    } else if (event instanceof AssistantTurnEndEvent) {
      // session.idle is the authoritative turn boundary.
    } else if (event instanceof ToolExecutionStartEvent start) {
      var data = start.getData();
      if (data != null) {
        String toolName = toolName(data.mcpToolName(), data.toolName());
        ThreadEvent stored = persistEvent(threadId, event, "tool.start", "tool",
            toJson(data.arguments()), data.toolCallId(), toolName, "running", data.turnId());
        ActivityResponse activity = new ActivityResponse(
            data.toolCallId(), stored.getTurnId(), toolName, "running",
            stored.getContent(), null, stored.getCreatedAt(), null);
        broadcaster.broadcast(threadId, WsEvent.of("tool.started", threadId, activity));
      }
    } else if (event instanceof ToolExecutionProgressEvent progress) {
      var data = progress.getData();
      if (data != null) {
        broadcaster.broadcast(threadId, WsEvent.of("tool.progress", threadId, Map.of(
            "toolCallId", data.toolCallId() == null ? "" : data.toolCallId(),
            "message", data.progressMessage() == null ? "" : data.progressMessage())));
      }
    } else if (event instanceof ToolExecutionCompleteEvent complete) {
      var data = complete.getData();
      if (data != null) {
        String toolName = toolName(null, null);
        ThreadEvent stored = persistEvent(threadId, event, "tool.complete", "tool",
            toolResult(data), data.toolCallId(), toolName,
            Boolean.TRUE.equals(data.success()) ? "complete" : "error", data.turnId());
        ActivityResponse activity = new ActivityResponse(
            data.toolCallId(), stored.getTurnId(), toolName,
            "error".equals(stored.getStatus()) ? "error" : "complete",
            null, stored.getContent(), stored.getCreatedAt(), stored.getCreatedAt());
        broadcaster.broadcast(threadId, WsEvent.of("tool.completed", threadId, activity));
      }
    } else if (event instanceof SessionIdleEvent idle) {
      finishTurn(threadId, Boolean.TRUE.equals(idle.getData() == null ? null : idle.getData().aborted()));
    } else if (event instanceof SessionErrorEvent error) {
      var data = error.getData();
      failTurn(threadId, data == null ? "Copilot 会话错误" : data.message());
    } else if (event instanceof SessionTitleChangedEvent title) {
      if (title.getData() != null && title.getData().title() != null) {
        updateThread(threadId, thread -> thread.setTitle(title.getData().title()));
        broadcaster.broadcast(threadId, WsEvent.of("thread.title", threadId,
            Map.of("title", title.getData().title())));
        threadMapper.selectByPrimaryKey(threadId).ifPresent(thread ->
            broadcaster.broadcastAll(WsEvent.of("thread.updated", threadId, threadService.toResponse(thread))));
      }
    } else if (event instanceof SessionUsageInfoEvent usage) {
      var data = usage.getData();
      if (data != null) {
        updateThread(threadId, thread -> {
          if (data.currentTokens() != null) {
            thread.setTotalInputTokens(data.currentTokens());
          }
          if (data.tokenLimit() != null) {
            thread.setTotalOutputTokens(data.tokenLimit());
          }
        });
        broadcaster.broadcast(threadId, WsEvent.of("usage.updated", threadId, Map.of(
            "currentTokens", data.currentTokens() == null ? 0 : data.currentTokens(),
            "tokenLimit", data.tokenLimit() == null ? 0 : data.tokenLimit())));
      }
    } else if (event instanceof SessionWarningEvent warning) {
      var data = warning.getData();
      if (data != null) {
        broadcaster.broadcast(threadId, WsEvent.of("thread.warning", threadId,
            Map.of("message", String.valueOf(data))));
      }
    } else if (event instanceof UserMessageEvent
        || event instanceof SessionStartEvent
        || event instanceof SessionResumeEvent) {
      // Persisted by ChatService or not needed for the UI timeline.
    }
  }

  private String ensureAssistantMessage(String threadId, String messageId, String status) {
    TurnRegistry.TurnState turn = turnRegistry.get(threadId);
    String id = messageId;
    if (id == null || id.isBlank()) {
      id = turn != null && turn.getAssistantMessageId() != null
          ? turn.getAssistantMessageId()
          : UUID.randomUUID().toString();
    }
    if (turn != null) {
      turn.setAssistantMessageId(id);
    }
    ThreadMessage existing = messageMapper.selectByPrimaryKey(id).orElse(null);
    if (existing == null) {
      ThreadMessage message = new ThreadMessage();
      message.setId(id);
      message.setThreadId(threadId);
      message.setRole("assistant");
      message.setTurnId(turn == null ? null : turn.getTurnId());
      message.setContent("");
      message.setStatus(status);
      message.setInputTokens(0L);
      message.setOutputTokens(0L);
      message.setCreatedAt(Instant.now());
      messageMapper.insert(message);
      broadcaster.broadcast(threadId, WsEvent.of("message.started", threadId, messageResponse(id)));
    } else if (!status.equals(existing.getStatus())) {
      existing.setStatus(status);
      messageMapper.updateByPrimaryKeySelective(existing);
    }
    return id;
  }

  private void upsertFinalAssistant(String threadId, String messageId, String content,
                                    String reasoning, String model, Long outputTokens) {
    String id = ensureAssistantMessage(threadId, messageId, "complete");
    ThreadMessage message = messageMapper.selectByPrimaryKey(id).orElse(null);
    if (message == null) {
      return;
    }
    if (content != null) {
      message.setContent(content);
    }
    if (reasoning != null) {
      message.setReasoning(reasoning);
    }
    if (model != null) {
      message.setModel(model);
    }
    if (outputTokens != null) {
      message.setOutputTokens(outputTokens);
    }
    message.setStatus("complete");
    messageMapper.updateByPrimaryKeySelective(message);

    Thread thread = threadMapper.selectByPrimaryKey(threadId).orElse(null);
    if (thread != null) {
      String preview = content == null ? null : content.strip();
      if (preview != null && preview.length() > 200) {
        preview = preview.substring(0, 200);
      }
      thread.setLastMessagePreview(preview);
      thread.setLastMessageAt(Instant.now());
      thread.setUpdatedAt(Instant.now());
      if (thread.getMessageCount() == null) {
        thread.setMessageCount(1);
      }
      threadMapper.updateByPrimaryKeySelective(thread);
    }
  }

  private ThreadEvent persistEvent(String threadId, SessionEvent event, String eventType,
                                   String role, String content, String toolCallId,
                                   String toolName, String status, String turnId) {
    ThreadEvent entity = new ThreadEvent();
    entity.setEventId(event.getId() == null ? UUID.randomUUID().toString() : event.getId().toString());
    entity.setThreadId(threadId);
    entity.setSequenceNumber(eventMapper.selectMaxSequence(threadId) + 1);
    entity.setEventType(eventType);
    entity.setRole(role);
    entity.setContent(content);
    entity.setPayload(toJson(event));
    entity.setToolCallId(toolCallId);
    entity.setToolName(toolName);
    entity.setTurnId(turnId != null ? turnId : turnId(threadId));
    entity.setStatus(status);
    entity.setCreatedAt(Instant.now());
    try {
      eventMapper.insert(entity);
    } catch (DuplicateKeyException ex) {
      log.debug("忽略重复事件 thread={} event={}", threadId, entity.getEventId());
    }
    return entity;
  }

  private void finishTurn(String threadId, boolean aborted) {
    TurnRegistry.TurnState turn = turnRegistry.finish(threadId);
    if (turn != null && turn.getAssistantMessageId() != null && !turn.getText().isBlank()) {
      ThreadMessage message = messageMapper.selectByPrimaryKey(turn.getAssistantMessageId()).orElse(null);
      if (message != null && (message.getContent() == null || message.getContent().isBlank())) {
        message.setContent(turn.getText());
        message.setReasoning(turn.getReasoning());
        message.setStatus("complete");
        messageMapper.updateByPrimaryKeySelective(message);
      }
    }
    updateThread(threadId, thread -> {
      thread.setStatus("IDLE");
      thread.setLastError(null);
    });
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("turnId", turn == null ? "" : turn.getTurnId());
    payload.put("aborted", aborted);
    broadcaster.broadcast(threadId, WsEvent.of("turn.completed", threadId, payload));
  }

  private void failTurn(String threadId, String error) {
    TurnRegistry.TurnState turn = turnRegistry.finish(threadId);
    updateThread(threadId, thread -> {
      thread.setStatus("ERROR");
      thread.setLastError(error);
    });
    broadcaster.broadcast(threadId, WsEvent.of("thread.error", threadId, Map.of(
        "message", error == null ? "未知错误" : error,
        "turnId", turn == null ? "" : turn.getTurnId())));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("turnId", turn == null ? "" : turn.getTurnId());
    payload.put("aborted", false);
    broadcaster.broadcast(threadId, WsEvent.of("turn.completed", threadId, payload));
  }

  private void updateThread(String threadId, java.util.function.Consumer<Thread> consumer) {
    threadMapper.selectByPrimaryKey(threadId).ifPresent(thread -> {
      consumer.accept(thread);
      thread.setUpdatedAt(Instant.now());
      threadMapper.updateByPrimaryKeySelective(thread);
    });
  }

  private MessageResponse messageResponse(String messageId) {
    ThreadMessage message = messageMapper.selectByPrimaryKey(messageId).orElse(null);
    if (message == null) {
      return null;
    }
    return new MessageResponse(
        message.getId(), message.getRole(), message.getTurnId(), message.getContent(),
        message.getReasoning(), message.getStatus(), message.getModel(),
        message.getInputTokens() == null ? 0 : message.getInputTokens(),
        message.getOutputTokens() == null ? 0 : message.getOutputTokens(),
        message.getCreatedAt());
  }

  private String toolName(String mcpToolName, String toolName) {
    return mcpToolName != null && !mcpToolName.isBlank() ? mcpToolName : toolName;
  }

  private String toolResult(ToolExecutionCompleteEvent.ToolExecutionCompleteEventData data) {
    if (data == null) {
      return null;
    }
    String result = null;
    if (data.result() != null) {
      result = data.result().detailedContent() != null
          ? data.result().detailedContent()
          : data.result().content();
      if (result == null && data.result().contents() != null) {
        result = toJson(data.result().contents());
      }
    }
    if (result == null && data.error() != null) {
      result = data.error().message();
    }
    if (result == null) {
      result = Boolean.TRUE.equals(data.success()) ? "工具执行完成" : "工具执行失败";
    }
    return result.length() > MAX_TOOL_RESULT_CHARS ? result.substring(0, MAX_TOOL_RESULT_CHARS) : result;
  }

  private String turnId(String threadId) {
    TurnRegistry.TurnState turn = turnRegistry.get(threadId);
    return turn == null ? null : turn.getTurnId();
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return String.valueOf(value);
    }
  }

}
