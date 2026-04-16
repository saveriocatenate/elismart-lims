package it.elismart_lims.service;

import it.elismart_lims.model.User;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Bootstraps the application with a default admin user on first boot.
 *
 * <p>Runs once at startup via {@link CommandLineRunner}. If the {@code app_user}
 * table is empty, a user with username {@code admin} is created with role
 * {@link UserRole#ADMIN}.</p>
 *
 * <p><strong>Password resolution order:</strong></p>
 * <ol>
 *   <li>If the {@code ADMIN_PASSWORD} environment variable is set and non-empty,
 *       that value is used (BCrypt-hashed before storage).</li>
 *   <li>Otherwise a cryptographically random 16-character password is generated
 *       using {@link SecureRandom} and printed <em>only</em> to {@code stdout}
 *       (never to the log file).</li>
 * </ol>
 *
 * <p>The generated or configured password is printed to {@code System.out} in a
 * visible ASCII box so it can be captured from the console at first boot.
 * The password string is <strong>never</strong> passed to {@link org.slf4j.Logger}.</p>
 *
 * <p>If the database already contains at least one user, this service is a no-op.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitService implements CommandLineRunner {

    /** Characters used for random password generation — no ambiguous glyphs (0/O, 1/l/I). */
    private static final String ALPHABET =
            "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the default admin user if no users are present in the database.
     *
     * @param args application startup arguments (unused)
     */
    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        String password = resolveAdminPassword();

        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .enabled(true)
                .build();

        userRepository.save(admin);

        printCredentials(password);
        log.info("Admin user created on first boot. Password was printed to stdout.");
    }

    /**
     * Returns the admin password to use on first boot.
     *
     * <p>Reads {@code ADMIN_PASSWORD} from the environment. If the variable is
     * absent or blank, a random 16-character password is generated and returned.</p>
     *
     * <p>Package-private for testability — tests subclass this method to inject
     * a fixed password without setting environment variables.</p>
     *
     * @return plaintext password (not yet hashed)
     */
    String resolveAdminPassword() {
        String envPassword = System.getenv("ADMIN_PASSWORD");
        if (envPassword != null && !envPassword.isBlank()) {
            return envPassword;
        }
        return generateRandomPassword();
    }

    /**
     * Generates a cryptographically random 16-character password formatted as
     * four groups of four characters separated by hyphens (e.g. {@code aK7x-mP2q-Rn4w-Ys8v}).
     *
     * <p>Characters are drawn from {@link #ALPHABET}, which excludes visually
     * ambiguous glyphs ({@code 0}, {@code O}, {@code 1}, {@code l}, {@code I}).</p>
     *
     * <p>Package-private for testability.</p>
     *
     * @return generated password string
     */
    String generateRandomPassword() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(19); // 16 chars + 3 hyphens
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Prints the first-boot credentials to {@code System.out} inside a visible
     * ASCII box.
     *
     * <p>This method intentionally uses {@code System.out.println} rather than
     * the SLF4J logger so the password never ends up in log files, which may be
     * readable by other system users or shipped to log aggregators.</p>
     *
     * @param password the plaintext admin password to display
     */
    private void printCredentials(String password) {
        // Password line content (between the box walls):
        //   "  Password: " (12 chars) + password (up to 19 chars) = up to 31 chars
        // Box interior width is 62 chars (between ║ and ║).
        String passwordLine = "  Password: " + password;
        int boxWidth = 62;
        int padding = boxWidth - passwordLine.length();
        String paddedPassword = passwordLine + " ".repeat(Math.max(0, padding));

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PRIMO AVVIO — Utente admin creato                          ║");
        System.out.println("║  Username: admin                                            ║");
        System.out.println("║" + paddedPassword + "║");
        System.out.println("║  Salvala adesso, non verrà mostrata di nuovo.               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}
