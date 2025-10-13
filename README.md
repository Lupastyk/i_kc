# Finance Microservices - Camel & Kotlin Skills Evaluation

A skills evaluation project demonstrating Apache Camel integration with Kotlin microservices. This project contains a multi-module Gradle setup with existing transaction services and requires implementing a token service with Camel routes.

## Project Overview

This project contains:
- **transactions-service**: A complete Quarkus-based REST API for handling financial transactions with JWT authentication
- **token-service**: **(TO BE IMPLEMENTED)** - A Camel-based service that orchestrates token exchange and transaction retrieval

## Current Project Structure

```
finance-microservices/
├── transactions-service/        # ✅ COMPLETED - Transaction REST API (Port 8082)
│   ├── src/main/kotlin/com/finance/transactions/
│   │   ├── model/Transaction.kt      # Transaction data model
│   │   ├── resource/TransactionResource.kt  # REST endpoints
│   │   └── service/TransactionService.kt    # Business logic
│   └── build.gradle.kts
├── token-service/              # 🔨 TO BE IMPLEMENTED (Port 8081)
├── docker-compose.yaml         # 🔨 TO BE GENERATED 
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Multi-module configuration
└── README.md                  # This file
```

## Setup Instructions

### Prerequisites
- Java 17+
- Docker & Docker Compose (for Kafka)
- Gradle 7.5+

### Initial Setup
1. Clone this repository
2. Run the setup script: `./setup.sh`
3. Build the existing services: `./gradlew build`

### Manual Setup Validation
After running the setup, verify your environment is correctly configured:

1. **Keycloak Admin Access**:
   - Navigate to http://localhost:8080/admin
   - Login with `admin/admin123`
   - Verify access to the admin console

2. **Test User Verification**:
   - Confirm user `testuser` exists (the password is `testpass123`)
   - User should be in the `finance-app` realm

3. **OAuth Flow Test**:
   - Open this URL in your browser:
   ```
   http://localhost:8080/realms/finance-app/protocol/openid-connect/auth?client_id=finance-client&redirect_uri=http://localhost:8081/token&response_type=code&scope=openid
   ```
   - Login with `testuser/testpass123`
   - Verify successful redirect (endpoint doesn't exist yet, but redirect should work)
   - Note the authorization code in the redirect URL

These steps confirm Keycloak is properly configured for your implementation.

### Expected Project Structure After Implementation

```
finance-microservices/
├── transactions-service/        # ✅ COMPLETED
├── token-service/              # 🔨 YOUR IMPLEMENTATION
│   ├── src/main/kotlin/com/finance/token/
│   │   ├── routes/             # Camel routes
│   │   ├── processors/         # Camel processors
│   │   ├── model/             # Data models
│   │   └── config/            # Configuration
│   └── build.gradle.kts
├── docker-compose.yaml         # 🔨 YOUR IMPLEMENTATION - Kafka setup
└── [other files...]
```

## Assignment: Implement Token Service

### Objective
Create a **token-service** using **Apache Camel** and **Kotlin** that:

1. **Exposes REST endpoint** `/token?code=<auth_code>` to handle OAuth code exchange
2. **Calls transactions-service** to retrieve user transactions using JWT token
3. **Publishes transactions to Kafka** topic

### Requirements

#### 1. OAuth Flow Implementation
- **Endpoint**: `GET /token?code=<auth_code>`
- **OAuth Code Exchange**:
  1. Receive authorization code from Keycloak redirect
  2. Exchange authorization code for JWT access token with Keycloak (use generated keys folder for signing)
- **Triggered by**:
  ```
  http://localhost:8080/realms/finance-app/protocol/openid-connect/auth?client_id=finance-client&redirect_uri=http://localhost:8081/token&response_type=code&scope=openid
  ```

#### 2. Transaction Fetching & Kafka Publishing
- **Fetch Transactions**: Call transactions-service `/api/transactions` endpoint using the JWT access token
- **Authentication**: Include JWT token in `Authorization: Bearer <token>` header
- **Kafka Publishing**: Publish retrieved transaction data to `user-transactions` topic
- **Message Format**: JSON containing user info and transaction data
- **Error Handling**: Handle service unavailability and authentication failures


#### 4. Technical Implementation
- **Framework**: Apache Camel with Quarkus
- **Language**: Kotlin
- **Architecture**: Follow the same patterns as transactions-service
- **Logging**: Include appropriate logging for monitoring
- **Testing**: Basic unit tests for main components (integration tests for the full flow is a bonus)


### Running the Complete Solution

After implementation, the full system should run with:

```bash
# Start Kafka
docker-compose up -d

# Start transactions service
./gradlew :transactions-service:quarkusDev

# Start your token service
./gradlew :token-service:quarkusDev

# Test the integration (open in browser)
http://localhost:8080/realms/finance-app/protocol/openid-connect/auth?client_id=finance-client&redirect_uri=http://localhost:8081/token&response_type=code&scope=openid
```

### Resources

- **Apache Camel**: https://camel.apache.org/
- **Camel Quarkus**: https://camel.apache.org/camel-quarkus/
- **Existing code**: Review transactions-service for patterns and structure
- **Kafka**: https://kafka.apache.org/quickstart

### Submission

Provide:
1. Complete token-service implementation
2. docker-compose.yaml with Kafka setup
3. Updated build.gradle.kts if needed
4. Brief documentation of your approach
5. Instructions for testing the integration

---

*This assignment evaluates practical skills in Apache Camel, Kotlin, microservices integration, and message queuing with Kafka.*