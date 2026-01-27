# Security: OAuth2 and JWT

This document explains the OAuth2/JWT implementation, token structure, and security configuration.

## Architecture Overview

```
┌─────────────┐        1. Request Token          ┌──────────────┐
│   Client    │ ────────────────────────────────> │ Auth Service │
│             │                                   │  (Port 8081)  │
│             │ <──────────────────────────────── │              │
│             │     2. JWT Access Token           └──────────────┘
└──────┬──────┘
       │
       │ 3. API Request + JWT Token
       ▼
┌─────────────────────────────────────┐
│      API Gateway / BFF               │
│  - Validates JWT                    │
│  - Extracts roles                   │
│  - Authorizes request               │
└─────────────────────────────────────┘
```

## Token Flow

### Step 1: Get Access Token

**Endpoint**: `POST http://localhost:8081/oauth2/token`

**Request**:
```bash
curl -X POST http://localhost:8081/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user",
    "password": "password",
    "roles": ["ROLE_USER"]
  }'
```

**Response**:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTcwNjM0NTYwMCwiZXhwIjoxNzA2MzQ5MjAwfQ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### Step 2: Use Token in API Requests

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/reports/request \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "reportType": "performance",
    "clientId": "client-123",
    "startDate": "2024-01-01",
    "endDate": "2024-12-31"
  }'
```

## JWT Token Structure

### Header

```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

- **Algorithm**: RS256 (RSA Signature with SHA-256)
- **Type**: JWT

### Payload

```json
{
  "sub": "user",
  "roles": ["ROLE_USER"],
  "iat": 1706345600,
  "exp": 1706349200
}
```

- **sub**: Subject (username)
- **roles**: Array of RBAC roles
- **iat**: Issued at (Unix timestamp)
- **exp**: Expiration (Unix timestamp, 1 hour from issue)

### Signature

Signed with RSA private key using RS256 algorithm.

## Token Generation

### Implementation

**Location**: `services/auth-service/src/main/java/com/edjobim/auth/service/TokenService.java`

```java
public String generateToken(String username, List<String> roles) {
    Instant now = Instant.now();
    Instant expiry = now.plus(1, ChronoUnit.HOURS);

    return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(keyPair.getPrivate())
            .compact();
}
```

### Key Pair Generation

**Location**: `TokenService` constructor

```java
public TokenService() {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    this.keyPair = keyGen.generateKeyPair();
}
```

- **Algorithm**: RSA
- **Key Size**: 2048 bits
- **Storage**: In-memory (regenerated on restart)

**Production Note**: In production, load key pair from secure storage (HSM, Kubernetes secrets, etc.)

## JWKS Endpoint

### Purpose

Expose public key for JWT validation without sharing private key.

### Endpoint

**URL**: `GET http://localhost:8081/oauth2/jwks`

**Response**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "auth-service-key-1",
      "use": "sig",
      "alg": "RS256",
      "n": "base64url-encoded-modulus",
      "e": "AQAB"
    }
  ]
}
```

### Implementation

**Location**: `services/auth-service/src/main/java/com/edjobim/auth/service/TokenService.java`

```java
public Map<String, Object> getJwks() {
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    
    Map<String, Object> jwk = new HashMap<>();
    jwk.put("kty", "RSA");
    jwk.put("kid", "auth-service-key-1");
    jwk.put("use", "sig");
    jwk.put("alg", "RS256");
    jwk.put("n", Base64.getUrlEncoder().withoutPadding()
            .encodeToString(publicKey.getModulus().toByteArray()));
    jwk.put("e", Base64.getUrlEncoder().withoutPadding()
            .encodeToString(publicKey.getPublicExponent().toByteArray()));

    Map<String, Object> jwks = new HashMap<>();
    jwks.put("keys", List.of(jwk));
    return jwks;
}
```

## Token Validation

### API Gateway Configuration

**Location**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/config/SecurityConfig.java`

```java
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
}
```

**Configuration**: `services/api-gateway-bff/src/main/resources/application.yml`

```yaml
jwt:
  jwks-uri: http://localhost:8081/oauth2/jwks
```

### Validation Process

1. **Extract Token**: From `Authorization: Bearer <token>` header
2. **Decode Header**: Extract algorithm (`RS256`)
3. **Fetch JWKS**: Download public keys from JWKS endpoint
4. **Verify Signature**: Validate token signature using public key
5. **Validate Claims**: Check expiration (`exp`), issued at (`iat`)
6. **Extract Roles**: Read `roles` claim for authorization

### Spring Security Configuration

