package com.ecom.inventory.web;

import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final Map<String, AtomicInteger> stock = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        for (int i = 1; i <= 5; i++) stock.put("p-" + i, new AtomicInteger(100));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "inventory-service", "status", "UP", "ts", Instant.now().toString());
    }

    @GetMapping("/{productId}")
    public Map<String, Object> get(@PathVariable String productId) {
        AtomicInteger s = stock.computeIfAbsent(productId, k -> new AtomicInteger(0));
        return Map.of("productId", productId, "available", s.get());
    }

    @PostMapping("/{productId}/reserve")
    public Map<String, Object> reserve(@PathVariable String productId, @RequestBody Map<String, Object> body) {
        int qty = ((Number) body.getOrDefault("qty", 1)).intValue();
        AtomicInteger s = stock.computeIfAbsent(productId, k -> new AtomicInteger(0));
        synchronized (s) {
            if (s.get() < qty) return Map.of("ok", false, "error", "insufficient stock", "available", s.get());
            s.addAndGet(-qty);
            return Map.of("ok", true, "productId", productId, "reserved", qty, "available", s.get());
        }
    }

    @PostMapping("/{productId}/release")
    public Map<String, Object> release(@PathVariable String productId, @RequestBody Map<String, Object> body) {
        int qty = ((Number) body.getOrDefault("qty", 1)).intValue();
        AtomicInteger s = stock.computeIfAbsent(productId, k -> new AtomicInteger(0));
        s.addAndGet(qty);
        return Map.of("ok", true, "productId", productId, "available", s.get());
    }

    @PostMapping("/restock")
    public Map<String, Object> restock(@RequestBody Map<String, Object> body) {
        String productId = String.valueOf(body.get("productId"));
        int qty = ((Number) body.getOrDefault("qty", 1)).intValue();
        AtomicInteger s = stock.computeIfAbsent(productId, k -> new AtomicInteger(0));
        s.addAndGet(qty);
        Map<String, Object> resp = new HashMap<>();
        resp.put("productId", productId);
        resp.put("available", s.get());
        return resp;
    }
}
