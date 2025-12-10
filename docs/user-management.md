# User Management API

## Overview

The User Management API provides endpoints for administrators to manage users, assign them to organizations and teams, and update their roles and permissions.

## User Model

```json
{
  "id": "user-123",
  "email": "user@example.com",
  "name": "John Doe",
  "profilePicture": "https://example.com/avatar.jpg",
  "phoneNumber": "+1234567890",
  "company": "Acme Corp",
  "role": "user",
  "bio": "Software Engineer",
  "isAdmin": false,
  "orgId": "org-456",
  "teamId": "team-789",
  "userUpdatedFields": ["name", "phoneNumber"],
  "lastGoogleSync": 1640000000000,
  "createdAt": 1640000000000,
  "modifiedAt": 1640000000000
}
```

## User Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| `user` | Standard user | Can create and manage flows, executions, test suites |
| `admin` | Administrator | All user permissions + user management, resource management |
| `viewer` | Read-only user | Can view flows and executions but cannot create or modify |

## API Endpoints

### Get All Users

Retrieve a paginated list of all users with optional search functionality.

**Endpoint**: `GET /api/v1/users`

**Query Parameters**:
- `search` (optional): Search term to filter users by name or email
- `limit` (optional, default: 50): Number of results per page (max: 100)
- `page` (optional, default: 0): Page number (0-indexed)

**Permissions Required**: `users:read` (Admin only)

**Example Request**:
```bash
curl -X GET "http://localhost:9000/api/v1/users?search=john&limit=20&page=0" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Example Response**:
```json
{
  "data": [
    {
      "id": "user-123",
      "email": "john@example.com",
      "name": "John Doe",
      "phoneNumber": "+1234567890",
      "orgId": "org-456",
      "teamId": "team-789",
      "role": "user",
      "isAdmin": false,
      "createdAt": 1640000000000,
      "modifiedAt": 1640000000000
    }
  ],
  "pagination": {
    "page": 0,
    "limit": 20,
    "total": 1
  }
}
```

### Get User by ID

Retrieve a specific user by their ID.

**Endpoint**: `GET /api/v1/users/:id`

**Permissions Required**: `users:read` (Admin only)

**Example Request**:
```bash
curl -X GET "http://localhost:9000/api/v1/users/user-123" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Example Response**:
```json
{
  "id": "user-123",
  "email": "john@example.com",
  "name": "John Doe",
  "phoneNumber": "+1234567890",
  "orgId": "org-456",
  "teamId": "team-789",
  "role": "user",
  "isAdmin": false,
  "createdAt": 1640000000000,
  "modifiedAt": 1640000000000
}
```

### Update User

Update user metadata including organization, team, role, and admin status.

**Endpoint**: `PUT /api/v1/users/:id`

**Permissions Required**: `users:update` (Admin only)

**Request Body**:
```json
{
  "name": "John Smith",
  "phoneNumber": "+1234567890",
  "orgId": "org-456",
  "teamId": "team-789",
  "role": "admin",
  "isAdmin": true,
  "isActive": true
}
```

**All fields are optional** - only provided fields will be updated.

**Example Request**:
```bash
curl -X PUT "http://localhost:9000/api/v1/users/user-123" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orgId": "org-456",
    "teamId": "team-789",
    "role": "admin",
    "isAdmin": true
  }'
```

**Example Response**:
```json
{
  "id": "user-123",
  "email": "john@example.com",
  "name": "John Smith",
  "phoneNumber": "+1234567890",
  "orgId": "org-456",
  "teamId": "team-789",
  "role": "admin",
  "isAdmin": true,
  "createdAt": 1640000000000,
  "modifiedAt": 1640000000001
}
```

## Database Schema

The user data is stored in a single `users` table with the following structure:

```sql
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    bio TEXT,
    profile_picture VARCHAR(500),
    phone_number VARCHAR(50),
    company VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    org_id VARCHAR(255),
    team_id VARCHAR(255),
    user_updated_fields TEXT NOT NULL DEFAULT '[]',
    last_google_sync BIGINT,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL,
    FOREIGN KEY (org_id) REFERENCES organisations(id) ON DELETE SET NULL,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL
);
```

### Indexes

- `idx_users_email` - Fast lookup by email
- `idx_users_name` - Search by name
- `idx_users_org_id` - Filter by organization
- `idx_users_team_id` - Filter by team
- `idx_users_role` - Filter by role
- `idx_users_created_at` - Sort by creation date

## Use Cases

### Assign User to Organization and Team

```bash
curl -X PUT "http://localhost:9000/api/v1/users/user-123" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orgId": "org-456",
    "teamId": "team-789"
  }'
```

### Promote User to Admin

```bash
curl -X PUT "http://localhost:9000/api/v1/users/user-123" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "role": "admin",
    "isAdmin": true
  }'
```

### Search for Users

```bash
# Search by name
curl -X GET "http://localhost:9000/api/v1/users?search=john" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Search by email
curl -X GET "http://localhost:9000/api/v1/users?search=@example.com" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Deactivate User

```bash
curl -X PUT "http://localhost:9000/api/v1/users/user-123" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "isActive": false
  }'
```

## Error Responses

### User Not Found
```json
{
  "error": "User not found: user-123"
}
```
**Status Code**: 404

### Invalid Role
```json
{
  "error": "Invalid role: superadmin. Valid roles are: admin, user, viewer"
}
```
**Status Code**: 400

### Insufficient Permissions
```json
{
  "error": "Insufficient permissions"
}
```
**Status Code**: 403

## Security Considerations

1. **Admin-Only Access**: All user management endpoints require admin permissions
2. **Organization Isolation**: Users can only see and manage users within their organization (future enhancement)
3. **Audit Trail**: All user updates are logged with timestamps
4. **Role Validation**: Role changes are validated against allowed values

## Best Practices

1. **Assign Organizations First**: Create organizations before assigning users
2. **Team Hierarchy**: Ensure teams belong to the correct organization
3. **Role Management**: Use the least privileged role necessary
4. **Regular Audits**: Periodically review user roles and permissions
5. **Search Optimization**: Use specific search terms for better performance
