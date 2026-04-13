# Auth API

Base path: `/api/auth`

Authentication endpoints are public (no JWT required).

## Endpoints

### POST /api/auth/login

Authenticate a user and receive a signed JWT token.

**Request**:
```json
{
  "username": "analyst1",
  "password": "secret"
}
```

**Response 200**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "analyst1",
  "role": "ANALYST"
}
```

**Response 401**: invalid credentials (`BadCredentialsException`).

---

### POST /api/auth/register

Register a new user. **Requires ADMIN role** (JWT required).

**Request**:
```json
{
  "username": "reviewer1",
  "password": "s3cur3",
  "role": "REVIEWER"
}
```

**Fields**:
- `username` (String, required) — must be unique
- `password` (String, required) — stored BCrypt-hashed; minimum length enforced at application level
- `role` (String, required) — one of `ANALYST`, `REVIEWER`, `ADMIN`

**Response 201**:
```json
{
  "id": 5,
  "username": "reviewer1",
  "role": "REVIEWER"
}
```

**Response 409**: username already exists.

**Response 403**: caller does not have the ADMIN role.
