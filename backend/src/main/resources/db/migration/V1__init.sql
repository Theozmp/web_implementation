-- V1: initial schema for the event reservation system.
--
-- Notes:
-- enum-like columns are VARCHAR(30) so Hibernate's EnumType.STRING validates
-- cleanly without MySQL's ENUM type. Datetime columns are DATETIME(6), which maps
-- to LocalDateTime without timezone issues. version columns start at 0 and
-- Hibernate bumps them on each UPDATE for optimistic locking. FK column names are
-- the snake_case Spring derives from the entity fields (organizerId -> organizer_id).

SET NAMES utf8mb4;

-- users
-- roles: ADMIN, ORGANIZER, PARTICIPANT
-- status: PENDING then ACTIVE (approved) or REJECTED
-- must_change_password forces a new-password prompt on login
CREATE TABLE users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    username             VARCHAR(50)  NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    role                 VARCHAR(30)  NOT NULL,
    status               VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    must_change_password TINYINT(1)   NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email    (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- categories (reference data, seeded at the bottom of this file)
CREATE TABLE categories (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- events
-- statuses: DRAFT, PUBLISHED, then CANCELLED or COMPLETED
-- capacity is the total seat/ticket ceiling for the event.
-- version is Hibernate's optimistic-lock counter.
CREATE TABLE events (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(255)  NOT NULL,
    description    TEXT,
    organizer_id   BIGINT        NOT NULL,
    status         VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    start_datetime DATETIME(6)   NOT NULL,
    end_datetime   DATETIME(6)   NOT NULL,
    venue_name     VARCHAR(255),
    address        VARCHAR(255),
    city           VARCHAR(100),
    country        VARCHAR(100),
    latitude       DECIMAL(10,8),           -- GPS latitude  (-90 to +90)
    longitude      DECIMAL(11,8),           -- GPS longitude (-180 to +180)
    capacity       INT           NOT NULL,
    thumbnail_url  VARCHAR(500),
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES users (id),
    INDEX idx_events_start_datetime (start_datetime),
    INDEX idx_events_city           (city),
    INDEX idx_events_status         (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- event_categories (M:N join table between events and categories)
-- ON DELETE CASCADE: removing an event cleans up its category links.
CREATE TABLE event_categories (
    event_id    BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (event_id, category_id),
    CONSTRAINT fk_ec_event    FOREIGN KEY (event_id)    REFERENCES events     (id) ON DELETE CASCADE,
    CONSTRAINT fk_ec_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ticket_types
-- each event has one or more tiers (e.g. General Admission, VIP).
-- the SUM(quantity) <= events.capacity invariant lives in the service layer, not a
-- trigger, since atomic multi-row checks would need serialisable isolation and hurt
-- concurrency. version is the optimistic lock used in the booking flow.
CREATE TABLE ticket_types (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    event_id   BIGINT         NOT NULL,
    name       VARCHAR(100)   NOT NULL,
    price      DECIMAL(10,2)  NOT NULL,
    quantity   INT            NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at DATETIME(6)    NOT NULL,
    updated_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tt_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    INDEX idx_tt_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- bookings
-- no ON DELETE CASCADE: bookings outlive a cancelled event, kept for audit/refunds.
-- the booking service adjusts ticket_types.quantity atomically.
CREATE TABLE bookings (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    event_id       BIGINT         NOT NULL,
    attendee_id    BIGINT         NOT NULL,
    ticket_type_id BIGINT         NOT NULL,
    quantity       INT            NOT NULL,
    total_price    DECIMAL(10,2)  NOT NULL,
    status         VARCHAR(30)    NOT NULL DEFAULT 'CONFIRMED',
    booked_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bookings_event       FOREIGN KEY (event_id)       REFERENCES events       (id),
    CONSTRAINT fk_bookings_attendee    FOREIGN KEY (attendee_id)    REFERENCES users        (id),
    CONSTRAINT fk_bookings_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id),
    INDEX idx_bookings_event_id    (event_id),
    INDEX idx_bookings_attendee_id (attendee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- event_photos
-- ON DELETE CASCADE: photos are owned by the event.
CREATE TABLE event_photos (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    event_id    BIGINT       NOT NULL,
    url         VARCHAR(500) NOT NULL,
    uploaded_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ep_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    INDEX idx_ep_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- messages (soft-delete via deleted_by_sender / deleted_by_recipient)
-- a row stays until both parties delete it.
-- read_at is NULL until the recipient opens the message.
CREATE TABLE messages (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    sender_id            BIGINT       NOT NULL,
    recipient_id         BIGINT       NOT NULL,
    subject              VARCHAR(255) NOT NULL,
    body                 TEXT         NOT NULL,
    sent_at              DATETIME(6)  NOT NULL,
    read_at              DATETIME(6),              -- NULL means unread
    deleted_by_sender    TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_by_recipient TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_msg_sender    FOREIGN KEY (sender_id)    REFERENCES users (id),
    CONSTRAINT fk_msg_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),
    INDEX idx_msg_recipient_id (recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- event_views (used by the Biased MF recommender)
-- viewer_id is nullable: anonymous visitors are tracked by IP only.
-- ON DELETE SET NULL on viewer keeps view stats when a user is deleted.
-- ON DELETE CASCADE on event: views are meaningless without the event.
CREATE TABLE event_views (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    event_id   BIGINT      NOT NULL,
    viewer_id  BIGINT,               -- NULL = anonymous visitor
    viewed_at  DATETIME(6) NOT NULL,
    ip_address VARCHAR(45),          -- fits IPv4 and IPv6 addresses
    PRIMARY KEY (id),
    CONSTRAINT fk_ev_event  FOREIGN KEY (event_id)  REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_ev_viewer FOREIGN KEY (viewer_id) REFERENCES users  (id) ON DELETE SET NULL,
    INDEX idx_ev_event_id  (event_id),
    INDEX idx_ev_viewer_id (viewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- seed: starter categories
INSERT INTO categories (name) VALUES
    ('Music'),
    ('Sports'),
    ('Theatre'),
    ('Conference'),
    ('Workshop'),
    ('Exhibition'),
    ('Festival'),
    ('Comedy'),
    ('Family'),
    ('Other');
