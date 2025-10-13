#!/bin/bash

# Fully Automated Keycloak Finance Environment Setup
# This script sets up everything without manual intervention

set -e  # Exit on any error

echo "🚀 Starting automated Keycloak Finance environment setup..."

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
REALM_NAME="finance-app"
CLIENT_ID="finance-client"
KEYSTORE_PASSWORD="changeit"

# Step 1: Create directory structure
echo -e "${BLUE}📁 Creating directory structure...${NC}"
mkdir -p keycloak-config
mkdir -p keys

# Step 2: Generate RSA keys and certificates
echo -e "${BLUE}🔐 Generating RSA keys and certificates...${NC}"

if [ ! -f "keys/private_key.pem" ]; then
    echo "Generating private key..."
    openssl genrsa -out keys/private_key.pem 2048
    
    echo "Generating public key..."
    openssl rsa -in keys/private_key.pem -pubout -out keys/public_key.pem
    
    echo "Generating certificate..."
    openssl req -new -x509 -key keys/private_key.pem -out keys/client-cert.pem -days 365 -subj "/CN=finance-client"
    
    echo "Creating PKCS12 keystore..."
    openssl pkcs12 -export -in keys/client-cert.pem -inkey keys/private_key.pem -out keys/keystore.p12 -name finance-client -passout pass:${KEYSTORE_PASSWORD}
    
    echo "Converting to JKS format..."
    keytool -importkeystore -srckeystore keys/keystore.p12 -srcstoretype PKCS12 -destkeystore keys/keystore.jks -deststoretype JKS -srcstorepass ${KEYSTORE_PASSWORD} -deststorepass ${KEYSTORE_PASSWORD} -noprompt
    
    echo -e "${GREEN}✅ Keys and certificates generated successfully!${NC}"
else
    echo -e "${YELLOW}⚠️  Keys already exist, skipping generation${NC}"
fi

# Step 3: Create JWKS format
echo -e "${BLUE}🔑 Generating JWKS format...${NC}"
if [ ! -f "keys/jwks.json" ]; then
    # Extract modulus and exponent for JWKS
    MODULUS_HEX=$(openssl rsa -in keys/private_key.pem -noout -modulus | cut -d'=' -f2)
    MODULUS_BASE64URL=$(echo -n $MODULUS_HEX | xxd -r -p | base64 | tr '/+' '_-' | tr -d '=')

    # Create JWKS
    cat > keys/jwks.json << EOF
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig", 
      "kid": "finance-client-key",
      "n": "$MODULUS_BASE64URL",
      "e": "AQAB",
      "alg": "RS256"
    }
  ]
}
EOF
    echo -e "${GREEN}✅ JWKS generated successfully!${NC}"
fi

# Step 4: Update realm configuration with generated certificate
echo -e "${BLUE}🏗️  Updating realm configuration with certificate...${NC}"

# Extract certificate content (remove headers and newlines)
CERT_CONTENT=$(grep -v "BEGIN CERTIFICATE\|END CERTIFICATE" keys/client-cert.pem | tr -d '\n')

