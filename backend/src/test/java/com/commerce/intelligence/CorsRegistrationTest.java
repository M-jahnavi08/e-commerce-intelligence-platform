package com.commerce.intelligence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

import com.commerce.intelligence.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, properties = {
  "app.jwt-secret=cors-test-only-secret-at-least-thirty-two-bytes"
})
@Import(SecurityConfig.class)
class CorsRegistrationTest {
  @Autowired MockMvc mvc;
  @MockBean AuthService auth;

  @Test
  void localOriginsCanPreflightAndRegister() throws Exception {
    when(auth.register(anyString(), anyString())).thenReturn(new AuthService.Session("test-token", "cors@example.test", "CUSTOMER", 123));
    for (String origin : new String[]{"http://127.0.0.1:8088", "http://localhost:8088", "http://127.0.0.1:5173", "http://localhost:5173"}) {
      mvc.perform(options("/api/auth/register").header("Origin", origin)
        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
      mvc.perform(post("/api/auth/register").header("Origin", origin).contentType("application/json")
        .content("{\"email\":\"cors@example.test\",\"password\":\"test-password-123\"}"))
        .andExpect(status().isCreated()).andExpect(header().string("Access-Control-Allow-Origin", origin));
    }
    verify(auth, times(4)).register("cors@example.test", "test-password-123");
  }

  @Test
  void unrelatedAndLookalikeOriginsAreRejectedBeforeRegistration() throws Exception {
    for (String origin : new String[]{"https://untrusted.example", "http://127.0.0.1:9999", "http://localhost.evil.example:8088", "null"}) {
      mvc.perform(options("/api/auth/register").header("Origin", origin).header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
      mvc.perform(post("/api/auth/register").header("Origin", origin).contentType("application/json")
        .content("{\"email\":\"cors@example.test\",\"password\":\"test-password-123\"}"))
        .andExpect(status().isForbidden());
    }
    verifyNoInteractions(auth);
  }
}
