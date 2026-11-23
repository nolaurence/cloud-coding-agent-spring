package com.cca.spring.service;

import com.cca.spring.domain.PermissionRequestEntity;
import com.cca.spring.domain.Thread;
import com.cca.spring.domain.ThreadEvent;
import com.cca.spring.domain.ThreadMessage;
import com.cca.spring.mapper.PermissionRequestMapper;
import com.cca.spring.mapper.ThreadEventMapper;
import com.cca.spring.mapper.ThreadMapper;
import com.cca.spring.mapper.ThreadMessageMapper;
import com.cca.spring.web.dto.ApiDtos.ActivityResponse;
import com.cca.spring.web.dto.ApiDtos.MessageResponse;
import com.cca.spring.web.dto.ApiDtos.ThreadDetailResponse;
import com.cca.spring.web.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TimelineService {

  private final ThreadMapper threadMapper;
  private final ThreadMessageMapper messageMapper;
  private final ThreadEventMapper eventMapper;
  private final PermissionRequestMapper permissionMapper;
  private final ThreadService threadService;
  private final PermissionService permissionService;

  public TimelineService(ThreadMapper threadMapper,
                         ThreadMessageMapper messageMapper,
                         ThreadEventMapper eventMapper,
                         PermissionRequestMapper permissionMapper,
                         ThreadService threadService,
                         PermissionService permissionService) {
    this.threadMapper = threadMapper;
    this.messageMapper = messageMapper;
    this.eventMapper = eventMapper;
    this.permissionMapper = permissionMapper;
    this.threadService = threadService;
    this.permissionService = permissionService;
  }

  public ThreadDetailResponse detail(String threadId) {
    Thread thread = threadMapper.selectByPrimaryKey(threadId)
        .orElseThrow(() -> new NotFoundException("会话不存在: " + threadId));
    List<MessageResponse> messages = messageMapper.selectByThread(threadId).stream()
        .map(this::toMessage).toList();
    List<ActivityResponse> activities = activities(threadId);
    List<PermissionRequestEntity> pending = permissionMapper.selectPendingByThread(threadId);
    return new ThreadDetailResponse(
        threadService.toResponse(thread),
        messages,
        activities,
        pending.stream().map(permissionService::toResponse).toList());
  }

  public List<ActivityResponse> activities(String threadId) {
    Map<String, ActivityBuilder> builders = new LinkedHashMap<>();
    for (ThreadEvent event : eventMapper.selectByThread(threadId)) {
      if ("tool.start".equals(event.getEventType())) {
        ActivityBuilder builder = builders.computeIfAbsent(event.getToolCallId(), key -> new ActivityBuilder());
        builder.id = event.getToolCallId();
        builder.turnId = event.getTurnId();
        builder.toolName = event.getToolName();
        builder.args = event.getContent();
        builder.startedAt = event.getCreatedAt();
        builder.status = "running";
      } else if ("tool.complete".equals(event.getEventType())) {
        ActivityBuilder builder = builders.computeIfAbsent(event.getToolCallId(), key -> new ActivityBuilder());
        builder.id = event.getToolCallId();
        builder.turnId = event.getTurnId();
        builder.toolName = event.getToolName() != null ? event.getToolName() : builder.toolName;
        builder.result = event.getContent();
        builder.endedAt = event.getCreatedAt();
        builder.status = "error".equals(event.getStatus()) ? "error" : "complete";
      }
    }
    List<ActivityResponse> result = new ArrayList<>();
    for (ActivityBuilder builder : builders.values()) {
      result.add(new ActivityResponse(
          builder.id, builder.turnId, builder.toolName, builder.status,
          builder.args, builder.result, builder.startedAt, builder.endedAt));
    }
    return result;
  }

  public MessageResponse toMessage(ThreadMessage message) {
    return new MessageResponse(
        message.getId(),
        message.getRole(),
        message.getTurnId(),
        message.getContent(),
        message.getReasoning(),
        message.getStatus(),
        message.getModel(),
        message.getInputTokens() == null ? 0 : message.getInputTokens(),
        message.getOutputTokens() == null ? 0 : message.getOutputTokens(),
        message.getCreatedAt());
  }

  private static final class ActivityBuilder {
    private String id;
    private String turnId;
    private String toolName;
    private String status;
    private String args;
    private String result;
    private Instant startedAt;
    private Instant endedAt;
  }
}