# Create the complete realm configuration with the actual certificate
cat > keycloak-config/finance-app-realm.json << EOF
{
  "realm": "finance-app",
  "enabled": true,
  "displayName": "Finance Application",
  "accessTokenLifespan": 300,
  "refreshTokenMaxReuse": 0,
  "ssoSessionIdleTimeout": 1800,
  "ssoSessionMaxLifespan": 36000,
  "accessCodeLifespan": 60,
  "sslRequired": "external",
  "registrationAllowed": false,
  "rememberMe": false,
  "verifyEmail": false,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "resetPasswordAllowed": true,
  "editUsernameAllowed": false,
  "bruteForceProtected": false,
  "roles": {
    "realm": [
      {
        "name": "default-roles-finance-app",
        "description": "Default roles for finance-app",
        "composite": true,
        "composites": {
          "realm": ["offline_access", "uma_authorization"]
        }
      },
      {
        "name": "offline_access",
        "description": "Offline access",
        "composite": false
      },
      {
        "name": "uma_authorization", 
        "description": "UMA authorization",
        "composite": false
      },
      {
        "name": "finance-user",
        "description": "Can access finance data",
        "composite": false
      }
    ]
  },
  "users": [
    {
      "username": "testuser",
      "enabled": true,
      "emailVerified": true,
      "firstName": "Test",
      "lastName": "User", 
      "email": "test@example.com",
      "credentials": [
        {
          "type": "password",
          "value": "testpass123",
          "temporary": false
        }
      ],
      "realmRoles": ["default-roles-finance-app", "finance-user"]
    }
  ],
  "clients": [
    {
      "clientId": "finance-client",
      "name": "Finance Application Client",
      "description": "Client for Finance Application with JWT assertion",
      "enabled": true,
      "clientAuthenticatorType": "client-jwt",
      "redirectUris": [
        "http://localhost:3000/callback",
        "http://localhost:8081/token",
        "http://localhost:8081/callback", 
        "https://oauth.pstmn.io/v1/callback"
      ],
      "webOrigins": [
        "http://localhost:8081",
        "https://oauth.pstmn.io",
        "http://localhost:3000"
      ],
      "bearerOnly": false,
      "consentRequired": false,
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "publicClient": false,
      "frontchannelLogout": true,
      "protocol": "openid-connect",
      "attributes": {
        "token.endpoint.auth.signing.alg": "RS256",
        "use.jwks.url": "false",
        "jwt.credential.certificate": "${CERT_CONTENT}"
      },
      "fullScopeAllowed": true,
      "defaultClientScopes": [
        "web-origins",
        "acr", 
        "roles",
        "profile",
        "email"
      ],
      "optionalClientScopes": [
        "address",
        "phone", 
        "offline_access",
        "microprofile-jwt"
      ]
    },
    {
      "clientId": "finance-api-server",
      "name": "Finance API Resource Server", 
      "description": "Resource server for Finance API",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "bearerOnly": true,
      "standardFlowEnabled": false,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "publicClient": false,
      "protocol": "openid-connect",
      "webOrigins": ["http://localhost:8082"],
      "fullScopeAllowed": true
    }
  ],
  "clientScopes": [
    {
      "name": "finance-api",
      "description": "Access to finance API",
      "protocol": "openid-connect",
      "attributes": {
        "include.in.token.scope": "true",
        "display.on.consent.screen": "true"
      }
    }
  ],
  "defaultDefaultClientScopes": [
    "profile",
    "email",
    "roles",
    "web-origins",
    "acr"
  ],
  "browserSecurityHeaders": {
    "contentSecurityPolicyReportOnly": "",
    "xContentTypeOptions": "nosniff",
    "xFrameOptions": "SAMEORIGIN",
    "xXSSProtection": "1; mode=block"
  },
  "browserFlow": "browser",
  "directGrantFlow": "direct grant", 
  "clientAuthenticationFlow": "clients"
}
EOF

echo -e "${GREEN}✅ Realm configuration created with embedded certificate!${NC}"

# Step 5: Create docker-compose.yml
echo -e "${BLUE}🐳 Creating Docker Compose configuration...${NC}"
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  postgres:
    image: postgres:15
    container_name: keycloak-postgres
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - keycloak-network

  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    container_name: keycloak
    command: start-dev --import-realm
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: password
      KC_HOSTNAME: localhost
      KC_HOSTNAME_PORT: 8080
      KC_HOSTNAME_STRICT_HTTPS: false
      KC_HOSTNAME_STRICT_BACKCHANNEL: false
      KC_HTTP_ENABLED: true
      KC_HOSTNAME_STRICT: false
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin123
    ports:
      - "8080:8080"
    volumes:
      - ./keycloak-config:/opt/keycloak/data/import
      - ./keys:/opt/keycloak/keys
    depends_on:
      - postgres
    networks:
      - keycloak-network

volumes:
  postgres_data:

networks:
  keycloak-network:
    driver: bridge
EOF


# Step 6: Clean up existing containers and start fresh
echo -e "${BLUE}🧹 Cleaning up existing containers...${NC}"
docker-compose down -v 2>/dev/null || true
docker stop keycloak-postgres keycloak 2>/dev/null || true
docker rm keycloak-postgres keycloak 2>/dev/null || true

echo -e "${BLUE}🚀 Starting Keycloak environment (logs will appear below)...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop when ready${NC}"
echo -e "${BLUE}Starting in 3 seconds...${NC}"
sleep 3

# Start containers in foreground to show logs
docker-compose up
