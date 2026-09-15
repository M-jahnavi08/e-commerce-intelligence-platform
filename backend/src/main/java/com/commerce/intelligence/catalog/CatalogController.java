package com.commerce.intelligence.catalog;

import com.commerce.intelligence.domain.*;
import com.commerce.intelligence.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {

  private final CatalogService service;
  private final CategoryRepository categories;
  private final CategoryCache cache;

  public CatalogController(
    CatalogService s,
    CategoryRepository c,
    CategoryCache cache
  ) {
    service = s;
    categories = c;
    this.cache = cache;
  }

  @GetMapping("/products")
  public Page<CatalogService.ProductView> search(
    @RequestParam(required = false) String q,
    @RequestParam(required = false) UUID category,
    @RequestParam(required = false) BigDecimal min,
    @RequestParam(required = false) BigDecimal max,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "12") int size,
    @RequestParam(defaultValue = "name") String sort
  ) {
    return service.search(q, category, min, max, page, size, sort);
  }

  @GetMapping("/products/{id}")
  public CatalogService.ProductView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/categories")
  public List<Category> categories() {
    return cache.get();
  }

  public record CreateCategory(@NotBlank @Size(max = 100) String name) {}

  @PostMapping("/admin/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public Category createCategory(@Valid @RequestBody CreateCategory request) {
    Category c = new Category();
    c.id = UUID.randomUUID();
    c.name = request.name().trim();
    Category saved = categories.save(c);
    cache.invalidate();
    return saved;
  }

  public record CreateProduct(
    @NotBlank @Size(max = 64) String sku,
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 4000) String description,
    @NotNull UUID categoryId,
    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    BigDecimal price,
    @Min(0) int stock
  ) {}

  public record UpdateProduct(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 4000) String description,
    @NotNull UUID categoryId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @NotNull Boolean active,
    @NotNull @Min(0) Long version
  ) {}

  @PutMapping("/admin/products/{id}")
  public CatalogService.ProductView update(@PathVariable UUID id, @Valid @RequestBody UpdateProduct r) {
    return service.update(id, r);
  }

  @PostMapping("/admin/products")
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogService.ProductView create(
    @Valid @RequestBody CreateProduct r
  ) {
    return service.create(
      r.sku(),
      r.name(),
      r.description(),
      r.categoryId(),
      r.price(),
      r.stock()
    );
  }
}
