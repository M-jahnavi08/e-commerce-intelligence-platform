package com.commerce.intelligence.catalog;

import com.commerce.intelligence.domain.Category;
import com.commerce.intelligence.repository.CategoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CategoryCache {

  private final StringRedisTemplate redis;
  private final CategoryRepository categories;
  private final ObjectMapper json;
  private final boolean enabled;

  public CategoryCache(
    StringRedisTemplate r,
    CategoryRepository c,
    ObjectMapper j,
    @Value("${app.cache-enabled}") boolean e
  ) {
    redis = r;
    categories = c;
    json = j;
    enabled = e;
  }

  public List<Category> get() {
    if (enabled) try {
      String cached = redis.opsForValue().get("catalog:categories:v2");
      if (cached != null) return json.readValue(
        cached,
        new TypeReference<List<Category>>() {}
      ).stream().filter(c -> c.active).toList();
    } catch (Exception ignored) {}
    var result = categories.findAll(
      org.springframework.data.domain.Sort.by("name")
    ).stream().filter(c -> c.active).toList();
    if (enabled) try {
      redis
        .opsForValue()
        .set(
          "catalog:categories:v2",
          json.writeValueAsString(result),
          Duration.ofSeconds(60)
        );
    } catch (Exception ignored) {}
    return result;
  }

  public void invalidate() {
    if (enabled) try {
      redis.delete("catalog:categories:v2");
    } catch (Exception ignored) {}
  }
}
