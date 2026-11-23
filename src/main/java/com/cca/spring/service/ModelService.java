package com.cca.spring.service;

import com.cca.spring.config.AppProperties;
import com.cca.spring.web.dto.ApiDtos.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelService {

  private static final Logger log = LoggerFactory.getLogger(ModelService.class);

  private final CopilotRuntime runtime;
  private final AppProperties properties;

  public ModelService(CopilotRuntime runtime, AppProperties properties) {
    this.runtime = runtime;
    this.properties = properties;
  }

  public List<ModelResponse> list() {
    try {
      return runtime.listModels().stream()
          .map(model -> new ModelResponse(
              model.id(),
              model.name() == null ? model.id() : model.name(),
              model.vendor(),
              model.supportedReasoningEfforts(),
              model.defaultReasoningEffort()))
          .toList();
    } catch (Exception ex) {
      log.warn("无法读取 Copilot 模型列表，返回内置模型: {}", ex.getMessage());
      String fallback = properties.getCopilot().getModel();
      return List.of(
          new ModelResponse("auto", "Auto", null, null, null),
          new ModelResponse(fallback, fallback, null, null, null));
    }
  }
}
