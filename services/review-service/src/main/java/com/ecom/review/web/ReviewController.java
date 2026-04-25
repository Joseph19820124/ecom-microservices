package com.ecom.review.web;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final Map<String, Map<String, Object>> reviews = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "review-service", "status", "UP", "ts", Instant.now().toString());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = "r-" + UUID.randomUUID();
        Map<String, Object> r = new HashMap<>(body);
        r.put("id", id);
        r.put("createdAt", Instant.now().toString());
        reviews.put(id, r);
        return r;
    }

    @GetMapping
    public Collection<Map<String, Object>> list(@RequestParam(required = false) String productId) {
        if (productId == null) return reviews.values();
        return reviews.values().stream().filter(r -> productId.equals(r.get("productId"))).toList();
    }

    @GetMapping("/product/{productId}/summary")
    public Map<String, Object> summary(@PathVariable String productId) {
        var ratings = reviews.values().stream()
                .filter(r -> productId.equals(r.get("productId")))
                .mapToDouble(r -> ((Number) r.getOrDefault("rating", 0)).doubleValue())
                .toArray();
        double avg = ratings.length == 0 ? 0 : Arrays.stream(ratings).average().orElse(0);
        return Map.of("productId", productId, "count", ratings.length, "avgRating", avg);
    }
}
