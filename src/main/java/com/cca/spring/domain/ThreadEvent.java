package com.cca.spring.domain;

import io.mybatis.provider.Entity;

import java.time.Instant;

@Entity.Table("thread_events")
public class ThreadEvent {

  @Entity.Column(id = true, useGeneratedKeys = true)
  private Long id;

  @Entity.Column("event_id")
  private String eventId;

  @Entity.Column("thread_id")
  private String threadId;

  @Entity.Column("sequence_number")
  private Long sequenceNumber;

  @Entity.Column("event_type")
  private String eventType;

  @Entity.Column("role")
  private String role;

  @Entity.Column("content")
  private String content;

  @Entity.Column("payload")
  private String payload;

  @Entity.Column("tool_call_id")
  private String toolCallId;

  @Entity.Column("tool_name")
  private String toolName;

  @Entity.Column("turn_id")
  private String turnId;

  @Entity.Column("status")
  private String status;

  @Entity.Column("created_at")
  private Instant createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }
  public String getThreadId() { return threadId; }
  public void setThreadId(String threadId) { this.threadId = threadId; }
  public Long getSequenceNumber() { return sequenceNumber; }
  public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public String getToolCallId() { return toolCallId; }
  public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
  public String getToolName() { return toolName; }
  public void setToolName(String toolName) { this.toolName = toolName; }
  public String getTurnId() { return turnId; }
  public void setTurnId(String turnId) { this.turnId = turnId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
