package com.cca.spring.web;

import com.cca.spring.config.AppProperties;
import com.cca.spring.service.CopilotRuntime;
import com.cca.spring.service.ModelService;
import com.cca.spring.web.dto.ApiDtos.HealthResponse;
import com.cca.spring.web.dto.ApiDtos.ModelResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

  private final JdbcTemplate jdbcTemplate;
  private final CopilotRuntime copilotRuntime;
  private final ModelService modelService;
  private final AppProperties properties;

  public SystemController(JdbcTemplate jdbcTemplate,
                          CopilotRuntime copilotRuntime,
                          ModelService modelService,
                          AppProperties properties) {
    this.jdbcTemplate = jdbcTemplate;
    this.copilotRuntime = copilotRuntime;
    this.modelService = modelService;
    this.properties = properties;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    String database;
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      database = "up";
    } catch (Exception ex) {
      database = "down: " + ex.getMessage();
    }
    return new HealthResponse(
        "ok",
        "1.0.0",
        database,
        copilotRuntime.status(),
        properties.getWorkspaceRoot(),
        Instant.now());
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of(
        "defaultModel", properties.getCopilot().getModel(),
        "securityEnabled", properties.getSecurity().isEnabled(),
        "copilotEnabled", properties.getCopilot().isEnabled(),
        "copilotStatus", copilotRuntime.status(),
        "workspaceRoot", properties.getWorkspaceRoot());
  }

  @GetMapping("/models")
  public List<ModelResponse> models() {
    return modelService.list();
  }
}
