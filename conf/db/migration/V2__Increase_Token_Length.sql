-- Change token columns to TEXT to support long JWTs
ALTER TABLE user_auth ALTER COLUMN auth_token TYPE TEXT;
ALTER TABLE user_auth ALTER COLUMN refresh_token TYPE TEXT;
ALTER TABLE user_auth ALTER COLUMN password_reset_token TYPE TEXT;
