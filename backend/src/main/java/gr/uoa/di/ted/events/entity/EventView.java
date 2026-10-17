package gr.uoa.di.ted.events.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Records each view of an event detail page, by a user or an anonymous visitor.
 *
 * viewer is nullable (anonymous visits are tracked by ipAddress only). The FK is
 * ON DELETE SET NULL so deleting a user keeps the view stats the recommender uses.
 * ipAddress holds IPv4 or IPv6 (45 chars covers a full IPv6 address).
 */
@Entity
@Table(name = "event_views")
@Getter
@Setter
@NoArgsConstructor
public class EventView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // null means the view came from an unauthenticated visitor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id")
    private User viewer;

    @Column(nullable = false)
    private LocalDateTime viewedAt;

    @Column(length = 45)
    private String ipAddress;

    @PrePersist
    void prePersist() {
        viewedAt = LocalDateTime.now();
    }
}
