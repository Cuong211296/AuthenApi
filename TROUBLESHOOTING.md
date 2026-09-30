# 🔧 Troubleshooting Guide

## ❌ "Cannot connect to server" Error

### Root Cause
Frontend không thể kết nối đến backend do một trong những lý do:

1. **CORS không được cấu hình** ← **Nguyên nhân chính**
2. Backend không chạy
3. MySQL không chạy
4. Firewall chặn kết nối
5. Port sai hoặc không khả dụng

---

## ✅ Giải Pháp Step-by-Step

### Step 1: Test Backend Connection

Mở file `test-connection.html` để check:
```bash
# Option A: Live Server
- Right-click test-connection.html → Open with Live Server

# Option B: Direct
- Double-click test-connection.html

# Option C: Python
python -m http.server 8000
# Open: http://localhost:8000/test-connection.html
```

Nếu test báo "Backend is running" ✓ → Chuyển Step 2
Nếu không → Backend chưa chạy, xem Step 4

### Step 2: Rebuild Backend với CORS Config

**CORS config đã được thêm vào SecurityConfig.java**

Rebuild:
```bash
cd D:\LoginFeature\AuthenApi
mvn clean package
```

hoặc (không test):
```bash
mvn clean package -DskipTests
```

### Step 3: Start Backend với CORS

```bash
mvn spring-boot:run
```

Output sẽ như thế này:
```
...
o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8081 (http)
o.s.b.a.ActivationWebApplication        : Started Application in 3.234 seconds
```

✓ Nếu thấy "Tomcat started on port(s): 8081" → Backend ready!

### Step 4: Test CORS Config

Mở browser console (F12) và chạy:

```javascript
// Test CORS preflight
fetch('http://localhost:8081/identity/auth/token', {
    method: 'OPTIONS',
    headers: {
        'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'content-type'
    }
})
.then(r => {
    console.log('✓ CORS OK!', r.status);
    console.log('CORS Headers:', r.headers.get('access-control-allow-origin'));
})
.catch(e => console.error('✗ CORS Error:', e));
```

Expected response:
```
✓ CORS OK! 200
CORS Headers: http://localhost:5500
```

---

## 🔍 Common Issues & Fixes

### Issue 1: "Failed to fetch" - Network Error

**Symptoms:**
- `Failed to fetch` error in console
- `No 'Access-Control-Allow-Origin' header`

**Solution:**
1. Verify backend is running:
   ```bash
   mvn spring-boot:run
   ```

2. Check if port 8081 is available:
   ```powershell
   netstat -ano | findstr :8081
   ```

3. Kill any process using port 8081:
   ```powershell
   # Find PID
   netstat -ano | findstr :8081
   
   # Kill it (replace PID)
   taskkill /PID 12345 /F
   ```

4. Restart backend:
   ```bash
   mvn spring-boot:run
   ```

### Issue 2: "CORS Error" - Origin Not Allowed

**Symptoms:**
- `Access to XMLHttpRequest has been blocked by CORS policy`
- `The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*'`

**Solution:**
Check if your frontend origin is in CORS whitelist in `SecurityConfig.java`:

```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",    // React
    "http://localhost:5500",    // Live Server
    "http://localhost:8000",    // Python HTTP server
    "http://127.0.0.1:3000",
    "http://127.0.0.1:5500",
    "http://127.0.0.1:8000"
));
```

**If your origin is not listed:**
1. Add your origin to the list
2. Rebuild: `mvn clean package`
3. Restart: `mvn spring-boot:run`

### Issue 3: Database Connection Error

**Symptoms:**
- Backend starts but then crashes
- `Communications link failure` error in logs

**Solution:**
1. Check MySQL is running:
   ```bash
   # Windows
   sc query MySQL80
   
   # If not running, start it
   net start MySQL80
   ```

2. Verify connection string in `application.yaml`:
   ```yaml
   datasource:
     url: "jdbc:mysql://localhost:3306/identity_service"
     username: root
     password: root
   ```

3. Check database exists:
   ```sql
   CREATE DATABASE IF NOT EXISTS identity_service;
   USE identity_service;
   ```

4. Restart backend

### Issue 4: "User not found" on Login

**Symptoms:**
- Login form submits but says "Người dùng không tồn tại"

**Solution:**
Create a test user:

```bash
# Via API
curl -X POST http://localhost:8081/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "dob": "2000-01-01"
  }'
```

Or via MySQL:
```sql
INSERT INTO user (id, username, password, dob) 
VALUES (UUID(), 'testuser', '$2a$10$...hashedPassword', '2000-01-01');
```

### Issue 5: "Invalid token" after Login

**Symptoms:**
- Login successful, token is generated
- Dashboard loads but token is invalid

