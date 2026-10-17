-- Link messages to an optional event and add the missing sender index.
--
-- messages was created in V1 without event_id; adding it here keeps existing rows
-- (NULL means a general message). ON DELETE SET NULL: deleting an event drops the
-- event context but keeps the thread readable.

ALTER TABLE messages
    ADD COLUMN event_id BIGINT NULL AFTER recipient_id,
    ADD CONSTRAINT fk_msg_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE SET NULL,
    ADD INDEX idx_msg_sender_id (sender_id);
