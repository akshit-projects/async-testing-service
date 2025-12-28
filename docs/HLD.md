# High-Level Design (HLD) - Async Testing Service

## 1. Overview

The Async Testing Service is a distributed, event-driven platform for orchestrating and executing complex asynchronous workflows. It enables users to define, execute, and monitor multi-step test flows across various systems including HTTP APIs, databases, message queues, and observability platforms.

### Key Capabilities
- **Multi-step Flow Orchestration**: Define complex workflows with sequential and parallel execution
- **Asynchronous Execution**: Non-blocking execution with real-time progress streaming
- **Multi-tenancy**: Organization and team-based isolation
- **Observability Integration**: Native integration with Prometheus, Grafana Loki, and other monitoring tools
- **Event-driven Architecture**: Kafka-based job distribution and Redis-based real-time updates

---

## 2. System Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        UI[Web UI/API Clients]
    end
    
    subgraph "API Layer"
        PlayApp[Play Framework Application]
        Controllers[Controllers]
        Services[Business Services]
        Auth[Authentication & Authorization]
    end
    
    subgraph "Data Layer"
        PostgreSQL[(PostgreSQL Database)]
        Redis[(Redis Cache/PubSub)]
    end
    
    subgraph "Message Queue"
        Kafka[Kafka Topics]
    end
    
    subgraph "Worker Layer"
        Workers[Worker Nodes]
        FlowRunner[Flow Runner]
        StepRunners[Step Runners]
    end
    
    subgraph "External Systems"
        HTTP[HTTP APIs]
        DBs[(External Databases)]
        Prometheus[Prometheus]
        Loki[Grafana Loki]
        ExtKafka[External Kafka]
    end
    
    UI --> PlayApp
    PlayApp --> Controllers
    Controllers --> Services
    Controllers --> Auth
    Services --> PostgreSQL
    Services --> Redis
    Services --> Kafka
    
    Kafka --> Workers
    Workers --> FlowRunner
    FlowRunner --> StepRunners
    FlowRunner --> Redis
    FlowRunner --> PostgreSQL
    
    StepRunners --> HTTP
    StepRunners --> DBs
    StepRunners --> Prometheus
    StepRunners --> Loki
    StepRunners --> ExtKafka
    
    Redis -.Real-time Updates.-> PlayApp
```

---

## 3. Core Components

### 3.1 Play Framework Application (API Server)

**Responsibilities:**
- REST API exposure for flow/execution management
- User authentication and authorization (JWT-based)
- WebSocket endpoints for real-time execution updates
- Request validation and error handling

**Key Controllers:**
- `AuthController`: User authentication
- `FlowsController`: Flow CRUD operations
- `ExecutionController`: Execution management and streaming
- `TestSuiteController`: Test suite management
- `ResourceController`: External resource configuration
- `OrganisationsController`, `TeamsController`: Multi-tenancy

### 3.2 Worker Application

**Responsibilities:**
- Consume execution jobs from Kafka
- Execute flows step-by-step
- Publish real-time updates via Redis
- Handle retries and error recovery

**Key Components:**
- `FlowRunner`: Orchestrates step execution
- `StepRunnerRegistry`: Maps step types to implementations
- Step Runners:
  - `HttpStepRunner`: HTTP requests
  - `SqlStepRunner`: Database queries
  - `KafkaPublisherStepRunner`, `KafkaConsumerStepRunner`: Kafka operations
  - `RedisStepRunner`: Redis operations
  - `DelayStepRunner`: Timed delays
  - `LokiStepRunner`: (New) Grafana Loki log queries
  - `PrometheusStepRunner`: (New) Prometheus metric queries

### 3.3 Data Layer

**PostgreSQL Database:**
- Schema managed via Flyway migrations
- Tables:
  - `user_auth`, `user_profiles`: User management
  - `organisations`, `teams`: Multi-tenancy
  - `flows`, `flow_versions`: Flow definitions
  - `executions`, `execution_steps`: Execution state
  - `test_suites`, `test_suite_executions`: Test suite management
  - `resources`: External system configurations

**Redis:**
- Real-time execution updates (Pub/Sub)
- Distributed caching
- Session management

### 3.4 Message Queue (Kafka)

**Topics:**
- `worker-queue-topic`: Execution job distribution
- Custom topics for user-defined workflows

---

## 4. Data Flow - Flow Execution

```mermaid
sequenceDiagram
    participant Client
    participant API as Play API
    participant DB as PostgreSQL
    participant Kafka
    participant Worker
    participant Redis
    participant External as External Systems

    Client->>API: POST /flows/run
    API->>DB: Create Execution
    DB-->>API: Execution Record
    API->>DB: Save Execution Steps
    API->>Kafka: Publish Execution Job
    API-->>Client: 201 Created (Execution ID)
    
    Client->>API: WebSocket Connect
    API->>Redis: Subscribe to Updates
    
    Worker->>Kafka: Consume Job
    Worker->>DB: Update Status: InProgress
    Worker->>Redis: Publish Status Update
    Redis-->>API: Update Event
    API-->>Client: Status: InProgress
    
    loop For Each Step
        Worker->>External: Execute Step
        External-->>Worker: Response
        Worker->>DB: Update Step Status
        Worker->>Redis: Publish Step Update
        Redis-->>API: Step Update Event
        API-->>Client: Step Update
    end
    
    Worker->>DB: Update Status: Completed
    Worker->>Redis: Publish Final Status
    Redis-->>API: Completion Event
    API-->>Client: Status: Completed
