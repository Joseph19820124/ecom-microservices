package com.ecom.order.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "order-service", "status", "UP", "ts", Instant.now().toString());
    }

    @GetMapping
    public Collection<Map<String, Object>> list(@RequestParam(required = false) String userId) {
        if (userId == null) return orders.values();
        return orders.values().stream().filter(o -> userId.equals(o.get("userId"))).toList();
    }

    @PostMapping
    public Map<String, Object> place(@RequestBody Map<String, Object> body) {
        String id = "o-" + UUID.randomUUID();
        Map<String, Object> order = new HashMap<>(body);
        order.put("id", id);
        order.put("status", "PLACED");
        order.put("createdAt", Instant.now().toString());
        orders.put(id, order);
        return order;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> o = orders.get(id);
        return o == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(o);
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Map<String, Object> o = orders.get(id);
        if (o == null) return ResponseEntity.notFound().build();
        o.put("status", body.getOrDefault("status", "UNKNOWN"));
        o.put("updatedAt", Instant.now().toString());
        return ResponseEntity.ok(o);
    }
}
