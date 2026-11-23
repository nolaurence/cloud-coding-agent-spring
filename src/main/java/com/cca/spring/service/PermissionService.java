package com.cca.spring.service;

import com.cca.spring.domain.PermissionRequestEntity;
import com.cca.spring.domain.Thread;
import com.cca.spring.mapper.PermissionRequestMapper;
import com.cca.spring.web.dto.ApiDtos.PermissionResponse;
import com.cca.spring.web.dto.ApiDtos.ResolvePermissionRequest;
import com.cca.spring.web.dto.ApiDtos.WsEvent;
import com.cca.spring.web.exception.ConflictException;
import com.cca.spring.web.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.copilot.rpc.PermissionInvocation;
import com.microsoft.copilot.rpc.PermissionRequest;
import com.microsoft.copilot.rpc.PermissionRequestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PermissionService {

  private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

  private final PermissionRequestMapper permissionMapper;
  private final EventBroadcaster broadcaster;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<String, PendingPermission> pending = new ConcurrentHashMap<>();

  public PermissionService(PermissionRequestMapper permissionMapper,
                           EventBroadcaster broadcaster,
                           ObjectMapper objectMapper) {
    this.permissionMapper = permissionMapper;
    this.broadcaster = broadcaster;
    this.objectMapper = objectMapper;
  }

  public CompletableFuture<PermissionRequestResult> awaitDecision(
      Thread thread, PermissionRequest request, PermissionInvocation invocation) {
    if (!"request".equals(thread.getPermissionMode())) {
      return CompletableFuture.completedFuture(PermissionRequestResult.approveOnce());
    }

    PermissionRequest prompt = invocation == null ? null : invocation.getPromptRequest();
    String kind = prompt != null && prompt.getKind() != null ? prompt.getKind() : request.getKind();
    String toolName = request.getServerDisplayName();
    String title = humanize(kind) + (toolName == null || toolName.isBlank() ? "" : "：" + toolName);

    Map<String, Object> arguments = new LinkedHashMap<>();
    arguments.put("kind", kind);
    arguments.put("toolCallId", request.getToolCallId());
    arguments.put("managedApprovalRequired", request.getManagedApprovalRequired());
    arguments.put("readOnlyDirectories", request.getReadOnlyDirectories());
    arguments.put("extensionData", request.getExtensionData());
    if (prompt != null) {
      arguments.put("promptKind", prompt.getKind());
      arguments.put("promptExtensionData", prompt.getExtensionData());
    }

    PermissionRequestEntity entity = new PermissionRequestEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setThreadId(thread.getId());
    entity.setRequestId(UUID.randomUUID().toString());
    entity.setToolCallId(request.getToolCallId());
    entity.setToolName(toolName);
    entity.setKind(kind);
    entity.setTitle(title);
    entity.setDescription(describe(arguments));
    entity.setArgumentsJson(toJson(arguments));
    entity.setStatus("PENDING");
    entity.setCreatedAt(Instant.now());
    permissionMapper.insert(entity);

    CompletableFuture<PermissionRequestResult> future = new CompletableFuture<>();
    pending.put(entity.getId(), new PendingPermission(thread.getId(), future));
    broadcaster.broadcast(thread.getId(), WsEvent.of("permission.requested", thread.getId(), toResponse(entity)));
    log.info("等待权限确认 thread={} kind={} tool={}", thread.getId(), kind, toolName);
    return future;
  }

  public PermissionResponse resolve(String id, ResolvePermissionRequest request) {
    PendingPermission pendingPermission = pending.remove(id);
    if (pendingPermission == null) {
      PermissionRequestEntity existing = permissionMapper.selectById(id)
          .orElseThrow(() -> new NotFoundException("权限请求不存在: " + id));
      if (!"PENDING".equals(existing.getStatus())) {
        throw new ConflictException("权限请求已处理");
      }
      throw new ConflictException("权限请求已失效");
    }
    PermissionRequestEntity entity = permissionMapper.selectById(id)
        .orElseThrow(() -> new NotFoundException("权限请求不存在: " + id));
    Instant now = Instant.now();
    entity.setStatus(request.approve() ? "APPROVED" : "REJECTED");
    entity.setDecision(request.approve() ? "approve" : "decline");
    entity.setFeedback(request.feedback());
    entity.setResolvedAt(now);
    permissionMapper.updateByPrimaryKeySelective(entity);

    pendingPermission.future().complete(request.approve()
        ? PermissionRequestResult.approveOnce()
        : PermissionRequestResult.reject(request.feedback()));
    broadcaster.broadcast(entity.getThreadId(), WsEvent.of("permission.resolved", entity.getThreadId(),
        Map.of("requestId", id, "decision", entity.getDecision(), "status", entity.getStatus())));
    return toResponse(entity);
  }

  public void cancelPending(String threadId) {
    for (var entry : pending.entrySet()) {
      if (!entry.getValue().threadId().equals(threadId)) {
        continue;
      }
      PendingPermission pendingPermission = entry.getValue();
      pending.remove(entry.getKey());
      permissionMapper.selectById(entry.getKey()).ifPresent(entity -> {
        entity.setStatus("CANCELLED");
        entity.setDecision("cancel");
        entity.setFeedback("会话已中止");
        entity.setResolvedAt(Instant.now());
        permissionMapper.updateByPrimaryKeySelective(entity);
      });
      pendingPermission.future().complete(PermissionRequestResult.reject("会话已中止"));
    }
  }

  public List<PermissionResponse> pending(String threadId) {
    return permissionMapper.selectPendingByThread(threadId).stream().map(this::toResponse).toList();
  }

  public PermissionResponse toResponse(PermissionRequestEntity entity) {
    return new PermissionResponse(
        entity.getId(),
        entity.getThreadId(),
        entity.getRequestId(),
        entity.getToolCallId(),
        entity.getToolName(),
        entity.getKind(),
        entity.getTitle(),
        entity.getDescription(),
        entity.getArgumentsJson(),
        entity.getStatus(),
        entity.getDecision(),
        entity.getFeedback(),
        entity.getCreatedAt(),
        entity.getResolvedAt());
  }

  private String describe(Map<String, Object> arguments) {
    Object extension = arguments.get("extensionData");
    if (extension instanceof Map<?, ?> map && !map.isEmpty()) {
      return map.toString();
    }
    Object promptExtension = arguments.get("promptExtensionData");
    if (promptExtension instanceof Map<?, ?> map && !map.isEmpty()) {
      return map.toString();
    }
    return "Copilot 请求执行一次需要确认的操作";
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  private String humanize(String kind) {
    if (kind == null) {
      return "请求操作";
    }
    return switch (kind) {
      case "commands", "command", "bash" -> "请求执行命令";
      case "write", "edit" -> "请求写入文件";
      case "read" -> "请求读取文件";
      case "url" -> "请求访问网络";
      case "workflow" -> "请求运行工作流";
      case "dangerous-action" -> "请求执行危险操作";
      default -> "请求确认操作";
    };
  }

  private record PendingPermission(String threadId, CompletableFuture<PermissionRequestResult> future) {
  }
}
