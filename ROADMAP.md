# EliSmart LIMS — Review Consolidation & Development Plan

**Data:** 09/04/2026
**Fonti:** Technical Audit (TA), Product Owner (PO), QA Due Diligence (QA), UX Audit (UX)

---

## Sezione 1 — Catalogo Issue per Severità

### CRITICAL

| ID    | Issue                                                                                                                                                                                                                           | Segnalato da                        | Stato                                                                                                                |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| CR-01 | **Audit trail identity-blind** — `createdBy` è sempre `"system"`, manca `updatedBy`. Viola ALCOA+ (Attributable) e 21 CFR Part 11 §11.10(e). Nessuna tracciabilità di chi ha creato/modificato un record.                       | TA [C3], PO [W2], QA [TR-01, TR-02] | Da fare                                                                                                              |
| CR-02 | **Nessuna change history** — Il PUT sovrascrive i valori originali senza conservare lo storico. Nessun log delle modifiche, nessun campo "reason for change". Equivale a scrivere a matita.                                     | PO [W2], QA [TR-02]                 | Da fare                                                                                                              |
| CR-03 | **Nessun motore di validazione automatica OK/KO** — Il protocollo definisce `maxCvAllowed` e `maxErrorAllowed` ma nessun engine li applica. Lo status è impostato manualmente dall'utente.                                      | PO [W1], QA [CAL-01, CAL-02]        | Da fare                                                                                                              |
| CR-04 | **Nessun curve fitting engine** — `CurveType` è un campo enum ma nessuna curva viene calcolata. Nessuna interpolazione delle concentrazioni, nessun calcolo automatico di %Recovery. Lo scienziato deve calcolare esternamente. | PO [W5], QA [CAL-02]                | Da fare                                                                                                              |
| CR-05 | **%Recovery non calcolata dal sistema** — È un valore client-supplied salvato verbatim. Nessun legame matematico con i segnali raw. Non verificabile in audit.                                                                  | QA [CAL-02]                         | Da fare                                                                                                              |
| CR-06 | **%CV formula scientificamente discrepante** — Il codice calcola `                                                                                                                                                              | s1-s2                               | /mean*100` (percent range), non il vero %CV (`SD/mean*100`). La differenza è un fattore √2. Formula non documentata. | QA [CAL-01] | Da fare |
| CR-07 | **cvPct client-supplied alla creazione** — Al POST il valore è salvato verbatim dal client. Il ricalcolo server-side avviene solo al PUT. Un utente può inserire cvPct arbitrario.                                              | QA [CAL-01]                         | Da fare                                                                                                              |
| CR-08 | **Nessun backup e disaster recovery** — DB è un singolo file H2. Nessun backup automatico, nessuna replica, nessun point-in-time recovery. Corruzione = perdita totale dati.                                                    | PO [W4, T4], QA [REL-01]            | Da fare                                                                                                              |
| CR-09 | **Nessuna autenticazione sugli endpoint REST** — Tutti gli endpoint sono aperti. Qualsiasi processo con accesso alla porta 8080 può leggere/modificare/cancellare qualsiasi dato.                                               | TA [S1], PO [W7], QA [REL-03]       | Deferred Phase 2                                                                                                     |
| CR-10 | **Auth gate mancante su tutte le 10 pagine Streamlit** — `check_auth()` esisteva ma non era mai chiamata. Accesso diretto via URL possibile senza login.                                                                        | TA [C3]                             | **FIXED**                                                                                                            |
| CR-11 | **Entry manuale dei segnali senza import CSV** — Nessun percorso di importazione da plate reader. 20-40 valori inseriti a mano per esperimento. Blocker per lab con >1 plate/giorno.                                            | PO [O5], UX [Journey 3]             | Da fare                                                                                                              |
| CR-12 | **H2 database incompatibile con SaaS** — Single-tenant, file-based, no clustering, no concurrent access da istanze multiple.                                                                                                    | PO [W4]                             | Da fare (Phase 3)                                                                                                    |

### HIGH

