# Token Service

Quarkus + Apache Camel microservice that:
- accepts an OAuth **authorization code**, exchanges it for an **access token** in Keycloak,
- calls the **Transactions Service** with `Authorization: Bearer <token>`,
- publishes the result to **Kafka**,
- returns the transactions JSON to the caller.

---

## Features

- OAuth 2.0 **Authorization Code** exchange (Keycloak)
- Bearer token call to `transactions-service`
- Kafka publish to `user-transactions`
- Typed JSON mapping (Jackson + Kotlin data classes)
- Health endpoint
- Unit & integration tests (WireMock, Testcontainers)

---

## Service Ports

- **token-service**: `8081`
- **transactions-service**: `8082`
- **keycloak**: `8080`
- **kafka**: `9092`

---

## Project Layout

```
token-service/
└── src/main/kotlin/com/finance/token/
    ├── routes/TokenRoutes.kt            # Camel routes
    ├── processors/JwtClaimsProcessor.kt # Extracts user claims from JWT
    ├── model/
    │   ├── TokenResponse.kt
    │   ├── Transaction.kt
    │   ├── TransactionsResponse.kt
    │   ├── UserAndTransactions.kt
    │   ├── HealthStatus.kt
    │   └── HealthResponse.kt
    └── resources/application.properties
```

---

## Endpoints

### `GET /token?code=<auth_code>`
Exchanges the code for an access token, fetches the user's transactions, publishes to Kafka, and returns the transactions JSON.

**Request**
```
GET http://localhost:8081/token?code=AUTH_CODE
```

**Behavior**
1. Exchange `code` → `access_token` in Keycloak.
2. Call `http://localhost:8082/api/transactions` with `Authorization: Bearer <access_token>`.
3. Publish a record to Kafka topic `user-transactions`.
4. Return the **transactions** JSON (typed as `TransactionsResponse`).

**Response (example)**
```json
{
  "userId": "user123",
  "transactions": [
    { "id": "t1", "amount": 10.0, "currency": "USD", "timestamp": 1710000000 }
  ],
  "count": 1
}
```

### `GET /api/health`
Simple health probe.

**Response**
```json
{ "status": "UP" }
```

---

## Kafka Message Format

The service publishes a JSON record to topic **`user-transactions`**:

```json
{
  "userId": "user123",
  "user": {
    "sub": "u123",
    "preferred_username": "john",
    "email": "john@example.com",
    "iss": "http://localhost:8080/realms/finance-app",
    "exp": 9999999999
  },
  "transactions": [
    { "id": "t1", "amount": 10.0, "currency": "USD", "timestamp": 1710000000 }
  ]
}
```

- `userId` is taken from the response of `transactions-service`.
- `user` claims are parsed from the JWT payload.

---

## Configuration

`token-service/src/main/resources/application.properties`

```properties
# HTTP
quarkus.http.port=8081

# Camel REST
camel.rest.component=platform-http
camel.rest.bindingMode=off
camel.rest.apiContextPath=/openapi

# External services
transactions.baseUrl=${TRANSACTIONS_BASE_URL:http://localhost:8082}
keycloak.tokenUrl=${KEYCLOAK_TOKEN_URL:http://localhost:8080/realms/finance-app/protocol/openid-connect/token}

# OAuth client
oauth.clientId=${OAUTH_CLIENT_ID:finance-client}
oauth.clientSecret=${OAUTH_CLIENT_SECRET:}    # optional (confidential client)
oauth.redirectUri=${OAUTH_REDIRECT_URI:http://localhost:8081/token}

# Kafka
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
kafka.topic.user-transactions=${KAFKA_TOPIC_USER_TX:user-transactions}
kafka.enabled=${KAFKA_ENABLED:true}
```

> Any value in braces can be overridden with an environment variable.

---

## Running Locally

### 1) Start infrastructure (Keycloak + Kafka)
From the project root:
```bash
docker compose up -d
```

This brings up:
- Postgres + Keycloak (realm import expected under `./keycloak-config`)
- Zookeeper + Kafka (single-broker dev setup)

### 2) Start services
In separate terminals:

**transactions-service:**
```bash
./gradlew :transactions-service:quarkusDev
```

**token-service:**
```bash
./gradlew :token-service:quarkusDev
```

### 3) Perform OAuth login (get `code`)
Open in a browser:
```
http://localhost:8080/realms/finance-app/protocol/openid-connect/auth?client_id=finance-client&redirect_uri=http://localhost:8081/token&response_type=code&scope=openid
```

- Log in with a test user configured in the realm.
- You'll be redirected to `http://localhost:8081/token?code=<auth_code>`.
- The service executes the flow and returns transactions JSON.

---

## How the Flow Works

1. `/token` receives `code` query param.
2. The route posts a form to Keycloak **Token Endpoint** to exchange the code for `access_token`.
3. On success, the route:
    - extracts user claims from the JWT (`JwtClaimsProcessor`),
    - calls **Transactions Service** with `Authorization: Bearer <access_token>`,
    - publishes a JSON message to Kafka topic `user-transactions`,
    - returns the transactions JSON to the HTTP client.

Errors (missing `code`, Keycloak error, etc.) are returned as JSON with a short message.

---

## Testing

### Unit
- `JwtClaimsProcessorTest` - verifies JWT payload parsing into `userClaims` and `userClaimsJson`.

Run:
```bash
./gradlew :token-service:test --tests "com.finance.token.processors.JwtClaimsProcessorTest"
```

### Integration
- `TokenRoutesTest` - end-to-end flow with WireMock stubs for Keycloak and Transactions (Kafka disabled).
- `KafkaPublishTest` - full publish path using **Testcontainers Kafka**; verifies a record arrives to `user-transactions` and includes `userId`.

Run all tests:
```bash
./gradlew :token-service:test
```

---

## Troubleshooting

- **401 from transactions-service**  
  Make sure the `access_token` is valid and `transactions-service` is running on `8082`.

- **Keycloak code exchange fails**  
  Check `oauth.clientId`, `oauth.clientSecret` (if confidential), and `oauth.redirectUri` match the Keycloak client settings.

- **Kafka errors (LEADER_NOT_AVAILABLE)**  
  Wait a second after starting Kafka, or ensure the topic is created by the producer (Camel can auto-create in this setup).

---

## Notes

- The HTTP response returns the **transactions** JSON.  
  The combined payload (`userId`, `user`, `transactions`) is published to Kafka only.
- All JSON is produced via Jackson and Kotlin data classes.
