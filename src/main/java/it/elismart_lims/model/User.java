package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Authenticated user of the EliSmart LIMS system.
 *
 * <p>The {@code password} field stores a BCrypt hash — the plaintext password is
 * never persisted. The table is named {@code app_user} to avoid conflicts with
 * {@code USER}, which is a reserved word in H2 and SQL standard.</p>
 *
 * <p>Audit columns ({@code created_at}, {@code updated_at}, {@code created_by})
 * are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends Auditable {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login name for this user.
     * Maximum 50 characters, matches the DB column constraint.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * BCrypt-hashed password. Never exposed in response DTOs.
     * The raw plaintext is discarded after hashing at registration time.
     */
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * Role that determines which endpoints this user may access.
     * Stored as its string name; constrained by a DB CHECK to the {@link UserRole} values.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /**
     * Whether this account is active.
     * Disabled accounts cannot authenticate.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User that = (User) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
