package com.commerce.intelligence.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(
    RequestLoggingFilter.class
  );

  @Override
  protected void doFilterInternal(
    HttpServletRequest req,
    HttpServletResponse res,
    FilterChain chain
  ) throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    long start = System.nanoTime();
    MDC.put("requestId", id);
    res.setHeader("X-Request-Id", id);
    try {
      chain.doFilter(req, res);
    } finally {
      String path = req.getRequestURI().replaceAll("[\\r\\n]", "");
      log.info(
        "request method={} path={} status={} durationMs={}",
        req.getMethod(),
        path,
        res.getStatus(),
        (System.nanoTime() - start) / 1000000
      );
      MDC.remove("requestId");
    }
  }
}
