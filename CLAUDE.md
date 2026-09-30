# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **identify-service**, a Spring Boot 3.2.3 REST API for authentication and authorization. It manages user authentication via JWT tokens, role-based access control (RBAC), and token lifecycle (generation, refresh, invalidation).

**Key Tech Stack:**
- Java 17 (pom `java.version`; Maven runs on JDK 17) with Spring Boot 3.2.3
- Spring Security with OAuth2 Resource Server
- JWT (Nimbus JOSE JWT with HMAC512 signing)
- MySQL with JPA/Hibernate (with `ddl-auto: update`)
- MapStruct for DTO/entity mapping, Lombok for boilerplate reduction

## Common Commands

### Build
```bash
# Build the project
mvn clean package

# Build without running tests
mvn clean package -DskipTests
```

### Run
```bash
# Run the application locally
mvn spring-boot:run

# Application runs on port 8081 with context path /identity
# Base URL: http://localhost:8081/identity
```

### Testing
```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName
```

### Database
The application uses MySQL with Hibernate auto-update (`ddl-auto: update`). Ensure MySQL is running on localhost:3306 with:
- Database: `identity_service`
- Username: `root`
- Password: `root`

(See `src/main/resources/application.yaml` for configuration)

## Database Workflow

Claude accesses MySQL through the project-scoped MCP server `identity-db` ([.mcp.json](.mcp.json) -> [scripts/mcp_mysql.py](scripts/mcp_mysql.py)), which reads credentials from `.env` (never hardcode them). Requires `pip install mysql-mcp-server` and a Claude Code restart to load.

When a feature changes entities or schema:
1. Inspect the real schema first (MCP, or `python scripts/dbtool.py`), do not assume Hibernate applied it. `ddl-auto: update` logs DDL failures and keeps starting, and it cannot change primary keys, column types, or backfill existing rows.
2. Take a backup: `python scripts/dbtool.py backup` (writes gitignored `backups/`).
3. Write a plain-MySQL migration SQL file and run it with `python scripts/dbtool.py run <file>`. Existing rows must be backfilled (new NOT NULL columns need a DEFAULT).
4. Start the app and confirm no `Error executing DDL` in the log, then exercise the affected endpoint.

If `mvn spring-boot:run` fails with `UnsupportedClassVersionError ... 65.0`, `target/` holds classes built by an IDE with JDK 21: run `mvn clean compile`.

## Architecture Overview

### Layer Structure
```
controller          → HTTP endpoints, request/response handling
  ↓
service             → Business logic, authentication, authorization
  ↓
repository          → Data persistence (JPA)
  ↓
entity              → Domain models mapped to database
```

### Key Components

**Authentication & Security:**
- `AuthenticationService`: Core JWT logic — token generation (HMAC512), verification, refresh, logout
  - `generateToken()`: Creates JWT with user roles/permissions in scope claim
  - `verifyToken()`: Validates signature, expiration, and blacklist status
  - `logout()`: Adds token to blacklist (InvalidatedToken) by JTI (JWT ID)
  - `refreshToken()`: Invalidates old token, issues new one with same user

- `SecurityConfig`: Spring Security configuration
  - OAuth2 Resource Server with custom JWT decoder
  - Public POST: `/auth/token`, `/auth/introspect`, `/auth/logout`, `/auth/refresh`, `/users` (signup), `/payments/momo/ipn` (HMAC-signed by MoMo)
  - Public GET: `/products/**`, `/categories/**`, `/shipping/fee`, `/shipping/provinces`
  - `/admin/**` requires role ADMIN; all other endpoints require a valid JWT
  - Method-level security enabled (`@PreAuthorize`, `@Secured`)

- `CustomJwtDecoder`: Custom decoder for JWT validation via configured signerKey

**Controllers (all under `/identity` context path):**
- `AuthenticationController`: `/auth/token` (authenticate), `/auth/introspect`, `/auth/logout`, `/auth/refresh`
- `UserController`: User CRUD operations
- `RoleController`: Role management
- `PermissionController`: Permission management

**Data Model:**
- `User`: Username, password (BCrypt), roles (many-to-many)
- `Role`: Name, permissions (many-to-many)
- `Permission`: Name
- `InvalidatedToken`: Blacklist of logout/refreshed tokens (JTI + expiry time)

