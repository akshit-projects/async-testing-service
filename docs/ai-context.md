# AI Context - Async Testing Service

## Project Overview
The **Async Testing Service** is a distributed, event-driven platform designed to orchestrate and execute complex asynchronous workflows. It allows users to define multi-step test flows involving HTTP APIs, databases, message queues (Kafka, Redis), and observability platforms (Prometheus, Loki).

### Core Goals
- **Distributed Orchestration**: Scalable execution of workflows via Kafka-driven workers.
- **Real-time Monitoring**: Live status updates via WebSocket and Redis Pub/Sub.
- **Multi-tenancy**: Isolation by Organization and Team.
- **Observability**: Integration with logging and metrics for automated diagnostics.

---

## Technical Stack
- **Language**: Scala 2.13
- **API Framework**: Play Framework 2.9 (Scala)
- **Runtime**: Akka Streams 2.6
- **Database**: PostgreSQL 14+ (Slick 3.4 for ORM, Flyway for migrations)
- **Caching/PubSub**: Redis 6+
- **Message Queue**: Apache Kafka 3.x
- **Serialization**: Circe (JSON), Jackson (Internal)
- **Security**: JWT (jwt-scala), BCrypt
- **Observability**: Prometheus, Grafana Loki

---

## Architecture
The system follows an **API-Worker-Data** split:

1.  **API Layer (Root Project)**:
    - Exposes REST endpoints for flow/execution management.
    - Handles Auth (JWT), multi-tenancy logic, and WebSocket streaming.
    - Path: [app/](file:///Users/akshitpersonal/Downloads/async-testing-service-main/app)
2.  **Worker Layer (workers project)**:
    - Consumes execution jobs from Kafka.
    - Executes steps via `FlowRunner`.
    - Publishes real-time updates to Redis.
    - Path: [workers/](file:///Users/akshitpersonal/Downloads/async-testing-service-main/workers)
3.  **Domain Layer (domain project)**:
    - Contains core models and enums used across API and workers.
    - Path: [domain/](file:///Users/akshitpersonal/Downloads/async-testing-service-main/domain)
4.  **Library Layer (library project)**:
    - Shared repositories, utilities, and common logic.
    - Path: [library/](file:///Users/akshitpersonal/Downloads/async-testing-service-main/library)

---

## Project Structure
- `conf/`: Play Framework configuration (`application.conf`, `routes`) and DB migrations (`db/migration`).
- `app/ab/async/tester/`:
    - `controllers/`: REST and WebSocket controllers.
    - `service/`: Business logic implementations.
    - `modules/`: Dependency injection modules (Guice).
- `domain/src/main/scala/ab/async/tester/domain/`:
    - `flow/`: Flow and FlowVersion models.
    - `step/`: Meta definitions for various step types (HTTP, SQL, Kafka, Loki, etc.).
    - `execution/`: Execution and Step status models.
- `workers/src/main/scala/ab/async/tester/workers/`:
    - `FlowRunner.scala`: Main execution engine.
    - `StepRunnerRegistry.scala`: Registry for step implementations.
- `library/src/main/scala/ab/async/tester/library/`:
    - `repository/`: Slick-based database access.
    - `substitution/`: Logic for ${variable} replacement in steps.

---

## Core Entities
- **Flow**: A blueprint of multiple Steps to be executed.
- **Step**: An individual action (e.g., HTTP GET, SQL Query). Each step has a `meta` (configuration).
- **Execution**: A specific run of a Flow.
- **Resource**: External system configurations (DB credentials, Loki URLs, etc.) shared across flows.
- **Variable**: Dynamic values used for substitution (e.g., `${env_url}`).

---

## Key Workflows

### Flow Execution
1.  **API**: Receives Run request -> Creates `Execution` and `ExecutionSteps` in PG -> Publishes message to Kafka.
2.  **Worker**: Listens to Kafka -> `FlowRunner.executeFlow` -> Updates status to `InProgress`.
3.  **Step Execution**: For each step, `FlowRunner` performs:
    - **Variable Substitution**: Replaces placeholders from previous step outputs or global variables.
    - **Step Runner**: Fetches implementation from `StepRunnerRegistry` and executes.
    - **Notification**: Publishes update to Redis.
4.  **Updates**: API subscribes to Redis -> Streams status via WebSockets to the client.

---

## Guidelines for Developers
- **Adding a New Step Type**:
    1. Define `<Type>StepMeta` in `domain`.
    2. Implement `<Type>StepRunner` in `workers`.
    3. Register the runner in `StepRunnerRegistry`.
    4. Update `FlowServiceImpl` (if needed) for resource extraction.
- **Database Changes**: Use Flyway migrations in `conf/db/migration`.
- **Variable Substitution**: Use `RuntimeVariableSubstitution` for parsing `${step.output}` patterns.

---

## Documentation Links
- [High-Level Design (HLD)](file:///Users/akshitpersonal/Downloads/async-testing-service-main/docs/HLD.md)
- [Low-Level Design (LLD)](file:///Users/akshitpersonal/Downloads/async-testing-service-main/docs/LLD.md)

Update this file, HLD and LLD doc in case of major changes in code.