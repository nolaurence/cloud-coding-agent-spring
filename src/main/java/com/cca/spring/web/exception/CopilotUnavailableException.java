package com.cca.spring.web.exception;

public class CopilotUnavailableException extends ApiException {
  public CopilotUnavailableException(String message) {
    super(503, "copilot_unavailable", message);
  }
}
