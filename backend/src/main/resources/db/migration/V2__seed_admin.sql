-- V2: seed the initial administrator account.
--
-- Default login (override via env vars before any real deployment): admin /
-- Admin@123456, set ADMIN_INITIAL_PASSWORD at runtime. The hash below was made with
-- Python's bcrypt (rounds=12); BCryptPasswordEncoder.matches() accepts the $2b$
-- prefix, which verifies the same as $2a$.
--
-- must_change_password = 1 makes the login flow redirect the admin to the
-- change-password screen before anything else.
INSERT INTO users (username, email, password_hash, first_name, last_name,
                   role, status, must_change_password, created_at, updated_at)
VALUES ('admin',
        'admin@events.local',
        '$2b$12$2TJ6.nJl93/6d2I8v.b1vu8cHoDLY0R5sEi3vVW/WZmlO0qnal9ZG',
        'System', 'Administrator',
        'ADMIN', 'ACTIVE', 1,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
