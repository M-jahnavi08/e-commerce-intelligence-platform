package com.commerce.intelligence.security;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.domain.Account;
import com.commerce.intelligence.repository.AccountRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final String dummy;

  public AuthService(AccountRepository a, PasswordEncoder p, JwtEncoder e) {
    accounts = a;
    passwords = p;
    encoder = e;
    dummy = p.encode(UUID.randomUUID().toString());
  }

  public record Session(
    String token,
    String email,
    String role,
    long expiresAt
  ) {}

  @Transactional
  public Session register(String email, String password) {
    validatePassword(password);
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (
      accounts.findByEmail(normalized).isPresent()
    ) throw ApiException.conflict("Unable to register this email");
    Account a = new Account();
    a.id = UUID.randomUUID();
    a.email = normalized;
    a.passwordHash = passwords.encode(password);
    a.role = "CUSTOMER";
    a.createdAt = Instant.now();
    accounts.saveAndFlush(a);
    return issue(a);
  }

  public Session login(String email, String password) {
    validatePassword(password);
    Account a = accounts
      .findByEmail(email.trim().toLowerCase(Locale.ROOT))
      .orElse(null);
    boolean valid = passwords.matches(
      password,
      a == null ? dummy : a.passwordHash
    );
    if (a == null || !valid) throw new ApiException(
      HttpStatus.UNAUTHORIZED,
      "Invalid email or password"
    );
    return issue(a);
  }

  private void validatePassword(String password) {
    if (
      password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "Password must be at most 72 UTF-8 bytes"
    );
  }

  private Session issue(Account a) {
    Instant now = Instant.now(),
      expires = now.plusSeconds(900);
    var claims = JwtClaimsSet.builder()
      .issuer("commerce-intelligence")
      .subject(a.id.toString())
      .issuedAt(now)
      .expiresAt(expires)
      .claim("role", a.role)
      .build();
    String token = encoder
      .encode(
        JwtEncoderParameters.from(
          JwsHeader.with(MacAlgorithm.HS256).build(),
          claims
        )
      )
      .getTokenValue();
    return new Session(token, a.email, a.role, expires.getEpochSecond());
  }
}
