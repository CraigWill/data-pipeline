# Kafka UI Connection Issue - RESOLVED ✅

## Problem
Kafka UI was unable to connect to Kafka, showing repeated errors:
```
Connection to node 1 (localhost/127.0.0.1:9092) could not be established. Broker may not be available.
```

## Root Cause
Kafka was configured with `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092`, which caused:
- Kafka to advertise itself as `localhost:9092`
- Docker containers trying to connect to their own localhost instead of the Kafka container
- Connection failures for Kafka UI, Debezium, and other services

## Solution
Configured Kafka with dual listeners for internal and external access:

### Changes to docker-compose.yml

**Kafka Service:**
```yaml
environment:
  # Two listeners: INTERNAL for container communication, EXTERNAL for host access
  KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
  KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
  KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092
  KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
```

**Kafka UI:**
```yaml
environment:
  KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092  # Changed from kafka:9092
```

**Debezium:**
```yaml
environment:
  BOOTSTRAP_SERVERS: kafka:29092  # Changed from kafka:9092
```

## Verification

### Kafka UI Status
```bash
curl -s http://localhost:8082/api/clusters
```

Result: ✅ Status "online", 1 broker, 2 topics

### Topics Available
- `cdc-events` - CDC events topic
- Other system topics

## Access Points

| Service | Internal (Docker) | External (Host) |
|---------|------------------|-----------------|
| Kafka | kafka:29092 | localhost:9092 |
| Kafka UI | - | http://localhost:8082 |
| Zookeeper | zookeeper:2181 | localhost:2181 |

## Benefits

1. **Container Communication**: Services inside Docker use `kafka:29092`
2. **Host Access**: Applications on host machine use `localhost:9092`
3. **No Conflicts**: Separate listeners prevent networking issues
4. **Standard Pattern**: Follows Kafka best practices for Docker deployments

Kafka UI is now fully functional and can manage Kafka topics, view messages, and monitor cluster health.