**Solution:**
1. Check JWT config in `application.yaml`:
   ```yaml
   jwt:
     signerKey: "ErVMH45SIHtSBkDCaQX9su4z2IaTgU0kAJj+oqW/NYjOmHkQ6CH1l7ZDPlJGItUm"
   ```

2. Check token expiration (default 1 hour):
   ```java
   .expirationTime(new Date(
       Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()
   ))
   ```

3. If still failing, try refreshing token from dashboard

---

## 🚀 Complete Setup Checklist

- [ ] **Backend**
  - [ ] `mvn clean package` - Build succeeds
  - [ ] `mvn spring-boot:run` - Server starts on port 8081
  - [ ] Console shows "Tomcat started on port(s): 8081"

- [ ] **Database**
  - [ ] MySQL running (`net start MySQL80` or service is active)
  - [ ] Database `identity_service` created
  - [ ] Tables created (auto-created by Hibernate)
  - [ ] Test user exists

- [ ] **CORS**
  - [ ] SecurityConfig has `corsConfigurationSource()` bean
  - [ ] Frontend origin in whitelist (localhost:5500, etc.)
  - [ ] Backend restarted after CORS changes

- [ ] **Frontend**
  - [ ] `login.html` opens in browser
  - [ ] test-connection.html shows "✓ Backend is running"
  - [ ] Can enter username/password
  - [ ] Login button submits without error

- [ ] **End-to-End**
  - [ ] Login succeeds
  - [ ] Redirected to dashboard.html
  - [ ] Token displays correctly
  - [ ] Can copy, refresh, or logout token

---

## 📊 What Each Test File Does

### `test-connection.html`
Tests backend connectivity and CORS:
1. Health check (backend is running)
2. CORS preflight (CORS configured)
3. API endpoints list
4. Login test (actual authentication)

**When to use:** Before trying to login, to diagnose connection issues

### `login.html`
The actual login page:
- Real authentication
- Password validation
- Error handling
- Redirects to dashboard

**When to use:** After connection tests pass, for actual login

### `dashboard.html`
User dashboard after login:
- Shows JWT token
- Token management (copy, refresh, logout)
- User info and roles/permissions

**When to use:** After successful login

---

## 🔐 Security Checklist

For **Development** (current setup):
- ✓ CORS configured for localhost only
- ✓ CSRF disabled (for API)
- ✓ JWT validation enabled
- ✓ Password hashing (BCrypt)

For **Production** (add these):
- [ ] Switch to HTTPS only
- [ ] Use httpOnly cookies instead of localStorage
- [ ] Add `Secure`, `SameSite` cookie flags
- [ ] Narrow CORS to specific domain
- [ ] Add security headers:
  ```
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Strict-Transport-Security: max-age=31536000
  Content-Security-Policy: default-src 'self'
  ```
- [ ] Set JWT expiration to reasonable value (15-30 min)
- [ ] Implement token rotation
- [ ] Use refresh tokens for extended sessions

---

## 📞 Need More Help?

1. **Check logs:**
   ```bash
   # Backend logs while running
   mvn spring-boot:run
   
   # Look for errors about CORS, database, or ports
   ```

2. **Browser console:** F12 → Console tab for JavaScript errors

3. **Network tab:** F12 → Network to see actual requests/responses

4. **Test with curl:**
   ```bash
   curl -v http://localhost:8081/identity/auth/token
   # Look for response headers and body
   ```

5. **Check configuration files:**
   - `application.yaml` - Server port, database, JWT key
   - `SecurityConfig.java` - CORS, public endpoints, auth
   - `AuthenticationService.java` - Token generation logic

---

## 📝 Log Examples

### ✓ Successful Backend Startup
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| '_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_|_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

2026-09-26T...  INFO 1234 --- [  main] c.e.i.IdentifyServiceApplication : Started Application in 3.234 seconds
2026-09-26T...  INFO 1234 --- [  main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8081 (http)
```

### ❌ CORS Error in Browser Console
```
Access to XMLHttpRequest at 'http://localhost:8081/identity/auth/token' 
from origin 'http://localhost:5500' has been blocked by CORS policy: 
No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

**Fix:** Add CORS configuration (already done in SecurityConfig.java, just restart backend)

### ❌ Database Connection Error
```
com.mysql.cj.jdbc.exceptions.CommunicationsException: 
Communications link failure: Communications link failure

The last packet sent successfully to the server was 0 milliseconds ago.
The driver has not received any packets from the server.
```

**Fix:** Start MySQL service: `net start MySQL80` or `services.msc`

---

**Good luck! 🚀** For more details, see [UI_SETUP.md](UI_SETUP.md) and [CLAUDE.md](CLAUDE.md)
