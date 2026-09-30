# Clothing Shop - Plan 1: Foundation and Login Fixes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the Spring Boot project for the shop (test infrastructure, secrets out of git, CORS, error codes) and fix the login/user defects found in review.

**Architecture:** Same layered structure as today (`controller -> service -> repository -> entity`, `AppException` + `ErrorCode`, `ApiResponse`). Integration tests run the real Spring context against in-memory H2 (profile `test`) so no MySQL is needed for `mvn test`.

**Tech Stack:** Java 17, Spring Boot 3.2.3, Spring Security (OAuth2 resource server, HS512 JWT), JPA/Hibernate, MapStruct, Lombok, JUnit 5, H2 (test only).

**Spec:** `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (sections 2, 7, 8)

**This is plan 1 of 4.** Later plans: `shop-2-catalog-cart-orders`, `shop-3-momo-and-email`, `shop-4-frontend`.

## Global Constraints

- Java 17, Spring Boot 3.2.3, MySQL 8 in dev/prod; context path `/identity`; all responses wrapped in `ApiResponse` (`code` 1000 = success).
- Secrets (JWT key, DB password, admin password, MoMo keys, SMTP) come only from `.env` / environment, never hardcoded defaults.
- Money is `long` VND. New DTOs are Java records. New IDs are UUID strings like existing entities.
- Tests: class names end in `Test` (surefire only picks up `*Test`). Spring tests use `@SpringBootTest @ActiveProfiles("test")`.
- Commits end with the trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Work on branch `feature/clothing-shop`.

## Review Focus

- Signup request carrying an existing user's `id` must not overwrite that user (found while planning; Task 3 test).
- `PUT /users/{id}` with a missing password or missing DOB must not blank those fields (Task 3 test).
- Login with unknown user vs wrong password must be indistinguishable; suspended or locked users cannot log in (Task 4 tests).
- A user editing themselves must not be able to grant themselves ADMIN (Task 3 test).
- App must refuse to start (clear error) when `JWT_SIGNER_KEY` is absent rather than silently using a public key (Task 1).

## File Structure

| File | Responsibility |
|---|---|
| `pom.xml` | + h2 (test), spring-boot-starter-mail, spring-security-test |
| `src/main/resources/application.yaml` | secrets without defaults, mail/shop config |
| `src/test/resources/application-test.yaml` | H2 test profile |
| `.env.example` | documented variable names |
| `configuration/ShopProperties.java` | typed `shop.*` config |
| `configuration/AccountSeeder.java` | seed ADMIN/USER roles and admin account |
| `configuration/ApplicationInitConfig.java` | runner that calls the seeder |
| `configuration/SecurityConfig.java` | CORS origins (public GET rules are added in plan 2) |
| `exception/ErrorCode.java` | all new shop error codes |
| `service/UserService.java`, `mapper/UserMapper.java`, `dto/request/UserUpdateRequest.java` | user fixes |
| `service/AuthenticationService.java`, `entity/User.java` | login hardening |
| `migration_v3_roles_backfill.sql` | backfill USER role for existing users |

---

### Task 0: Branch and toolchain baseline

**Files:** `.gitignore` (modify)

- [ ] **Step 1: Confirm Maven runs on JDK 17**

Run: `mvn -v`
Expected: the `Java version:` line shows `17.x`. If it shows 21 or 24, Lombok 1.18.30 or `UnsupportedClassVersionError` will bite. Set `JAVA_HOME` to a JDK 17 in the shell, then re-run `mvn -v`.

- [ ] **Step 2: Create the feature branch**

Run: `git checkout -b feature/clothing-shop`
Expected: `Switched to a new branch 'feature/clothing-shop'`

- [ ] **Step 3: Keep Python bytecode out of git**

Append this line to `.gitignore` (under a `### Python ###` heading):

```
__pycache__/
```

- [ ] **Step 4: Ask the user, then commit their existing uncommitted login work as a baseline**

The working tree holds the user's earlier login work (modified entities/services, `login.html`, `scripts/`, docs). Show `git status --short` and ask: "Commit all of this as a baseline on the new branch?" Only after a yes:

```bash
git check-ignore .env && git add -A && git status --short
git commit -m "chore: baseline existing login work"
```
Expected: `.env` is printed by `check-ignore` (it is ignored), and it does not appear in `git status`.

- [ ] **Step 5: Verify the project compiles**

