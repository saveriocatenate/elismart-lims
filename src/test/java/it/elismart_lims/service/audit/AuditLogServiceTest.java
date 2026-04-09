package it.elismart_lims.service.audit;

import it.elismart_lims.dto.AuditLogResponse;
import it.elismart_lims.model.AuditLog;
import it.elismart_lims.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditLogService}.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @BeforeEach
    void setUpSecurityContext() {
        // 3-arg constructor sets authenticated=true; the 2-arg constructor does not.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "testuser", "password",
                        List.of(new SimpleGrantedAuthority("ROLE_ANALYST")))
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // logChange
    // -------------------------------------------------------------------------

    @Test
    void logChange_persistsEntryWithCorrectFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.logChange("Experiment", 42L, "status", "PENDING", "COMPLETED", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("Experiment");
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getFieldName()).isEqualTo("status");
        assertThat(saved.getOldValue()).isEqualTo("PENDING");
        assertThat(saved.getNewValue()).isEqualTo("COMPLETED");
        assertThat(saved.getChangedBy()).isEqualTo("testuser");
        assertThat(saved.getChangedAt()).isNotNull();
        assertThat(saved.getReason()).isNull();
    }

    @Test
    void logChange_withReason_persistsReason() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.logChange("Experiment", 1L, "status", "KO", "OK", "Manual override approved by reviewer");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getReason()).isEqualTo("Manual override approved by reviewer");
        assertThat(captor.getValue().getChangedBy()).isEqualTo("testuser");
    }

    @Test
    void logChange_withNullOldValue_allowsCreationEvent() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.logChange("MeasurementPair", 7L, "signal1", null, "1.23", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getOldValue()).isNull();
        assertThat(captor.getValue().getNewValue()).isEqualTo("1.23");
    }

    @Test
    void logChange_withNoAuthentication_fallsBackToSystem() {
        SecurityContextHolder.clearContext();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.logChange("Protocol", 3L, "name", "Old", "New", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isEqualTo("system");
    }

    // -------------------------------------------------------------------------
    // logChanges
    // -------------------------------------------------------------------------

    @Test
    void logChanges_persistsOneRowPerField() {
        when(auditLogRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Pair<String, String>> changes = Map.of(
                "name", Pair.of("Alpha", "Beta"),
                "maxCvAllowed", Pair.of("10.0", "15.0")
        );

        auditLogService.logChanges("Protocol", 5L, changes, null);

        ArgumentCaptor<List<AuditLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditLogRepository).saveAll(captor.capture());

        List<AuditLog> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(AuditLog::getEntityType).containsOnly("Protocol");
        assertThat(saved).extracting(AuditLog::getChangedBy).containsOnly("testuser");
    }

    @Test
    void logChanges_withEmptyMap_doesNotCallRepository() {
        auditLogService.logChanges("Protocol", 1L, Map.of(), null);

        verify(auditLogRepository, org.mockito.Mockito.never()).saveAll(anyList());
    }

    // -------------------------------------------------------------------------
    // getHistory
    // -------------------------------------------------------------------------

    @Test
    void getHistory_returnsEntriesOrderedByChangedAtDesc() {
        LocalDateTime older = LocalDateTime.now().minusHours(2);
        LocalDateTime newer = LocalDateTime.now();

        AuditLog first = buildLog(1L, "Experiment", 10L, "status", newer);
        AuditLog second = buildLog(2L, "Experiment", 10L, "name", older);

        // Repository already returns in DESC order (enforced by the query method name)
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc("Experiment", 10L))
                .thenReturn(List.of(first, second));

        List<AuditLogResponse> result = auditLogService.getHistory("Experiment", 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).changedAt()).isEqualTo(newer);
        assertThat(result.get(1).changedAt()).isEqualTo(older);
    }

    @Test
    void getHistory_mapsAllFieldsCorrectly() {
        AuditLog log = buildLog(99L, "MeasurementPair", 7L, "signal1", LocalDateTime.now());
        log.setOldValue("0.5");
        log.setNewValue("1.0");
        log.setChangedBy("analyst1");
        log.setReason("correction");

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc("MeasurementPair", 7L))
                .thenReturn(List.of(log));

        List<AuditLogResponse> result = auditLogService.getHistory("MeasurementPair", 7L);

        assertThat(result).hasSize(1);
        AuditLogResponse r = result.get(0);
        assertThat(r.id()).isEqualTo(99L);
        assertThat(r.entityType()).isEqualTo("MeasurementPair");
        assertThat(r.entityId()).isEqualTo(7L);
        assertThat(r.fieldName()).isEqualTo("signal1");
        assertThat(r.oldValue()).isEqualTo("0.5");
        assertThat(r.newValue()).isEqualTo("1.0");
        assertThat(r.changedBy()).isEqualTo("analyst1");
        assertThat(r.reason()).isEqualTo("correction");
    }

    @Test
    void getHistory_returnsEmptyList_whenNoEntries() {
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc("Experiment", 999L))
                .thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.getHistory("Experiment", 999L);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuditLog buildLog(Long id, String entityType, Long entityId,
                               String fieldName, LocalDateTime changedAt) {
        return AuditLog.builder()
                .id(id)
                .entityType(entityType)
                .entityId(entityId)
                .fieldName(fieldName)
                .changedBy("testuser")
                .changedAt(changedAt)
                .build();
    }
}
