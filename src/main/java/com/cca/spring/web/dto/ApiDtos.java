package com.cca.spring.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ApiDtos {

  private ApiDtos() {
  }

  public record WorkspaceResponse(
      String id,
      String name,
      String path,
      String description,
      Integer sortOrder,
      boolean archived,
      Instant createdAt,
      Instant updatedAt) {
  }

  public record CreateWorkspaceRequest(
      @NotBlank @Size(max = 128) String name,
      @Size(max = 1024) String path,
      @Size(max = 512) String description) {
  }

  public record UpdateWorkspaceRequest(
      @Size(max = 128) String name,
      @Size(max = 512) String description,
      Boolean archived,
      Integer sortOrder) {
  }

  public record ThreadResponse(
      String id,
      String workspaceId,
      String title,
      String model,
      String reasoningEffort,
      String permissionMode,
      String status,
      boolean archived,
      Instant archivedAt,
      Instant pinnedAt,
      String lastMessagePreview,
      int messageCount,
      long totalInputTokens,
      long totalOutputTokens,
      Instant createdAt,
      Instant updatedAt,
      Instant lastMessageAt,
      String lastError) {
  }

  public record CreateThreadRequest(
      @Size(max = 255) String title,
      @Size(max = 128) String model,
      @Size(max = 32) String reasoningEffort,
      @Size(max = 32) String permissionMode) {
  }

  public record UpdateThreadRequest(
      @Size(max = 255) String title,
      @Size(max = 128) String model,
      @Size(max = 32) String reasoningEffort,
      @Size(max = 32) String permissionMode,
      Boolean archived,
      Boolean pinned) {
  }

  public record MessageResponse(
      String id,
      String role,
      String turnId,
      String content,
      String reasoning,
      String status,
      String model,
      long inputTokens,
      long outputTokens,
      Instant createdAt) {
  }

  public record SendMessageRequest(
      @NotBlank @Size(max = 128_000) String content,
      @Size(max = 128) String model,
      @Size(max = 32) String reasoningEffort) {
  }

  public record ActivityResponse(
      String id,
      String turnId,
      String toolName,
      String status,
      String args,
      String result,
      Instant startedAt,
      Instant endedAt) {
  }

  public record PermissionResponse(
      String id,
      String threadId,
      String requestId,
      String toolCallId,
      String toolName,
      String kind,
      String title,
      String description,
      String argumentsJson,
      String status,
      String decision,
      String feedback,
      Instant createdAt,
      Instant resolvedAt) {
  }

  public record ResolvePermissionRequest(boolean approve, @Size(max = 2000) String feedback) {
  }

  public record ThreadDetailResponse(
      ThreadResponse thread,
      List<MessageResponse> messages,
      List<ActivityResponse> activities,
      List<PermissionResponse> pendingPermissions) {
  }

  public record FileEntryResponse(
      String name,
      String path,
      String kind,
      Long size,
      Instant modifiedAt) {
  }

  public record FileContentResponse(
      String path,
      String content,
      long size,
      Instant modifiedAt,
      String version) {
  }

  public record FileWriteRequest(@NotBlank String path, String content) {
  }

  public record ModelResponse(
      String id,
      String name,
      String vendor,
      List<String> supportedReasoningEfforts,
      String defaultReasoningEffort) {
  }

  public record HealthResponse(
      String status,
      String version,
      String database,
      String copilot,
      String workspaceRoot,
      Instant timestamp) {
  }

  public record ApiError(String code, String message, Instant timestamp) {
  }

  public record WsEvent(String type, String threadId, Object data, Instant timestamp) {
    public static WsEvent of(String type, String threadId, Object data) {
      return new WsEvent(type, threadId, data, Instant.now());
    }
  }
}
