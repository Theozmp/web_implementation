package gr.uoa.di.ted.events.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


/**
 * An in-app message from one user to another.
 *
 * Soft delete: deleting on one side just sets deletedBySender or
 * deletedByRecipient; the row is only physically removed once both flags are true.
 * readAt is null until the recipient first opens the message.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    // null means a general message not tied to any event.
    // ON DELETE SET NULL in the DB, so the thread survives event deletion.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    // null = unread; set when the recipient first opens the message
    private LocalDateTime readAt;

    @Column(nullable = false)
    private boolean deletedBySender = false;

    @Column(nullable = false)
    private boolean deletedByRecipient = false;

    @PrePersist
    void prePersist() {
        sentAt = LocalDateTime.now();
    }
}
