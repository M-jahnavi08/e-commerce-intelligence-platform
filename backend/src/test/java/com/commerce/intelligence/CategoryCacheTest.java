package com.commerce.intelligence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.commerce.intelligence.catalog.CategoryCache;
import com.commerce.intelligence.domain.Category;
import com.commerce.intelligence.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CategoryCacheTest {
  @Test
  void redisOutageFallsBackToPostgres() {
    var redis = mock(StringRedisTemplate.class);
    var repository = mock(CategoryRepository.class);
    var categories = List.of(new Category());
    when(repository.findAll(any(Sort.class))).thenReturn(categories);
    when(redis.opsForValue()).thenThrow(new RuntimeException("Redis offline"));
    assertThat(new CategoryCache(redis, repository, new ObjectMapper(), true).get()).isEqualTo(categories);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cacheHitAvoidsDatabaseRead() {
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    var repository = mock(CategoryRepository.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("catalog:categories:v2")).thenReturn("[]");
    assertThat(new CategoryCache(redis, repository, new ObjectMapper(), true).get()).isEmpty();
    verifyNoInteractions(repository);
  }

  @Test
  @SuppressWarnings("unchecked")
  void malformedCacheEntryIsReplacedWithBoundedTtl() {
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    var repository = mock(CategoryRepository.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("catalog:categories:v2")).thenReturn("invalid json");
    when(repository.findAll(any(Sort.class))).thenReturn(List.of());
    var cache = new CategoryCache(redis, repository, new ObjectMapper(), true);
    assertThat(cache.get()).isEmpty();
    verify(values).set("catalog:categories:v2", "[]", Duration.ofSeconds(60));
    cache.invalidate();
    verify(redis).delete("catalog:categories:v2");
  }
}
