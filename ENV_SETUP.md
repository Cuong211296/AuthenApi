# 🔐 Environment Configuration Guide

## Overview

This application uses `.env` file for secure configuration management. Sensitive data like database credentials is **NOT hardcoded** in the codebase.

---

## 📋 How It Works

1. **`.env` file** - Local file with environment variables (⚠️ **NEVER commit to Git**)
2. **`EnvConfig.java`** - Custom Spring Boot EnvironmentPostProcessor that loads `.env`
3. **`application.yaml`** - Uses `${VARIABLE_NAME:default_value}` syntax to read from .env

**Priority Order:**
```
System Environment Variables > .env file > Default values in application.yaml
```

---

## 🚀 Setup Instructions

### Step 1: Copy .env Template (Already Created!)

The `.env` file already exists with default values:

```bash
cd D:\LoginFeature\AuthenApi
cat .env
```

### Step 2: Customize for Your Environment

Edit `.env` with your actual values:

```env
# Database - Change these to your setup
DB_URL=jdbc:mysql://localhost:3306/identity_service
DB_USERNAME=root
DB_PASSWORD=root

# Server
SERVER_PORT=8081
SERVER_CONTEXT_PATH=/identity

# JWT (Keep secure!)
JWT_SIGNER_KEY=ErVMH45SIHtSBkDCaQX9su4z2IaTgU0kAJj+oqW/NYjOmHkQ6CH1l7ZDPlJGItUm

# Environment type
ENVIRONMENT=development
```

### Step 3: Verify .env is in .gitignore

Check that `.env` won't be committed:

```bash
# Should show .env
grep "^\.env$" .gitignore
```

✅ Must output: `.env`

### Step 4: Run Application

```bash
# Build
mvn clean package -DskipTests

# Run - automatically loads .env
mvn spring-boot:run
```

**Expected output:**
```
[INFO] Loaded 13 properties from .env file
```

---

## 📝 Available Variables

### Database Configuration

| Variable | Default | Example |
|----------|---------|---------|
| `DB_URL` | `jdbc:mysql://localhost:3306/identity_service` | `jdbc:mysql://prod-db:3306/auth_db` |
| `DB_USERNAME` | `root` | `app_user` |
| `DB_PASSWORD` | `root` | `SecurePassword123!` |
| `DB_DRIVER` | `com.mysql.cj.jdbc.Driver` | (keep as is) |

### Server Configuration

| Variable | Default | Example |
|----------|---------|---------|
| `SERVER_PORT` | `8081` | `8080` |
| `SERVER_CONTEXT_PATH` | `/identity` | `/api/auth` |

### JWT Configuration

| Variable | Default |
|----------|---------|
| `JWT_SIGNER_KEY` | 64-char key | **CHANGE IN PRODUCTION!** |

### Application Settings

| Variable | Default | Values |
|----------|---------|--------|
| `ENVIRONMENT` | `development` | `development`, `staging`, `production` |
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `HIBERNATE_SHOW_SQL` | `true` | `true`, `false` |
| `JPA_HIBERNATE_DDL_AUTO` | `update` | `validate`, `update`, `create`, `create-drop` |

### CORS Configuration

| Variable | Default |
|----------|---------|
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:5500,http://localhost:8000` |

---

## 🔒 Security Best Practices

### ✅ DO:

1. **Keep `.env` local only**
   ```bash
   # .env should be in .gitignore
   echo ".env" >> .gitignore
   ```

2. **Use strong JWT key in production**
   ```bash
   # Generate secure random key
   openssl rand -base64 64
   ```

3. **Rotate secrets regularly**
   - Update `JWT_SIGNER_KEY` periodically
   - Use different keys per environment

4. **Use OS-level environment variables for CI/CD**
   ```bash
   # GitHub Actions, Jenkins, etc.
   export DB_PASSWORD="prod_password"
   mvn spring-boot:run
   ```

### ❌ DON'T:

1. ❌ Commit `.env` to Git
2. ❌ Share `.env` in email/chat
3. ❌ Use same secrets across environments
4. ❌ Log sensitive values
5. ❌ Hardcode credentials in code

---

## 🌍 Environment-Specific Setup

### Development (Local Machine)

```env
ENVIRONMENT=development
DB_URL=jdbc:mysql://localhost:3306/identity_service
DB_USERNAME=root
DB_PASSWORD=root
LOG_LEVEL=DEBUG
HIBERNATE_SHOW_SQL=true
```

### Staging (Test Server)

