package com.commerce.intelligence.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService s) {
    service = s;
  }

  public record Credentials(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 72) String password
  ) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthService.Session register(@Valid @RequestBody Credentials c) {
    return service.register(c.email(), c.password());
  }

  @PostMapping("/login")
  public AuthService.Session login(@Valid @RequestBody Credentials c) {
    return service.login(c.email(), c.password());
  }
}
