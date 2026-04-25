package com.ecom.product.web;

import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final Map<String, Map<String, Object>> products = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        for (int i = 1; i <= 5; i++) {
            String id = "p-" + i;
            products.put(id, new HashMap<>(Map.of(
                    "id", id,
                    "sku", "SKU-" + i,
                    "name", "Sample Product " + i,
                    "priceCents", 1000L * i,
                    "currency", "USD"
            )));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "product-service", "status", "UP", "ts", Instant.now().toString());
    }

    @GetMapping
    public Collection<Map<String, Object>> list(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) return products.values();
        return products.values().stream()
                .filter(p -> String.valueOf(p.get("name")).toLowerCase(Locale.ROOT)
                        .contains(q.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = "p-" + UUID.randomUUID();
        Map<String, Object> p = new HashMap<>(body);
        p.put("id", id);
        products.put(id, p);
        return p;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> p = products.get(id);
        return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
    }
}
