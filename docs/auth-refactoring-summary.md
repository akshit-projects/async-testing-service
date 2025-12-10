# Authentication System Refactoring Summary

## ✅ Completed Tasks

### 1. Controller Consolidation
- **Merged** `AuthController` and `AuthenticationController` into a single `AuthController`
- **Removed** duplicate `AuthenticationController.scala`
- **Consolidated** all authentication endpoints into one controller

### 2. Optimized Database Operations
Added optimized repository methods to reduce DB calls:

#### UserAuthRepository
- `updateAuthTokenAndLastLogin()` - Combines token update and last login timestamp in one query
- `updatePasswordAndClearResetToken()` - Updates password and clears reset token in one query

#### Updated Services
- `UserAuthenticationService.loginWithEmail()` - Now uses `updateAuthTokenAndLastLogin()`
- `UserAuthenticationService.resetPassword()` - Now uses `updatePasswordAndClearResetToken()`

### 3. Routes Configuration
Updated `conf/routes` with consolidated authentication endpoints:

```
# Authentication endpoints
POST     /api/v1/auth/google             # Google OAuth login
POST     /api/v1/auth/login              # Email/password login
POST     /api/v1/auth/register           # User registration
POST     /api/v1/auth/forgot-password    # Request password reset
POST     /api/v1/auth/reset-password     # Reset password with token
POST     /api/v1/auth/refresh            # Refresh access token
GET      /api/v1/auth/profile            # Get current user profile
PUT      /api/v1/auth/profile            # Update current user profile

# User Management endpoints (Admin only)
GET      /api/v1/users                   # List all users (paginated)
GET      /api/v1/users/:id               # Get user by ID
PUT      /api/v1/users/:id               # Update user (admin)
```

### 4. Database Schema
Split user data into two tables:
- `user_auth` - Sensitive data (email, phone, password_hash, tokens, etc.)
- `user_profiles` - Non-sensitive data (name, bio, company, role, org_ids, team_ids, etc.)

### 5. Domain Models
Created privacy-aware user models:
- `UserAuth` - Internal only, never exposed
- `UserProfile` - Non-sensitive profile data
- `PublicUserProfile` - Minimal data for public APIs (no PII)
- `DetailedUserProfile` - Admin view with email but no sensitive data

### 6. Services Created
- `UserAuthenticationService` - Email/password authentication, registration, password reset
- `UserManagementService` - Admin user management (updated to use split tables)

### 7. Security Utilities
- `PasswordHasher` - BCrypt password hashing (12 rounds)
- `TokenGenerator` - JWT token generation and verification

---

## ⚠️ Remaining Issues

### 1. **CRITICAL: Old UserRepository Still in Use**

The `AuthServiceImpl` still uses the old `UserRepository` which references the old `users` table that no longer exists. This affects:

**Files that need updating:**
- `app/ab/async/tester/service/auth/AuthServiceImpl.scala`
- `library/src/main/scala/ab/async/tester/library/repository/user/UserRepository.scala`
- `library/src/main/scala/ab/async/tester/library/repository/user/UserRepositoryImpl.scala`

**Methods affected:**
- `loginUser()` - Google OAuth login
- `upsertUser()` - Creates/updates user from Google claims
- `updateUserProfile()` - Updates user profile
- `getUserProfile()` - Gets user profile
- `adminUpdateUser()` - Admin user updates

### 2. **User Model Inconsistency**

The old `User` model is still being used in:
- `AuthService` interface
- `AuthServiceImpl` implementation
- `AuthController.googleLogin()` method
- `UserRepository` and `UserRepositoryImpl`

**Decision needed:**
- Should we keep the old `User` model for backward compatibility?
- Or migrate everything to use `UserAuth` + `UserProfile`?

### 3. **Missing Database Migration**

The old `users` table still exists in the schema. We need to:
1. Migrate existing data from `users` table to `user_auth` and `user_profiles`
2. Drop the old `users` table
3. Update all code references

---

## 🔧 Recommended Next Steps

### Option A: Complete Migration (Recommended)

