package com.cca.spring.service;

import com.cca.spring.config.AppProperties;
import com.cca.spring.domain.Thread;
import com.cca.spring.domain.Workspace;
import com.cca.spring.mapper.PermissionRequestMapper;
import com.cca.spring.mapper.ThreadEventMapper;
import com.cca.spring.mapper.ThreadMapper;
import com.cca.spring.mapper.ThreadMessageMapper;
import com.cca.spring.web.dto.ApiDtos.CreateThreadRequest;
import com.cca.spring.web.dto.ApiDtos.ThreadResponse;
import com.cca.spring.web.dto.ApiDtos.UpdateThreadRequest;
import com.cca.spring.web.dto.ApiDtos.WsEvent;
import com.cca.spring.web.exception.BadRequestException;
import com.cca.spring.web.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ThreadService {

  private static final Set<String> PERMISSION_MODES = Set.of("request", "auto", "full");

  private final ThreadMapper threadMapper;
  private final ThreadMessageMapper messageMapper;
  private final ThreadEventMapper eventMapper;
  private final PermissionRequestMapper permissionMapper;
  private final WorkspaceService workspaceService;
  private final EventBroadcaster broadcaster;
  private final AppProperties properties;

  public ThreadService(ThreadMapper threadMapper,
                       ThreadMessageMapper messageMapper,
                       ThreadEventMapper eventMapper,
                       PermissionRequestMapper permissionMapper,
                       WorkspaceService workspaceService,
                       EventBroadcaster broadcaster,
                       AppProperties properties) {
    this.threadMapper = threadMapper;
    this.messageMapper = messageMapper;
    this.eventMapper = eventMapper;
    this.permissionMapper = permissionMapper;
    this.workspaceService = workspaceService;
    this.broadcaster = broadcaster;
    this.properties = properties;
  }

  public List<ThreadResponse> list(String workspaceId, boolean includeArchived) {
    workspaceService.getEntity(workspaceId);
    return threadMapper.selectByWorkspace(workspaceId, includeArchived).stream()
        .map(this::toResponse).toList();
  }

  public Thread getEntity(String id) {
    return threadMapper.selectByPrimaryKey(id)
        .orElseThrow(() -> new NotFoundException("会话不存在: " + id));
  }

  public ThreadResponse get(String id) {
    return toResponse(getEntity(id));
  }

  @Transactional
  public ThreadResponse create(String workspaceId, CreateThreadRequest request) {
    Workspace workspace = workspaceService.getEntity(workspaceId);
    String permissionMode = normalizePermissionMode(request.permissionMode());
    Thread thread = new Thread();
    thread.setId(UUID.randomUUID().toString());
    thread.setWorkspaceId(workspace.getId());
    thread.setTitle(hasText(request.title()) ? request.title().trim() : "新会话");
    thread.setModel(hasText(request.model()) ? request.model().trim() : properties.getCopilot().getModel());
    thread.setReasoningEffort(blankToNull(request.reasoningEffort()));
    thread.setPermissionMode(permissionMode);
    thread.setStatus("IDLE");
    thread.setArchived(false);
    thread.setMessageCount(0);
    thread.setTotalInputTokens(0L);
    thread.setTotalOutputTokens(0L);
    thread.setCreatedAt(Instant.now());
    thread.setUpdatedAt(Instant.now());
    threadMapper.insert(thread);
    ThreadResponse response = toResponse(thread);
    broadcaster.broadcastAll(WsEvent.of("thread.created", thread.getId(), response));
    return response;
  }

  @Transactional
  public ThreadResponse update(String id, UpdateThreadRequest request) {
    Thread thread = getEntity(id);
    if (request.title() != null) {
      if (!hasText(request.title())) {
        throw new BadRequestException("会话标题不能为空");
      }
      thread.setTitle(request.title().trim());
    }
    if (request.model() != null) {
      thread.setModel(request.model().trim());
    }
    if (request.reasoningEffort() != null) {
      thread.setReasoningEffort(blankToNull(request.reasoningEffort()));
    }
    if (request.permissionMode() != null) {
      thread.setPermissionMode(normalizePermissionMode(request.permissionMode()));
    }
    if (request.archived() != null && request.archived() != Boolean.TRUE.equals(thread.getArchived())) {
      thread.setArchived(request.archived());
      thread.setArchivedAt(request.archived() ? Instant.now() : null);
    }
    if (request.pinned() != null) {
      thread.setPinnedAt(request.pinned() ? Instant.now() : null);
    }
    thread.setUpdatedAt(Instant.now());
    threadMapper.updateByPrimaryKeySelective(thread);
    ThreadResponse response = toResponse(thread);
    broadcaster.broadcastAll(WsEvent.of("thread.updated", thread.getId(), response));
    return response;
  }

  @Transactional
  public void delete(String id) {
    Thread thread = getEntity(id);
    messageMapper.deleteByThread(id);
    eventMapper.deleteByThread(id);
    permissionMapper.deleteByThread(id);
    threadMapper.deleteByPrimaryKey(thread.getId());
    broadcaster.broadcastAll(WsEvent.of("thread.deleted", id, Map.of("id", id)));
  }

  public ThreadResponse toResponse(Thread thread) {
    return new ThreadResponse(
        thread.getId(),
        thread.getWorkspaceId(),
        thread.getTitle(),
        thread.getModel(),
        thread.getReasoningEffort(),
        thread.getPermissionMode(),
        thread.getStatus(),
        Boolean.TRUE.equals(thread.getArchived()),
        thread.getArchivedAt(),
        thread.getPinnedAt(),
        thread.getLastMessagePreview(),
        thread.getMessageCount() == null ? 0 : thread.getMessageCount(),
        thread.getTotalInputTokens() == null ? 0 : thread.getTotalInputTokens(),
        thread.getTotalOutputTokens() == null ? 0 : thread.getTotalOutputTokens(),
        thread.getCreatedAt(),
        thread.getUpdatedAt(),
        thread.getLastMessageAt(),
        thread.getLastError());
  }

  public void persist(Thread thread) {
    thread.setUpdatedAt(Instant.now());
    threadMapper.updateByPrimaryKeySelective(thread);
  }

  private String normalizePermissionMode(String value) {
    String mode = hasText(value) ? value.trim().toLowerCase() : "request";
    if (!PERMISSION_MODES.contains(mode)) {
      throw new BadRequestException("permissionMode 必须是 request、auto 或 full");
    }
    return mode;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String blankToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }
}
