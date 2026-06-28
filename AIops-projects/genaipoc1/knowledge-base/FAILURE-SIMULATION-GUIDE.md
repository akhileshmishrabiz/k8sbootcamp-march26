# Failure Simulation Application - Complete Knowledge Base

## Application Overview

This is a Spring Boot Java application designed to simulate realistic production failures commonly seen in enterprise microservices environments. It generates authentic log patterns that mirror real-world issues in distributed systems, allowing AI-based log analysis and troubleshooting practice.

### Purpose
- Simulate 10 different types of enterprise application failures
- Generate production-quality log patterns without diagnostic hints
- Enable AI/LLM-based log analysis and root cause detection
- Provide realistic training scenarios for failure monitoring systems

### Technology Stack
- **Framework**: Spring Boot 3.x with Jakarta EE
- **Language**: Java 17+
- **Database**: PostgreSQL with JPA/Hibernate
- **Connection Pool**: HikariCP
- **Logging**: SLF4J with Logback
- **Container**: Embedded Tomcat
- **Deployment**: Kubernetes (kind)

---

## Failure Types and Diagnostic Guide

### 1. HTTP 409 Conflict — Duplicate Resource Creation

**Failure ID**: `conflict_409`

**What This Simulates**:
A user or entity creation attempt that conflicts with an existing record in the downstream service. This typically occurs when:
- A user tries to register with an email that already exists
- Duplicate employee IDs are submitted
- Race conditions in concurrent user creation
- Retry logic creating duplicate records

**Log Pattern**:
```json
{"timestamp":"2026-04-13T10:30:00.123Z","statusCode":409,"status":"Conflict","origin":"user-management","efx-transaction-id":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","efx-session-id":"12345678-abcd-ef12-3456-789012345678","errors":"Error: Something went wrong"}
service: "none"
stackTrace: "org.springframework.web.client.HttpClientErrorException: 409 409 CONFLICT
    at com.company.ews.ams.user.management.handler.RestErrorResponseHandler.httpException(RestErrorResponseHandler.java:87)
    at com.company.ews.ams.user.management.service.user.UserService.createUser(UserService.java:155)
    ...
```

**Key Indicators**:
- HTTP status code 409
- Origin: "user-management" service
- Exception: `HttpClientErrorException: 409 CONFLICT`
- Stacktrace shows `UserService.createUser()`

**Root Cause**:
The application attempted to create a user/entity that already exists in the database, violating a unique constraint (typically email or username).

**Diagnosis Steps**:
1. Look for `statusCode:409` in logs
2. Check the `efx-transaction-id` to trace the request
3. Identify which unique field caused the conflict (email, username, employee-id)
4. Verify if this is a legitimate duplicate or a retry storm

**Solution**:
- Check if the user already exists before creation
- Implement idempotent user creation (update existing instead of error)
- Add proper duplicate checking with meaningful error messages
- Review frontend validation to prevent duplicate submissions

---

### 2. HTTP 404 Not Found — Entity Lookup Failure

**Failure ID**: `not_found_404`

**What This Simulates**:
A downstream service call returns 404 because the requested resource doesn't exist. Common scenarios:
- User ID doesn't exist in the user service
- Order ID not found in order database
- Referenced entity was deleted
- Invalid UUID/ID passed from client

**Log Pattern**:
```
Inside handleClientAndServerExceptions - Error Response - {"timestamp":"2026-04-13T10:30:00Z","statusCode":404,"status":"NOT_FOUND","origin":"user-service","efx-transaction-id":"...","efx-session-id":"...","errors":"Error: Something went wrong"}
stackTrace: "org.springframework.web.client.HttpClientErrorException: 404 404 NOT_FOUND
    at com.company.ews.ams.user.management.handler.RestErrorResponseHandler.httpException(RestErrorResponseHandler.java:87)
    at com.company.ews.ams.user.management.esp.service.UserSelfServiceImpl.lambda$findByIdV3$0(UserSelfServiceImpl.java:342)
    ...
```

