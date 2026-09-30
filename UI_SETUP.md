# 🎨 Modern Login UI - Setup Guide

**Note:** This document is superseded by the React app in `shop-ui/`; see [CLAUDE.md § Frontend (shop-ui)](CLAUDE.md#frontend-shop-ui) for the current setup.

## Overview

This project includes a modern, responsive login & dashboard UI built with vanilla HTML5, CSS3, and JavaScript. No build tools required!

## 📋 Files Included

- **login.html** - Beautiful login page with authentication
- **dashboard.html** - User dashboard with token management
- **CLAUDE.md** - Project documentation (updated with UI info)

## 🚀 Quick Start

### Prerequisites
- Spring Boot backend running on `http://localhost:8081/identity`
- Modern web browser (Chrome, Firefox, Safari, Edge)
- MySQL running (for backend)

### Step 1: Start the Backend API

```bash
# Navigate to project directory
cd d:\LoginFeature\AuthenApi

# Start the Spring Boot application
mvn spring-boot:run
```

Server will be running on: `http://localhost:8081/identity`

### Step 2: Open the Login Page

**Option A: Using Live Server (Recommended)**
1. Install VS Code Extension: "Live Server" by Ritwick Dey
2. Right-click on `login.html` → "Open with Live Server"
3. Browser opens at `http://localhost:5500/login.html`

**Option B: Direct File Open**
1. Right-click `login.html` → "Open with" → Browser
2. Or drag and drop `login.html` into browser

**Option C: Using Python HTTP Server**
```bash
# From project root directory
python -m http.server 8000

# Then open: http://localhost:8000/login.html
```

### Step 3: Test Login

First, create a test user in your database:

```sql
INSERT INTO user (id, username, password, dob) VALUES (
    UUID(), 
    'testuser', 
    '$2a$10$...',  -- BCrypt hashed password
    '2000-01-01'
);
```

Or use the API to create a user:

```bash
curl -X POST http://localhost:8081/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "dob": "2000-01-01"
  }'
```

### Step 4: Login & Explore

1. Enter username and password
2. Click "Đăng nhập" button
3. On success, redirected to `dashboard.html`
4. View your JWT token, copy it, refresh it, or logout

## 🎯 Features

### Login Page (`login.html`)

```
┌─────────────────────────────────────┐
│  Identify Service                   │
│  Đăng nhập vào tài khoản            │
├─────────────────────────────────────┤
│                                     │
│  [Tên đăng nhập            ]  👤    │
│  [Mật khẩu                 ]  👁    │
│  ☐ Nhớ mật khẩu    Quên mật khẩu? │
│                                     │
│        [Đăng nhập]                  │
│                                     │
│  ──────── Hoặc ────────             │
│  [Google]    [GitHub]               │
│                                     │
│  Chưa có tài khoản? Đăng ký ngay    │
└─────────────────────────────────────┘
```

**Features:**
- ✅ Real-time validation (username 3+ chars, password 8+ chars)
- 👁️ Password visibility toggle
- 💾 Remember username checkbox
- 📱 Fully responsive design
- 🎨 Gradient purple theme
- ⚡ Smooth animations
- 🔔 Error/success notifications
- 🔗 Social login placeholders

### Dashboard Page (`dashboard.html`)

```
┌────────────────────────────────────────┐
│ Identify Service      [User] [Logout]  │
├────────────────────────────────────────┤
│                                        │
│  Welcome Back! 👋                      │
│  Dashboard of Identify Service...      │
│                                        │
│  [Username]  [Join Date]  [Status]    │
│                                        │
│  JWT Token                             │
│  ┌─────────────────────────────────┐  │
│  │ eyJhbGc...                      │  │
│  └─────────────────────────────────┘  │
│  [Copy] [Refresh] [Logout]             │
│                                        │
│  Roles & Permissions                   │
│  [ADMIN]  [USER]                       │
│  [✓ READ]  [✓ WRITE]                   │
│                                        │
└────────────────────────────────────────┘
```

**Features:**
- 📊 Display user info (username, join date, status)
- 🔐 Show JWT token with copy button
- 🔄 Refresh token without re-login
- 👥 Display user roles
- 🔑 Display permissions from roles
- 🚪 Logout with token invalidation

## 🔌 API Endpoints Used

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/auth/token` | Login, get JWT token |
| GET | `/users/{userId}` | Get user info (requires auth) |
| POST | `/auth/refresh` | Refresh expired token |
| POST | `/auth/logout` | Invalidate token |

## 🔐 Security Notes

### Current Implementation (Development)
- JWT stored in `localStorage` (susceptible to XSS)
- CORS allows requests from `localhost`
- No HTTPS

### For Production
```javascript
// ✅ Good: Use httpOnly cookies
Set-Cookie: auth_token=...; HttpOnly; Secure; SameSite=Strict

// ❌ Avoid: localStorage (XSS vulnerable)
localStorage.setItem('auth_token', token)

// ✅ Add headers
Content-Security-Policy: default-src 'self'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000
```

## 📱 Responsive Breakpoints

- **Mobile:** < 480px (optimized layout)
- **Tablet:** 480px - 768px (adjusted spacing)
- **Desktop:** > 768px (full experience)

## 🎨 Design System

**Colors:**
- Primary Gradient: `#667eea` → `#764ba2` (purple)
- Background: `#f5f7fa` (light gray)
- Text: `#333` (dark gray)
- Border: `#e0e0e0` (light gray)
- Success: `#28a745` (green)
- Error: `#dc3545` (red)

**Typography:**
- Font: Segoe UI, Tahoma, Geneva, Verdana, sans-serif
- Regular: 14px
- Heading: 20-32px
- Code: Courier New, monospace

**Components:**
- Button: 14px height, 10px border-radius, gradient background
- Input: 12px padding, 10px border-radius, smooth focus transition
- Card: 15px border-radius, subtle shadow, white background

## 🧪 Testing API Locally

Use curl or Postman to test:

```bash
# 1. Login
curl -X POST http://localhost:8081/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'

# 2. Get user info (replace TOKEN and USERNAME)
curl -X GET http://localhost:8081/identity/users/testuser \
  -H "Authorization: Bearer TOKEN"

# 3. Refresh token
curl -X POST http://localhost:8081/identity/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"token": "TOKEN"}'

# 4. Logout
curl -X POST http://localhost:8081/identity/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"token": "TOKEN"}'
```

## 🐛 Troubleshooting

### "Cannot connect to server"
- ❌ Backend not running: Run `mvn spring-boot:run`
- ❌ Wrong port: Ensure it's running on `8081`
- ❌ CORS issue: Check `SecurityConfig` allows frontend requests

### "Login failed - User not found"
- Create the user via API first
- Check username spelling

### "Invalid token"
- Token expired (1 hour default): Use refresh button
- Token invalidated: Login again

### "CORS error in console"
- Backend not configured for CORS
- Check `SecurityConfig` public endpoints

## 📚 Related Documentation

- [CLAUDE.md](CLAUDE.md) - Full project architecture & commands
- [pom.xml](pom.xml) - Backend dependencies
- [application.yaml](src/main/resources/application.yaml) - Backend configuration

## 🎓 Learning Resources

- [Tailwind CSS](https://tailwindcss.com) - Used via CDN
- [Font Awesome Icons](https://fontawesome.com) - Icons used
- [JWT Tokens](https://jwt.io) - Understanding JWT
- [Spring Security](https://spring.io/projects/spring-security) - Backend auth

## 💡 Next Steps

- [ ] Add password reset functionality
- [ ] Implement email verification
- [ ] Add 2FA (two-factor authentication)
- [ ] Add profile edit page
- [ ] Add user management admin panel
- [ ] Convert to React/Vue for larger app
- [ ] Add dark mode toggle
- [ ] Add i18n (multi-language support)

---

**Happy coding!** 🚀

For more info, check [CLAUDE.md](CLAUDE.md)
