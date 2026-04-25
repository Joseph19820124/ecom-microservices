package com.ecom.payment.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "payment-service", "status", "UP", "ts", Instant.now().toString());
    }

    @PostMapping("/charge")
    public Map<String, Object> charge(@RequestBody Map<String, Object> body) {
        String id = "pay-" + UUID.randomUUID();
        Map<String, Object> p = new HashMap<>(body);
        p.put("id", id);
        // demo logic: any negative amount fails; otherwise succeeds
        long amount = ((Number) body.getOrDefault("amountCents", 0)).longValue();
        String status = amount > 0 ? "SUCCEEDED" : "FAILED";
        p.put("status", status);
        p.put("processedAt", Instant.now().toString());
        payments.put(id, p);
        return p;
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable String id) {
        Map<String, Object> p = payments.get(id);
        if (p == null) return ResponseEntity.notFound().build();
        p.put("status", "REFUNDED");
        p.put("refundedAt", Instant.now().toString());
        return ResponseEntity.ok(p);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> p = payments.get(id);
        return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
    }
}