**Key Indicators**:
- HTTP status code 404
- Message: "Inside handleClientAndServerExceptions"
- Exception: `HttpClientErrorException: 404 NOT_FOUND`
- Stacktrace shows `UserSelfServiceImpl.findByIdV3()`

**Root Cause**:
The application tried to fetch an entity by ID, but the entity doesn't exist in the downstream system.

**Diagnosis Steps**:
1. Extract the entity ID from the error context
2. Check if the ID is valid (proper UUID format)
3. Verify the entity exists in the database
4. Check if the entity was recently deleted
5. Review the calling code for incorrect ID usage

**Solution**:
- Add existence checks before performing operations
- Return user-friendly "not found" messages
- Implement graceful degradation for missing entities
- Add defensive null checks in the calling code

---

### 3. BusinessException — Task Object Not Found

**Failure ID**: `business_exception`

**What This Simulates**:
A business logic validation failure during task completion. The task references an object (order, case, workflow) that no longer exists or is in an invalid state.

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-5] com.company.ews.es.taskapi.validation.TaskConstraintValidator - Business validation failed for task completion
com.company.ews.es.common.exception.BusinessException: object not found
    at com.company.ews.es.taskapi.validation.TaskConstraintValidator.identifyConstraintForTaskCompletion(TaskConstraintValidator.java:287)
    at com.company.ews.es.taskapi.service.TaskServiceImpl.completeTask(TaskServiceImpl.java:535)
    at com.company.ews.es.taskapi.service.TaskServiceImpl$$SpringCGLIB$$0.completeTask(<generated>)
    ...
    at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
    at com.company.ews.es.taskapi.controller.TaskController.completeTask(TaskController.java:205)
    ...
```

**Key Indicators**:
- Exception: `com.company.ews.es.common.exception.BusinessException`
- Message: "object not found"
- Deep stacktrace showing Spring AOP/CGLIB proxies (`$$SpringCGLIB$$`)
- Transaction interceptor in the stack
- Originates from `TaskConstraintValidator.identifyConstraintForTaskCompletion()`

**Root Cause**:
Task completion validation failed because the referenced business object (order, case, etc.) doesn't exist. This can happen when:
- The object was deleted before task completion
- Task references wrong object ID
- Data inconsistency between task and object tables
- Race condition in distributed workflow

**Diagnosis Steps**:
1. Identify the task ID being completed
2. Check what object the task references
3. Verify if the referenced object exists in the database
4. Check task and object states for consistency
5. Review recent deletions or state changes

**Solution**:
- Add existence validation before task completion
- Implement soft deletes instead of hard deletes
- Add foreign key constraints to maintain referential integrity
- Implement distributed transactions properly
- Add retry logic with existence checks

---

### 4. JWT Expired — Okta Authentication Failure

**Failure ID**: `jwt_expired`

**What This Simulates**:
Okta-based JWT authentication failure due to an expired token. This happens on EVERY request when active, simulating all API calls failing authentication.

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-12] com.company.ews.es.common.auth.OktaJwtTokenImpl - JWT verification failed
io.jsonwebtoken.ExpiredJwtException: JWT expired at 2026-04-13T09:25:00.000Z. Current time: 2026-04-13T10:30:00.123Z, a difference of 60123 milliseconds. Allowed clock skew: 0 milliseconds.
    at io.jsonwebtoken.impl.DefaultJwtParser.parse(DefaultJwtParser.java:427)
    at com.okta.jwt.impl.jjwt.TokenVerifierSupport.decode(TokenVerifierSupport.java:81)
    ... 93 common frames omitted
Wrapped by: com.okta.jwt.JwtVerificationException: Failed to parse token
    at com.company.ews.es.common.auth.OktaJwtTokenImpl.getOktaUserDetails(OktaJwtTokenImpl.java:25)
    at com.company.ews.es.common.auth.OktaAuthenticationManager.authenticate(OktaAuthenticationManager.java:43)
    at com.company.ews.es.common.auth.DualIdpAuthenticationManager.authenticate(DualIdpAuthenticationManager.java:67)
    at org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter.doAuthenticate(...)
    ...
```

