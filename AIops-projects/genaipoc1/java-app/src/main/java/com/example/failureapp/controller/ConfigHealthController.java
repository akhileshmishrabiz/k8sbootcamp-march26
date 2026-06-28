package com.example.failureapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/config")
@Slf4j
public class ConfigHealthController {

    // Secrets
    @Value("${payment.gateway.api.key:#{null}}")
    private String paymentApiKey;

    @Value("${stripe.secret.key:#{null}}")
    private String stripeSecretKey;

    @Value("${jwt.secret:#{null}}")
    private String jwtSecret;

    @Value("${encryption.key:#{null}}")
    private String encryptionKey;

    // ConfigMap values
    @Value("${feature.payment.enabled:false}")
    private boolean paymentEnabled;

    @Value("${feature.notifications.enabled:false}")
    private boolean notificationsEnabled;

    @Value("${payment.gateway.url:#{null}}")
    private String paymentGatewayUrl;

    @Value("${http.connect.timeout:5000}")
    private int connectTimeout;

    @Value("${http.read.timeout:30000}")
    private int readTimeout;

    @Value("${database.pool.size:10}")
    private int dbPoolSize;

    @Value("${circuit.breaker.failure.threshold:50}")
    private int circuitBreakerThreshold;

    @Value("${retry.max.attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.environment:unknown}")
    private String environment;

