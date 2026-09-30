# 📊 Database Schema - v2.0 (Improved)

## Overview

Updated schema for production-ready scalability with:
- ✅ Audit timestamps (created_at, updated_at, deleted_at)
- ✅ Soft delete support (status field instead of hard delete)
- ✅ Login method tracking (LOCAL, GOOGLE, GITHUB, etc.)
- ✅ Better ID strategy (UUID for all entities)
- ✅ Security fields (login attempts, last login, email verification)
- ✅ Proper indexing for performance
- ✅ Category/grouping for permissions

---

## 📋 Entity Models

### 1. User Table

```sql
CREATE TABLE user (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    firstname VARCHAR(100),
    lastname VARCHAR(100),
    dob DATE NOT NULL,
    email VARCHAR(150) UNIQUE,
    phone VARCHAR(20),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    login_method VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME,
    last_login_at DATETIME,
    login_attempts INT DEFAULT 0,
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status)
);
```

**Fields:**
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| username | String | Unique, indexed |
| password | String | BCrypt hashed |
| firstname | String | Optional |
| lastname | String | Optional |
| dob | Date | Date of birth |
| email | String | Optional, unique for OAuth mapping |
| phone | String | Optional |
| status | Enum | ACTIVE, INACTIVE, SUSPENDED, DELETED |
| login_method | String | LOCAL, GOOGLE, GITHUB, FACEBOOK, etc. |
| created_at | Datetime | Auto-set on creation |
| updated_at | Datetime | Auto-updated |
| deleted_at | Datetime | For soft delete (NULL = active) |
| last_login_at | Datetime | Track user activity |
| login_attempts | Integer | For brute-force protection |

**Status Values:**
- `ACTIVE` - User can login
- `INACTIVE` - User account disabled
- `SUSPENDED` - Temporary suspension (e.g., too many login attempts)
- `DELETED` - Soft deleted (kept for audit trail)

---

### 2. Role Table

```sql
CREATE TABLE role (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_role_name (name)
);
```

**Fields:**
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key (changed from String name) |
| name | String | Unique role name (ADMIN, USER, MODERATOR, etc.) |
| description | String | Role description |
| status | Enum | ACTIVE, INACTIVE |
| created_at | Datetime | Auto-set |
| updated_at | Datetime | Auto-updated |

**Predefined Roles:**
```
- ADMIN: Full system access
- USER: Standard user permissions
- MODERATOR: Moderation permissions
- GUEST: Limited read-only access
```

---

### 3. Permission Table

```sql
CREATE TABLE permission (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    category VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_permission_name (name)
);
```

**Fields:**
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| name | String | Unique permission name |
| description | String | Permission description |
| category | String | GROUP permission (AUTH, USER, ADMIN, etc.) |
| status | Enum | ACTIVE, INACTIVE |
| created_at | Datetime | Auto-set |
| updated_at | Datetime | Auto-updated |

**Permission Categories:**
```
- AUTH: Authentication (login, logout, etc.)
- USER: User management
- ADMIN: Admin operations
- REPORT: Reporting features
- AUDIT: Audit trail access
```

---

### 4. User_Role Table (Many-to-Many)

```sql
CREATE TABLE user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);
```

---

### 5. Role_Permission Table (Many-to-Many)

```sql
CREATE TABLE role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);
```

---

### 6. InvalidatedToken Table

```sql
CREATE TABLE invalidated_token (
    id VARCHAR(36) PRIMARY KEY,
    jti VARCHAR(1000) NOT NULL UNIQUE,
    expiry_time DATETIME NOT NULL,
    user_id VARCHAR(100),
    reason VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_token_expiry (expiry_time),
    INDEX idx_token_user (user_id)
);
```

**Fields:**
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| jti | String | JWT ID (unique token identifier) |
| expiry_time | Datetime | Token expiration time |
| user_id | String | Optional: which user invalidated |
| reason | String | LOGOUT, REFRESH, PASSWORD_CHANGE, etc. |
| created_at | Datetime | Auto-set |

**Reason Values:**
- `LOGOUT` - User initiated logout
- `REFRESH` - Token was refreshed
- `PASSWORD_CHANGE` - User changed password
- `ADMIN_REVOKE` - Admin revoked token
- `SECURITY_INCIDENT` - Security-related revocation

---

## 🔑 Key Changes from v1.0

| Feature | v1.0 | v2.0 | Benefit |
|---------|------|------|---------|
| **Timestamps** | ❌ None | ✅ created_at, updated_at, deleted_at | Audit trail |
| **Soft Delete** | ❌ Hard delete | ✅ status field | Data preservation |
| **Login Method** | ❌ Not tracked | ✅ login_method field | OAuth support |
| **Role ID** | String (name) | ✅ UUID | Better scalability |
| **Permission ID** | String (name) | ✅ UUID | Better scalability |
| **Email Field** | ❌ None | ✅ email (unique) | Email/OAuth auth |
| **Phone Field** | ❌ None | ✅ phone | Contact info |
| **Login Tracking** | ❌ None | ✅ last_login_at, login_attempts | Activity monitoring |
| **Token Info** | Only expiryTime | ✅ jti, reason, user_id | Better debugging |
| **Status Tracking** | ❌ None | ✅ User/Role/Permission status | Enable/disable features |
| **Indexing** | ❌ Minimal | ✅ Strategic indexes | Query performance |