```env
ENVIRONMENT=staging
DB_URL=jdbc:mysql://staging-db:3306/auth_db
DB_USERNAME=staging_user
DB_PASSWORD=${STAGING_DB_PASSWORD}
LOG_LEVEL=INFO
HIBERNATE_SHOW_SQL=false
```

### Production (Live Server)

```env
ENVIRONMENT=production
DB_URL=jdbc:mysql://prod-db.company.com:3306/auth_prod
DB_USERNAME=prod_app_user
DB_PASSWORD=${PROD_DB_PASSWORD}
LOG_LEVEL=WARN
HIBERNATE_SHOW_SQL=false
JPA_HIBERNATE_DDL_AUTO=validate
```

---

## 🔧 How EnvConfig Works

### File: `EnvConfig.java`

Spring Boot's `EnvironmentPostProcessor` that:

1. Reads `.env` file on startup
2. Parses key=value pairs
3. Adds as property source with **lower priority than system env vars**
4. Allows `application.yaml` to reference via `${VAR_NAME:default}`

### File: `spring.factories`

Registers `EnvConfig` as an auto-discovered processor:

```
org.springframework.boot.env.EnvironmentPostProcessor=\
  com.example.identifyservice.configuration.EnvConfig
```

---

## 🧪 Testing Configuration Loading

### Check if .env is loaded:

```bash
# Run with debug logging
LOGGING_LEVEL_ROOT=DEBUG mvn spring-boot:run
```

Look for:
```
[INFO] Loaded 13 properties from .env file
```

### Override specific variable:

```bash
# System env var overrides .env
export DB_USERNAME=override_user
mvn spring-boot:run
```

### Verify loaded values:

Create a test endpoint or check application startup logs:

```bash
# App logs should show loaded config
mvn spring-boot:run 2>&1 | grep -i "loaded"
```

---

## 🐳 Docker/Container Setup

### Build with environment variables:

```dockerfile
FROM openjdk:17-slim

WORKDIR /app
COPY target/*.jar app.jar

# Create .env from environment variables
RUN echo "DB_URL=${DB_URL}" >> .env && \
    echo "DB_USERNAME=${DB_USERNAME}" >> .env && \
    echo "DB_PASSWORD=${DB_PASSWORD}" >> .env

EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
```

### Run with Docker:

```bash
docker run \
  -e DB_URL="jdbc:mysql://db-host:3306/db" \
  -e DB_USERNAME="user" \
  -e DB_PASSWORD="pass" \
  -e JWT_SIGNER_KEY="secret-key" \
  my-app:latest
```

---

## 🆘 Troubleshooting

### Problem: "Unknown column" errors on startup

**Cause:** Database not configured correctly

**Fix:**
```bash
# Check .env values
grep DB_ .env

# Test database connection
mysql -h ${DB_HOST} -u ${DB_USERNAME} -p${DB_PASSWORD}
```

### Problem: Port already in use

**Cause:** `SERVER_PORT` already occupied

**Fix:**
```env
# Change port in .env
SERVER_PORT=8082
```

### Problem: JWT errors on login

**Cause:** `JWT_SIGNER_KEY` doesn't match between signup and login

**Fix:** Use same `JWT_SIGNER_KEY` for all instances

### Problem: Properties not loading

**Cause:** `.env` file not found or malformed

**Fix:**
```bash
# Verify .env exists
ls -la .env

# Check for syntax errors (no spaces around =)
cat .env | grep -v "^#" | grep -v "^$"
```

---

## 📚 Related Files

- **`.env`** - Environment variables (local, not committed)
- **`.env.example`** - Template for team members
- **`.gitignore`** - Ensures `.env` isn't committed
- **`application.yaml`** - Reads from `.env` with fallbacks
- **`EnvConfig.java`** - Loads `.env` file
- **`spring.factories`** - Registers EnvConfig

---

## 📝 Checklist for Deployment

- [ ] `.env` created with correct DB credentials
- [ ] `.env` added to `.gitignore`
- [ ] `JWT_SIGNER_KEY` is strong and unique
- [ ] `ENVIRONMENT` set to `production` or `staging`
- [ ] `HIBERNATE_SHOW_SQL=false` in production
- [ ] `JPA_HIBERNATE_DDL_AUTO=validate` in production
- [ ] `LOG_LEVEL=WARN` for production
- [ ] Database connection tested
- [ ] Application starts without errors

---

**Version:** 1.0  
**Last Updated:** 2026-09-26
