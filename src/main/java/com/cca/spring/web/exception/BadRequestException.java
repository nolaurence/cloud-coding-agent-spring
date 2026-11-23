package com.cca.spring.web.exception;

public class BadRequestException extends ApiException {
  public BadRequestException(String message) {
    super(400, "bad_request", message);
  }
}