1. **Update AuthServiceImpl to use split tables:**
   ```scala
   class AuthServiceImpl @Inject()(
     config: Configuration,
     wsClient: WSClient,
     userAuthRepository: UserAuthRepository,
     userProfileRepository: UserProfileRepository
   )
   ```

2. **Rewrite Google OAuth login flow:**
   - Check if user exists in `user_auth` by email
   - If exists, update `user_profiles` with Google data
   - If new, create entries in both `user_auth` and `user_profiles`
   - Return combined user data

3. **Update AuthService interface:**
   ```scala
   trait AuthService {
     def loginUser(loginRequest: LoginRequest): Future[DetailedUserProfile]
     def updateUserProfile(userId: String, updateRequest: UpdateProfileRequest): Future[PublicUserProfile]
     def getUserProfile(userId: String): Future[Option[PublicUserProfile]]
   }
   ```

4. **Create database migration script:**
   ```sql
   -- Migrate data from users to user_auth and user_profiles
   INSERT INTO user_auth (id, email, phone_number, created_at, updated_at)
   SELECT id, email, phone_number, created_at, modified_at FROM users;
   
   INSERT INTO user_profiles (id, name, bio, profile_picture, company, role, is_admin, 
                              org_ids, team_ids, user_updated_fields, last_google_sync, 
                              created_at, updated_at)
   SELECT id, first_name, bio, profile_picture, company, role, is_admin,
          org_ids, team_ids, user_updated_fields, last_google_sync,
          created_at, modified_at FROM users;
   
   -- Drop old table
   DROP TABLE users;
   ```

5. **Remove old UserRepository:**
   - Delete `UserRepository.scala`
   - Delete `UserRepositoryImpl.scala`
   - Update all imports

### Option B: Gradual Migration

1. **Keep both systems temporarily:**
   - Maintain old `UserRepository` for Google OAuth
   - Use new repositories for email/password auth
   - Gradually migrate features

2. **Add adapter layer:**
   - Create methods to convert between `User` and `UserAuth`+`UserProfile`
   - Sync data between old and new tables

---

## 📊 Performance Improvements

### Database Operations Reduced

**Before optimization:**
- Login: 3 DB queries (find user, update token, update last login)
- Password reset: 3 DB queries (find by token, update password, clear token)

**After optimization:**
- Login: 2 DB queries (find user, update token + last login)
- Password reset: 2 DB queries (find by token, update password + clear token)

**Savings:** ~33% reduction in DB operations for these flows

---

## 🔒 Security Improvements

1. **Data Separation:**
   - Sensitive data (passwords, tokens) isolated in `user_auth`
   - Profile data in separate table
   - Easier to apply different security policies

2. **API Response Safety:**
   - `PublicUserProfile` - No PII exposed
   - `DetailedUserProfile` - Email only (admin APIs)
   - `UserAuth` - Never exposed in responses

3. **Password Security:**
   - BCrypt with 12 rounds
   - Password reset tokens expire after 24 hours
   - Tokens cleared after successful reset

---

## 📝 Testing Checklist

- [ ] Test Google OAuth login flow
- [ ] Test email/password login
- [ ] Test user registration
- [ ] Test password reset flow
- [ ] Test token refresh
- [ ] Test user profile updates
- [ ] Test admin user management
- [ ] Verify no sensitive data in API responses
- [ ] Test database migration script
- [ ] Load test optimized DB operations

---

## 🚨 Breaking Changes

1. **API Endpoints Changed:**
   - `/api/v1/login` → `/api/v1/auth/google`
   - `/api/v1/profile` → `/api/v1/auth/profile`
   - `/api/v1/admin/users` → Removed (use `/api/v1/users/:id` instead)

2. **Response Format:**
   - User responses now return `PublicUserProfile` or `DetailedUserProfile`
   - No longer include sensitive fields like `phoneNumber` in public APIs

3. **Database Schema:**
   - `users` table split into `user_auth` and `user_profiles`
   - Requires data migration

---

## 📚 Additional Documentation Needed

1. API documentation for new authentication endpoints
2. Migration guide for existing users
3. Security best practices guide
4. Admin user management guide