```

---

## 5. Multi-tenancy Architecture

```mermaid
graph TD
    subgraph "Organization A"
        TeamA1[Team A1]
        TeamA2[Team A2]
    end
    
    subgraph "Organization B"
        TeamB1[Team B1]
    end
    
    User1[User 1] -->|Member| TeamA1
    User2[User 2] -->|Member| TeamA1
    User2 -->|Member| TeamA2
    User3[User 3] -->|Member| TeamB1
    
    TeamA1 --> FlowsA1[Flows]
    TeamA2 --> FlowsA2[Flows]
    TeamB1 --> FlowsB1[Flows]
    
    FlowsA1 --> ExecutionsA1[Executions]
    FlowsA2 --> ExecutionsA2[Executions]
    FlowsB1 --> ExecutionsB1[Executions]
```

**Isolation:**
- Data isolated by `orgId` and `teamId`
- Row-level security via query filters
- Users can belong to multiple teams/organizations

---

## 6. Security Architecture

### Authentication Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant Auth as AuthService
    participant DB as PostgreSQL
    
    Client->>API: POST /auth/login
    API->>Auth: Validate Credentials
    Auth->>DB: Query User
    DB-->>Auth: User Record
    Auth->>Auth: Verify Password (BCrypt)
    Auth->>Auth: Generate JWT Token
    Auth-->>API: JWT + Refresh Token
    API-->>Client: Auth Response
    
    Client->>API: GET /flows (Authorization: Bearer <JWT>)
    API->>API: Validate JWT
    API->>DB: Verify User Active
    API->>API: Check Permissions
    API-->>Client: Flows Response
```

**Security Features:**
- JWT-based authentication
- BCrypt password hashing
- Role-based access control (RBAC)
- Permission-based authorization
- Token refresh mechanism
- Secure password reset flow

---

## 7. Observability & Monitoring

### Metrics (Prometheus)
- Execution metrics: count, duration, success/failure rate
- Step-level metrics
- API request metrics
- Worker health metrics

### Logging
- Structured logging (JSON format)
- Log levels: DEBUG, INFO, WARN, ERROR
- Correlation IDs for request tracing

### Real-time Monitoring
- WebSocket-based execution streaming
- Redis Pub/Sub for live updates
- Execution state tracking

---

## 8. Scalability Considerations

### Horizontal Scaling
- **API Layer**: Stateless design, can scale horizontally
- **Worker Layer**: Kafka consumer groups for parallel processing
- **Database**: Connection pooling (HikariCP)

### Performance Optimizations
- Async/non-blocking execution (Akka Streams)
- Database query optimization (indexed columns)
- Redis caching for frequently accessed data
- Kafka partitioning for workload distribution

---

## 9. Future Enhancements

### OnCall Automation Platform
Integration with incident management systems for automated diagnostics:

```mermaid
graph LR
    OpsGenie[OpsGenie Alert] --> Trigger[Flow Trigger]
    Trigger --> Check1[Check Recent Deployments]
    Trigger --> Check2[Scan Logs for Exceptions]
    Trigger --> Check3[Compare Metrics]
    
    Check1 --> Spinnaker[Spinnaker API]
    Check2 --> Loki[Grafana Loki]
    Check3 --> Prometheus[Prometheus]
    
    Check1 --> Report[Diagnostic Report]
    Check2 --> Report
    Check3 --> Report
    
    Report --> Publish[Publish to Slack/Email]
```

**Planned Integrations:**
- OpsGenie: Alert triggering
- Spinnaker: Deployment history
- PagerDuty: Incident correlation
- Slack/Email: Report distribution
- AI/ML: Anomaly detection and root cause analysis

---

## 10. Technology Stack

| Layer | Technology |
|-------|-----------|
| **API Framework** | Play Framework 2.9 (Scala) |
| **Database** | PostgreSQL 14+ |
| **Cache/PubSub** | Redis 6+ |
| **Message Queue** | Apache Kafka 3.x |
| **ORM** | Slick 3.4 |
| **Migration** | Flyway 11 |
| **Async Runtime** | Akka Streams 2.6 |
| **Auth** | JWT (jwt-scala), BCrypt |
| **Metrics** | Prometheus Client |
| **HTTP Client** | Play WS |
| **Build Tool** | sbt 1.10 |
| **Container** | Docker (Alpine Linux) |

---

## 11. Deployment Architecture

```mermaid
graph TB
    subgraph "Load Balancer"
        LB[NGINX/ALB]
    end
    
    subgraph "API Tier"
        API1[Play App 1]
        API2[Play App 2]
        API3[Play App N]
    end
    
    subgraph "Worker Tier"
        W1[Worker 1]
        W2[Worker 2]
        W3[Worker N]
    end
    
    subgraph "Data Tier"
        PG[PostgreSQL Primary]
        PGR[PostgreSQL Replica]
        RedisC[Redis Cluster]
        KafkaC[Kafka Cluster]
    end
    
    LB --> API1
    LB --> API2
    LB --> API3
    
    API1 --> PG
    API2 --> PG
    API3 --> PG
    
    API1 --> RedisC
    API2 --> RedisC
    API3 --> RedisC
    
    API1 --> KafkaC
    API2 --> KafkaC
    API3 --> KafkaC
    
    KafkaC --> W1
    KafkaC --> W2
    KafkaC --> W3
    
    W1 --> PG
    W2 --> PG
    W3 --> PG
    
    W1 --> RedisC
    W2 --> RedisC
    W3 --> RedisC
    
    PG -.Replication.-> PGR
```

**Deployment Characteristics:**
- Containerized deployment (Docker)
- Kubernetes orchestration (future)
- Blue-green deployment support
- Health checks and readiness probes
- Graceful shutdown handling

