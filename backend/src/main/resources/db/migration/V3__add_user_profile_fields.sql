-- V3: add the user profile fields the registration form needs.
--
-- All new columns are nullable so the seeded admin (V2) is unaffected; it predates
-- these fields and has no profile data to back-fill.
ALTER TABLE users
    ADD COLUMN phone        VARCHAR(30),
    ADD COLUMN address_line VARCHAR(255),
    ADD COLUMN city         VARCHAR(100),
    ADD COLUMN country      VARCHAR(100),
    ADD COLUMN latitude     DECIMAL(10,8),   -- GPS latitude  (-90 to +90)
    ADD COLUMN longitude    DECIMAL(11,8),   -- GPS longitude (-180 to +180)
    ADD COLUMN tax_id       VARCHAR(20);     -- Greek AFM, validated at the service layer
