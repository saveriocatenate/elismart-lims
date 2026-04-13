# User API

Base path: `/api/users`

All endpoints require ADMIN role.

## Endpoints

### GET /api/users

List all registered users.

**Response 200**:
```json
[
  { "id": 1, "username": "admin",    "role": "ADMIN"    },
  { "id": 2, "username": "analyst1", "role": "ANALYST"  },
  { "id": 3, "username": "reviewer", "role": "REVIEWER" }
]
```

---

### PUT /api/users/{id}/role

Change the role of an existing user.

**Path params**:
- `id` (Long) — the user ID

**Request**:
```json
{
  "role": "REVIEWER"
}
```

**Response 200**: updated `UserResponse` with new role.

**Response 404**: user not found.

**Response 400**: invalid role value.

---

### DELETE /api/users/{id}

Delete a user from the system. Cannot delete your own account.

**Path params**:
- `id` (Long) — the user ID

**Response 204**: deleted successfully.

**Response 404**: user not found.

**Response 400**: attempt to delete the currently authenticated user.
