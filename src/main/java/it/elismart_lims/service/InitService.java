package it.elismart_lims.service;

import it.elismart_lims.model.User;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the application with a default admin user if no users exist yet.
 *
 * <p>Runs once at startup via {@link CommandLineRunner}. If the {@code app_user}
 * table is empty, a user with username {@code admin} and a BCrypt-hashed password
 * {@code admin} is created with the {@link UserRole#ADMIN} role.</p>
 *
 * <p><strong>The default password must be changed immediately after first login.</strong>
 * A prominent warning is logged to the console on every creation.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the default admin user if no users are present in the database.
     *
     * @param args application startup arguments (unused)
     */
    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin"))
                    .role(UserRole.ADMIN)
                    .enabled(true)
                    .build();

            userRepository.save(admin);

            log.warn("*************************************************************");
            log.warn("* Default admin user created. Username: admin               *");
            log.warn("* CHANGE PASSWORD IMMEDIATELY via PUT /api/users/{id}/role  *");
            log.warn("*************************************************************");
        }
    }
}
