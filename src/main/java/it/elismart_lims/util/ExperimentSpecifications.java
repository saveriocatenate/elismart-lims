package it.elismart_lims.util;

import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.model.Experiment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building JPA Specifications used in dynamic experiment search queries.
 */
public final class ExperimentSpecifications {

    private ExperimentSpecifications() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a Specification that filters experiments by the criteria in the request.
     *
     * @param request the search criteria
     * @return a composite AND Specification of all non-null predicates
     */
    public static Specification<Experiment> buildSpecification(ExperimentSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.name() != null && !request.name().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + request.name().toLowerCase() + "%"));
            }

            if (request.status() != null && !request.status().isBlank()) {
                predicates.add(cb.equal(root.get("status"), request.status()));
            }

            LocalDateTime exactDate = request.date();
            LocalDateTime dateFrom = request.dateFrom();
            LocalDateTime dateTo = request.dateTo();

            if (exactDate != null) {
                predicates.add(cb.equal(root.get("date"), exactDate));
            } else {
                if (dateFrom != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("date"), dateFrom));
                }
                if (dateTo != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("date"), dateTo));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