**Location**: `SecurityConfig.java`

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.decoder(jwtDecoder()))
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/**").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
}
```

## Role-Based Access Control (RBAC)

### Roles

- **ROLE_USER**: Standard user (can request reports)
- **ROLE_ADMIN**: Administrator (can request reports, manage system)

### Method Security

**Location**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/controller/ReportController.java`

```java
@PostMapping("/request")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
public ResponseEntity<ReportResponse> requestReport(@Valid @RequestBody ReportRequest request) {
    // Only users with ROLE_USER or ROLE_ADMIN can access
}
```

### Role Extraction

Spring Security automatically extracts roles from JWT `roles` claim and maps them to `ROLE_*` authorities.

## Correlation ID Propagation

### Purpose

Track requests across services for security auditing and debugging.

### Implementation

**Filter**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/config/CorrelationIdFilter.java`

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                               FilterChain filterChain) {
    String correlationId = request.getHeader("X-Correlation-ID");
    if (correlationId == null || correlationId.isEmpty()) {
        correlationId = UUID.randomUUID().toString();
    }
    
    MDC.put("correlationId", correlationId);
    response.setHeader("X-Correlation-ID", correlationId);
    
    filterChain.doFilter(request, response);
}
```

### Propagation

1. **HTTP Headers**: `X-Correlation-ID` header
2. **Kafka Headers**: Included in Kafka message headers
3. **Logging**: Logged via MDC (Mapped Diagnostic Context)

## Security Best Practices

### ✅ Implemented

1. **RSA-256 Signing**: Strong cryptographic algorithm
2. **Token Expiration**: 1-hour expiry prevents long-lived tokens
3. **HTTPS**: Use HTTPS in production (not enforced locally)
4. **Role-Based Authorization**: Method-level security
5. **JWKS Endpoint**: Public key distribution without secret sharing

### ⚠️ Production Considerations

1. **Key Management**:
   - Store private key in secure storage (HSM, Kubernetes secrets)
   - Rotate keys periodically
   - Use key versioning (`kid` in JWKS)

2. **Token Refresh**:
   - Implement refresh tokens for long-lived sessions
   - Current implementation: Only access tokens

3. **Token Revocation**:
   - Implement token blacklist/revocation
   - Current implementation: Tokens valid until expiration

4. **Rate Limiting**:
   - Limit token issuance requests
   - Prevent brute force attacks

5. **Audit Logging**:
   - Log all authentication attempts
   - Log authorization failures

## Testing Security

### Test Valid Token

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:8081/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "password", "roles": ["ROLE_USER"]}' \
  | jq -r '.accessToken')

# Use token
curl -X POST http://localhost:8080/api/v1/reports/request \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reportType": "performance", "clientId": "client-123", "startDate": "2024-01-01", "endDate": "2024-12-31"}'
```

### Test Invalid Token

```bash
# Missing token
curl -X POST http://localhost:8080/api/v1/reports/request \
  -H "Content-Type: application/json" \
  -d '{"reportType": "performance", "clientId": "client-123", "startDate": "2024-01-01", "endDate": "2024-12-31"}'

# Expected: 401 Unauthorized

# Invalid token
curl -X POST http://localhost:8080/api/v1/reports/request \
  -H "Authorization: Bearer invalid-token" \
  -H "Content-Type: application/json" \
  -d '{"reportType": "performance", "clientId": "client-123", "startDate": "2024-01-01", "endDate": "2024-12-31"}'

# Expected: 401 Unauthorized
```

### Test Expired Token

```bash
# Use expired token (wait 1 hour after issuance)
# Expected: 401 Unauthorized
```

## Troubleshooting

### Issue: 401 Unauthorized

**Symptoms**: API requests return 401

**Diagnosis**:
```bash
# Check token format
echo $TOKEN | cut -d. -f1 | base64 -d  # Should show header

# Verify JWKS endpoint
curl http://localhost:8081/oauth2/jwks

# Check API Gateway logs
docker-compose logs api-gateway-bff | grep -i "jwt\|token\|unauthorized"
```

**Solutions**:
1. Verify token is not expired
2. Check JWKS endpoint is accessible
3. Verify token signature is valid
4. Check roles claim exists in token

### Issue: 403 Forbidden

**Symptoms**: API requests return 403 (authorization failure)

**Diagnosis**:
```bash
# Check token roles
echo $TOKEN | cut -d. -f2 | base64 -d | jq '.roles'

# Verify endpoint requires role
# Check @PreAuthorize annotation
```

**Solutions**:
1. Ensure token has required role
2. Check method security configuration
3. Verify role mapping (JWT claim → Spring authority)

## Next Steps

- See [00-ARCHITECTURE.md](00-ARCHITECTURE.md) for overall security architecture
- See [09-INTERVIEW-CROSS-QUESTIONS.md](09-INTERVIEW-CROSS-QUESTIONS.md) for security interview questions