**Key Indicators**:
- Exception: `io.jsonwebtoken.ExpiredJwtException`
- Message shows exact expiration time and current time with millisecond difference (~60000ms = 1 hour)
- Wrapped by `com.okta.jwt.JwtVerificationException`
- Occurs in authentication filter chain
- High frequency (appears on every request)

**Root Cause**:
The JWT token used for authentication has expired. Tokens typically expire after 1 hour. This is a critical auth failure affecting all requests.

**Diagnosis Steps**:
1. Check the time difference in the error message
2. Verify token was issued more than 1 hour ago
3. Check if token refresh is working
4. Look for burst of these errors (indicates widespread auth failure)

**Solution**:
- Implement automatic token refresh before expiration
- Add token expiration monitoring
- Refresh tokens proactively (at 80% of TTL)
- Add graceful session expiration handling in UI
- Implement refresh token rotation
- Add clock skew tolerance in JWT validation

**Impact**:
- All authenticated API calls fail
- Users are logged out
- System appears completely broken
- High error rate (100% of requests)

---

### 5. Invalid UUID — Type Mismatch Exception

**Failure ID**: `invalid_uuid`

**What This Simulates**:
Legacy systems sending numeric IDs to endpoints expecting UUID format. Logs appear periodically (every 30 seconds) simulating ongoing bad traffic.

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-8] com.company.ews.ams.user.management.controller.EmployerController - Type conversion failed
org.springframework.web.method.annotation.MethodArgumentTypeMismatchException: Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Method parameter 'employer-id'
Caused by: java.lang.IllegalArgumentException: Invalid UUID string: 14229
    at java.base/java.util.UUID.fromString1(Unknown Source)
    at java.base/java.util.UUID.fromString(Unknown Source)
    at org.springframework.beans.propertyeditors.UUIDEditor.setAsText(UUIDEditor.java:37)
    ... 147 common frames omitted
Wrapped by: org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
    at org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver.resolveArgument(...)
    ...
```

**Key Indicators**:
- Exception: `MethodArgumentTypeMismatchException`
- Converting String to UUID failed
- Specific invalid value shown (e.g., "14229" - a numeric ID)
- Parameter name: 'employer-id' or similar
- Periodic occurrence pattern

**Root Cause**:
Legacy integration sending old-style numeric IDs to new UUID-based endpoints. Common during system migrations.

**Diagnosis Steps**:
1. Identify the invalid value (numeric string)
2. Check which endpoint is being called
3. Identify the calling system (legacy integration)
4. Review migration status

**Solution**:
- Add UUID validation at API gateway
- Create adapter endpoints for legacy systems
- Implement ID mapping service (numeric → UUID)
- Add clear error messages for invalid formats
- Update legacy systems to use UUIDs
- Add request validation before parameter binding

---

### 6. Database Constraint Violation — Duplicate Key

**Failure ID**: `db_constraint_violation`

**What This Simulates**:
Database insert/update fails due to unique constraint violation (duplicate email, username) or foreign key constraint failure.

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-7] com.company.ews.domain.UserRepository - Database constraint violation
org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "users_email_key"  Detail: Key (email)=(john.doe@company.com) already exists.]; SQL [n/a]
Caused by: org.hibernate.exception.ConstraintViolationException: ...
Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "users_email_key"
  Detail: Key (email)=(john.doe@company.com) already exists.
    at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2675)
    at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeUpdate(...)
    at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(...)
    ... 95 common frames omitted
```

**Key Indicators**:
- Exception: `org.springframework.dao.DataIntegrityViolationException`
- PostgreSQL error: "duplicate key value violates unique constraint"
- Specific constraint name: "users_email_key"
- Duplicate value shown in detail
- HikariCP connection pool in stack

