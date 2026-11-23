package com.cca.spring.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TurnRegistry {

  private final ConcurrentHashMap<String, TurnState> turns = new ConcurrentHashMap<>();

  public TurnState start(String threadId, String turnId) {
    TurnState state = new TurnState(threadId, turnId, Instant.now());
    turns.put(threadId, state);
    return state;
  }

  public TurnState get(String threadId) {
    return turns.get(threadId);
  }

  public TurnState finish(String threadId) {
    TurnState state = turns.remove(threadId);
    if (state != null) {
      state.markFinished();
    }
    return state;
  }

  public boolean finishIfActive(String threadId) {
    return finish(threadId) != null;
  }

  public boolean isActive(String threadId) {
    TurnState state = turns.get(threadId);
    return state != null && !state.isFinished();
  }

  public static final class TurnState {
    private final String threadId;
    private final String turnId;
    private final Instant startedAt;
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private String assistantMessageId;
    private volatile boolean finished;

    private TurnState(String threadId, String turnId, Instant startedAt) {
      this.threadId = threadId;
      this.turnId = turnId;
      this.startedAt = startedAt;
    }

    public String getThreadId() { return threadId; }
    public String getTurnId() { return turnId; }
    public Instant getStartedAt() { return startedAt; }
    public boolean isFinished() { return finished; }
    private void markFinished() { this.finished = true; }

    public synchronized String getAssistantMessageId() { return assistantMessageId; }

    public synchronized void setAssistantMessageId(String assistantMessageId) {
      this.assistantMessageId = assistantMessageId;
    }

    public synchronized void appendText(String delta) {
      if (delta != null) {
        text.append(delta);
      }
    }

    public synchronized void appendReasoning(String delta) {
      if (delta != null) {
        reasoning.append(delta);
      }
    }

    public synchronized String getText() { return text.toString(); }
    public synchronized String getReasoning() { return reasoning.toString(); }
  }
}
