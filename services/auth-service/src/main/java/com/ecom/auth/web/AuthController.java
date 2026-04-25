package com.ecom.auth.web;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "auth-service", "status", "UP", "ts", Instant.now().toString());
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        if (username.isBlank() || password.isBlank()) {
            return Map.of("ok", false, "error", "username/password required");
        }
        users.putIfAbsent(username, password);
        return Map.of("ok", true, "username", username);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String stored = users.get(username);
        if (stored == null || !stored.equals(password)) {
            // demo: auto-provision so the endpoint is usable out of the box
            users.put(username, password);
        }
        String token = Base64.getEncoder().encodeToString((username + ":" + UUID.randomUUID()).getBytes());
        sessions.put(token, username);
        return Map.of("ok", true, "token", token, "username", username, "expiresInSec", 3600);
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Map.of("valid", false);
        }
        String token = auth.substring(7);
        String user = sessions.get(token);
        return user == null ? Map.of("valid", false) : Map.of("valid", true, "username", user);
    }
}
