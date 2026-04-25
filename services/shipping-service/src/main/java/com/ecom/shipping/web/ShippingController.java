package com.ecom.shipping.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/shipping")
public class ShippingController {

    private final Map<String, Map<String, Object>> shipments = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "shipping-service", "status", "UP", "ts", Instant.now().toString());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = "ship-" + UUID.randomUUID();
        Map<String, Object> s = new HashMap<>(body);
        s.put("id", id);
        s.put("trackingNumber", "TRK" + System.currentTimeMillis());
        s.put("status", "CREATED");
        s.put("createdAt", Instant.now().toString());
        shipments.put(id, s);
        return s;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> s = shipments.get(id);
        return s == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(s);
    }

    @GetMapping("/track/{trackingNumber}")
    public Map<String, Object> track(@PathVariable String trackingNumber) {
        return shipments.values().stream()
                .filter(s -> trackingNumber.equals(s.get("trackingNumber")))
                .findFirst()
                .orElse(Map.of("trackingNumber", trackingNumber, "status", "NOT_FOUND"));
    }

    @PostMapping("/{id}/advance")
    public ResponseEntity<Map<String, Object>> advance(@PathVariable String id) {
        Map<String, Object> s = shipments.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        String cur = String.valueOf(s.getOrDefault("status", "CREATED"));
        String next = switch (cur) {
            case "CREATED" -> "PICKED_UP";
            case "PICKED_UP" -> "IN_TRANSIT";
            case "IN_TRANSIT" -> "OUT_FOR_DELIVERY";
            case "OUT_FOR_DELIVERY" -> "DELIVERED";
            default -> "DELIVERED";
        };
        s.put("status", next);
        s.put("updatedAt", Instant.now().toString());
        return ResponseEntity.ok(s);
    }
}
