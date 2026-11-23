package com.cca.spring.web.exception;

public class NotFoundException extends ApiException {
  public NotFoundException(String message) {
    super(404, "not_found", message);
  }
}
