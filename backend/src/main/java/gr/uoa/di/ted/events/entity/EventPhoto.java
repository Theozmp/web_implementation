package gr.uoa.di.ted.events.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A photo associated with an event.
 *
 * Removed via SQL ON DELETE CASCADE when the parent event is deleted, so no JPA
 * cascade is needed on the Event side. url holds a path or external URL to the
 * stored image.
 */
@Entity
@Table(name = "event_photos")
@Getter
@Setter
@NoArgsConstructor
public class EventPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void prePersist() {
        uploadedAt = LocalDateTime.now();
    }
}
