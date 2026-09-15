package com.commerce.intelligence.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private record Window(long expiresAt, int count) {}

  private final Map<String, Window> windows = new HashMap<>();
  private final Clock clock = Clock.systemUTC();

  private synchronized boolean allow(String ip) {
    long now = clock.millis();
    windows.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    Window w = windows.get(ip);
    if (w == null) {
      if (windows.size() >= 10000) return false;
      windows.put(ip, new Window(now + 60000, 1));
      return true;
    }
    if (w.count() >= 30) return false;
    windows.put(ip, new Window(w.expiresAt(), w.count() + 1));
    return true;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest req,
    HttpServletResponse res,
    FilterChain chain
  ) throws ServletException, IOException {
    if (
      "POST".equals(req.getMethod()) &&
      req.getRequestURI().startsWith("/api/auth/") &&
      !allow(req.getRemoteAddr())
    ) {
      res.setStatus(429);
      res.setHeader("Retry-After", "60");
      res.setContentType("application/problem+json");
      res
        .getWriter()
        .write(
          "{\"status\":429,\"detail\":\"Too many authentication attempts. Retry in one minute.\"}"
        );
      return;
    }
    chain.doFilter(req, res);
  }
}
