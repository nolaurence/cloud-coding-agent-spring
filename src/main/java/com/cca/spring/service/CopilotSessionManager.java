package com.cca.spring.service;

import com.cca.spring.config.AppProperties;
import com.cca.spring.domain.Thread;
import com.cca.spring.domain.Workspace;
import com.cca.spring.mapper.ThreadMapper;
import com.cca.spring.mapper.WorkspaceMapper;
import com.microsoft.copilot.CopilotClient;
import com.microsoft.copilot.CopilotSession;
import com.microsoft.copilot.generated.rpc.PermissionMode;
import com.microsoft.copilot.generated.SessionEvent;
import com.microsoft.copilot.rpc.CreateSessionFilesystemOptions;
import com.microsoft.copilot.rpc.CreateSessionOptions;
import com.microsoft.copilot.rpc.MessageOptions;
import com.microsoft.copilot.rpc.ResumeSessionOptions;
import com.microsoft.copilot.rpc.SessionGitHubOptions;
import com.microsoft.copilot.rpc.SessionPermissionOptions;
import com.microsoft.copilot.rpc.SessionToolOptions;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.Closeable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class CopilotSessionManager {

  private static final Logger log = LoggerFactory.getLogger(CopilotSessionManager.class);

  private final AppProperties properties;
  private final WorkspaceMapper workspaceMapper;
  private final ThreadMapper threadMapper;
  private final CopilotRuntime runtime;
  private final CopilotEventDispatcher dispatcher;
  private final PermissionService permissionService;
  private final ConcurrentHashMap<String, LiveSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

  public CopilotSessionManager(AppProperties properties,
                               WorkspaceMapper workspaceMapper,
                               ThreadMapper threadMapper,
                               CopilotRuntime runtime,
                               CopilotEventDispatcher dispatcher,
                               PermissionService permissionService) {
    this.properties = properties;
    this.workspaceMapper = workspaceMapper;
    this.threadMapper = threadMapper;
    this.runtime = runtime;
    this.dispatcher = dispatcher;
    this.permissionService = permissionService;
  }

  public CopilotSession openSession(Thread thread) {
    LiveSession live = sessions.get(thread.getId());
    if (live != null) {
      live.touch();
      return live.session();
    }
    synchronized (lockFor(thread.getId())) {
      live = sessions.get(thread.getId());
      if (live != null) {
        live.touch();
        return live.session();
      }
      Workspace workspace = workspaceMapper.selectByPrimaryKey(thread.getWorkspaceId())
          .orElseThrow(() -> new IllegalArgumentException("会话所属工作区不存在"));
      String model = StringUtils.hasText(thread.getModel())
          ? thread.getModel()
          : properties.getCopilot().getModel();
      SessionPermissionOptions permissions = permissionOptions(thread);
      CopilotClient client = runtime.client();
      CopilotSession session = null;

      if (StringUtils.hasText(thread.getCopilotSessionId())) {
        try {
          ResumeSessionOptions resume = new ResumeSessionOptions()
              .setWorkingDirectory(workspace.getPath())
              .setModel(model)
              .setTools(toolOptions())
              .setPermissions(permissions)
              .setContinuePendingWork(false)
              .setAllowTranscriptRecovery(true);
          session = client.resumeSession(thread.getCopilotSessionId(), resume)
              .get(45, TimeUnit.SECONDS);
          log.info("已恢复 Copilot 会话 thread={} session={}", thread.getId(), thread.getCopilotSessionId());
        } catch (Exception ex) {
          log.warn("恢复 Copilot 会话失败，将创建新会话 thread={}: {}", thread.getId(), ex.getMessage());
        }
      }

      if (session == null) {
        CreateSessionOptions options = new CreateSessionOptions()
            .setSessionId(thread.getId())
            .setName(thread.getTitle())
            .setClientName("cloud-coding-agent-spring")
            .setModel(model)
            .setWorkingDirectory(workspace.getPath())
            .setTools(toolOptions())
            .setPermissions(permissions)
            .setFilesystem(new CreateSessionFilesystemOptions().setTrustWorkingDirectory("session"));
        if (StringUtils.hasText(properties.getCopilot().getGithubToken())) {
          options.setGitHub(new SessionGitHubOptions().setToken(properties.getCopilot().getGithubToken()));
        }
        try {
          session = client.createSession(options).get(90, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          java.lang.Thread.currentThread().interrupt();
          throw new IllegalStateException("Copilot 会话创建被中断", ex);
        } catch (Exception ex) {
          throw new IllegalStateException("创建 Copilot 会话失败: " + rootMessage(ex), ex);
        }
        log.info("已创建 Copilot 会话 thread={} session={}", thread.getId(), session.getSessionId());
      }

      thread.setCopilotSessionId(session.getSessionId());
      threadMapper.updateByPrimaryKeySelective(thread);
      Closeable subscription = session.on(SessionEvent.class,
          event -> dispatcher.dispatch(thread.getId(), event));
      sessions.put(thread.getId(), new LiveSession(session, subscription, Instant.now()));
      return session;
    }
  }

  public void send(Thread thread, String prompt, String clientTurnId) {
    openSession(thread).send(new MessageOptions()
        .setPrompt(prompt)
        .setClientTurnId(clientTurnId));
  }

  public void abort(String threadId) {
    LiveSession live = sessions.get(threadId);
    if (live != null) {
      try {
        live.session().abort().exceptionally(ex -> {
          log.warn("中止 Copilot 会话失败 thread={}: {}", threadId, ex.getMessage());
          return null;
        });
      } catch (Exception ex) {
        log.warn("中止 Copilot 会话异常 thread={}: {}", threadId, ex.getMessage());
      }
    }
  }

  public void close(String threadId) {
    LiveSession live = sessions.remove(threadId);
    if (live != null) {
      live.close();
    }
  }

  @Scheduled(fixedDelay = 300_000)
  public void closeIdleSessions() {
    Instant threshold = Instant.now().minus(properties.getCopilot().getSessionIdMinutes(), ChronoUnit.MINUTES);
    for (Map.Entry<String, LiveSession> entry : sessions.entrySet()) {
      if (!entry.getValue().lastUsed().isBefore(threshold)) {
        continue;
      }
      threadMapper.selectByPrimaryKey(entry.getKey()).ifPresent(thread -> {
        if (!"RUNNING".equals(thread.getStatus())) {
          close(entry.getKey());
        }
      });
    }
  }

  @PreDestroy
  public void shutdown() {
    for (String threadId : sessions.keySet().toArray(new String[0])) {
      close(threadId);
    }
  }

  public int activeSessionCount() {
    return sessions.size();
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  private SessionToolOptions toolOptions() {
    return new SessionToolOptions().setAvailableTools(
        new ArrayList<>(properties.getCopilot().getTools()));
  }

  private SessionPermissionOptions permissionOptions(Thread thread) {
    SessionPermissionOptions options = new SessionPermissionOptions()
        .setOnRequest((request, invocation) -> permissionService.awaitDecision(thread, request, invocation));
    if ("request".equals(thread.getPermissionMode())) {
      options.setMode(PermissionMode.MANUAL);
    } else {
      options.setMode(PermissionMode.ALLOW_ALL);
    }
    return options;
  }

  private Object lockFor(String threadId) {
    return locks.computeIfAbsent(threadId, key -> new Object());
  }

  private static final class LiveSession {
    private final CopilotSession session;
    private final Closeable subscription;
    private volatile Instant lastUsed;

    private LiveSession(CopilotSession session, Closeable subscription, Instant lastUsed) {
      this.session = session;
      this.subscription = subscription;
      this.lastUsed = lastUsed;
    }

    private CopilotSession session() {
      return session;
    }

    private Instant lastUsed() {
      return lastUsed;
    }

    private void touch() {
      lastUsed = Instant.now();
    }

    private void close() {
      try {
        subscription.close();
      } catch (Exception ignored) {
        // noop
      }
      try {
        session.close();
      } catch (Exception ex) {
        // Session may already be detached.
      }
    }
  }
}
