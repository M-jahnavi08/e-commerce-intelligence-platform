package com.commerce.intelligence.catalog;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.domain.*;
import com.commerce.intelligence.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

  private final ProductRepository products;
  private final InventoryRepository inventory;
  private final CategoryRepository categories;

  public CatalogService(
    ProductRepository p,
    InventoryRepository i,
    CategoryRepository c
  ) {
    products = p;
    inventory = i;
    categories = c;
  }

  public record ProductView(
    UUID id,
    String sku,
    String name,
    String description,
    UUID categoryId,
    BigDecimal price,
    String imageUrl,
    int stock,
    boolean active,
    long version
  ) {}

  public List<ProductView> views(List<Product> entries) {
    Map<UUID, Integer> quantities = new HashMap<>();
    inventory
      .findAllById(
        entries
          .stream()
          .map(p -> p.id)
          .toList()
      )
      .forEach(i -> quantities.put(i.productId, i.quantity));
    return entries
      .stream()
      .map(p -> view(p, quantities.getOrDefault(p.id, 0)))
      .toList();
  }

  public ProductView view(Product p) {
    return view(
      p,
      inventory
        .findById(p.id)
        .map(i -> i.quantity)
        .orElse(0)
    );
  }

  private ProductView view(Product p, int quantity) {
    return new ProductView(
      p.id,
      p.sku,
      p.name,
      p.description,
      p.categoryId,
      p.price,
      p.imageUrl,
      quantity,
      p.active,
      p.version
    );
  }

  @Transactional(readOnly = true)
  public Page<ProductView> search(
    String query,
    UUID category,
    BigDecimal min,
    BigDecimal max,
    int page,
    int size,
    String sort
  ) {
    if (
      page < 0 ||
      size < 1 ||
      size > 100 ||
      (min != null && min.signum() < 0) ||
      (max != null && max.signum() < 0) ||
      (min != null && max != null && min.compareTo(max) > 0)
    ) throw new IllegalArgumentException();
    Specification<Product> spec = (r, q, b) -> b.isTrue(r.get("active"));
    if (query != null && !query.isBlank()) {
      String pattern =
        "%" +
        query
          .toLowerCase(Locale.ROOT)
          .replace("\\", "\\\\")
          .replace("%", "\\%")
          .replace("_", "\\_") +
        "%";
      spec = spec.and((r, q, b) ->
        b.like(b.lower(r.get("name")), pattern, '\\')
      );
    }
    if (category != null) spec = spec.and((r, q, b) ->
      b.equal(r.get("categoryId"), category)
    );
    if (min != null) spec = spec.and((r, q, b) ->
      b.greaterThanOrEqualTo(r.get("price"), min)
    );
    if (max != null) spec = spec.and((r, q, b) ->
      b.lessThanOrEqualTo(r.get("price"), max)
    );
    Sort ordering = switch (sort) {
      case "price-asc" -> Sort.by("price").ascending();
      case "price-desc" -> Sort.by("price").descending();
      default -> Sort.by("name");
    };
    var result = products.findAll(
      spec,
      PageRequest.of(page, size, ordering.and(Sort.by("id")))
    );
    return new PageImpl<>(
      views(result.getContent()),
      result.getPageable(),
      result.getTotalElements()
    );
  }

  @Transactional(readOnly = true)
  public ProductView get(UUID id) {
    Product p = products
      .findById(id)
      .filter(x -> x.active)
      .orElseThrow(ApiException::missing);
    return view(p);
  }

  @Transactional(readOnly = true)
  public ProductView getIncludingInactive(UUID id) {
    return view(products.findById(id).orElseThrow(ApiException::missing));
  }

  @Transactional
  public ProductView update(UUID id, CatalogController.UpdateProduct r) {
    Product p = products.lockById(id).orElseThrow(ApiException::missing);
    if (p.version != r.version()) throw ApiException.conflict(
      "Product changed. Reload before saving again."
    );
    if (
      categories
        .findById(r.categoryId())
        .filter(c -> c.active)
        .isEmpty()
    ) throw ApiException.missing();
    p.name = r.name().trim();
    p.description = r.description().trim();
    p.categoryId = r.categoryId();
    p.price = r.price();
    p.active = r.active();
    if (r.imageUrl() != null) p.imageUrl = validatedImage(r.imageUrl());
    products.flush();
    return view(p);
  }

  @Transactional
  public ProductView create(
    String sku,
    String name,
    String description,
    UUID categoryId,
    BigDecimal price,
    int stock,
    String imageUrl
  ) {
    if (
      categories
        .findById(categoryId)
        .filter(c -> c.active)
        .isEmpty()
    ) throw ApiException.missing();
    Product p = new Product();
    p.id = UUID.randomUUID();
    p.sku = sku;
    String image = sku.toLowerCase(Locale.ROOT).replaceFirst("^demo-", "");
    if (
      Set.of(
        "headphones",
        "earbuds",
        "speaker",
        "keyboard",
        "lamp",
        "stand",
        "bag",
        "bottle",
        "notebook",
        "mug",
        "throw",
        "tray",
        "hub",
        "watch",
        "shirt",
        "sneakers",
        "skincare",
        "sunglasses",
        "pouch"
      ).contains(image)
    ) {
      p.imageUrl = "/images/products/" + image + ".svg";
    }
    if (imageUrl != null) p.imageUrl = validatedImage(imageUrl);
    p.name = name.trim();
    p.description = description.trim();
    p.categoryId = categoryId;
    p.price = price;
    products.save(p);
    Inventory i = new Inventory();
    i.productId = p.id;
    i.quantity = stock;
    inventory.save(i);
    return view(p);
  }

  // Only bundled assets are accepted: no remote tracking URLs or arbitrary paths.
  private String validatedImage(String path) {
    if (
      !path.matches(
        "/images/products/(headphones|earbuds|speaker|keyboard|lamp|stand|bag|bottle|notebook|mug|throw|tray|hub|watch|shirt|sneakers|skincare|sunglasses|pouch|placeholder)\\.svg"
      )
    ) throw new IllegalArgumentException("Unsupported product image");
    return path;
  }
}