**Root Cause**:
Attempting to insert a record with a value that violates a unique constraint in the database.

**Diagnosis Steps**:
1. Identify the constraint being violated
2. Extract the duplicate value from error message
3. Query database for existing record
4. Check if this is legitimate or a bug
5. Review application logic for duplicate checking

**Solution**:
- Add pre-insert existence checks
- Implement proper duplicate handling (update vs. error)
- Use database UPSERT operations
- Add application-level validation
- Handle race conditions with proper locking
- Return user-friendly error messages

---

### 7. Malformed JSON Request — Deserialization Failure

**Failure ID**: `malformed_request`

**What This Simulates**:
JSON parsing failure due to incorrect data format, typically date/time format mismatches. Logs appear periodically (every 45 seconds).

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-9] org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver - JSON parsing failed
org.springframework.http.converter.HttpMessageNotReadableException: JSON parse error: Cannot deserialize value of type `java.time.LocalDateTime` from String "2026-04-03": not supported as a value; nested exception is com.fasterxml.jackson.databind.exc.InvalidDefinitionException
 at [Source: (org.springframework.util.StreamUtils$NonClosingInputStream); line: 5, column: 19] (through reference chain: com.company.ews.domain.User["createdAt"])
    at org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter.readJavaType(...)
Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot deserialize value of type `java.time.LocalDateTime` from String "2026-04-03": not supported as a value
    ...
```

**Key Indicators**:
- Exception: `HttpMessageNotReadableException`
- JSON parse error for date/time field
- Specific field name: "createdAt" or similar
- Expected type vs. actual value mismatch
- Jackson deserialization exception

**Root Cause**:
Client sending date in wrong format (e.g., "2026-04-03" instead of "2026-04-03T10:30:00").

**Diagnosis Steps**:
1. Identify the field causing the error
2. Check expected vs. actual format
3. Review API documentation for correct format
4. Check client implementation

**Solution**:
- Add custom Jackson deserializers with format flexibility
- Document required date/time formats clearly
- Add request validation before deserialization
- Support multiple date formats
- Return clear validation error messages
- Add API schema validation

---

### 8. Transaction Commit Failure — JPA Validation

**Failure ID**: `transaction_failure`

**What This Simulates**:
JPA transaction fails at commit time due to entity validation errors (missing required fields, constraint violations).

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-11] org.springframework.transaction.support.TransactionTemplate - Transaction commit failed
org.springframework.transaction.TransactionSystemException: Could not commit JPA transaction
    at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:566)
    at org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(...)
Caused by: jakarta.validation.ConstraintViolationException: Validation failed for classes [com.company.ews.domain.Task] during persist time
List of constraint violations:[
    ConstraintViolationImpl{interpolatedMessage='must not be null', propertyPath=assignedTo, rootBeanClass=class com.company.ews.domain.Task}
    ConstraintViolationImpl{interpolatedMessage='must not be blank', propertyPath=taskType, rootBeanClass=class com.company.ews.domain.Task}
]
    at org.hibernate.cfg.beanvalidation.BeanValidationEventListener.validate(...)
    ... 94 common frames omitted
```

**Key Indicators**:
- Exception: `TransactionSystemException`
- Caused by: `jakarta.validation.ConstraintViolationException`
- Detailed list of constraint violations
- Fails at transaction commit time
- Shows field names and validation rules

**Root Cause**:
Entity validation fails when Hibernate tries to persist the entity. Required fields are null or invalid.

**Diagnosis Steps**:
1. Review constraint violation list
2. Identify missing/invalid fields
3. Trace back to where entity was created
4. Check business logic for proper field population
5. Review validation annotations on entity

**Solution**:
- Add validation before transaction commit
- Use @Valid annotation on method parameters
- Implement proper null checks
- Add builder pattern with required fields
- Return validation errors early in the request lifecycle
- Add comprehensive unit tests for entity validation

