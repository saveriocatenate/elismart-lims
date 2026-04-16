package it.elismart_lims.util;

import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExperimentSpecifications}.
 */
class ExperimentSpecificationsTest {

    @Test
    void constructor_shouldThrowException() throws Exception {
        Constructor<ExperimentSpecifications> constructor = ExperimentSpecifications.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(e.getCause().getMessage()).contains("Utility class");
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("specificationFilterCases")
    void buildSpecification_shouldReturnNonNullSpec(
            String testDescription,
            ExperimentSearchRequest request,
            String createdByFilter) {

        Specification<Experiment> spec = ExperimentSpecifications.buildSpecification(request, createdByFilter);

        assertThat(spec).isNotNull();
    }

    private static Stream<Arguments> specificationFilterCases() {
        LocalDateTime exact = LocalDateTime.of(2026, 4, 5, 10, 0);
        LocalDateTime from  = LocalDateTime.of(2026, 4, 1,  0, 0);
        LocalDateTime to    = LocalDateTime.of(2026, 4, 30, 23, 59);

        return Stream.of(
                Arguments.of("all null → empty predicate",
                        new ExperimentSearchRequest(null,  null,  null, null, null,  0, 20, false), null),

                Arguments.of("non-blank name filter",
                        new ExperimentSearchRequest("Run", null,  null, null, null,  0, 20, false), null),

                Arguments.of("blank name → ignored",
                        new ExperimentSearchRequest("   ", null,  null, null, null,  0, 20, false), null),

                Arguments.of("non-null status filter",
                        new ExperimentSearchRequest(null,  null,  null, null, ExperimentStatus.OK,  0, 20, false), null),

                Arguments.of("null status → ignored",
                        new ExperimentSearchRequest(null,  null,  null, null, null, 0, 20, false), null),

                Arguments.of("exact date filter",
                        new ExperimentSearchRequest(null,  exact, null, null, null,  0, 20, false), null),

                Arguments.of("dateFrom only",
                        new ExperimentSearchRequest(null,  null,  from, null, null,  0, 20, false), null),

                Arguments.of("dateTo only",
                        new ExperimentSearchRequest(null,  null,  null, to,   null,  0, 20, false), null),

                Arguments.of("dateFrom + dateTo range",
                        new ExperimentSearchRequest(null,  null,  from, to,   null,  0, 20, false), null),

                Arguments.of("combined name + range + status",
                        new ExperimentSearchRequest("Run", null,  from, to,   ExperimentStatus.OK,  0, 20, false), null),

                Arguments.of("exact date overrides dateFrom + dateTo",
                        new ExperimentSearchRequest(null,  exact, from, to,   null,  0, 20, false), null),

                Arguments.of("mine=false → createdByFilter null",
                        new ExperimentSearchRequest(null,  null,  null, null, null, 0, 20, false), null),

                Arguments.of("mine=true → createdByFilter non-null",
                        new ExperimentSearchRequest(null,  null,  null, null, null, 0, 20, true), "alice")
        );
    }
}
