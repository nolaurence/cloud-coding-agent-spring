package com.cca.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private String dataDir = "./data";
  private String workspaceRoot = "./workspace";
  private boolean bootstrapDefaultWorkspace = true;
  private String defaultModel = "gpt-5";
  private final Security security = new Security();
  private final Copilot copilot = new Copilot();

  public String getDataDir() { return dataDir; }
  public void setDataDir(String dataDir) { this.dataDir = dataDir; }
  public String getWorkspaceRoot() { return workspaceRoot; }
  public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
  public boolean isBootstrapDefaultWorkspace() { return bootstrapDefaultWorkspace; }
  public void setBootstrapDefaultWorkspace(boolean bootstrapDefaultWorkspace) { this.bootstrapDefaultWorkspace = bootstrapDefaultWorkspace; }
  public String getDefaultModel() { return defaultModel; }
  public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
  public Security getSecurity() { return security; }
  public Copilot getCopilot() { return copilot; }

  public static class Security {
    private boolean enabled;
    private String apiKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
  }

  public static class Copilot {
    private boolean enabled = true;
    private String model = "gpt-5";
    private String githubToken = "";
    private long startupTimeoutSeconds = 90;
    private long turnTimeoutMinutes = 45;
    private long sessionIdMinutes = 30;
    private List<String> tools = new ArrayList<>(List.of("builtin:*"));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getGithubToken() { return githubToken; }
    public void setGithubToken(String githubToken) { this.githubToken = githubToken; }
    public long getStartupTimeoutSeconds() { return startupTimeoutSeconds; }
    public void setStartupTimeoutSeconds(long startupTimeoutSeconds) { this.startupTimeoutSeconds = startupTimeoutSeconds; }
    public long getTurnTimeoutMinutes() { return turnTimeoutMinutes; }
    public void setTurnTimeoutMinutes(long turnTimeoutMinutes) { this.turnTimeoutMinutes = turnTimeoutMinutes; }
    public long getSessionIdMinutes() { return sessionIdMinutes; }
    public void setSessionIdMinutes(long sessionIdMinutes) { this.sessionIdMinutes = sessionIdMinutes; }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
  }
}
