package com.cca.spring.domain;

import io.mybatis.provider.Entity;

import java.time.Instant;

@Entity.Table("thread_messages")
public class ThreadMessage {

  @Entity.Column(id = true)
  private String id;

  @Entity.Column("thread_id")
  private String threadId;

  @Entity.Column("role")
  private String role;

  @Entity.Column("turn_id")
  private String turnId;

  @Entity.Column("content")
  private String content;

  @Entity.Column("reasoning")
  private String reasoning;

  @Entity.Column("status")
  private String status;

  @Entity.Column("model")
  private String model;

  @Entity.Column("input_tokens")
  private Long inputTokens;

  @Entity.Column("output_tokens")
  private Long outputTokens;

  @Entity.Column("created_at")
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getThreadId() { return threadId; }
  public void setThreadId(String threadId) { this.threadId = threadId; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getTurnId() { return turnId; }
  public void setTurnId(String turnId) { this.turnId = turnId; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getReasoning() { return reasoning; }
  public void setReasoning(String reasoning) { this.reasoning = reasoning; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public Long getInputTokens() { return inputTokens; }
  public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
  public Long getOutputTokens() { return outputTokens; }
  public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
