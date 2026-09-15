package com.commerce.intelligence.intelligence;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
public class IntelligenceController {

  private final ProductRepository products;
  private final CategoryRepository categories;
  private final JdbcTemplate jdbc;
  private final RestClient ml;

  public IntelligenceController(
    ProductRepository p,
    CategoryRepository c,
    JdbcTemplate j,
    @Value("${app.ml-url}") String url,
    @Value("${app.ml-token}") String token
  ) {
    products = p;
    categories = c;
    jdbc = j;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(12));
    ml = RestClient.builder()
      .baseUrl(url)
      .requestFactory(factory)
      .defaultHeader("X-Service-Token", token)
      .build();
  }

  @GetMapping("/products/{id}/recommendations")
  public Map<?, ?> recommendations(@PathVariable UUID id) {
    products
      .findById(id)
      .filter(p -> p.active)
      .orElseThrow(ApiException::missing);
    var names = new HashMap<UUID, String>();
    categories.findAll().forEach(c -> names.put(c.id, c.name));
    var all = products.findByActiveTrue(
      org.springframework.data.domain.PageRequest.of(
        0,
        5000,
        org.springframework.data.domain.Sort.by("id")
      )
    );
    if (all.getTotalElements() > 5000) return Map.of(
      "status",
      "unavailable",
      "reason",
      "Catalog exceeds the synchronous model limit; offline indexing is required."
    );
    var catalog = all
      .stream()
      .map(p ->
        Map.of(
          "id",
          p.id,
          "name",
          p.name,
          "description",
          p.description,
          "category",
          names.getOrDefault(p.categoryId, "")
        )
      )
      .toList();
    return call(
      "/recommendations",
      Map.of("productId", id, "products", catalog, "limit", 5)
    );
  }

  @GetMapping("/admin/intelligence/forecast/{id}")
  public Map<?, ?> forecast(@PathVariable UUID id) {
    if (!products.existsById(id)) throw ApiException.missing();
    var observations = jdbc.queryForList(
      "select (o.created_at at time zone 'UTC')::date::text as date,sum(i.quantity) as units from orders o join order_items i on i.order_id=o.id where o.status='PAID' and i.product_id=? and o.created_at < (current_timestamp at time zone 'UTC')::date at time zone 'UTC' and o.created_at>=current_timestamp-interval '365 days' group by 1 order by 1",
      id
    );
    return call(
      "/forecast",
      Map.of(
        "observations",
        observations,
        "asOf",
        LocalDate.now(ZoneOffset.UTC).minusDays(1),
        "horizon",
        7
      )
    );
  }

  @GetMapping("/admin/intelligence/anomalies")
  public Map<?, ?> anomalies() {
    var observations = jdbc.queryForList(
      "select (created_at at time zone 'UTC')::date::text as date,sum(total) as revenue from orders where status='PAID' and created_at < (current_timestamp at time zone 'UTC')::date at time zone 'UTC' and created_at>=current_timestamp-interval '365 days' group by 1 order by 1"
    );
    return call(
      "/anomalies",
      Map.of(
        "observations",
        observations,
        "asOf",
        LocalDate.now(ZoneOffset.UTC).minusDays(1)
      )
    );
  }

  private Map<?, ?> call(String path, Object body) {
    try {
      return ml.post().uri(path).body(body).retrieve().body(Map.class);
    } catch (org.springframework.web.client.RestClientException ex) {
      throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Intelligence service is unavailable. No predictions were generated."
      );
    }
  }
}
