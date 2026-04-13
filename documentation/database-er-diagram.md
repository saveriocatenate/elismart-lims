```mermaid
%%{init: {'theme': 'neutral', 'themeVariables': { 'primaryColor': '#e8f0fe', 'primaryTextColor': '#1a1a2e', 'primaryBorderColor': '#3b82f6', 'lineColor': '#6366f1', 'secondaryColor': '#fef3c7', 'tertiaryColor': '#d1fae5', 'noteBkgColor': '#f8fafc', 'noteTextColor': '#334155', 'fontFamily': 'Inter, system-ui, sans-serif' }}}%%

erDiagram
    PROTOCOL ||--o{ PROTOCOL_REAGENT_SPEC : "requires"
    PROTOCOL ||--o{ EXPERIMENT : "template_for"
    REAGENT_CATALOG ||--o{ PROTOCOL_REAGENT_SPEC : "available_in"
    REAGENT_CATALOG ||--o{ REAGENT_BATCH : "tracks_lots_of"
    REAGENT_BATCH ||--o{ USED_REAGENT_BATCH : "linked_via"
    EXPERIMENT ||--o{ USED_REAGENT_BATCH : "records"
    EXPERIMENT ||--o{ MEASUREMENT_PAIR : "contains"
    MEASUREMENT_PAIR }o--o| SAMPLE : "describes"
    APP_USER ||--o{ AUDIT_LOG : "authors"
    AI_INSIGHT }o--o{ EXPERIMENT : "analyzes"

    PROTOCOL {
        Long id PK
        String name
        Integer num_calibration_pairs "Expected calibration pairs for curve fit"
        Integer num_control_pairs "Expected control pairs for QC evaluation"
        Double max_cv_allowed "Max acceptable %CV between replicates"
        Double max_error_allowed "Max acceptable %Recovery error"
        String curve_type "FOUR_PARAMETER_LOGISTIC | FIVE_PARAMETER_LOGISTIC | LOG_LOGISTIC_3P | LINEAR | SEMI_LOG_LINEAR | POINT_TO_POINT"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    EXPERIMENT {
        Long id PK
        String name
        LocalDateTime date
        String status "PENDING | COMPLETED | OK | KO | VALIDATION_ERROR"
        Long protocol_id FK
        String curve_parameters "JSON: fitted curve params (A,B,C,D for 4PL etc.)"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    MEASUREMENT_PAIR {
        Long id PK
        Long experiment_id FK
        Long sample_id FK "nullable — set only for SAMPLE-type pairs"
        String pair_type "CALIBRATION | CONTROL | SAMPLE"
        Double concentration_nominal "X value (known concentration)"
        Double signal_1 "Raw replicate signal 1 (NOT NULL)"
        Double signal_2 "Raw replicate signal 2 (NOT NULL)"
        Double signal_mean "Server-computed: (signal_1 + signal_2) / 2"
        Double cv_pct "Server-computed: |s1−s2| / (√2 · mean) × 100"
        Double recovery_pct "Server-computed after curve fit: (interp / nominal) × 100"
        String pair_status "PASS | FAIL — set by ValidationEngine"
        Boolean is_outlier "Flagged by OutlierDetectionService or manual override"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    REAGENT_CATALOG {
        Long id PK
        String name "e.g., Anti-IgG Antibody (UNIQUE with manufacturer)"
        String manufacturer "UNIQUE with name"
        String description
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    PROTOCOL_REAGENT_SPEC {
        Long id PK
        Long protocol_id FK
        Long reagent_id FK
        Boolean is_mandatory
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    REAGENT_BATCH {
        Long id PK
        Long reagent_id FK "references REAGENT_CATALOG"
        String lot_number "Specific batch identifier"
        LocalDate expiry_date
        String supplier "optional"
        String notes "optional"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    USED_REAGENT_BATCH {
        Long id PK
        Long experiment_id FK
        Long reagent_batch_id FK "references REAGENT_BATCH (via V13 migration)"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    SAMPLE {
        Long id PK
        String barcode "Unique sample identifier"
        String matrix_type "e.g., serum, plasma, urine"
        String patient_study_id "Patient or study reference"
        LocalDate collection_date
        String preparation_method "optional"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    APP_USER {
        Long id PK
        String username "UNIQUE"
        String password "BCrypt-hashed"
        String role "ANALYST | REVIEWER | ADMIN"
        Boolean enabled
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
        String updated_by "Set by JPA auditing"
    }

    AUDIT_LOG {
        Long id PK
        String entity_type "e.g., Experiment, MeasurementPair"
        Long entity_id
        String field_name "Which field changed"
        String old_value "Stringified previous value"
        String new_value "Stringified new value"
        String changed_by "Username from SecurityContext"
        Timestamp changed_at
        String reason "Optional — required for status overrides and outlier flags"
    }

    AI_INSIGHT {
        Long id PK
        String user_question "Question submitted by the user"
        String ai_response "Full Gemini response text"
        String generated_by "Username who triggered the analysis"
        Timestamp generated_at
    }
```

## Schema Notes

- Migrations live in `src/main/resources/db/migration/` (V1–V14).
- `USED_REAGENT_BATCH.reagent_batch_id` replaced the old `reagent_id + lot_number` columns in V12–V13.
- `EXPERIMENT.curve_parameters` stores fitted model coefficients as a JSON string (added in V10).
- All entities extend `Auditable` — `created_at`, `updated_at`, `created_by`, `updated_by` are populated automatically by Spring Data JPA auditing.
- `AUDIT_LOG` has no `updated_at` column — entries are immutable by design.