Run: `mvn -q clean compile -DskipTests`
Expected: no output, exit code 0.

---

### Task 1: Test infrastructure, secrets, CORS, error codes

**Files:**
- Modify: `pom.xml`, `src/main/resources/application.yaml`, `src/main/java/com/example/identifyservice/configuration/SecurityConfig.java`, `src/main/java/com/example/identifyservice/exception/ErrorCode.java`, `src/main/java/com/example/identifyservice/IdentifyServiceApplication.java`, `src/test/java/com/example/identifyservice/IdentifyServiceApplicationTests.java`
- Create: `src/test/resources/application-test.yaml`, `.env.example`, `src/main/java/com/example/identifyservice/configuration/ShopProperties.java`
- Test: `src/test/java/com/example/identifyservice/configuration/CorsConfigTest.java`

**Interfaces:**
- Produces: `ShopProperties(String adminPassword, String mailFrom, String frontendUrl, int orderExpiryMinutes)` (bean, prefix `shop`); all `ErrorCode` constants listed in Step 6; H2 `test` profile.

- [ ] **Step 1: Add dependencies to `pom.xml`** (inside `<dependencies>`)

```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
	<groupId>com.h2database</groupId>
	<artifactId>h2</artifactId>
	<scope>test</scope>
</dependency>
<dependency>
	<groupId>org.springframework.security</groupId>
	<artifactId>spring-security-test</artifactId>
	<scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create the test profile** `src/test/resources/application-test.yaml`

```yaml
spring:
  datasource:
    url: "jdbc:h2:mem:${random.uuid};MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  mail:
    host: localhost
    port: 3025

jwt:
  signerKey: "test-signer-key-test-signer-key-test-signer-key-test-signer-key-0123456789"

shop:
  admin-password: "Admin#12345"

