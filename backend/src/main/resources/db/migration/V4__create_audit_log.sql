-- Tracks approve / reject actions taken by admins on user accounts.
-- admin_id  = the admin who performed the action
-- target_id = the user whose account was changed
-- action    = 'APPROVE' | 'REJECT'
-- reason    = optional free-text reason (stored only for REJECT actions)
CREATE TABLE admin_audit_log (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    admin_id   BIGINT       NOT NULL,
    target_id  BIGINT       NOT NULL,
    action     VARCHAR(30)  NOT NULL,
    reason     VARCHAR(500),
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_audit_admin  FOREIGN KEY (admin_id)  REFERENCES users(id),
    CONSTRAINT fk_audit_target FOREIGN KEY (target_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
