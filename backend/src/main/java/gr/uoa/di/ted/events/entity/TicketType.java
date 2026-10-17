package gr.uoa.di.ted.events.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A pricing tier for an event (e.g. "General Admission", "VIP").
 *
 * quantity is the remaining seats for this tier; the booking service decrements it
 * under a SELECT ... FOR UPDATE lock (with @Version as a backstop) to avoid
 * overselling. Owned by Event via @OneToMany(cascade=ALL, orphanRemoval=true), so
 * ticket types are saved and deleted through the Event aggregate root.
 */
@Entity
@Table(name = "ticket_types")
@Getter
@Setter
@NoArgsConstructor
public class TicketType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // owning side of the Event/TicketType relationship. LAZY so listing ticket
    // types doesn't also pull the full event each time.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // remaining seats for this tier; decremented by the booking service inside a
    // locked transaction.
    @Column(nullable = false)
    private int quantity;

    // optimistic-lock version; see Event.version for the same pattern.
    @Version
    private Long version;
}