| ID    | Issue                                                                                                                                                                                                                    | Segnalato da   | Stato             |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|-------------------|
| HI-01 | **Nessuna CORS configuration** — Necessaria quando frontend e backend sono su origini diverse.                                                                                                                           | TA [S2]        | Deferred Phase 2  |
| HI-02 | **GeminiController.analyze() senza @Valid + nessun constraint sul DTO** — Request vuota arrivava al service layer.                                                                                                       | TA [H1]        | **FIXED**         |
| HI-03 | **Nessun export PDF/Excel** — Nessun certificato di analisi, nessun report stampabile, nessun Excel export. I lab devono allegare report a batch record.                                                                 | PO [W6]        | Da fare           |
| HI-04 | **Nessuna documentazione di qualifica** — Assenti tutti i documenti GAMP 5: URS, FS, DQ, IQ, OQ, PQ, Validation Summary, Risk Assessment, Change Control, Backup Plan, Training Records, DR Plan.                        | QA [Sez. 3]    | Da fare (Phase 3) |
| HI-05 | **Outlier handling solo manuale** — `isOutlier` è un boolean senza test statistico automatico (Grubbs, Dixon Q), senza audit di chi ha flaggato e perché, senza criterio pre-definito.                                   | QA [CAL-03]    | Da fare           |
| HI-06 | **Nessuna electronic signature** — Richiesta da 21 CFR Part 11 §11.50 per approvazione esperimenti.                                                                                                                      | QA [Sez. 6]    | Da fare (Phase 3) |
| HI-07 | **AI analysis: path a 3 pagine, no loading indicator, analisi non persistente** — Nessun accesso diretto dalla pagina dettaglio esperimento. UI freezata durante chiamata Gemini (sincrona). Risultato perso al refresh. | UX [Journey 4] | Da fare           |
| HI-08 | **Nessun color coding QC nei risultati** — %CV=32% visivamente identico a %CV=4%. Nessun confronto visivo con i limiti del protocollo. Outlier mostrato come testo true/false.                                           | UX [Journey 5] | Da fare           |
| HI-09 | **Startup a due processi senza wrapper script** — Backend + frontend separati, nessun health check sulla login page. Se backend è down, l'app crasha con `ConnectionError` raw.                                          | UX [Journey 1] | Da fare           |
| HI-10 | **Data privacy con Gemini AI** — Dati proprietari inviati a Google. Problemi GDPR/HIPAA per lab europei e clinici. Nessuna opzione on-premise o DPA.                                                                     | PO [T1]        | Da fare (Phase 3) |
| HI-11 | **Gemini API single-point-of-failure** — Nessun fallback, nessun retry, nessun graceful degradation. Endpoint torna 502 se Gemini non disponibile.                                                                       | PO [T5]        | Da fare           |
| HI-12 | **GlobalExceptionHandler mancava 4 handler Spring** — `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `DataIntegrityViolationException`, catch-all. Stack trace esposti.                       | TA [C2]        | **FIXED**         |

### MEDIUM

| ID    | Issue                                                                                                                                                           | Segnalato da             | Stato             |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|-------------------|
| ME-01 | **orphanRemoval mancante su OneToMany di Experiment** — Righe orfane nel DB alla rimozione dalla collection in-memory.                                          | TA [C1]                  | **FIXED**         |
| ME-02 | **ExperimentController.search() senza @Valid** — `page=-1` o `size=0` causavano eccezione opaca.                                                                | TA [M1]                  | **FIXED**         |
| ME-03 | **ProtocolReagentSpecController — protocolId poteva essere 0 o negativo**                                                                                       | TA [M2]                  | **FIXED**         |
| ME-04 | **Mancava UNIQUE constraint DB su reagent_catalog(name, manufacturer)** — Race condition su POST concorrenti.                                                   | TA [M3]                  | **FIXED**         |
| ME-05 | **requirements.txt non version-pinned**                                                                                                                         | TA [M4]                  | **FIXED**         |
| ME-06 | **@Data su JPA entities** — `equals()/hashCode()` su tutti i campi, problematico con Set e entity tracked.                                                      | TA [R1]                  | Da fare           |
| ME-07 | **Nessun metodo di sync bidirezionale su Experiment** — Mancano `addUsedReagentBatch()` / `addMeasurementPair()`.                                               | TA [R2]                  | Da fare           |
| ME-08 | **isOutlier non aggiornabile via PUT** — Manca nel DTO di update o endpoint PATCH dedicato.                                                                     | TA [R3]                  | Da fare           |
| ME-09 | **GeminiService cattura Exception generica** — Non distingue auth failure da timeout.                                                                           | TA [R4]                  | Da fare           |
| ME-10 | **N+1 query su ProtocolReagentSpecRepository.findByProtocolId** — Manca `@EntityGraph`.                                                                         | TA [R5]                  | Da fare           |
| ME-11 | **Signal model limitato a duplicate-OD pair** — `signal1/signal2` è ELISA-nativo. PCR, Western, citometria richiedono modello astratto (canale, valore, unità). | PO [W3]                  | Da fare (Phase 4) |
| ME-12 | **Entità Sample assente** — `PairType.SAMPLE` esiste ma nessuna entità con barcode, matrix, patient ID, collection date.                                        | PO [Phase 2], QA [TR-03] | Da fare           |
| ME-13 | **Jackson fail-on-unknown-properties non abilitato** — Campi JSON sconosciuti ignorati silenziosamente.                                                         | TA [S3]                  | Da fare           |
| ME-14 | **Nessun RBAC** — Un solo utente condiviso, nessun ruolo (Analyst/Reviewer/Admin).                                                                              | PO [W7, Phase 2]         | Da fare           |
| ME-15 | **Signal NOT NULL mancante a livello DB** — Esperimento creabile con segnali null. Nessun check di consistenza `signal_mean = (s1+s2)/2`.                       | QA [REL-02]              | Da fare           |
| ME-16 | **Form validation non field-inline** — Errori in cima al form, utente deve scrollare. Nessun draft state cross-page.                                            | UX [Journey 2, 7]        | Da fare           |
| ME-17 | **Errori transient spariscono su st.rerun()** — Messaggi di errore persi prima che l'utente li legga. HTTP 409 mostra testo del constraint DB.                  | UX [Journey 7]           | Da fare           |
| ME-18 | **Edit mode e view mode visivamente identici** — Nessun dialog di conferma prima del save. Scroll position perso dopo save.                                     | UX [Journey 6]           | Da fare           |
| ME-19 | **Data retention policy assente** — Nessuna policy di retention minima (OECD GLP richiede 10 anni).                                                             | QA [Sez. 6]              | Da fare (Phase 3) |
| ME-20 | **Nessun feedback live %CV durante creazione** — Ricalcolo solo in edit mode, non durante primo inserimento.                                                    | UX [Journey 3]           | Da fare           |

### LOW

| ID    | Issue                                                                                                          | Segnalato da   | Stato          |
|-------|----------------------------------------------------------------------------------------------------------------|----------------|----------------|
| LO-01 | **H2 console abilitata solo in dev profile** — Correttamente disabilitata in base config.                      | TA [S4]        | Nessuna azione |
| LO-02 | **unsafe_allow_html=True** — Usato solo per CSS statico, nessun input utente renderizzato come HTML.           | TA [S5]        | Nessuna azione |
| LO-03 | **Nessun tooltip/help text su CurveType** — Lo scienziato non sa cosa significa FOUR_PARAMETER_LOGISTIC.       | UX [Journey 2] | Da fare        |
| LO-04 | **concentrationNominal senza annotazione unità** — "Conc." senza ng/mL, µg/mL etc. Errore silenzioso di unità. | UX [Journey 3] | Da fare        |
| LO-05 | **Nessun link cliccabile dal dettaglio esperimento al protocollo**                                             | UX [Journey 5] | Da fare        |
| LO-06 | **Status PENDING/COMPLETED/OK/KO/VALIDATION_ERROR non spiegati nella UI**                                      | UX [Journey 5] | Da fare        |
| LO-07 | **Nessuna conferma visiva dopo save protocollo** — Solo redirect, l'utente non sa se ha funzionato.            | UX [Journey 2] | Da fare        |
| LO-08 | **Reagent batch list senza header nome reagente** — Lista piatta di Lot/Expiry senza raggruppamento.           | UX [Journey 3] | Da fare        |

---

## Sezione 2 — Piano di Sviluppo

Le feature e i fix sono raggruppati in fasi logiche, dalla stabilizzazione del core fino al SaaS. 
L'ordine interno è indicativo — la prioritizzazione finale e la divisione in sprint spetta al team.

---

### Fase 1 — Stabilizzazione Core & Data Integrity

**Obiettivo:** I dati nel sistema sono corretti, verificabili e non si perdono.

| #    | Attività                                                                                                                                                            | Issue risolte | Effort stimato |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------|
| 1.1  | **Fix formula %CV** — Implementare SD/Mean×100 oppure documentare esplicitamente la scelta di Range/Mean. Aggiornare API docs e UI.                                 | CR-06         | S              |
| 1.2  | **Ricalcolo server-side cvPct al POST** — Il server deve ricalcolare cvPct e signalMean alla creazione, non solo al PUT. Rifiutare valori client-supplied derivati. | CR-07         | S              |
| 1.3  | **NOT NULL constraint su signal_1, signal_2** — Migration Flyway + check consistenza signal_mean.                                                                   | ME-15         | S              |
| 1.4  | **@Data → @Getter/@Setter su JPA entities** — equals/hashCode basati solo su id.                                                                                    | ME-06         | S              |
| 1.5  | **Metodi sync bidirezionali su Experiment** — `addUsedReagentBatch()`, `addMeasurementPair()`.                                                                      | ME-07         | S              |
| 1.6  | **@EntityGraph su ProtocolReagentSpecRepository** — Fix N+1.                                                                                                        | ME-10         | S              |
| 1.7  | **isOutlier aggiornabile via PUT o PATCH**                                                                                                                          | ME-08         | S              |
| 1.8  | **Jackson fail-on-unknown-properties = true**                                                                                                                       | ME-13         | XS             |
| 1.9  | **GeminiService: catch specifici** — Distinguere auth failure, timeout, rate limit.                                                                                 | ME-09         | S              |
| 1.10 | **Gemini fallback e retry** — Retry con backoff, graceful degradation (messaggio utente) se Gemini è down.                                                          | HI-11         | M              |

---

### Fase 2 — Autenticazione & Audit Trail

**Obiettivo:** Sapere chi ha fatto cosa e quando. Prerequisito per qualsiasi uso regolato.

| #   | Attività                                                                                                                                        | Issue risolte | Effort stimato |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------|
| 2.1 | **Spring Security + JWT** — Autenticazione per-user su tutti gli endpoint REST.                                                                 | CR-09         | L              |
| 2.2 | **AuditorAwareImpl da SecurityContext** — `createdBy` e `updatedBy` con username reale.                                                         | CR-01         | S (dopo 2.1)   |
| 2.3 | **Change history table** — Tabella `audit_log` con: entity, field, old_value, new_value, changed_by, changed_at, reason. Intercettare ogni PUT. | CR-02         | L              |
| 2.4 | **CORS configuration**                                                                                                                          | HI-01         | S              |
| 2.5 | **RBAC base** — Ruoli Analyst / Reviewer / Admin. Protezione endpoint per ruolo.                                                                | ME-14         | M              |
| 2.6 | **User management portal** — Invito, disattivazione, assegnazione ruoli.                                                                        | —             | M              |

---

### Fase 3 — Motore di Validazione & Curve Fitting

**Obiettivo:** Il sistema calcola, valida e decide. Non è più un data-entry tool.

| #   | Attività                                                                                                                                                                                                                                              | Issue risolte | Effort stimato |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------|
| 3.1 | **Validation engine** — Alla chiusura dell'esperimento, il sistema confronta cvPct e recoveryPct di ogni pair con i limiti del protocollo. Auto-set status OK/KO per pair e per esperimento. Impedire override manuale senza firma + giustificazione. | CR-03         | L              |
| 3.2 | **4PL curve fitting engine** — Fit della curva di calibrazione (4 parametri logistici) sui calibrator pairs. Interpolazione della concentrazione per sample/control pairs. Calcolo automatico %Recovery.                                              | CR-04, CR-05  | XL             |
| 3.3 | **Outlier detection automatica** — Implementare almeno un test configurabile (Grubbs o soglia %Difference). Audit log di chi ha flaggato e perché. Possibilità di override manuale con firma e reason.                                                | HI-05         | M              |
| 3.4 | **Live %CV feedback durante creazione** — Ricalcolo real-time nel form di creazione esperimento, non solo in edit.                                                                                                                                    | ME-20         | S              |

---

### Fase 4 — Import/Export & Reporting

**Obiettivo:** Il sistema si integra col workflow reale del lab.

| #   | Attività                                                                                                                                                             | Issue risolte | Effort stimato |
|-----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------|
| 4.1 | **CSV import da plate reader** — Parsing di formati Tecan, BioTek, Molecular Devices (o formato generico configurabile). Mapping automatico well → measurement pair. | CR-11         | L              |
| 4.2 | **PDF experiment report** — Certificato di analisi con metadata, reagent lots, curva, tabella risultati, status, firma.                                              | HI-03         | L              |
| 4.3 | **Excel export** — Export esperimento singolo e batch di esperimenti in xlsx.                                                                                        | HI-03         | M              |
| 4.4 | **Entità Sample** — Barcode, matrix type, patient/study ID, collection date, preparation method. Link a measurement pairs.                                           | ME-12         | M              |
| 4.5 | **Expiry-date alerts** — Flag reagenti con lotti in scadenza entro 30/60/90 giorni.                                                                                  | —             | S              |

---

### Fase 5 — UX Polish

**Obiettivo:** Lo scienziato non odia l'app.

| #   | Attività                                                                                                                                    | Issue risolte       | Effort stimato |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------|---------------------|----------------|
| 5.1 | **Color coding QC** — %CV e %Recovery colorati (verde/giallo/rosso) in base ai limiti del protocollo. Outlier con icona/highlight.          | HI-08               | M              |
| 5.2 | **AI shortcut da dettaglio esperimento** — Bottone "Analyze with AI" diretto. Loading indicator asincrono. Persistenza risultato AI nel DB. | HI-07               | M              |
| 5.3 | **Wrapper script startup** — Script unico `./start.sh` che avvia backend e frontend. Health check sulla login page.                         | HI-09               | S              |
| 5.4 | **Validation inline nei form** — Errori accanto al campo, non in cima alla pagina.                                                          | ME-16               | M              |
| 5.5 | **Errori persistenti e user-friendly** — Errori che sopravvivono a `st.rerun()`. Messaggi tradotti dal DB-speak a linguaggio umano.         | ME-17               | M              |
| 5.6 | **Distinzione visiva edit/view mode** — Conferma dialog prima del save. Mantenimento scroll position.                                       | ME-18               | S              |
| 5.7 | **Tooltip CurveType, unità concentrazione, spiegazione status**                                                                             | LO-03, LO-04, LO-06 | S              |
| 5.8 | **Conferma visiva post-save, link protocollo, header reagent batch**                                                                        | LO-05, LO-07, LO-08 | S              |

---

### Fase 6 — Compliance & Regulated Market

**Obiettivo:** Entrare nel mercato regolato (GLP/GMP/21 CFR Part 11).

| #   | Attività                                                                                                                                                           | Issue risolte | Effort stimato |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------|
| 6.1 | **Migrazione a PostgreSQL** — Schema migration, backup automatico schedulato, point-in-time recovery.                                                              | CR-08, CR-12  | L              |
| 6.2 | **Electronic signature** — Firma digitale per approvazione esperimenti. Username, timestamp, hash. Record locking post-firma.                                      | HI-06         | L              |
| 6.3 | **On-premise AI option** — Supporto Ollama o Azure OpenAI come alternativa a Gemini. Model selection configurabile.                                                | HI-10         | M              |
| 6.4 | **Data retention policy** — Policy minima 10 anni con enforcement automatico.                                                                                      | ME-19         | S              |
| 6.5 | **Pacchetto documentazione GAMP 5** — URS, FS, DQ, IQ, OQ, PQ, Validation Summary Report, Risk Assessment, Change Control, Backup Plan, Training Records, DR Plan. | HI-04         | XL (non-dev)   |
| 6.6 | **Multi-tenancy** — Entità Organization/Lab con isolamento dati per tenant.                                                                                        | CR-12         | XL             |
| 6.7 | **Soglie di validazione configurabili per lotto** — Override limiti protocollo a livello di singolo lotto reagente.                                                | —             | S              |
| 6.8 | **Email/webhook alerts** — KO result, lotto in scadenza, import fallito.                                                                                           | —             | M              |

---

### Fase 7 — Platform & Scale

**Obiettivo:** Da single-assay tool a piattaforma orizzontale.

| #   | Attività                                                                                                        | Issue risolte | Effort stimato |
|-----|-----------------------------------------------------------------------------------------------------------------|---------------|----------------|
| 7.1 | **Signal model generico** — Entità `SignalReading` con channel, value, unit. Supporto n replicati (non solo 2). | ME-11         | XL             |
| 7.2 | **PCR protocol support** — Ct values, efficiency, ΔΔCt, linear regression.                                      | ME-11         | XL             |
| 7.3 | **Infrastruttura SaaS** — Kubernetes, managed PostgreSQL, backup, SLA, monitoring.                              | —             | XL             |
| 7.4 | **REST API pubblica per integrazioni** — ERP, sample trackers, bioinformatics.                                  | —             | L              |
| 7.5 | **Inter-laboratory comparison reports** — Ring test, external QC.                                               | —             | L              |
| 7.6 | **AI trend monitoring proattivo** — Analisi schedulata, alert su trend anomali.                                 | —             | L              |
| 7.7 | **Marketplace template protocolli** — Kit commerciali pre-validati.                                             | —             | L              |

---

### Legenda Effort

| Sigla | Significato   |
|-------|---------------|
| XS    | < 1 giorno    |
| S     | 1–3 giorni    |
| M     | 4–7 giorni    |
| L     | 1–3 settimane |
| XL    | > 3 settimane |