package it.elismart_lims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify that the application context loads successfully.
 * Uses the {@code test} profile so it connects to the isolated test database
 * rather than the development data file.
 */
@SpringBootTest
@ActiveProfiles("test")
class EliSmartLimsApplicationTest {

    @Test
    void contextLoads() {
    }
}