---

### 9. Downstream Service Timeout — Integration Failure

**Failure ID**: `downstream_timeout`

**What This Simulates**:
Timeout when calling downstream microservice (payment service), typically due to network issues, service overload, or deadlocks.

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-15] com.company.ews.payment.service.PaymentGatewayClient - Downstream service timeout
org.springframework.web.client.ResourceAccessException: I/O error on POST request for "http://payment-service/api/payments": Read timed out; nested exception is java.net.SocketTimeoutException: Read timed out
    at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:789)
    at com.company.ews.payment.service.PaymentGatewayClient.processPayment(PaymentGatewayClient.java:87)
    at com.company.ews.order.service.OrderService.processOrder(OrderService.java:256)
Caused by: java.net.SocketTimeoutException: Read timed out
    at java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:283)
    at org.apache.http.impl.conn.DefaultHttpResponseParser.parseHead(...)
    ... 95 common frames omitted
```

**Key Indicators**:
- Exception: `org.springframework.web.client.ResourceAccessException`
- Root cause: `java.net.SocketTimeoutException: Read timed out`
- Shows downstream service URL
- Apache HTTP client in stack
- RestTemplate usage

**Root Cause**:
Downstream service (payment-service) not responding within timeout period. Could be:
- Service overloaded
- Network issues
- Service crashed
- Database deadlock in downstream service
- Long-running query

**Diagnosis Steps**:
1. Identify which downstream service
2. Check if service is running
3. Review service logs for errors
4. Check network connectivity
5. Monitor service response times
6. Check for cascading failures

**Solution**:
- Implement circuit breaker pattern
- Add retry logic with exponential backoff
- Set appropriate timeout values
- Add fallback mechanisms
- Implement request queuing
- Monitor and alert on high latency
- Add health checks for downstream services
- Implement timeout budgets across call chains

---

### 10. Optimistic Locking Failure — Concurrent Update

**Failure ID**: `optimistic_lock`

**What This Simulates**:
Two threads/requests trying to update the same JPA entity simultaneously, detected by version mismatch.

**Log Pattern**:
```
2026-04-13 10:30:00.123 ERROR [http-nio-8080-exec-18] com.company.ews.es.taskapi.service.TaskServiceImpl - Optimistic locking failure
org.springframework.orm.jpa.JpaOptimisticLockingFailureException: Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect): [com.company.ews.domain.Task#12345]
    at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(...)
    at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(...)
Caused by: org.hibernate.StaleObjectStateException: Row was updated or deleted by another transaction: [com.company.ews.domain.Task#12345]
    at org.hibernate.event.internal.DefaultFlushEntityEventListener.checkOptimisticLock(DefaultFlushEntityEventListener.java:265)
    at org.hibernate.internal.SessionImpl.flush(SessionImpl.java:1408)
    at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:562)
    ... 82 common frames omitted
```

**Key Indicators**:
- Exception: `JpaOptimisticLockingFailureException`
- Caused by: `org.hibernate.StaleObjectStateException`
- Message: "Row was updated or deleted by another transaction"
- Entity ID shown in error
- Fails during flush/commit

**Root Cause**:
Entity was modified by another transaction between read and update. Optimistic locking detected version mismatch.

**Diagnosis Steps**:
1. Identify the entity and ID
2. Check for concurrent access patterns
3. Review transaction boundaries
4. Check if retry logic is appropriate
5. Monitor for high-concurrency scenarios

**Solution**:
- Implement retry logic for optimistic lock failures
- Use pessimistic locking for high-contention scenarios
- Reduce transaction scope
- Add proper exception handling and retry
- Consider using queuing for serialized updates
- Add version field monitoring
- Implement conflict resolution strategy
- Use database-level locking for critical updates

---

## API Reference

### Health Check Endpoint
```
GET /api/health
```
Returns application health status. Status depends on active failures.

**Response (Healthy)**:
```json
{
  "status": "healthy",
  "message": "Application is running normally",
  "timestamp": "2026-04-13T10:30:00.123Z"
}
```

**Response (Unhealthy - JWT Expired)**:
```json
{
  "status": "error",
  "message": "Authentication failed"
}
HTTP Status: 401 Unauthorized
```

### Trigger Failure
```
POST /api/failure/trigger/{type}
```
Activates a specific failure mode.

**Parameters**:
- `type`: One of the 10 failure type IDs

**Example**:
```bash
curl -X POST http://localhost:8080/api/failure/trigger/conflict_409
```

**Response**:
```json
{
  "message": "conflict_409 failure mode activated",
  "type": "conflict_409",
  "status": "triggered"
}
```

### Clear Failure
```
POST /api/failure/clear/{type}
```
Deactivates a specific failure mode.

**Example**:
```bash
curl -X POST http://localhost:8080/api/failure/clear/conflict_409
```

### Clear All Failures
```
POST /api/failure/clear-all
```
Deactivates all failure modes and restores normal operation.

### Get Failure Status
```
GET /api/failure/status
```
Returns current state of all failure modes.

**Response**:
```json
{
  "failures": {
    "conflict_409": false,
    "not_found_404": false,
    "business_exception": true,
    "jwt_expired": false,
    ...
  },
  "timestamp": "2026-04-13T10:30:00.123Z"
}
```

---

## Architecture Details

### Failure Activation Mechanism
- Failures are controlled via a `ConcurrentHashMap<String, Boolean>`
- Thread-safe state management
- No persistence - state resets on restart
- Health endpoint checks active failures on each call

### Logging Behavior

**JWT Expired (jwt_expired)**:
- Logs on EVERY health check request
- Simulates authentication failing on all API calls
- High frequency logging (multiple entries per minute)

**Invalid UUID (invalid_uuid)**:
- Logs every 30 seconds via `@Scheduled` task
- Simulates ongoing bad traffic from legacy systems
- Uses Spring's scheduling framework

**Malformed Request (malformed_request)**:
- Logs every 45 seconds via `@Scheduled` task
- Simulates periodic malformed requests
- Different frequency to create realistic varied patterns

**All Other Failures**:
- Log once when health endpoint is called while failure is active
- Single log entry per health check

### Realistic Log Characteristics

1. **Fresh Correlation IDs**: New UUID generated for each log entry
2. **Realistic Timestamps**: Uses actual current time
3. **Variable Thread Names**: Random thread numbers (http-nio-8080-exec-1 through exec-20)
4. **Enterprise Package Names**: Uses com.company.ews.* pattern matching real enterprise apps
5. **No Diagnostic Hints**: Logs contain NO banners, labels, or explanations
6. **Production-Quality Stack Traces**: Full Java stack traces with realistic line numbers

---

## Common Diagnostic Patterns

### Pattern 1: HTTP Error Codes
- **409**: Duplicate resource (conflict_409)
- **404**: Resource not found (not_found_404)
- **401**: Authentication failure (jwt_expired)

### Pattern 2: Exception Types
- `HttpClientErrorException`: HTTP client errors (409, 404)
- `BusinessException`: Business logic validation failures
- `ExpiredJwtException`: JWT token expired
- `MethodArgumentTypeMismatchException`: Type conversion failures
- `DataIntegrityViolationException`: Database constraint violations
- `HttpMessageNotReadableException`: JSON parsing failures
- `TransactionSystemException`: Transaction commit failures
- `ResourceAccessException`: Downstream service issues
- `JpaOptimisticLockingFailureException`: Concurrent update conflicts

### Pattern 3: Service Origins
- `user-management`: User service operations
- `taskapi`: Task workflow operations
- `payment-service`: Payment processing
- `auth`: Authentication/authorization

### Pattern 4: Thread Patterns
- Single thread name: Isolated issue
- Multiple thread names: Concurrent/widespread issue
- High frequency: System-wide failure (like JWT expiration)

---

## Troubleshooting Common Questions

### Q: Why are there so many JWT expired errors?
**A**: The `jwt_expired` failure simulates ALL requests failing authentication. This is expected when that failure mode is active. Every health check call generates a new error. Clear the failure to stop it.

### Q: How do I know which failure is active?
**A**: Call `GET /api/failure/status` to see all failure states. Look for `"true"` values.

### Q: Can multiple failures be active simultaneously?
**A**: Yes! You can activate multiple failure modes. This simulates complex production scenarios with cascading failures.

### Q: Why do invalid_uuid errors keep appearing?
**A**: The `invalid_uuid` failure uses a scheduled task that runs every 30 seconds. This simulates ongoing bad traffic. Clear the failure to stop it.

### Q: What's the difference between business_exception and db_constraint_violation?
**A**: `business_exception` is application-level validation (object not found), while `db_constraint_violation` is database-level constraint (unique key violation).

### Q: How realistic are these logs?
**A**: Extremely realistic. The logs are modeled after real enterprise Spring Boot applications with authentic:
- Stack traces with actual Spring/Hibernate classes
- Realistic package structures
- Proper exception chaining
- Enterprise service patterns

---

## Integration with AI/LLM Analysis

When feeding logs to an AI system for analysis:

1. **Provide Full Logs**: Include entire stack traces, not summaries
2. **Include Correlation IDs**: These help trace requests across services
3. **Note Timestamp Patterns**: Frequency and timing reveal issue severity
4. **Look for Exception Chains**: "Caused by" chains show root causes
5. **Identify Service Names**: Helps isolate which microservice has issues

### Expected AI Analysis Output

For each failure, the AI should identify:
- **Root Cause**: What actually went wrong
- **Affected Service**: Which microservice/component failed
- **Impact**: Severity and scope of the issue
- **Resolution Steps**: How to fix it
- **Prevention**: How to avoid it in the future

---

## Best Practices for Using This Application

1. **Start Simple**: Trigger one failure at a time to learn patterns
2. **Observe Logs**: Watch how logs appear in real-time
3. **Practice Diagnosis**: Try to diagnose before checking the guide
4. **Test Combinations**: Try multiple failures to see interactions
5. **Clear Between Tests**: Always clear failures before starting new tests
6. **Monitor Health**: Use health endpoint to verify failure state

---

## Deployment Configuration

### Environment Variables
- `spring.datasource.url`: PostgreSQL connection URL
- `spring.datasource.username`: Database username
- `spring.datasource.password`: Database password
- `spring.jpa.hibernate.ddl-auto`: Schema management (update/create/validate)

### Container Configuration
- **Port**: 8080 (HTTP)
- **JVM**: Java 17+
- **Memory**: Recommended 512MB minimum
- **CPU**: 0.5 cores minimum

### Kubernetes Resources
- **Service Name**: failure-app-service
- **Namespace**: default
- **Labels**: app=failure-app
- **Health Probe**: GET /api/health

---

## Version and Compatibility

- **Spring Boot**: 3.x
- **Java**: 17 or higher
- **Database**: PostgreSQL 12+
- **Container**: Docker-compatible runtime
- **Kubernetes**: 1.20+

---

## Support and Troubleshooting

### The application won't start
- Check PostgreSQL is running and accessible
- Verify database credentials
- Check port 8080 is available
- Review application startup logs

### Failures not appearing in logs
- Verify failure is active via `/api/failure/status`
- Call `/api/health` to trigger logging
- Check application logs are being collected
- For periodic failures (invalid_uuid, malformed_request), wait for scheduled execution

### Cannot clear failures
- Use `/api/failure/clear-all` to reset everything
- Restart the application to reset all states
- Verify API endpoints are accessible

---

This knowledge base provides comprehensive information about the Failure Simulation Application, enabling accurate AI-based log analysis and troubleshooting guidance.
