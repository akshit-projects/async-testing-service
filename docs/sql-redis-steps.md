# SQL and Redis Step Types

The async testing service now supports SQL query execution and Redis cache operations as step types, enabling comprehensive database and cache testing workflows.

## SQL Query Steps

### Overview
SQL steps allow execution of SELECT queries against PostgreSQL databases with proper validation and security controls.

### Security Features
- **Query Validation**: Only SELECT statements are allowed
- **Injection Prevention**: Parameterized queries with sanitization
- **Pattern Detection**: Blocks dangerous SQL patterns and functions
- **Nested Query Limits**: Prevents overly complex queries

### Step Configuration

```json
{
  "name": "getUserData",
  "stepType": "sql",
  "meta": {
    "resourceId": "postgres-db",
    "query": "SELECT id, name, email FROM users WHERE email = ${email}",
    "parameters": {
      "email": "user@example.com"
    },
    "expectedRowCount": 1,
    "expectedColumns": ["id", "name", "email"],
    "timeout": 5000
  },
  "timeoutMs": 10000
}
```

### Parameters

- **resourceId** (required): PostgreSQL resource identifier
- **query** (required): SELECT query with optional parameter placeholders
- **parameters** (optional): Map of parameter names to values
- **expectedRowCount** (optional): Expected number of rows for validation
- **expectedColumns** (optional): Expected column names for validation
- **timeout** (optional): Query-specific timeout in milliseconds

### Variable Substitution

SQL responses support the following variable references:

- `${stepName.rowCount}` - Number of rows returned
- `${stepName.executionTimeMs}` - Query execution time
- `${stepName.columns.0}` - Column name by index
- `${stepName.rows.0.columnName}` - Value from specific row and column
- `${stepName.rows.first.columnName}` - Value from first row
- `${stepName.rows.last.columnName}` - Value from last row

### Example Usage

```json
{
  "steps": [
    {
      "name": "getUser",
      "stepType": "sql",
      "meta": {
        "resourceId": "main-db",
        "query": "SELECT id, name, status FROM users WHERE email = ${userEmail}",
        "parameters": {
          "userEmail": "john@example.com"
        },
        "expectedRowCount": 1
      }
    },
    {
      "name": "updateUserCache",
      "stepType": "redis",
      "meta": {
        "resourceId": "cache",
        "operation": "SET",
        "key": "user:${getUser.rows.first.id}",
        "value": "${getUser.rows.first.name}"
      }
    }
  ]
}
```

## Redis Operations

### Overview
Redis steps provide comprehensive cache operations including string, hash, and list operations.

### Supported Operations

#### String Operations
- **GET**: Retrieve string value
- **SET**: Set string value with optional TTL
- **DEL**: Delete key
- **EXISTS**: Check key existence
- **EXPIRE**: Set key expiration
- **TTL**: Get key time-to-live

#### Hash Operations
- **HGET**: Get hash field value
- **HSET**: Set hash field value
- **HGETALL**: Get all hash fields and values
- **HDEL**: Delete hash field
- **HKEYS**: Get all hash field names
- **HVALS**: Get all hash values
- **HLEN**: Get hash field count
- **HEXISTS**: Check hash field existence

#### List Operations
- **LPUSH**: Push to list head
- **RPUSH**: Push to list tail
- **LPOP**: Pop from list head
- **RPOP**: Pop from list tail
- **LLEN**: Get list length
- **LRANGE**: Get list range

### Step Configuration

```json
{
  "name": "cacheUserData",
  "stepType": "redis",
  "meta": {
    "resourceId": "redis-cache",
    "operation": "SET",
    "key": "user:123",
    "value": "{\"name\": \"John\", \"email\": \"john@example.com\"}",
    "ttl": 3600,
    "expectedExists": true
  },
  "timeoutMs": 5000
}
```

### Parameters

- **resourceId** (required): Redis resource identifier
- **operation** (required): Redis operation to perform
- **key** (required): Redis key
- **value** (optional): Value for SET, HSET, LPUSH, RPUSH operations
- **field** (optional): Hash field for HGET, HSET, HDEL, HEXISTS operations
- **fields** (optional): Additional fields for LRANGE (start, end)
- **ttl** (optional): Time-to-live in seconds for SET, EXPIRE operations
- **expectedValue** (optional): Expected value for validation
- **expectedExists** (optional): Expected existence for validation

### Variable Substitution

Redis responses support the following variable references:

- `${stepName.operation}` - Operation performed
- `${stepName.key}` - Key used
- `${stepName.value}` - Value returned (GET, LPOP, RPOP)
- `${stepName.exists}` - Boolean existence result
- `${stepName.count}` - Numeric result (DEL, LLEN, HLEN)
- `${stepName.values.fieldName}` - Hash field value (HGETALL)

### Example Operations

#### String Operations
```json
{
  "name": "setUserSession",
  "stepType": "redis",
  "meta": {
    "resourceId": "session-cache",
    "operation": "SET",
    "key": "session:${userId}",
    "value": "${sessionData}",
    "ttl": 1800
  }
}
```

#### Hash Operations
```json
{
  "name": "updateUserProfile",
  "stepType": "redis",
  "meta": {
    "resourceId": "user-cache",
    "operation": "HSET",
    "key": "user:${userId}:profile",
    "field": "last_login",
    "value": "${currentTimestamp}"
  }
}
```

#### List Operations
```json
{
  "name": "addToActivityLog",
  "stepType": "redis",
  "meta": {
    "resourceId": "activity-cache",
    "operation": "LPUSH",
    "key": "user:${userId}:activity",
    "value": "${activityEvent}"
  }
}
```

## Resource Configuration

### PostgreSQL Resource
```json
{
  "id": "postgres-main",
  "name": "Main Database",
  "type": "postgresql",
  "config": {
    "host": "localhost",
    "port": "5432",
    "database": "testdb",
    "username": "testuser",
    "password": "testpass"
  }
}
```

### Redis Resource
```json
{
  "id": "redis-cache",
  "name": "Cache Server",
  "type": "redis",
  "config": {
    "host": "localhost",
    "port": "6379",
    "database": "0",
    "password": "optional-password",
    "timeout": "2000"
  }
}
```

## Security Considerations

### SQL Security
- Only SELECT statements allowed
- Parameterized queries prevent injection
- Query complexity limits prevent resource exhaustion
- No stored procedures or functions allowed
- Comments and dangerous patterns blocked

### Redis Security
- Connection pooling with proper cleanup
- Timeout controls prevent hanging connections
- Operation validation ensures proper parameters
- No dangerous Redis commands (FLUSHDB, FLUSHALL, etc.)

## Error Handling

Both SQL and Redis steps provide detailed error information:

- Connection failures
- Query/operation timeouts
- Validation failures
- Parameter errors
- Resource not found errors

## Performance Considerations

### SQL Steps
- Use connection pooling for better performance
- Limit query complexity and result set size
- Index database tables for query performance
- Monitor query execution times

### Redis Steps
- Connection pooling reduces overhead
- Use appropriate data structures for operations
- Monitor memory usage for large values
- Set appropriate TTL values for cache efficiency

## Best Practices

1. **Use Parameterized Queries**: Always use parameters for dynamic values
2. **Validate Results**: Use expectedRowCount and expectedColumns for SQL
3. **Handle Timeouts**: Set appropriate timeouts for operations
4. **Monitor Performance**: Track execution times and optimize queries
5. **Use Variable Substitution**: Leverage previous step results effectively
6. **Resource Management**: Properly configure connection pools and timeouts
