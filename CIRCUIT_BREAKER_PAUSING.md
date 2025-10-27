# Circuit Breaker with Kafka Listener Pausing

This document explains how the circuit breaker system works with Kafka listener pausing to prevent message processing when downstream services are failing.

## Overview

The system implements a sophisticated pattern where:

1. **Circuit Breakers** monitor the health of downstream services
2. **Kafka Listeners** are automatically paused when circuit breakers are open
3. **Automatic Recovery** resumes listeners when services recover
4. **Kafka Native Retries** handle message retry logic (no additional retry mechanism needed)

## Architecture

```
Kafka Message → Circuit Breaker Check → Listener Status → Processing
     ↓              ↓                    ↓              ↓
   Message      Is Circuit           Is Listener     Business
   Received     Open/Closed?         Paused?         Logic
     ↓              ↓                    ↓              ↓
   Process      If Open:            If Paused:      Success/
   Message      → Pause Listener   → Skip Message   Failure
     ↓              ↓                    ↓              ↓
   Success      Log Warning         Log Info        Update
   (Acknowledge)                    (Skip)          Metrics
```

## Components

### 1. KafkaListenerManager

**Purpose**: Manages Kafka listener containers based on circuit breaker state.

**Key Features**:

- Monitors circuit breaker state transitions
- Automatically pauses listeners when circuit breakers open
- Resumes listeners when circuit breakers close

**Configuration**:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      conceptConsumer:
        failureRateThreshold: 60
        waitDurationInOpenState: 30s
        slidingWindowSize: 50
```

### 2. Enhanced KafkaConsumer

**Purpose**: Improved error handling that works with circuit breaker pausing.

**Key Features**:

- Distinguishes between retryable and non-retryable exceptions
- Acknowledges messages for retryable exceptions (lets circuit breaker handle retries)
- Nacks messages for non-retryable exceptions (immediate failure)

## Circuit Breaker States

### CLOSED (Normal Operation)

- Circuit breaker allows all calls through
- Listeners are running normally
- Metrics are collected for health monitoring

### OPEN (Service Failing)

- Circuit breaker blocks all calls
- Listeners are automatically paused
- Fallback methods are called instead
- System waits for recovery period

### HALF_OPEN (Testing Recovery)

- Circuit breaker allows limited test calls
- Listeners remain paused during testing
- If test calls succeed, circuit closes
- If test calls fail, circuit reopens

## Configuration

### Circuit Breaker Settings

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 50
        minimumNumberOfCalls: 5
        failureRateThreshold: 60
        waitDurationInOpenState: 30s
        slowCallRateThreshold: 60
        slowCallDurationThreshold: 5s
    instances:
      conceptConsumer:
        # Inherits from default config
      datasetConsumer:
        # Inherits from default config
```

### Kafka Retry Settings

Kafka handles retries automatically at the broker level, so no additional retry configuration is needed. The circuit breaker will handle failures and pause listeners when services are down.

## Monitoring

The circuit breaker system provides automatic monitoring through the KafkaListenerManager. Circuit breaker states and listener status are logged automatically, and the system handles all state transitions without requiring external monitoring endpoints.

## Usage Examples

The circuit breaker system operates automatically without requiring manual intervention. The KafkaListenerManager handles all circuit breaker state transitions and listener pausing/resuming automatically based on service health.

## Benefits

### 1. **System Protection**

- Prevents overwhelming failing services
- Reduces cascading failures
- Protects system resources

### 2. **Automatic Recovery**

- No manual intervention required
- Automatic detection of service recovery
- Gradual resumption of processing

### 3. **Observability**

- Clear visibility into system health
- Detailed metrics and status
- Easy troubleshooting

### 4. **Automatic Control**

- Automatic failure detection
- Self-healing capabilities
- Graceful degradation

## Best Practices

### 1. **Monitoring**

- Set up alerts for circuit breaker state changes
- Monitor listener pause/resume events
- Track failure rates and recovery times

### 2. **Configuration**

- Adjust failure thresholds based on service characteristics
- Set appropriate wait durations for recovery
- Configure retry policies for transient failures

### 3. **Testing**

- Test circuit breaker behavior in staging
- Verify listener pausing/resuming works correctly
- Validate fallback mechanisms

### 4. **Operations**

- Monitor system health regularly
- Have runbooks for common scenarios
- Trust the automatic recovery mechanisms

## Troubleshooting

### Circuit Breaker Stuck Open

1. Check downstream service health
2. Verify configuration settings
3. Consider manual intervention if needed

### Listeners Not Pausing

1. Check circuit breaker state
2. Verify listener container status
3. Review logs for errors

### Listeners Not Resuming

1. Check circuit breaker recovery
2. Verify system resources
3. Review logs for automatic recovery attempts

## Related Documentation

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/docs/current/reference/html/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
