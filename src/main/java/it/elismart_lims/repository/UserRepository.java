package it.elismart_lims.repository;

import it.elismart_lims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique login name.
     *
     * @param username the login name to search for
     * @return an {@link Optional} containing the user if found, or empty if no match
     */
    Optional<User> findByUsername(String username);
}
