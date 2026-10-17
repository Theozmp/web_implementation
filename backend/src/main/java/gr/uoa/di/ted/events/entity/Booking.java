package gr.uoa.di.ted.events.entity;

import gr.uoa.di.ted.events.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A confirmed (or cancelled) seat reservation by a PARTICIPANT.
 *
 * Doesn't extend BaseEntity: it has only bookedAt (immutable) and no updatedAt;
 * cancellations are tracked through the status column.
 *
 * The booking service checks quantity <= ticketType.quantity (remaining seats),
 * decrements ticketType.quantity in an @Transactional method, and sets
 * totalPrice = quantity * ticketType.price.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id", nullable = false)
    private User attendee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(nullable = false)
    private LocalDateTime bookedAt;

    @PrePersist
    void prePersist() {
        bookedAt = LocalDateTime.now();
    }
}
