package com.ecom.user.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/users")
public class UserController {

    private final Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "user-service", "status", "UP", "ts", Instant.now().toString());
    }

    @GetMapping
    public Collection<Map<String, Object>> list() {
        return users.values();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> u = new HashMap<>(body);
        u.put("id", id);
        u.put("createdAt", Instant.now().toString());
        users.put(id, u);
        return u;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> u = users.get(id);
        return u == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(u);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        users.remove(id);
        return Map.of("ok", true, "id", id);
    }
}