---

## 🚀 Future Scalability Features

### Ready to Add:

1. **Email Verification**
   ```sql
   ALTER TABLE user ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
   ALTER TABLE user ADD COLUMN email_verified_at DATETIME;
   ```

2. **Two-Factor Authentication**
   ```sql
   CREATE TABLE user_2fa (
       id VARCHAR(36) PRIMARY KEY,
       user_id VARCHAR(36) NOT NULL,
       type VARCHAR(50), -- SMS, EMAIL, AUTHENTICATOR
       secret VARCHAR(255),
       enabled BOOLEAN DEFAULT FALSE,
       created_at DATETIME
   );
   ```

3. **Refresh Token Management**
   ```sql
   CREATE TABLE refresh_token (
       id VARCHAR(36) PRIMARY KEY,
       user_id VARCHAR(36) NOT NULL,
       token VARCHAR(1000) NOT NULL UNIQUE,
       expires_at DATETIME NOT NULL,
       created_at DATETIME,
       last_used_at DATETIME
   );
   ```

4. **Audit Logging**
   ```sql
   CREATE TABLE audit_log (
       id VARCHAR(36) PRIMARY KEY,
       user_id VARCHAR(36),
       action VARCHAR(100),
       resource VARCHAR(100),
       details JSON,
       created_at DATETIME,
       INDEX idx_user (user_id),
       INDEX idx_action (action)
   );
   ```

5. **Session Management**
   ```sql
   CREATE TABLE user_session (
       id VARCHAR(36) PRIMARY KEY,
       user_id VARCHAR(36) NOT NULL,
       ip_address VARCHAR(50),
       user_agent VARCHAR(500),
       created_at DATETIME,
       last_activity DATETIME,
       expires_at DATETIME
   );
   ```

---

## 📋 Migration

Applied via `migration_v1_to_v2.sql` (run with `python scripts/dbtool.py run <file>`; take `python scripts/dbtool.py backup` first). Legacy join tables were kept as `user_roles_legacy` / `role_permissions_legacy` and can be dropped once verified.

---

## 📊 Database Diagram (Relationships)

```
┌─────────────────┐
│     USER        │
├─────────────────┤
│ id (PK)         │───┐
│ username        │   │
│ password        │   │
│ email           │   │
│ status          │   │
│ login_method    │   │
│ created_at      │   │
│ ...             │   │
└─────────────────┘   │
                      │ (Many-to-Many)
                      │
                    ┌─────────────────┐
                    │  USER_ROLE      │
                    ├─────────────────┤
                    │ user_id (FK)    │
                    │ role_id (FK)    │
                    └─────────────────┘
                      │
                      │
                      ├─────────────────────────┐
                      │                         │
                      ▼                         ▼
              ┌─────────────────┐      ┌─────────────────┐
              │      ROLE       │      │   PERMISSION    │
              ├─────────────────┤      ├─────────────────┤
              │ id (PK)         │      │ id (PK)         │
              │ name (UNIQUE)   │      │ name (UNIQUE)   │
              │ description     │      │ category        │
              │ status          │      │ status          │
              │ created_at      │      │ created_at      │
              └─────────────────┘      └─────────────────┘
                      │                        △
                      │                        │
                      │ (Many-to-Many)         │
                      └────────────────────────┘
                           │
                      ┌─────────────────┐
                      │ ROLE_PERMISSION │
                      ├─────────────────┤
                      │ role_id (FK)    │
                      │ permission_id   │
                      │ (FK)            │
                      └─────────────────┘
```

---

## 🔒 Security Best Practices

✅ **Implemented:**
- Passwords hashed with BCrypt
- Soft delete for data preservation
- Login attempt tracking
- Token invalidation/blacklisting
- Audit timestamps

✅ **Recommended for Next Phase:**
- Add email verification
- Implement 2FA
- Add session management
- Enhanced audit logging
- Rate limiting per user
- Geographic location tracking

---

## 📈 Performance Tuning

### Indexes Added:
```sql
-- User table
idx_username       -- Quick username lookup for login
idx_email          -- Email-based OAuth lookups
idx_status         -- Filter active users

-- Role table
idx_role_name      -- RBAC lookups

-- Permission table
idx_permission_name -- Permission checks

-- InvalidatedToken table
idx_token_expiry   -- Cleanup of expired tokens
idx_token_user     -- User session revocation
```

### Query Optimization Tips:
1. Always filter by `status = 'ACTIVE'` to exclude deleted records
2. Use indexes when filtering by status, username, or email
3. Consider caching role/permission data (rarely changes)
4. Archive old invalidated tokens periodically

---

## 📝 Notes

- `CURRENT_TIMESTAMP` is auto-set by database
- `ON UPDATE CURRENT_TIMESTAMP` auto-updates on record change
- Soft delete: Set `deleted_at` to non-null value, don't actually delete rows
- Always check `status != 'DELETED'` when querying (or filter by `status = 'ACTIVE'`)

---

**Version:** 2.0  
**Last Updated:** 2026-09-26  
**Status:** Production Ready
