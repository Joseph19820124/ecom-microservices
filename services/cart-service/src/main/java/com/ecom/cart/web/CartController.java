package com.ecom.cart.web;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final Map<String, Map<String, Integer>> carts = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "cart-service", "status", "UP", "ts", Instant.now().toString());
    }

    @GetMapping("/{userId}")
    public Map<String, Object> view(@PathVariable String userId) {
        Map<String, Integer> items = carts.getOrDefault(userId, Map.of());
        return Map.of("userId", userId, "items", items, "size", items.values().stream().mapToInt(Integer::intValue).sum());
    }

    @PostMapping("/{userId}/items")
    public Map<String, Object> addItem(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        String productId = String.valueOf(body.get("productId"));
        int qty = ((Number) body.getOrDefault("qty", 1)).intValue();
        carts.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).merge(productId, qty, Integer::sum);
        return view(userId);
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public Map<String, Object> remove(@PathVariable String userId, @PathVariable String productId) {
        Map<String, Integer> c = carts.get(userId);
        if (c != null) c.remove(productId);
        return view(userId);
    }

    @PostMapping("/{userId}/checkout")
    public Map<String, Object> checkout(@PathVariable String userId) {
        Map<String, Integer> items = carts.remove(userId);
        if (items == null || items.isEmpty()) return Map.of("ok", false, "error", "empty cart");
        return Map.of("ok", true, "userId", userId, "items", items, "checkoutId", UUID.randomUUID().toString());
    }
}
