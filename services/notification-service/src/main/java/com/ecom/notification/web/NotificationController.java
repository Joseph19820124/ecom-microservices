package com.ecom.notification.web;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final Map<String, Map<String, Object>> notifications = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "notification-service", "status", "UP", "ts", Instant.now().toString());
    }

    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, Object> body) {
        String id = "n-" + UUID.randomUUID();
        Map<String, Object> n = new HashMap<>(body);
        n.put("id", id);
        n.put("status", "DELIVERED");
        n.put("sentAt", Instant.now().toString());
        notifications.put(id, n);
        return n;
    }

    @GetMapping
    public Collection<Map<String, Object>> list(@RequestParam(required = false) String userId) {
        if (userId == null) return notifications.values();
        return notifications.values().stream().filter(n -> userId.equals(n.get("userId"))).toList();
    }
}
