package com.commerce.intelligence.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ErrorHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> known(ApiException e) {
    return problem(e.status, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
    return problem(
      HttpStatus.BAD_REQUEST,
      "Invalid request: " +
        e
          .getBindingResult()
          .getFieldErrors()
          .stream()
          .map(f -> f.getField() + " " + f.getDefaultMessage())
          .distinct()
          .collect(java.util.stream.Collectors.joining(", "))
    );
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
  })
  ResponseEntity<ProblemDetail> bad(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class,
  })
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(
      HttpStatus.CONFLICT,
      "The record changed or conflicts with an existing record"
    );
  }

  private ResponseEntity<ProblemDetail> problem(
    HttpStatus status,
    String detail
  ) {
    return ResponseEntity.status(status).body(
      ProblemDetail.forStatusAndDetail(status, detail)
    );
  }
}
