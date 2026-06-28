package com.example.failureapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class FailureController {

    private static final Map<String, Boolean> failureStates = new ConcurrentHashMap<>();
    private final Random random = new Random();

    static {
        // New realistic failure modes
        failureStates.put("conflict_409", false);
        failureStates.put("not_found_404", false);
        failureStates.put("business_exception", false);
        failureStates.put("jwt_expired", false);
        failureStates.put("invalid_uuid", false);
        failureStates.put("db_constraint_violation", false);
        failureStates.put("malformed_request", false);
        failureStates.put("transaction_failure", false);
        failureStates.put("downstream_timeout", false);
        failureStates.put("optimistic_lock", false);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();

        // JWT expired failures happen on every request when active
        if (failureStates.get("jwt_expired")) {
            simulateJwtExpired();
            response.put("status", "error");
            response.put("message", "Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // Check other failure modes
        if (failureStates.get("conflict_409")) {
            simulateConflict409();
            response.put("status", "error");
            response.put("message", "Conflict error occurred");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (failureStates.get("not_found_404")) {
            simulateNotFound404();
            response.put("status", "error");
            response.put("message", "Resource not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        if (failureStates.get("business_exception")) {
            simulateBusinessException();
            response.put("status", "error");
            response.put("message", "Business logic error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        if (failureStates.get("db_constraint_violation")) {
            simulateDbConstraintViolation();
            response.put("status", "error");
            response.put("message", "Database error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        if (failureStates.get("transaction_failure")) {
            simulateTransactionFailure();
            response.put("status", "error");
            response.put("message", "Transaction error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        if (failureStates.get("downstream_timeout")) {
            simulateDownstreamTimeout();
            response.put("status", "error");
            response.put("message", "Service timeout");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        if (failureStates.get("optimistic_lock")) {
            simulateOptimisticLock();
            response.put("status", "error");
            response.put("message", "Concurrent update error");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        response.put("status", "healthy");
        response.put("message", "Application is running normally");
        response.put("timestamp", new Date());
        return ResponseEntity.ok(response);
    }

    // ========== REALISTIC PRODUCTION FAILURE SIMULATIONS ==========

    /**
     * Simulates HTTP 409 Conflict - Duplicate Resource Creation
     * Realistic scenario: User creation with duplicate email
     */
    private void simulateConflict409() {
        String transactionId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        String errorJson = String.format(
            "{\"timestamp\":\"%s\",\"statusCode\":409,\"status\":\"Conflict\",\"origin\":\"user-management\",\"efx-transaction-id\":\"%s\",\"efx-session-id\":\"%s\",\"errors\":\"Error: Something went wrong\"}",
            timestamp, transactionId, sessionId
        );

        log.error(errorJson);
        log.error("service: \"none\"");

        String stackTrace = "org.springframework.web.client.HttpClientErrorException: 409 409 CONFLICT\\n" +
            "    at com.company.ews.ams.user.management.handler.RestErrorResponseHandler.httpException(RestErrorResponseHandler.java:87)\\n" +
            "    at com.company.ews.ams.user.management.handler.RestErrorResponseHandler.handleResponse(RestErrorResponseHandler.java:52)\\n" +
            "    at org.springframework.web.client.ResponseErrorHandler.handleError(ResponseErrorHandler.java:63)\\n" +
            "    at org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:825)\\n" +
            "    at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:783)\\n" +
            "    at org.springframework.web.client.RestTemplate.execute(RestTemplate.java:739)\\n" +
            "    at org.springframework.web.client.RestTemplate.postForEntity(RestTemplate.java:464)\\n" +
            "    at com.company.ews.ams.user.management.service.user.UserService.createUser(UserService.java:155)\\n" +
            "    at com.company.ews.ams.user.management.controller.UserController.registerUser(UserController.java:89)\\n" +
            "    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)\\n" +
            "    at java.base/java.lang.reflect.Method.invoke(Unknown Source)\\n" +
            "    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)\\n" +
            "    at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)";

        log.error("stackTrace: \"{}\"", stackTrace);
    }

    /**
     * Simulates HTTP 404 Not Found - Entity Lookup Failure
     * Realistic scenario: Downstream service returns 404 for missing resource
     */
    private void simulateNotFound404() {
        String transactionId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        log.error("Inside handleClientAndServerExceptions - Error Response - {{\"timestamp\":\"{}\",\"statusCode\":404,\"status\":\"NOT_FOUND\",\"origin\":\"user-service\",\"efx-transaction-id\":\"{}\",\"efx-session-id\":\"{}\",\"errors\":\"Error: Something went wrong\"}}",
            timestamp, transactionId, sessionId);

        String stackTrace = "org.springframework.web.client.HttpClientErrorException: 404 404 NOT_FOUND\\n" +
            "    at com.company.ews.ams.user.management.handler.RestErrorResponseHandler.httpException(RestErrorResponseHandler.java:87)\\n" +
            "    at com.company.ews.ams.user.management.handler.RestErrorResponseHandler.handleResponse(RestErrorResponseHandler.java:52)\\n" +
            "    at org.springframework.web.client.ResponseErrorHandler.handleError(ResponseErrorHandler.java:63)\\n" +
            "    at org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:825)\\n" +
            "    at com.company.ews.ams.user.management.esp.service.UserSelfServiceImpl.lambda$findByIdV3$0(UserSelfServiceImpl.java:342)\\n" +
            "    at java.base/java.util.Optional.orElseThrow(Unknown Source)\\n" +
            "    at com.company.ews.ams.user.management.esp.service.UserSelfServiceImpl.findByIdV3(UserSelfServiceImpl.java:342)\\n" +
            "    at com.company.ews.ams.user.management.controller.UserController.getUserById(UserController.java:125)\\n" +
            "    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)\\n" +
            "    at java.base/java.lang.reflect.Method.invoke(Unknown Source)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)";

        log.error("stackTrace: \"{}\"", stackTrace);
    }

    /**
     * Simulates BusinessException - Object Not Found in Task Completion
     * Realistic scenario: Task completion fails because referenced object doesn't exist
     */
    private void simulateBusinessException() {
        String timestamp = Instant.now().toString();
        String threadName = "http-nio-8080-exec-" + (random.nextInt(10) + 1);

        log.error("{} ERROR [{}] com.company.ews.es.taskapi.validation.TaskConstraintValidator - Business validation failed for task completion",
            timestamp.substring(0, 23).replace("T", " "), threadName);

        String stackTrace = "com.company.ews.es.common.exception.BusinessException: object not found\\n" +
            "    at com.company.ews.es.taskapi.validation.TaskConstraintValidator.identifyConstraintForTaskCompletion(TaskConstraintValidator.java:287)\\n" +
            "    at com.company.ews.es.taskapi.validation.TaskConstraintValidator.validateTaskCompletion(TaskConstraintValidator.java:156)\\n" +
            "    at com.company.ews.es.taskapi.service.TaskServiceImpl.completeTask(TaskServiceImpl.java:535)\\n" +
            "    at com.company.ews.es.taskapi.service.TaskServiceImpl$$SpringCGLIB$$0.completeTask(<generated>)\\n" +
            "    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)\\n" +
            "    at java.base/java.lang.reflect.Method.invoke(Unknown Source)\\n" +
            "    at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:351)\\n" +
            "    at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)\\n" +
            "    at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)\\n" +
            "    at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:763)\\n" +
            "    at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:388)\\n" +
            "    at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)\\n" +
            "    at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)\\n" +
            "    at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:763)\\n" +
            "    at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:708)\\n" +
            "    at com.company.ews.es.taskapi.service.TaskServiceImpl$$SpringCGLIB$$1.completeTask(<generated>)\\n" +
            "    at com.company.ews.es.taskapi.controller.TaskController.completeTask(TaskController.java:205)\\n" +
            "    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)\\n" +
            "    at java.base/java.lang.reflect.Method.invoke(Unknown Source)\\n" +
            "    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)\\n" +
            "    at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808)\\n" +
            "    at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)\\n" +
            "    at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1072)\\n" +
            "    at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:965)\\n" +
            "    at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006)\\n" +
            "    at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:909)\\n" +
            "    at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590)\\n" +
            "    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883)";

        log.error(stackTrace);
    }

    /**
     * Simulates ExpiredJwtException - Okta JWT Token Expired
     * Realistic scenario: Authentication fails due to expired JWT token
     * This should log on EVERY request when active (simulating all requests failing auth)
     */
    private void simulateJwtExpired() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        Instant now = Instant.now();
        Instant expiredTime = now.minus(random.nextInt(10) + 55, ChronoUnit.MINUTES);
        long diffMillis = now.toEpochMilli() - expiredTime.toEpochMilli();

        String timestamp = now.toString().substring(0, 23).replace("T", " ");

        log.error("{} ERROR [{}] com.company.ews.es.common.auth.OktaJwtTokenImpl - JWT verification failed",
            timestamp, threadName);

        String stackTrace = "io.jsonwebtoken.ExpiredJwtException: JWT expired at " + expiredTime.toString() + ". Current time: " + now.toString() + ", a difference of " + diffMillis + " milliseconds. Allowed clock skew: 0 milliseconds.\\n" +
            "    at io.jsonwebtoken.impl.DefaultJwtParser.parse(DefaultJwtParser.java:427)\\n" +
            "    at io.jsonwebtoken.impl.DefaultJwtParser.parse(DefaultJwtParser.java:529)\\n" +
            "    at io.jsonwebtoken.impl.ImmutableJwtParser.parse(ImmutableJwtParser.java:153)\\n" +
            "    at com.okta.jwt.impl.jjwt.TokenVerifierSupport.decode(TokenVerifierSupport.java:81)\\n" +
            "    ... 93 common frames omitted\\n" +
            "Wrapped by: com.okta.jwt.JwtVerificationException: Failed to parse token\\n" +
            "    at com.okta.jwt.impl.jjwt.TokenVerifierSupport.decode(TokenVerifierSupport.java:87)\\n" +
            "    at com.okta.jwt.impl.jjwt.JjwtAccessTokenVerifier.decode(JjwtAccessTokenVerifier.java:56)\\n" +
            "    at com.company.ews.es.common.auth.OktaJwtTokenImpl.getOktaUserDetails(OktaJwtTokenImpl.java:25)\\n" +
            "    at com.company.ews.es.common.auth.OktaAuthenticationManager.authenticate(OktaAuthenticationManager.java:43)\\n" +
            "    at com.company.ews.es.common.auth.DualIdpAuthenticationManager.authenticate(DualIdpAuthenticationManager.java:67)\\n" +
            "    at org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter.doAuthenticate(AbstractPreAuthenticatedProcessingFilter.java:109)\\n" +
            "    at org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter.doFilter(AbstractPreAuthenticatedProcessingFilter.java:95)\\n" +
            "    at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:154)\\n" +
            "    at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:140)\\n" +
            "    at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)\\n" +
            "    at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:233)\\n" +
            "    at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:191)\\n" +
            "    at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:352)\\n" +
            "    at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:268)\\n" +
            "    at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178)";

        log.error(stackTrace);
    }

    /**
     * Simulates MethodArgumentTypeMismatchException - Invalid UUID Path Variable
     * Realistic scenario: Legacy system sends numeric ID instead of UUID
     * This should log periodically to simulate traffic hitting the endpoint
     */
    private void simulateInvalidUuid() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        String timestamp = Instant.now().toString().substring(0, 23).replace("T", " ");
        String invalidValue = String.valueOf(random.nextInt(90000) + 10000);

        log.error("{} ERROR [{}] com.company.ews.ams.user.management.controller.EmployerController - Type conversion failed",
            timestamp, threadName);

        String stackTrace = "org.springframework.web.method.annotation.MethodArgumentTypeMismatchException: Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Method parameter 'employer-id'\\n" +
            "Caused by: java.lang.IllegalArgumentException: Invalid UUID string: " + invalidValue + "\\n" +
            "    at java.base/java.util.UUID.fromString1(Unknown Source)\\n" +
            "    at java.base/java.util.UUID.fromString(Unknown Source)\\n" +
            "    at org.springframework.beans.propertyeditors.UUIDEditor.setAsText(UUIDEditor.java:37)\\n" +
            "    at org.springframework.beans.TypeConverterDelegate.doConvertTextValue(TypeConverterDelegate.java:467)\\n" +
            "    at org.springframework.beans.TypeConverterDelegate.doConvertValue(TypeConverterDelegate.java:440)\\n" +
            "    at org.springframework.beans.TypeConverterDelegate.convertIfNecessary(TypeConverterDelegate.java:192)\\n" +
            "    at org.springframework.beans.TypeConverterDelegate.convertIfNecessary(TypeConverterDelegate.java:125)\\n" +
            "    ... 147 common frames omitted\\n" +
            "Wrapped by: org.springframework.web.method.annotation.MethodArgumentTypeMismatchException: Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Method parameter 'employer-id'\\n" +
            "    at org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver.resolveArgument(AbstractNamedValueMethodArgumentResolver.java:133)\\n" +
            "    at org.springframework.web.method.support.HandlerMethodArgumentResolverComposite.resolveArgument(HandlerMethodArgumentResolverComposite.java:122)\\n" +
            "    at org.springframework.web.method.support.InvocableHandlerMethod.getMethodArgumentValues(InvocableHandlerMethod.java:179)\\n" +
            "    at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:146)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)";

        log.error(stackTrace);
    }

    /**
     * Simulates DataIntegrityViolationException - Database Constraint Violation
     * Realistic scenario: Duplicate key violation or foreign key constraint
     */
    private void simulateDbConstraintViolation() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        String timestamp = Instant.now().toString().substring(0, 23).replace("T", " ");
        String email = "user" + random.nextInt(1000) + "@company.com";

        log.error("{} ERROR [{}] com.company.ews.domain.UserRepository - Database constraint violation",
            timestamp, threadName);

        String stackTrace = "org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint \"users_email_key\"  Detail: Key (email)=(" + email + ") already exists.]; SQL [n/a]\\n" +
            "Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint \"users_email_key\"  Detail: Key (email)=(" + email + ") already exists.]\\n" +
            "    at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:90)\\n" +
            "    at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:56)\\n" +
            "    at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)\\n" +
            "    at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:95)\\n" +
            "    at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:200)\\n" +
            "Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint \"users_email_key\"\\n" +
            "  Detail: Key (email)=(" + email + ") already exists.\\n" +
            "    at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2675)\\n" +
            "    at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2365)\\n" +
            "    at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:355)\\n" +
            "    at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:496)\\n" +
            "    at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:413)\\n" +
            "    at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:190)\\n" +
            "    at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:152)\\n" +
            "    at com.zaxxer.hikari.pool.ProxyPreparedStatement.executeUpdate(ProxyPreparedStatement.java:61)\\n" +
            "    at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeUpdate(HikariProxyPreparedStatement.java)\\n" +
            "    at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:197)\\n" +
            "    ... 95 common frames omitted";

        log.error(stackTrace);
    }

    /**
     * Simulates HttpMessageNotReadableException - Malformed Request Body
     * Realistic scenario: JSON parsing failure due to incorrect date format
     */
    private void simulateMalformedRequest() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        String timestamp = Instant.now().toString().substring(0, 23).replace("T", " ");

        log.error("{} ERROR [{}] org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver - JSON parsing failed",
            timestamp, threadName);

        String stackTrace = "org.springframework.http.converter.HttpMessageNotReadableException: JSON parse error: Cannot deserialize value of type `java.time.LocalDateTime` from String \"2026-04-03\": not supported as a value; nested exception is com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot deserialize value of type `java.time.LocalDateTime` from String \"2026-04-03\": not supported as a value\\n" +
            " at [Source: (org.springframework.util.StreamUtils$NonClosingInputStream); line: 5, column: 19] (through reference chain: com.company.ews.domain.User[\"createdAt\"])\\n" +
            "    at org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter.readJavaType(AbstractJackson2HttpMessageConverter.java:390)\\n" +
            "    at org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter.read(AbstractJackson2HttpMessageConverter.java:338)\\n" +
            "    at org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver.readWithMessageConverters(AbstractMessageConverterMethodArgumentResolver.java:224)\\n" +
            "Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot deserialize value of type `java.time.LocalDateTime` from String \"2026-04-03\": not supported as a value\\n" +
            " at [Source: (org.springframework.util.StreamUtils$NonClosingInputStream); line: 5, column: 19] (through reference chain: com.company.ews.domain.User[\"createdAt\"])\\n" +
            "    at com.fasterxml.jackson.databind.exc.InvalidDefinitionException.from(InvalidDefinitionException.java:67)\\n" +
            "    at com.fasterxml.jackson.databind.DeserializationContext.reportBadDefinition(DeserializationContext.java:1904)\\n" +
            "    at com.fasterxml.jackson.databind.DeserializationContext.handleMissingInstantiator(DeserializationContext.java:1370)\\n" +
            "    at com.fasterxml.jackson.databind.deser.std.StdValueInstantiator._createFromStringFallbacks(StdValueInstantiator.java:371)";

        log.error(stackTrace);
    }

    /**
     * Simulates TransactionSystemException - Transaction Commit Failure
     * Realistic scenario: JPA validation fails at transaction commit time
     */
    private void simulateTransactionFailure() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        String timestamp = Instant.now().toString().substring(0, 23).replace("T", " ");

        log.error("{} ERROR [{}] org.springframework.transaction.support.TransactionTemplate - Transaction commit failed",
            timestamp, threadName);

        String stackTrace = "org.springframework.transaction.TransactionSystemException: Could not commit JPA transaction\\n" +
            "    at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:566)\\n" +
            "    at org.springframework.transaction.support.AbstractPlatformTransactionManager.processCommit(AbstractPlatformTransactionManager.java:743)\\n" +
            "    at org.springframework.transaction.support.AbstractPlatformTransactionManager.commit(AbstractPlatformTransactionManager.java:711)\\n" +
            "    at org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(TransactionAspectSupport.java:654)\\n" +
            "    at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:407)\\n" +
            "Caused by: jakarta.validation.ConstraintViolationException: Validation failed for classes [com.company.ews.domain.Task] during persist time for groups [jakarta.validation.groups.Default, ]\\n" +
            "List of constraint violations:[\\n" +
            "    ConstraintViolationImpl{interpolatedMessage='must not be null', propertyPath=assignedTo, rootBeanClass=class com.company.ews.domain.Task, messageTemplate='{jakarta.validation.constraints.NotNull.message}'}\\n" +
            "    ConstraintViolationImpl{interpolatedMessage='must not be blank', propertyPath=taskType, rootBeanClass=class com.company.ews.domain.Task, messageTemplate='{jakarta.validation.constraints.NotBlank.message}'}\\n" +
            "]\\n" +
            "    at org.hibernate.cfg.beanvalidation.BeanValidationEventListener.validate(BeanValidationEventListener.java:138)\\n" +
            "    at org.hibernate.cfg.beanvalidation.BeanValidationEventListener.onPreInsert(BeanValidationEventListener.java:80)\\n" +
            "    at org.hibernate.action.internal.EntityInsertAction.preInsert(EntityInsertAction.java:232)\\n" +
            "    at org.hibernate.action.internal.EntityInsertAction.execute(EntityInsertAction.java:107)\\n" +
            "    at org.hibernate.engine.spi.ActionQueue.executeActions(ActionQueue.java:612)\\n" +
            "    at org.hibernate.engine.spi.ActionQueue.lambda$executeActions$1(ActionQueue.java:483)\\n" +
            "    at org.hibernate.engine.transaction.internal.TransactionImpl.commit(TransactionImpl.java:101)\\n" +
            "    at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:562)\\n" +
            "    ... 94 common frames omitted";

        log.error(stackTrace);
    }

    /**
     * Simulates RestTemplate Timeout - Downstream Service Timeout
     * Realistic scenario: Call to payment service times out
     */
    private void simulateDownstreamTimeout() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        String timestamp = Instant.now().toString().substring(0, 23).replace("T", " ");

        log.error("{} ERROR [{}] com.company.ews.payment.service.PaymentGatewayClient - Downstream service timeout",
            timestamp, threadName);

        String stackTrace = "org.springframework.web.client.ResourceAccessException: I/O error on POST request for \"http://payment-service/api/payments\": Read timed out; nested exception is java.net.SocketTimeoutException: Read timed out\\n" +
            "    at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:789)\\n" +
            "    at org.springframework.web.client.RestTemplate.execute(RestTemplate.java:739)\\n" +
            "    at org.springframework.web.client.RestTemplate.postForEntity(RestTemplate.java:464)\\n" +
            "    at com.company.ews.payment.service.PaymentGatewayClient.processPayment(PaymentGatewayClient.java:87)\\n" +
            "    at com.company.ews.payment.service.PaymentService.createPayment(PaymentService.java:124)\\n" +
            "    at com.company.ews.order.service.OrderService.processOrder(OrderService.java:256)\\n" +
            "Caused by: java.net.SocketTimeoutException: Read timed out\\n" +
            "    at java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:283)\\n" +
            "    at java.base/sun.nio.ch.NioSocketImpl.implRead(NioSocketImpl.java:309)\\n" +
            "    at java.base/sun.nio.ch.NioSocketImpl.read(NioSocketImpl.java:350)\\n" +
            "    at java.base/sun.nio.ch.NioSocketImpl$1.read(NioSocketImpl.java:803)\\n" +
            "    at java.base/java.net.Socket$SocketInputStream.read(Socket.java:966)\\n" +
            "    at org.apache.http.impl.io.SessionInputBufferImpl.streamRead(SessionInputBufferImpl.java:137)\\n" +
            "    at org.apache.http.impl.io.SessionInputBufferImpl.fillBuffer(SessionInputBufferImpl.java:153)\\n" +
            "    at org.apache.http.impl.io.SessionInputBufferImpl.readLine(SessionInputBufferImpl.java:280)\\n" +
            "    at org.apache.http.impl.conn.DefaultHttpResponseParser.parseHead(DefaultHttpResponseParser.java:138)\\n" +
            "    at org.apache.http.impl.conn.DefaultHttpResponseParser.parseHead(DefaultHttpResponseParser.java:56)\\n" +
            "    at org.apache.http.impl.io.AbstractMessageParser.parse(AbstractMessageParser.java:259)\\n" +
            "    at org.apache.http.impl.DefaultBHttpClientConnection.receiveResponseHeader(DefaultBHttpClientConnection.java:163)\\n" +
            "    at org.apache.http.impl.conn.CPoolProxy.receiveResponseHeader(CPoolProxy.java:157)\\n" +
            "    at org.apache.http.protocol.HttpRequestExecutor.doReceiveResponse(HttpRequestExecutor.java:273)\\n" +
            "    at org.apache.http.protocol.HttpRequestExecutor.execute(HttpRequestExecutor.java:125)\\n" +
            "    at org.apache.http.impl.execchain.MainClientExec.execute(MainClientExec.java:272)\\n" +
            "    at org.apache.http.impl.execchain.ProtocolExec.execute(ProtocolExec.java:186)\\n" +
            "    at org.apache.http.impl.client.InternalHttpClient.doExecute(InternalHttpClient.java:185)\\n" +
            "    at org.springframework.http.client.HttpComponentsClientHttpRequest.executeInternal(HttpComponentsClientHttpRequest.java:87)\\n" +
            "    at org.springframework.http.client.AbstractBufferingClientHttpRequest.executeInternal(AbstractBufferingClientHttpRequest.java:48)\\n" +
            "    at org.springframework.http.client.AbstractClientHttpRequest.execute(AbstractClientHttpRequest.java:66)\\n" +
            "    at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:776)\\n" +
            "    ... 95 common frames omitted";

        log.error(stackTrace);
    }

    /**
     * Simulates OptimisticLockingFailureException - Concurrent Update Conflict
     * Realistic scenario: Two threads update the same entity simultaneously
     */
    private void simulateOptimisticLock() {
        String threadName = "http-nio-8080-exec-" + (random.nextInt(20) + 1);
        String timestamp = Instant.now().toString().substring(0, 23).replace("T", " ");
        long entityId = random.nextInt(90000) + 10000;

        log.error("{} ERROR [{}] com.company.ews.es.taskapi.service.TaskServiceImpl - Optimistic locking failure",
            timestamp, threadName);

        String stackTrace = "org.springframework.orm.jpa.JpaOptimisticLockingFailureException: Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect): [com.company.ews.domain.Task#" + entityId + "]\\n" +
            "    at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:331)\\n" +
            "    at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:233)\\n" +
            "    at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:550)\\n" +
            "    at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)\\n" +
            "    at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:242)\\n" +
            "    at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:152)\\n" +
            "Caused by: org.hibernate.StaleObjectStateException: Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect): [com.company.ews.domain.Task#" + entityId + "]\\n" +
            "    at org.hibernate.event.internal.DefaultFlushEntityEventListener.checkOptimisticLock(DefaultFlushEntityEventListener.java:265)\\n" +
            "    at org.hibernate.event.internal.DefaultFlushEntityEventListener.onFlushEntity(DefaultFlushEntityEventListener.java:161)\\n" +
            "    at org.hibernate.event.service.internal.EventListenerGroupImpl.fireEventOnEachListener(EventListenerGroupImpl.java:127)\\n" +
            "    at org.hibernate.internal.SessionImpl.flushEntities(SessionImpl.java:1348)\\n" +
            "    at org.hibernate.internal.SessionImpl.flushEverythingToExecutions(SessionImpl.java:2769)\\n" +
            "    at org.hibernate.internal.SessionImpl.flush(SessionImpl.java:1408)\\n" +
            "    at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:562)\\n" +
            "    ... 82 common frames omitted";

        log.error(stackTrace);
    }

    // Scheduled task to periodically log invalid_uuid errors (simulating ongoing bad traffic)
    @Scheduled(fixedDelay = 30000)
    public void periodicInvalidUuidCheck() {
        if (failureStates.get("invalid_uuid")) {
            simulateInvalidUuid();
        }
    }

    // Scheduled task to periodically log malformed_request errors
    @Scheduled(fixedDelay = 45000)
    public void periodicMalformedRequestCheck() {
        if (failureStates.get("malformed_request")) {
            simulateMalformedRequest();
        }
    }

    // ========== FAILURE CONTROL ENDPOINTS ==========

    @PostMapping("/failure/trigger/{type}")
    public ResponseEntity<Map<String, String>> triggerFailure(@PathVariable String type) {
        Map<String, String> response = new HashMap<>();
        String lowerType = type.toLowerCase();

        if (!failureStates.containsKey(lowerType)) {
            response.put("error", "Unknown failure type: " + type);
            return ResponseEntity.badRequest().body(response);
        }

        failureStates.put(lowerType, true);

        response.put("message", type + " failure mode activated");
        response.put("type", type);
        response.put("status", "triggered");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/failure/clear/{type}")
    public ResponseEntity<Map<String, String>> clearFailure(@PathVariable String type) {
        Map<String, String> response = new HashMap<>();

        if (failureStates.containsKey(type.toLowerCase())) {
            failureStates.put(type.toLowerCase(), false);
            response.put("message", type + " failure mode cleared");
            response.put("type", type);
            response.put("status", "cleared");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Unknown failure type: " + type);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/failure/clear-all")
    public ResponseEntity<Map<String, String>> clearAllFailures() {
        failureStates.replaceAll((k, v) -> false);

        Map<String, String> response = new HashMap<>();
        response.put("message", "All failure modes cleared");
        response.put("status", "cleared");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/failure/status")
    public ResponseEntity<Map<String, Object>> getFailureStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("failures", failureStates);
        response.put("timestamp", new Date());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Application is responding");
        response.put("timestamp", new Date().toString());
        return ResponseEntity.ok(response);
    }
}
