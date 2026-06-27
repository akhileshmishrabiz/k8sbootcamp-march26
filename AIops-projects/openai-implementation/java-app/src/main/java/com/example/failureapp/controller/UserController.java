package com.example.failureapp.controller;

import com.example.failureapp.model.User;
import com.example.failureapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        try {
            User user = User.builder()
                    .email(request.email)
                    .name(request.name)
                    .password(request.password)
                    .accountBalance(request.initialBalance != null ? request.initialBalance : 0.0)
                    .build();

            User createdUser = userService.createUser(user);
            log.info("User created: id={}, email={}", createdUser.getId(), createdUser.getEmail());

            return ResponseEntity.ok(createdUser);
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userService.getUserByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/balance")
    public ResponseEntity<Map<String, Object>> updateBalance(
            @PathVariable Long id,
            @RequestBody BalanceUpdateRequest request) {
        try {
            User user = userService.updateUserBalance(id, request.amount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", user.getId());
            response.put("newBalance", user.getAccountBalance());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating balance for user {}: {}", id, e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // DTOs
    public static class CreateUserRequest {
        public String email;
        public String name;
        public String password;
        public Double initialBalance;
    }

    public static class BalanceUpdateRequest {
        public Double amount;
    }
}
