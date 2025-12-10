-- Migration script to split users table into user_auth and user_profiles
-- Run this script to migrate existing data from the old users table to the new split tables

-- Step 1: Create backup of existing users table
CREATE TABLE users_backup AS SELECT * FROM users;

-- Step 2: Migrate data to user_auth table
INSERT INTO user_auth (
    id,
    email,
    phone_number,
    password_hash,
    auth_token,
    refresh_token,
    token_expires_at,
    google_id,
    last_login_at,
    password_reset_token,
    password_reset_expires_at,
    email_verified,
    created_at,
    updated_at
)
SELECT 
    id,
    email,
    phone_number,
    NULL as password_hash,  -- Old table didn't have password_hash
    NULL as auth_token,      -- Old table didn't have auth_token
    NULL as refresh_token,   -- Old table didn't have refresh_token
    NULL as token_expires_at,
    NULL as google_id,       -- Old table didn't have google_id
    NULL as last_login_at,
    NULL as password_reset_token,
    NULL as password_reset_expires_at,
    false as email_verified,
    created_at,
    modified_at as updated_at
FROM users
ON CONFLICT (id) DO NOTHING;

-- Step 3: Migrate data to user_profiles table
INSERT INTO user_profiles (
    id,
    name,
    bio,
    profile_picture,
    company,
    role,
    is_admin,
    is_active,
    org_ids,
    team_ids,
    user_updated_fields,
    last_google_sync,
    created_at,
    updated_at
)
SELECT 
    id,
    first_name as name,
    bio,
    profile_picture,
    company,
    role,
    is_admin,
    true as is_active,  -- Default all existing users to active
    org_ids,
    team_ids,
    user_updated_fields,
    last_google_sync,
    created_at,
    modified_at as updated_at
FROM users
ON CONFLICT (id) DO NOTHING;

-- Step 4: Verify migration
-- Check counts match
DO $$
DECLARE
    users_count INTEGER;
    auth_count INTEGER;
    profile_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO users_count FROM users;
    SELECT COUNT(*) INTO auth_count FROM user_auth;
    SELECT COUNT(*) INTO profile_count FROM user_profiles;
    
    IF users_count != auth_count OR users_count != profile_count THEN
        RAISE EXCEPTION 'Migration count mismatch: users=%, auth=%, profiles=%', 
            users_count, auth_count, profile_count;
    END IF;
    
    RAISE NOTICE 'Migration successful: % users migrated', users_count;
END $$;

-- Step 5: Drop old users table (ONLY after verifying migration is successful)
-- UNCOMMENT THE FOLLOWING LINE AFTER VERIFYING THE MIGRATION
-- DROP TABLE users;

-- Step 6: Clean up backup table (ONLY after confirming everything works)
-- UNCOMMENT THE FOLLOWING LINE AFTER CONFIRMING EVERYTHING WORKS
-- DROP TABLE users_backup;

-- Notes:
-- 1. The old users table had these fields that are now in user_auth:
--    - email, phone_number
-- 2. The old users table had these fields that are now in user_profiles:
--    - first_name (now 'name'), bio, profile_picture, company, role, is_admin
--    - org_ids, team_ids, user_updated_fields, last_google_sync
-- 3. New fields in user_auth that didn't exist before:
--    - password_hash, auth_token, refresh_token, token_expires_at
--    - google_id, last_login_at, password_reset_token, password_reset_expires_at
--    - email_verified
-- 4. New fields in user_profiles that didn't exist before:
--    - is_active (defaulted to true for all existing users)

