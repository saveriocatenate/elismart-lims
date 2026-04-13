package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted record of a single AI analysis call, linking the user's question, the AI-generated
 * response, and the experiments that were analyzed.
 *
 * <p>This entity does NOT extend {@link Auditable}: it uses its own explicit {@code generatedAt}
 * and {@code generatedBy} fields, since those values are set once at creation and never updated.
 * The entity is append-only — no UPDATE operations are expected on it.</p>
 */
@Entity
@Table(name = "ai_insight")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsight {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The analyst's free-text question submitted to the AI.
     * Stored as CLOB to support arbitrarily long questions, including optional context blocks.
     */
    @Column(name = "user_question", nullable = false, columnDefinition = "CLOB")
    private String userQuestion;

    /**
     * The full text response produced by the Gemini model.
     * Stored as CLOB since AI responses can easily exceed 4 000 characters.
     */
    @Column(name = "ai_response", nullable = false, columnDefinition = "CLOB")
    private String aiResponse;

    /** Timestamp when this insight was generated. Set once at service layer — never supplied by caller. */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    /**
     * Username of the principal who triggered this analysis.
     * Populated from {@link org.springframework.security.core.context.SecurityContextHolder}.
     */
    @Column(name = "generated_by", nullable = false, length = 100)
    private String generatedBy;

    /**
     * Experiments that were included in this analysis.
     * The join table {@code ai_insight_experiment} stores the many-to-many relationship.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ai_insight_experiment",
            joinColumns = @JoinColumn(name = "ai_insight_id"),
            inverseJoinColumns = @JoinColumn(name = "experiment_id")
    )
    @Builder.Default
    private List<Experiment> experiments = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiInsight that = (AiInsight) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