    /**
     * Comprehensive configuration health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkConfigHealth() {
        Map<String, Object> response = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        log.info("Starting configuration health check...");

        // Check critical secrets
        if (paymentApiKey == null || paymentApiKey.isEmpty()) {
            String error = "CRITICAL: Payment Gateway API key is missing";
            errors.add(error);
            log.error("=== CONFIGURATION ERROR ===");
            log.error(error);
            log.error("Expected secret: payment.gateway.api.key");
            log.error("Source: Secret 'app-secrets' in namespace 'failure-monitoring'");
            log.error("Impact: Payment processing will fail");
            log.error("Fix: Ensure secret exists with: kubectl get secret app-secrets -n failure-monitoring");
        }

        if (jwtSecret == null || jwtSecret.isEmpty()) {
            String error = "CRITICAL: JWT secret is missing";
            errors.add(error);
            log.error("=== SECURITY CONFIGURATION ERROR ===");
            log.error(error);
            log.error("Expected secret: jwt.secret");
            log.error("Impact: User authentication will fail, security compromised");
            log.error("Fix: Set JWT secret in app-secrets ConfigMap");
        }

        if (encryptionKey == null || encryptionKey.isEmpty()) {
            String warning = "WARNING: Encryption key is missing";
            warnings.add(warning);
            log.warn(warning);
            log.warn("Sensitive data encryption disabled");
        }

        // Check ConfigMap values
        if (paymentGatewayUrl == null || paymentGatewayUrl.isEmpty()) {
            String error = "CRITICAL: Payment gateway URL not configured";
            errors.add(error);
            log.error("=== CONFIGURATION ERROR ===");
            log.error(error);
            log.error("Expected config: payment.gateway.url");
            log.error("Source: ConfigMap 'app-config'");
            log.error("Fix: Verify ConfigMap with: kubectl get configmap app-config -n failure-monitoring -o yaml");
        }

        // Validate timeout configurations
        if (connectTimeout <= 0 || connectTimeout > 60000) {
            String warning = "WARNING: Invalid connect timeout: " + connectTimeout + "ms";
            warnings.add(warning);
            log.warn(warning);
            log.warn("Recommended range: 1000-10000ms");
        }

        if (readTimeout <= 0 || readTimeout > 120000) {
            String warning = "WARNING: Invalid read timeout: " + readTimeout + "ms";
            warnings.add(warning);
            log.warn(warning);
            log.warn("Recommended range: 5000-60000ms");
        }

        // Validate pool sizes
        if (dbPoolSize < 5) {
            String warning = "WARNING: Database pool size too small: " + dbPoolSize;
            warnings.add(warning);
            log.warn(warning);
            log.warn("Recommended minimum: 10 connections");
            log.warn("Current setting may cause connection exhaustion under load");
        }

        if (dbPoolSize > 100) {
            String warning = "WARNING: Database pool size very large: " + dbPoolSize;
            warnings.add(warning);
            log.warn(warning);
            log.warn("This may exhaust database connections");
        }

        // Check feature flags
        if (!paymentEnabled) {
            String warning = "WARNING: Payment feature is disabled";
            warnings.add(warning);
            log.warn(warning);
            log.warn("Feature flag 'feature.payment.enabled' is false");
        }

        // Build response
        response.put("timestamp", new Date());
        response.put("environment", environment);

        Map<String, Object> secretsStatus = new HashMap<>();
        secretsStatus.put("paymentApiKey", paymentApiKey != null ? "CONFIGURED" : "MISSING");
        secretsStatus.put("stripeSecretKey", stripeSecretKey != null ? "CONFIGURED" : "MISSING");
        secretsStatus.put("jwtSecret", jwtSecret != null ? "CONFIGURED" : "MISSING");
        secretsStatus.put("encryptionKey", encryptionKey != null ? "CONFIGURED" : "MISSING");
        response.put("secrets", secretsStatus);

        Map<String, Object> configStatus = new HashMap<>();
        configStatus.put("paymentGatewayUrl", paymentGatewayUrl != null ? paymentGatewayUrl : "NOT_SET");
        configStatus.put("connectTimeout", connectTimeout + "ms");
        configStatus.put("readTimeout", readTimeout + "ms");
        configStatus.put("dbPoolSize", dbPoolSize);
        configStatus.put("circuitBreakerThreshold", circuitBreakerThreshold + "%");
        configStatus.put("maxRetryAttempts", maxRetryAttempts);
        response.put("configuration", configStatus);

        Map<String, Boolean> features = new HashMap<>();
        features.put("paymentEnabled", paymentEnabled);
        features.put("notificationsEnabled", notificationsEnabled);
        response.put("features", features);

        response.put("errors", errors);
        response.put("warnings", warnings);

        if (!errors.isEmpty()) {
            response.put("status", "UNHEALTHY");
            log.error("Configuration health check: FAILED with {} errors", errors.size());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } else if (!warnings.isEmpty()) {
            response.put("status", "DEGRADED");
            log.warn("Configuration health check: DEGRADED with {} warnings", warnings.size());
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } else {
            response.put("status", "HEALTHY");
            log.info("Configuration health check: OK");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Simulate missing secret scenario
     */
    @PostMapping("/simulate/missing-secret")
    public ResponseEntity<Map<String, String>> simulateMissingSecret() {
        Map<String, String> response = new HashMap<>();

        log.error("=== SIMULATING MISSING SECRET SCENARIO ===");
        log.error("Attempting to process payment with missing API key");
        log.error("Payment Gateway API Key: null");
        log.error("java.lang.IllegalStateException: Payment gateway credentials not configured");
        log.error("\tat com.example.failureapp.service.PaymentService.processPayment(PaymentService.java:45)");
        log.error("\tat com.example.failureapp.controller.OrderController.createOrder(OrderController.java:123)");
        log.error("Root cause: Secret 'app-secrets' missing key 'payment.gateway.api.key'");
        log.error("Kubernetes secret path: /var/run/secrets/app-secrets/payment.gateway.api.key");
        log.error("Fix: kubectl create secret generic app-secrets --from-literal=payment.gateway.api.key=YOUR_KEY -n failure-monitoring");

        response.put("status", "error");
        response.put("message", "Payment processing failed - API key not configured");
        response.put("errorCode", "MISSING_SECRET");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Simulate incorrect config scenario
     */
    @PostMapping("/simulate/invalid-config")
    public ResponseEntity<Map<String, String>> simulateInvalidConfig() {
        Map<String, String> response = new HashMap<>();

        log.error("=== INVALID CONFIGURATION DETECTED ===");
        log.error("Database connection pool configuration error");
        log.error("Configured pool size: -1 (invalid)");
        log.error("com.zaxxer.hikari.HikariConfig$ValidationException: poolSize cannot be negative");
        log.error("Configuration source: ConfigMap 'app-config' key 'database.pool.size'");
        log.error("Current value: '-1'");
        log.error("Valid range: 1-100");
        log.error("Application startup aborted due to invalid configuration");
        log.error("Fix: kubectl edit configmap app-config -n failure-monitoring");
        log.error("Update database.pool.size to a valid value (recommended: 20)");

        response.put("status", "error");
        response.put("message", "Invalid database pool configuration");
        response.put("errorCode", "INVALID_CONFIG");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Simulate config not loaded scenario
     */
    @PostMapping("/simulate/config-not-mounted")
    public ResponseEntity<Map<String, String>> simulateConfigNotMounted() {
        Map<String, String> response = new HashMap<>();

        log.error("=== CONFIGURATION MOUNT FAILURE ===");
        log.error("ConfigMap not mounted to pod filesystem");
        log.error("Expected mount path: /etc/config/app-config");
        log.error("Error: No such file or directory");
        log.error("Pod name: failure-app-dd869bf5b-xyz123");
        log.error("Volume mount configuration missing in deployment spec");
        log.error("Check deployment with: kubectl get deployment failure-app -n failure-monitoring -o yaml");
        log.error("Verify volumes section includes:");
        log.error("  volumes:");
        log.error("    - name: config");
        log.error("      configMap:");
        log.error("        name: app-config");
        log.error("Application falling back to default values");
        log.error("Impact: Features may behave unexpectedly");

        response.put("status", "error");
        response.put("message", "ConfigMap not properly mounted");
        response.put("errorCode", "CONFIG_NOT_MOUNTED");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Simulate feature flag causing failure
     */
    @PostMapping("/simulate/feature-disabled")
    public ResponseEntity<Map<String, String>> simulateFeatureDisabled() {
        Map<String, String> response = new HashMap<>();

        log.error("=== FEATURE DISABLED ERROR ===");
        log.error("Attempted to process payment but payment feature is disabled");
        log.error("Feature flag 'feature.payment.enabled' = false");
        log.error("User attempted payment operation: POST /api/orders/checkout");
        log.error("Order ID: 12345, Amount: $99.99");
        log.error("java.lang.UnsupportedOperationException: Payment processing is currently disabled");
        log.error("\tat com.example.failureapp.service.PaymentService.validateFeatureEnabled(PaymentService.java:67)");
        log.error("Configuration source: ConfigMap 'app-config'");
        log.error("To enable: Set feature.payment.enabled=true in app-config");
        log.error("Impact: All payment operations are rejected");

        response.put("status", "error");
        response.put("message", "Payment feature is disabled");
        response.put("errorCode", "FEATURE_DISABLED");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * Get all configuration values (for debugging)
     */
    @GetMapping("/values")
    public ResponseEntity<Map<String, Object>> getAllConfigValues() {
        Map<String, Object> config = new HashMap<>();

        // Secrets (masked)
        Map<String, String> secrets = new HashMap<>();
        secrets.put("paymentApiKey", maskSecret(paymentApiKey));
        secrets.put("stripeSecretKey", maskSecret(stripeSecretKey));
        secrets.put("jwtSecret", maskSecret(jwtSecret));
        secrets.put("encryptionKey", maskSecret(encryptionKey));
        config.put("secrets", secrets);

        // ConfigMap values (full values)
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("paymentGatewayUrl", paymentGatewayUrl);
        configMap.put("connectTimeout", connectTimeout);
        configMap.put("readTimeout", readTimeout);
        configMap.put("dbPoolSize", dbPoolSize);
        configMap.put("circuitBreakerThreshold", circuitBreakerThreshold);
        configMap.put("maxRetryAttempts", maxRetryAttempts);
        configMap.put("environment", environment);
        config.put("configMap", configMap);

        // Feature flags
        Map<String, Boolean> features = new HashMap<>();
        features.put("paymentEnabled", paymentEnabled);
        features.put("notificationsEnabled", notificationsEnabled);
        config.put("features", features);

        log.info("Configuration values requested");
        return ResponseEntity.ok(config);
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "NOT_SET";
        }
        if (secret.length() <= 4) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