logging:
  level:
    org.hibernate.SQL: WARN
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
```

- [ ] **Step 3: Write the failing CORS test** `CorsConfigTest.java`

```java
package com.example.identifyservice.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest {
    @Autowired
    MockMvc mvc;

    @Test
    void allowsViteDevOrigin() throws Exception {
        mvc.perform(options("/auth/token")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/auth/token")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 4: Make the existing context test use the test profile**

In `IdentifyServiceApplicationTests.java` add `import org.springframework.test.context.ActiveProfiles;` and the annotation `@ActiveProfiles("test")` above `@SpringBootTest`.

- [ ] **Step 5: Run and confirm the CORS test fails**

Run: `mvn -q test -Dtest=CorsConfigTest`
Expected: `allowsViteDevOrigin` FAILS (403, origin 5173 not allowed). `rejectsUnknownOrigin` passes. (If the context fails to start instead, read the error; it must be about the app, not H2.)

- [ ] **Step 6: Add the shop error codes.** In `ErrorCode.java` replace the line `INVALID_KEY(1010, "Uncategorized error", HttpStatus.BAD_REQUEST);` with:

```java
    INVALID_KEY(1010, "Uncategorized error", HttpStatus.BAD_REQUEST),
    INVALID_INPUT(1011, "Invalid input", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(2001, "Product not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(2002, "Category not found", HttpStatus.NOT_FOUND),
    VARIANT_NOT_FOUND(2003, "Product variant not found or unavailable", HttpStatus.NOT_FOUND),
    SLUG_EXISTED(2004, "Slug already exists", HttpStatus.BAD_REQUEST),
    SKU_EXISTED(2005, "SKU already exists", HttpStatus.BAD_REQUEST),
    OUT_OF_STOCK(2006, "Not enough stock", HttpStatus.CONFLICT),
    INVALID_QUANTITY(2007, "Invalid quantity", HttpStatus.BAD_REQUEST),
    CART_EMPTY(2008, "Cart is empty", HttpStatus.BAD_REQUEST),
    INVALID_PROVINCE(2009, "Province is not supported", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(2010, "Order not found", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS(2011, "Invalid order status change", HttpStatus.BAD_REQUEST),
    ORDER_NOT_PAYABLE(2012, "Order cannot be paid", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_SIGNATURE(2013, "Invalid payment signature", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH(2014, "Payment amount mismatch", HttpStatus.BAD_REQUEST),
    PAYMENT_GATEWAY_ERROR(2015, "Payment gateway error", HttpStatus.BAD_GATEWAY);
```

- [ ] **Step 7: Add the Vite origins to CORS.** In `SecurityConfig.corsConfigurationSource()` add these two entries to the `Arrays.asList(...)` of allowed origins:

```java
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
```

- [ ] **Step 8: Create `ShopProperties.java`** and enable scanning

```java
package com.example.identifyservice.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop")
public record ShopProperties(String adminPassword, String mailFrom, String frontendUrl, int orderExpiryMinutes) {
}
```

In `IdentifyServiceApplication.java` add `import org.springframework.boot.context.properties.ConfigurationPropertiesScan;` and annotate the class with `@ConfigurationPropertiesScan` (below `@SpringBootApplication`).

- [ ] **Step 9: Rewrite `application.yaml`** (secrets lose their defaults)

```yaml
server:
  port: ${SERVER_PORT:8081}
  servlet:
    context-path: ${SERVER_CONTEXT_PATH:/identity}

spring:
  datasource:
    url: "${DB_URL:jdbc:mysql://localhost:3306/identity_service}"
    username: "${DB_USERNAME:root}"
    password: "${DB_PASSWORD}"
    driver-class-name: "${DB_DRIVER:com.mysql.cj.jdbc.Driver}"
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 20000

  jpa:
    hibernate:
      ddl-auto: ${JPA_HIBERNATE_DDL_AUTO:update}
    show-sql: ${HIBERNATE_SHOW_SQL:false}
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true

  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:2525}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: ${MAIL_SMTP_AUTH:true}
      mail.smtp.starttls.enable: ${MAIL_STARTTLS:true}

jwt:
  signerKey: "${JWT_SIGNER_KEY}"

shop:
  admin-password: "${ADMIN_PASSWORD}"
  mail-from: ${MAIL_FROM:no-reply@shop.local}
  frontend-url: ${FRONTEND_URL:http://localhost:5173}
  order-expiry-minutes: 15

logging:
  level:
    root: ${LOG_LEVEL:INFO}
    org.hibernate.SQL: ${HIBERNATE_LOG_LEVEL:INFO}
```

- [ ] **Step 10: Create `.env.example`**

```
# Copy to .env (git-ignored). Run the app from the project root so .env is found.
DB_PASSWORD=
JWT_SIGNER_KEY=
ADMIN_PASSWORD=
# Generate a signer key: node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
MAIL_HOST=
MAIL_PORT=
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
MOMO_PARTNER_CODE=
MOMO_ACCESS_KEY=
MOMO_SECRET_KEY=
```

- [ ] **Step 11: Make the local `.env` complete.** List which keys exist without printing values:

Run: `grep -o '^[A-Z_]*=' .env`
Expected: contains `DB_PASSWORD=`, `JWT_SIGNER_KEY=`, `ADMIN_PASSWORD=`. For any missing key, append it. Generate a **new** signer key (the old one is in git history, so treat it as leaked; rotating logs everyone out, which is fine):

Run: `node -e "console.log('JWT_SIGNER_KEY='+require('crypto').randomBytes(48).toString('base64'))" >> .env`
(Only if `JWT_SIGNER_KEY=` is missing or you are rotating; remove the old line first.) Set `ADMIN_PASSWORD` to a strong password of the user's choosing.

- [ ] **Step 12: Run the tests**

Run: `mvn -q test -Dtest=CorsConfigTest,IdentifyServiceApplicationTests`
Expected: all PASS.

- [ ] **Step 13: Commit**

```bash
git add pom.xml src .env.example
git commit -m "chore: test profile, secrets from env, CORS for Vite, shop error codes"
```

---

### Task 2: Seed roles and admin, backfill existing users

**Files:**
- Create: `configuration/AccountSeeder.java`, `migration_v3_roles_backfill.sql`
- Modify: `configuration/ApplicationInitConfig.java`, `repository/RoleRepository.java`
- Test: `src/test/java/com/example/identifyservice/configuration/AccountSeederTest.java`

**Interfaces:**
- Produces: `RoleRepository.findByName(String) : Optional<Role>`; `AccountSeeder.seed()`; roles named `ADMIN` and `USER` always exist.

(All paths under `src/main/java/com/example/identifyservice/` unless a full path is shown.)

- [ ] **Step 1: Write the failing test**

```java
package com.example.identifyservice.configuration;

import com.example.identifyservice.entity.Role;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountSeederTest {
    @Autowired AccountSeeder seeder;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;

    @Test
    void adminExistsWithAdminRoleAndConfiguredPassword() {
        var admin = users.findByUsername("admin").orElseThrow();
        assertThat(admin.getRoles()).extracting(Role::getName).contains("ADMIN");
        assertThat(encoder.matches("Admin#12345", admin.getPassword())).isTrue();
        assertThat(roles.findByName("USER")).isPresent();
    }

    @Test
    void seedRepairsAdminThatLostItsRole() {
        var admin = users.findByUsername("admin").orElseThrow();
        admin.getRoles().clear();
        users.save(admin);
        em.flush();

        seeder.seed();
        em.flush();
        em.clear();

        assertThat(users.findByUsername("admin").orElseThrow().getRoles())
                .extracting(Role::getName).contains("ADMIN");
    }

    @Test
    void seedIsIdempotent() {
        seeder.seed();
        seeder.seed();
        assertThat(roles.findAll().stream().filter(r -> r.getName().equals("ADMIN")).count()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=AccountSeederTest`
Expected: compilation error, `AccountSeeder` and `findByName` do not exist.

- [ ] **Step 3: Add `findByName` to `RoleRepository`**

```java
    java.util.Optional<Role> findByName(String name);
```
(inside the interface body, after the class declaration line's `{`)

- [ ] **Step 4: Create `AccountSeeder.java`**

```java
package com.example.identifyservice.configuration;

import com.example.identifyservice.entity.Role;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountSeeder {
    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    ShopProperties shopProperties;

    @Transactional
    public void seed() {
        Role adminRole = ensureRole("ADMIN", "Administrator");
        ensureRole("USER", "Customer");

        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            log.warn("admin user created; its password is taken from ADMIN_PASSWORD");
            return User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(shopProperties.adminPassword()))
                    .dob(LocalDate.of(1990, 1, 1))
                    .roles(new HashSet<>())
                    .build();
        });
        if (admin.getRoles() == null) admin.setRoles(new HashSet<>());
        if (admin.getRoles().stream().noneMatch(r -> "ADMIN".equals(r.getName()))) {
            admin.getRoles().add(adminRole);
        }
        userRepository.save(admin);
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() ->
                roleRepository.save(Role.builder().name(name).description(description).build()));
    }
}
```

- [ ] **Step 5: Replace `ApplicationInitConfig.java`**

```java
package com.example.identifyservice.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ApplicationInitConfig {

    @Bean
    ApplicationRunner applicationRunner(AccountSeeder accountSeeder) {
        return args -> accountSeeder.seed();
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=AccountSeederTest`
Expected: 3 tests PASS.

- [ ] **Step 7: Write the backfill migration** `migration_v3_roles_backfill.sql` (project root)

```sql
-- Ensure base roles exist and give every existing non-admin user without a role the USER role.
INSERT INTO role (id, name, description, status, created_at, updated_at)
SELECT UUID(), 'USER', 'Customer', 'ACTIVE', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'USER');

INSERT INTO role (id, name, description, status, created_at, updated_at)
SELECT UUID(), 'ADMIN', 'Administrator', 'ACTIVE', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'ADMIN');

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM `user` u
JOIN role r ON r.name = 'USER'
WHERE u.username <> 'admin'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id);
```

(It is applied against MySQL in Plan 3, Task 13, following the CLAUDE.md database workflow: inspect, backup, run, verify.)

- [ ] **Step 8: Commit**

```bash
git add src migration_v3_roles_backfill.sql
git commit -m "fix: seed ADMIN/USER roles and admin account from env"
```

---

### Task 3: User service fixes (id injection, self-service update, default role)

**Files:**
- Modify: `service/UserService.java`, `mapper/UserMapper.java`, `dto/request/UserUpdateRequest.java`
- Test: `src/test/java/com/example/identifyservice/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `RoleRepository.findByName`, seeded roles (Task 2).
- Produces: `UserService.createUser` assigns role `USER` and ignores a client-supplied id; `UserService.updateUser(String userId, UserUpdateRequest)` allows self or ADMIN, changes roles only for ADMIN, keeps password/dob when not supplied.

- [ ] **Step 1: Write the failing test**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.UserCreationRequest;
import com.example.identifyservice.dto.request.UserUpdateRequest;
import com.example.identifyservice.dto.response.UserResponse;
import com.example.identifyservice.dto.response.RoleResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired EntityManager em;

    private UserResponse signUp(String username) {
        return userService.createUser(UserCreationRequest.builder()
                .username(username).password("password123")
                .firstname("A").lastname("B").dob(LocalDate.of(2000, 1, 1)).build());
    }

    @Test
    void newUserGetsUserRoleOnly() {
        var created = signUp("alice");
        assertThat(created.getRoles()).extracting(RoleResponse::getName).containsExactly("USER");
    }

    @Test
    void clientSuppliedIdCannotOverwriteAnotherUser() {
        var victim = signUp("victim");
        var attacker = userService.createUser(UserCreationRequest.builder()
                .id(victim.getId()).username("mallory").password("password123")
                .firstname("M").lastname("M").dob(LocalDate.of(2000, 1, 1)).build());
        em.flush();
        em.clear();

        assertThat(attacker.getId()).isNotEqualTo(victim.getId());
        assertThat(users.findById(victim.getId()).orElseThrow().getUsername()).isEqualTo("victim");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCanEditSelfWithoutPasswordAndCannotGrantAdmin() {
        var alice = signUp("alice");
        String hashBefore = users.findById(alice.getId()).orElseThrow().getPassword();
        String adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();

        userService.updateUser(alice.getId(),
                UserUpdateRequest.builder().firstname("Alicia").roles(List.of(adminRoleId)).build());
        em.flush();
        em.clear();

        var reloaded = users.findById(alice.getId()).orElseThrow();
        assertThat(reloaded.getFirstname()).isEqualTo("Alicia");
        assertThat(reloaded.getPassword()).isEqualTo(hashBefore);
        assertThat(reloaded.getDob()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(reloaded.getRoles()).extracting(r -> r.getName()).containsExactly("USER");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCannotEditSomeoneElse() {
        signUp("alice");
        var bob = signUp("bob");
        assertThatThrownBy(() -> userService.updateUser(bob.getId(),
                UserUpdateRequest.builder().firstname("Hacked").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanChangeRoles() {
        var alice = signUp("alice");
        String adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();
        userService.updateUser(alice.getId(), UserUpdateRequest.builder().roles(List.of(adminRoleId)).build());
        em.flush();
        em.clear();
        assertThat(users.findById(alice.getId()).orElseThrow().getRoles())
                .extracting(r -> r.getName()).containsExactly("ADMIN");
    }
}
```

Note: `RoleResponse` must expose `getName()`; check `dto/response/RoleResponse.java` and adjust the accessor name if the field differs.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=UserServiceTest`
Expected: FAILs (no USER role on new user, id injection succeeds, update NPE or blanks fields).

- [ ] **Step 3: Fix `UserMapper`**

```java
package com.example.identifyservice.mapper;

import com.example.identifyservice.dto.request.UserCreationRequest;
import com.example.identifyservice.dto.request.UserUpdateRequest;
import com.example.identifyservice.dto.response.UserResponse;
import com.example.identifyservice.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
```

- [ ] **Step 4: Validate the optional password.** In `UserUpdateRequest.java` add `import jakarta.validation.constraints.Size;` and annotate the field: `@Size(min = 8, message = "PASSWORD_INVALID") String password;`

- [ ] **Step 5: Update `UserService`.** Add imports `com.example.identifyservice.enums.Role` is NOT used (name clash); use string names. Replace `createUser` and `updateUser`:

```java
    public UserResponse createUser(UserCreationRequest request){
        if (userRepository.existsByUsername(request.getUsername()))
            throw new AppException(ErrorCode.USER_EXISTED);

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setRoles(new HashSet<>(java.util.Set.of(userRole)));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isSelf = authentication != null && user.getUsername().equals(authentication.getName());
        if (!isAdmin && !isSelf) throw new AppException(ErrorCode.UNAUTHORIZED);

        userMapper.updateUser(user, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (isAdmin && request.getRoles() != null) {
            user.setRoles(new HashSet<>(roleRepository.findAllById(request.getRoles())));
        }

        return userMapper.toUserResponse(userRepository.save(user));
    }
```
Delete the old `@PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")` line above `updateUser` and the commented-out role/timestamp lines in `createUser`. Keep the other methods unchanged.

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=UserServiceTest`
Expected: 5 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "fix: user signup/update - default role, no id injection, self-service rules"
```

---

### Task 4: Login hardening (generic errors, status, lockout, last login)

**Files:**
- Modify: `entity/User.java`, `service/AuthenticationService.java`
- Test: `src/test/java/com/example/identifyservice/service/AuthenticationServiceTest.java`

**Interfaces:**
- Produces: `User.lockedUntil : Instant`; `AuthenticationService.authenticate` throws `UNAUTHENTICATED` for unknown user, wrong password, non-ACTIVE status, or active lock; sets `lastLoginAt`; locks 15 minutes after 5 consecutive failures.

- [ ] **Step 1: Write the failing test**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.AuthenticationRequest;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationServiceTest {
    @Autowired AuthenticationService authService;
    @Autowired UserRepository users;

    User carol;

    @BeforeEach
    void setUp() {
        carol = users.save(User.builder().username("carol")
                .password(new BCryptPasswordEncoder(10).encode("password123"))
                .dob(LocalDate.of(2000, 1, 1)).build());
    }

    private AuthenticationRequest req(String u, String p) {
        return AuthenticationRequest.builder().username(u).password(p).build();
    }

    private void assertUnauthenticated(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void unknownUserAndWrongPasswordLookTheSame() {
        assertUnauthenticated(() -> authService.authenticate(req("nobody", "password123")));
        assertUnauthenticated(() -> authService.authenticate(req("carol", "wrong-password")));
    }

    @Test
    void successfulLoginRecordsLastLoginAndResetsAttempts() {
        assertUnauthenticated(() -> authService.authenticate(req("carol", "bad")));
        var response = authService.authenticate(req("carol", "password123"));
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getToken()).isNotBlank();
        assertThat(carol.getLastLoginAt()).isNotNull();
        assertThat(carol.getLoginAttempts()).isZero();
    }

    @Test
    void suspendedUserCannotLogin() {
        carol.setStatus("SUSPENDED");
        users.save(carol);
        assertUnauthenticated(() -> authService.authenticate(req("carol", "password123")));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() {
        for (int i = 0; i < 5; i++) {
            assertUnauthenticated(() -> authService.authenticate(req("carol", "bad")));
        }
        assertThat(carol.getLockedUntil()).isAfter(Instant.now());
        assertUnauthenticated(() -> authService.authenticate(req("carol", "password123")));
    }

    @Test
    void expiredLockNoLongerBlocksLogin() {
        carol.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        users.save(carol);
        assertThat(authService.authenticate(req("carol", "password123")).isAuthenticated()).isTrue();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=AuthenticationServiceTest`
Expected: compilation error (`getLockedUntil`), then behavioral failures.

- [ ] **Step 3: Add the column.** In `entity/User.java` after `Integer loginAttempts;` add:

```java
    Instant lockedUntil; // temporary lock after repeated failed logins
```

- [ ] **Step 4: Replace `authenticate` in `AuthenticationService`** and add constants/imports (`java.time.Instant` and `ChronoUnit` are already imported):

```java
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(10);
    // Compared against when the username does not exist, so timing does not reveal it.
    private static final String DUMMY_HASH = PASSWORD_ENCODER.encode("dummy-password");

    public AuthenticationResponse authenticate(AuthenticationRequest request){
        var user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            PASSWORD_ENCODER.matches(String.valueOf(request.getPassword()), DUMMY_HASH);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Instant now = Instant.now();
        if (!"ACTIVE".equals(user.getStatus())
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        boolean authenticated = PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            int attempts = (user.getLoginAttempts() == null ? 0 : user.getLoginAttempts()) + 1;
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_MINUTES, ChronoUnit.MINUTES));
                attempts = 0;
            }
            user.setLoginAttempts(attempts);
            userRepository.save(user);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        return AuthenticationResponse.builder()
                .token(generateToken(user))
                .authenticated(true)
                .build();
    }
```
Do **not** add `@Transactional` to this class: the failed-attempt counter must be saved even though an exception follows. `AuthenticationService` cannot inject the `PasswordEncoder` bean (it would create a cycle through `SecurityConfig` -> `CustomJwtDecoder`), which is why the encoder stays local.

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -q test -Dtest=AuthenticationServiceTest`
Expected: 5 tests PASS.

- [ ] **Step 6: Run the whole suite**

Run: `mvn -q test`
Expected: all tests from Tasks 1-4 PASS.

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "fix: harden login - generic errors, status check, lockout, last login"
```
