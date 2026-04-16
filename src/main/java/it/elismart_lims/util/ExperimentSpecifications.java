package it.elismart_lims.util;

import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
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
     * @param request           the search criteria
     * @param createdByFilter   when non-{@code null}, restricts results to experiments whose
     *                          {@code createdBy} field equals this value; pass {@code null}
     *                          to skip the owner filter (show all experiments)
     * @return a composite AND Specification of all non-null predicates
     */
    public static Specification<Experiment> buildSpecification(
            ExperimentSearchRequest request, String createdByFilter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.name() != null && !request.name().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + request.name().toLowerCase() + "%"));
            }

            ExperimentStatus status = request.status();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
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

            if (createdByFilter != null) {
                predicates.add(cb.equal(root.get("createdBy"), createdByFilter));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
