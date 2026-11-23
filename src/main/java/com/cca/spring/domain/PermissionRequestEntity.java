package com.cca.spring.domain;

import io.mybatis.provider.Entity;

import java.time.Instant;

@Entity.Table("permission_requests")
public class PermissionRequestEntity {

  @Entity.Column(id = true)
  private String id;

  @Entity.Column("thread_id")
  private String threadId;

  @Entity.Column("request_id")
  private String requestId;

  @Entity.Column("tool_call_id")
  private String toolCallId;

  @Entity.Column("tool_name")
  private String toolName;

  @Entity.Column("kind")
  private String kind;

  @Entity.Column("title")
  private String title;

  @Entity.Column("description")
  private String description;

  @Entity.Column("arguments_json")
  private String argumentsJson;

  @Entity.Column("status")
  private String status;

  @Entity.Column("decision")
  private String decision;

  @Entity.Column("feedback")
  private String feedback;

  @Entity.Column("created_at")
  private Instant createdAt;

  @Entity.Column("resolved_at")
  private Instant resolvedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getThreadId() { return threadId; }
  public void setThreadId(String threadId) { this.threadId = threadId; }
  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  public String getToolCallId() { return toolCallId; }
  public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
  public String getToolName() { return toolName; }
  public void setToolName(String toolName) { this.toolName = toolName; }
  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getArgumentsJson() { return argumentsJson; }
  public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getDecision() { return decision; }
  public void setDecision(String decision) { this.decision = decision; }
  public String getFeedback() { return feedback; }
  public void setFeedback(String feedback) { this.feedback = feedback; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getResolvedAt() { return resolvedAt; }
  public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
