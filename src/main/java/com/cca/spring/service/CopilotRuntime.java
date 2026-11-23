package com.cca.spring.service;

import com.cca.spring.config.AppProperties;
import com.cca.spring.web.exception.CopilotUnavailableException;
import com.microsoft.copilot.CopilotClient;
import com.microsoft.copilot.generated.rpc.Model;
import com.microsoft.copilot.rpc.CopilotClientMode;
import com.microsoft.copilot.rpc.CopilotClientOptions;
import com.microsoft.copilot.rpc.RuntimeConnection;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class CopilotRuntime {

  private static final Logger log = LoggerFactory.getLogger(CopilotRuntime.class);

  private final AppProperties properties;
  private volatile CopilotClient client;
  private volatile String lastError;

  public CopilotRuntime(AppProperties properties) {
    this.properties = properties;
  }

  public CopilotClient client() {
    if (!properties.getCopilot().isEnabled()) {
      throw new CopilotUnavailableException("Copilot 功能已在配置中关闭");
    }
    CopilotClient current = client;
    if (current != null) {
      return current;
    }
    synchronized (this) {
      current = client;
      if (current != null) {
        return current;
      }
      current = start();
      client = current;
      return current;
    }
  }

  public List<Model> listModels() {
    try {
      return client().listModels().get(30, TimeUnit.SECONDS);
    } catch (CopilotUnavailableException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new CopilotUnavailableException("读取 Copilot 模型列表失败: " + rootMessage(ex));
    }
  }

  public String status() {
    if (!properties.getCopilot().isEnabled()) {
      return "disabled";
    }
    if (client != null) {
      return "ready";
    }
    return lastError == null ? "not_started" : "error: " + lastError;
  }

  @PreDestroy
  public void shutdown() {
    CopilotClient current = client;
    client = null;
    if (current != null) {
      try {
        current.close();
      } catch (Exception ex) {
        log.warn("关闭 Copilot 运行时失败: {}", ex.getMessage());
      }
    }
  }

  private CopilotClient start() {
    try {
      Path home = Paths.get(properties.getDataDir(), "copilot").toAbsolutePath().normalize();
      Files.createDirectories(home);
      int idleSeconds = (int) Math.min(Integer.MAX_VALUE,
          Math.max(0, properties.getCopilot().getSessionIdMinutes() * 60));
      CopilotClientOptions options = new CopilotClientOptions()
          .setMode(CopilotClientMode.EMPTY)
          .setRuntime(RuntimeConnection.forDefault(home.toString(), idleSeconds));
      CopilotClient started = new CopilotClient(options);
      started.start().get(properties.getCopilot().getStartupTimeoutSeconds(), TimeUnit.SECONDS);
      lastError = null;
      log.info("Copilot 运行时已启动, data home={}", home);
      return started;
    } catch (Exception ex) {
      lastError = rootMessage(ex);
      log.error("Copilot 运行时启动失败", ex);
      throw new CopilotUnavailableException(
          "Copilot 运行时不可用。请确认已配置 GitHub Token（COPILOT_GITHUB_TOKEN）并且平台运行时依赖已安装。原因: " + lastError);
    }
  }

  public String getLastError() {
    return lastError;
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
}
