package gr.uoa.di.ted.events.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Event category reference data (Music, Sports, Theatre, ...). No audit
 * timestamps; it's static data seeded by Flyway and not expected to change at
 * runtime. No back-reference to Event: the @ManyToMany is unidirectional and owned
 * by Event, and Category never needs to walk to its events.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;
}
