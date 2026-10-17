package gr.uoa.di.ted.events.entity;

import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Registered user of the system.
 *
 * Role sets capabilities; status tracks the admin-approval workflow: PENDING moves
 * to ACTIVE on approval or REJECTED on rejection.
 *
 * mustChangePassword is true on the seeded admin (V2) and after an admin resets a
 * password; JwtAuthenticationFilter blocks everything except /auth/change-password
 * until it's cleared.
 *
 * Profile fields (phone, addressLine, ...) are nullable because the seeded admin
 * row was created in V2, before V3 added these columns.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    // never expose or log this; it's the BCrypt hash, not plaintext
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    // defaults to PENDING; admin accounts are seeded ACTIVE directly in SQL
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING;

    // forces a password change on the next successful login
    @Column(nullable = false)
    private boolean mustChangePassword = false;

    // profile fields added in V3; all nullable since the seeded admin predates them

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    // Greek Tax Identification Number (AFM); validated at the service layer
    @Column(length = 20)
    private String taxId;
}