**Error Handling:**
- `GlobalExceptionHandler`: Maps domain exceptions to HTTP responses
- `AppException`: Custom runtime exception with `ErrorCode` enum
- Common codes: `USER_NOT_EXISTED`, `UNAUTHENTICATED`, `UNAUTHORIZED`

### JWT Token Structure
- **Algorithm:** HMAC512 (HS512)
- **Signer Key:** Configured in `application.yaml` (`jwt.signerKey`)
- **Claims:**
  - `sub`: username
  - `iss`: "CuongBackend"
  - `iat`: issue time
  - `exp`: expiration (1 hour from issue)
  - `jti`: unique token ID (for blacklisting)
  - `scope`: roles and permissions (space-separated, e.g., "ROLE_ADMIN PERMISSION_READ")

### Important Notes

**Password Encoding:**
- Uses BCrypt with strength 10 (not configurable; new BCryptPasswordEncoder(10) created in AuthenticationService and SecurityConfig)

**Token Blacklist:**
- Logout and refresh store tokens in `InvalidatedToken` table by JTI
- `verifyToken()` checks blacklist before validating; expired tokens don't need to be in blacklist

**Scope Mapping:**
- Roles prefixed with `ROLE_` (e.g., user.getRoles() → "ROLE_ADMIN")
- Permission names added as-is (flat list in scope claim)
- Used by `JwtGrantedAuthoritiesConverter` (no prefix) to populate Spring Security authorities

**Configuration File:**
- `src/main/resources/application.yaml` contains DB connection, JWT signer key, and port
- Database configured for auto-update schema; in production, switch to `validate` or use migrations

## Shop (clothing shop backend)

Design: `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (see its section 11 for amendments).

**New packages:** `momo/` (request signer, gateway client, idempotent payment finalizer), `event/` (async order-confirmation email listener), `util/` (helpers).

**Endpoints (context path `/identity`):**
- Public GET: `/products`, `/products/{slug}`, `/categories`, `/shipping/fee?province=`, `/shipping/provinces`
- Authenticated: `/cart` (get, add, update, remove, clear), `POST /orders`, `GET /orders`, `GET /orders/{code}`, `POST /orders/{code}/pay/momo`, `GET /payments/momo/return?orderCode=`
- Public (MoMo callback): `POST /payments/momo/ipn`
- Admin: `/admin/products`, `/admin/variants`, `/admin/orders` (list, `PUT /{code}/status`), `/admin/shipping-rates`

**Environment variables (`.env`):** `ADMIN_PASSWORD`, `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` (optional `MOMO_ENDPOINT`, `MOMO_REQUEST_TYPE`, `MOMO_IPN_URL`), `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `FRONTEND_URL`. The app refuses to start without `JWT_SIGNER_KEY`, `DB_PASSWORD` and `ADMIN_PASSWORD`.

**Tests:** run on H2, so `mvn test` needs no MySQL (use `mvn -q clean test`).

**Existing databases:** run `migration_v3_roles_backfill.sql` via `python scripts/dbtool.py run` (after a backup) so existing users get role USER.

## Testing Tips

- Mock repositories in unit tests; use `@DataJpaTest` for persistence tests
- Tests inherit from Spring Boot test base; see `IdentifyServiceApplicationTests`
- JWT-protected endpoints require valid tokens in `Authorization: Bearer <token>` header

## Frontend (shop-ui)

React + Vite SPA for the clothing shop storefront and admin area (supersedes `login.html`, `signup.html`, `dashboard.html`, `test-connection.html`; keep these legacy files).

**Run:** `cd shop-ui && npm install && npm run dev` (requires Node 20; dev server on port 5173, fixed for backend CORS and MoMo redirect).

**Config:** `VITE_API_BASE` env var (default `http://localhost:8081/identity`).

**Pages:**
- Storefront: `/` (browse products), `/products/:slug` (detail), `/cart`, `/checkout`
- Authentication: `/login`, `/signup`
- Orders: `/payment/result`, `/orders` (list), `/orders/:code` (detail)
- Admin: `/admin/products`, `/admin/orders`, `/admin/shipping`

**Auth:** JWT stored in `localStorage` under `auth_token`; client proactively refreshes when near expiry. For production, use httpOnly cookies (`Set-Cookie: auth_token=...; HttpOnly; Secure; SameSite=Strict`) and Content-Security-Policy headers.

**Tests:** `npm test` (Vitest; must pass before production builds).

