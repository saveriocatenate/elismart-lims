```mermaid
%%{init: {'theme': 'neutral', 'themeVariables': { 'primaryColor': '#e8f0fe', 'primaryTextColor': '#1a1a2e', 'primaryBorderColor': '#3b82f6', 'lineColor': '#6366f1', 'secondaryColor': '#fef3c7', 'tertiaryColor': '#d1fae5', 'noteBkgColor': '#f8fafc', 'noteTextColor': '#334155', 'fontFamily': 'Inter, system-ui, sans-serif' }}}%%

erDiagram
    PROTOCOL ||--o{ PROTOCOL_REAGENT_SPEC : "requires"
    PROTOCOL ||--o{ EXPERIMENT : "template_for"
    REAGENT_CATALOG ||--o{ PROTOCOL_REAGENT_SPEC : "available_in"
    REAGENT_CATALOG ||--o{ USED_REAGENT_BATCH : "is_type_of"
    EXPERIMENT ||--o{ USED_REAGENT_BATCH : "records"
    EXPERIMENT ||--o{ MEASUREMENT_PAIR : "contains"

    PROTOCOL {
        Long id PK
        String name
        Integer num_calibration_pairs "Expected N pairs for curve"
        Integer num_control_pairs "Expected N pairs for controls"
        Double max_cv_allowed "Max %CV between replicates"
        Double max_error_allowed "Max %Recovery error"
        String curve_type "4PL, 5PL, 3PL, LINEAR, SEMI_LOG_LINEAR, POINT_TO_POINT"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
    }

    MEASUREMENT_PAIR {
        Long id PK
        Long experiment_id FK
        String pair_type "CALIBRATION, CONTROL"
        Double concentration_nominal "X value"
        Double signal_1 "Raw OD 1"
        Double signal_2 "Raw OD 2"
        Double signal_mean "Average"
        Double cv_pct "Precision (StdDev/Mean)"
        Double recovery_pct "Accuracy"
        Boolean is_outlier "Manually or automatically flagged"
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
    }

    EXPERIMENT {
        Long id PK
        String name
        LocalDateTime date
        String status "OK, KO, VALIDATION_ERROR"
        Long protocol_id FK
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
    }

    REAGENT_CATALOG {
        Long id PK
        String name "e.g., Anti-IgG Antibody"
        String manufacturer
        String description
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
    }
    
    PROTOCOL_REAGENT_SPEC {
        Long id PK
        Long protocol_id FK
        Long reagent_id FK
        Boolean is_mandatory
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
    }
    
    USED_REAGENT_BATCH {
        Long id PK
        Long experiment_id FK
        Long reagent_id FK
        String lot_number "Specific batch used"
        LocalDate expiry_date
        Timestamp created_at "Set by JPA auditing"
        Timestamp updated_at "Set by JPA auditing"
        String created_by "Set by JPA auditing"
    }