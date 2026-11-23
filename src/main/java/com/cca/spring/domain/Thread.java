package com.cca.spring.domain;

import io.mybatis.provider.Entity;

import java.time.Instant;

@Entity.Table("threads")
public class Thread {

  @Entity.Column(id = true)
  private String id;

  @Entity.Column("workspace_id")
  private String workspaceId;

  @Entity.Column("title")
  private String title;

  @Entity.Column("model")
  private String model;

  @Entity.Column("reasoning_effort")
  private String reasoningEffort;

  @Entity.Column("permission_mode")
  private String permissionMode;

  @Entity.Column("status")
  private String status;

  @Entity.Column("copilot_session_id")
  private String copilotSessionId;

  @Entity.Column("archived")
  private Boolean archived;

  @Entity.Column("archived_at")
  private Instant archivedAt;

  @Entity.Column("pinned_at")
  private Instant pinnedAt;

  @Entity.Column("last_error")
  private String lastError;

  @Entity.Column("last_message_preview")
  private String lastMessagePreview;

  @Entity.Column("message_count")
  private Integer messageCount;

  @Entity.Column("total_input_tokens")
  private Long totalInputTokens;

  @Entity.Column("total_output_tokens")
  private Long totalOutputTokens;

  @Entity.Column("created_at")
  private Instant createdAt;

  @Entity.Column("updated_at")
  private Instant updatedAt;

  @Entity.Column("last_message_at")
  private Instant lastMessageAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getWorkspaceId() { return workspaceId; }
  public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public String getReasoningEffort() { return reasoningEffort; }
  public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }
  public String getPermissionMode() { return permissionMode; }
  public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCopilotSessionId() { return copilotSessionId; }
  public void setCopilotSessionId(String copilotSessionId) { this.copilotSessionId = copilotSessionId; }
  public Boolean getArchived() { return archived; }
  public void setArchived(Boolean archived) { this.archived = archived; }
  public Instant getArchivedAt() { return archivedAt; }
  public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
  public Instant getPinnedAt() { return pinnedAt; }
  public void setPinnedAt(Instant pinnedAt) { this.pinnedAt = pinnedAt; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public String getLastMessagePreview() { return lastMessagePreview; }
  public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
  public Integer getMessageCount() { return messageCount; }
  public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
  public Long getTotalInputTokens() { return totalInputTokens; }
  public void setTotalInputTokens(Long totalInputTokens) { this.totalInputTokens = totalInputTokens; }
  public Long getTotalOutputTokens() { return totalOutputTokens; }
  public void setTotalOutputTokens(Long totalOutputTokens) { this.totalOutputTokens = totalOutputTokens; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public Instant getLastMessageAt() { return lastMessageAt; }
  public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
