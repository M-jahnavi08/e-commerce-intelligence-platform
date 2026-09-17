package com.commerce.intelligence.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {

  @Bean
  SecretKeySpec signingKey(@Value("${app.jwt-secret}") String secret) {
    if (
      secret.getBytes(StandardCharsets.UTF_8).length < 32
    ) throw new IllegalStateException(
      "JWT_SECRET must contain at least 32 bytes"
    );
    return new SecretKeySpec(
      secret.getBytes(StandardCharsets.UTF_8),
      "HmacSHA256"
    );
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key) {
    NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key)
      .macAlgorithm(MacAlgorithm.HS256)
      .build();
    d.setJwtValidator(
      JwtValidators.createDefaultWithIssuer("commerce-intelligence")
    );
    return d;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(
    HttpSecurity http,
    @Value("${app.cors-origin}") String origin
  ) throws Exception {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("role");
    roles.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(java.util.Arrays.stream(origin.split(","))
      .map(String::trim).filter(value -> !value.isEmpty()).toList());
    cors.setAllowCredentials(false);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
      List.of("Authorization", "Content-Type", "Idempotency-Key")
    );
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return http
      .cors(c -> c.configurationSource(source))
      .csrf(c -> c.disable())
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(a ->
        a
          .requestMatchers(
            "/actuator/health",
            "/actuator/health/readiness",
            "/actuator/health/liveness",
            "/api/auth/login",
            "/api/auth/register"
          )
          .permitAll()
          .requestMatchers(
            HttpMethod.GET,
            "/api/products/**",
            "/api/categories"
          )
          .permitAll()
          .requestMatchers("/api/admin/**")
          .hasRole("ADMIN")
          .anyRequest()
          .authenticated()
      )
      .oauth2ResourceServer(o ->
        o.jwt(j -> j.jwtAuthenticationConverter(converter))
      )
      .build();
  }
}
