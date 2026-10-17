package gr.uoa.di.ted.events.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Immutable record of an admin action on a user account (approve or reject).
 *
 * Does NOT extend BaseEntity: audit log rows are never updated, so there is no
 * updatedAt column. createdAt is set via @PrePersist to avoid an @EnableJpaAuditing
 * dependency here.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    /** "APPROVE" or "REJECT". Stored as a plain string so no migration is needed to add actions. */
    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }

    public static AdminAuditLog of(User admin, User target, String action, String reason) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.admin  = admin;
        entry.target = target;
        entry.action = action;
        entry.reason = reason;
        return entry;
    }
}
