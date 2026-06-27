package com.example.failureapp.service;

import com.example.failureapp.model.User;
import com.example.failureapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#id")
    public Optional<User> getUserById(Long id) {
        log.debug("Fetching user from database (cache miss): {}", id);
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User createUser(User user) {
        log.info("Creating new user: {}", user.getEmail());
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserBalance(Long userId, Double amount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Double newBalance = user.getAccountBalance() + amount;
        if (newBalance < 0) {
            log.error("Insufficient balance for user {}: current={}, requested={}", userId, user.getAccountBalance(), amount);
            throw new RuntimeException("Insufficient balance");
        }

        user.setAccountBalance(newBalance);
        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }
}
