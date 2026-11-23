package com.cca.spring.web.exception;

public class ConflictException extends ApiException {
  public ConflictException(String message) {
    super(409, "conflict", message);
  }
}
