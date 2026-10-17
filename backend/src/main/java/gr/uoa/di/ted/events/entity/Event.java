package gr.uoa.di.ted.events.entity;

import gr.uoa.di.ted.events.enums.EventStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An event created by an ORGANIZER.
 *
 * Relationships: organizer is a LAZY ManyToOne (don't load the full User per
 * event query); categories is a LAZY unidirectional ManyToMany with Event owning
 * the join table; ticketTypes is a OneToMany with cascade=ALL and orphanRemoval,
 * so the event is the aggregate root and list edits insert/delete child rows.
 *
 * @Version gives optimistic locking: concurrent updates to the same event detect
 * the conflict and retry instead of silently overwriting each other.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // LAZY is mandatory: eager-loading the organizer would add a SELECT per row
    // on every event list query (N+1).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false)
    private LocalDateTime startDatetime;

    @Column(nullable = false)
    private LocalDateTime endDatetime;

    private String venueName;
    private String address;
    private String city;
    private String country;

    // DECIMAL(10,8)/DECIMAL(11,8) in MySQL, enough precision for OpenStreetMap/Leaflet
    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 500)
    private String thumbnailUrl;

    // Hibernate bumps this on every UPDATE; used to catch concurrent modification.
    @Version
    private Long version;

    // LinkedHashSet keeps insertion order and prevents duplicate categories.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "event_categories",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new LinkedHashSet<>();

    // cascade=ALL propagates save/delete to ticket types; orphanRemoval deletes a
    // TicketType from the DB once it's removed from this list.
    @OneToMany(mappedBy = "event",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<TicketType> ticketTypes = new ArrayList<>();

    // convenience helpers

    /** Keeps the owning side (TicketType.event) in sync when adding a ticket type. */
    public void addTicketType(TicketType ticketType) {
        ticketTypes.add(ticketType);
        ticketType.setEvent(this);
    }

    public void removeTicketType(TicketType ticketType) {
        ticketTypes.remove(ticketType);
        ticketType.setEvent(null);
    }
}
