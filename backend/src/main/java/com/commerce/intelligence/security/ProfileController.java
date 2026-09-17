package com.commerce.intelligence.security;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.repository.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

  private final AccountRepository accounts;
  private final JdbcTemplate jdbc;

  public ProfileController(AccountRepository accounts, JdbcTemplate jdbc) {
    this.accounts = accounts;
    this.jdbc = jdbc;
  }

  @GetMapping
  public Map<String, Object> profile(@AuthenticationPrincipal Jwt jwt) {
    var id = UUID.fromString(jwt.getSubject());
    var a = accounts.findById(id).orElseThrow(ApiException::missing);
    return Map.of(
      "email",
      a.email,
      "displayName",
      a.displayName,
      "createdAt",
      a.createdAt,
      "purchases",
      jdbc.queryForMap(
        "select count(*) as orders,coalesce(sum(total),0) as spent from orders where user_id=? and status='PAID'",
        id
      )
    );
  }

  public record Update(@NotBlank @Size(max = 80) String displayName) {}

  @PutMapping
  @Transactional
  public Map<String, Object> update(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody Update request
  ) {
    var a = accounts
      .lockById(UUID.fromString(jwt.getSubject()))
      .orElseThrow(ApiException::missing);
    a.displayName = request.displayName().trim();
    return profile(jwt);
  }
}
