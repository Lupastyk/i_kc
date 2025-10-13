# Transactions Service

A Quarkus microservice that provides transaction data with JWT bearer token validation via Keycloak.

## Features

- **JWT Token Validation**: Validates access tokens issued by Keycloak
- **Bearer Token Authentication**: Requires `Authorization: Bearer <token>` header
- **Fake Transaction Data**: Generates realistic transaction data for testing
- **User-specific Data**: Returns transactions based on user ID from JWT token

## Endpoints

### GET /api/transactions
Returns fake transaction data for the authenticated user.

**Headers Required:**
```
Authorization: Bearer <access_token>
```

**Query Parameters:**
- `limit` (optional): Number of transactions to return (default: 20)

**Response:**
```json
{
  "userId": "user123",
  "transactions": [...],
  "count": 20,
  "tokenInfo": {
    "subject": "user123",
    "issuer": "http://localhost:8080/realms/finance-app",
    "expiresAt": 1234567890
  }
}
```

### GET /api/transactions/health
Health check endpoint that shows authentication status.

## Running the Service

```bash
cd transactions-service
./gradlew quarkusDev
```

The service will start on **port 8082**.

## Testing with Bearer Token

1. First, get an access token from the auth service:
   ```bash
   curl "http://localhost:8081/token?code=YOUR_AUTH_CODE"
   ```

2. Use the access token to call the transactions service:
   ```bash
   curl -H "Authorization: Bearer <access_token>" \
        "http://localhost:8082/api/transactions?limit=10"
   ```

## Keycloak Setup Requirements

For this service to work properly, the following Keycloak configuration is required:

### 1. Realm Configuration
- **Realm**: `finance-app` (must match the auth service)
- **Realm URL**: `http://localhost:8080/realms/finance-app`

### 2. Client Configuration
- **Client ID**: `finance-client` (same as auth service)
- **Client Type**: `OpenID Connect`
- **Access Type**: `confidential` or `public`
- **Valid Redirect URIs**: Include both services
  - `http://localhost:8081/*`
  - `http://localhost:8082/*`

### 3. Client Scopes & Roles
- Ensure the client has appropriate scopes
- Users should have access to the `finance-client`

### 4. Token Settings
- **Access Token Lifespan**: Reasonable duration (e.g., 15 minutes)
- **Signature Algorithm**: RS256 or PS256
- **Include user claims**: `preferred_username`, `email`, `sub`

### 5. Users
- Create test users in the realm
- Assign appropriate roles/groups if using role-based access

## Token Validation Process

1. **Token Reception**: Service receives JWT in Authorization header
2. **OIDC Validation**: Quarkus OIDC extension validates token with Keycloak
3. **Claims Extraction**: User ID extracted from token claims
4. **Data Generation**: Fake transaction data generated for user
5. **Response**: Returns user-specific transaction data

## Configuration

Key configuration in `application.properties`:

```properties
# OIDC Configuration for Keycloak token validation
quarkus.oidc.auth-server-url=http://localhost:8080/realms/finance-app
quarkus.oidc.client-id=finance-client
quarkus.oidc.application-type=service
quarkus.oidc.bearer-only=true
```

## Security Features

- **@Authenticated**: All endpoints require valid JWT token
- **Token Validation**: Automatic validation against Keycloak
- **User Context**: Access to authenticated user information
- **Claim Extraction**: Flexible user ID extraction from multiple token claims
- **Error Handling**: Proper error responses for invalid/missing tokens