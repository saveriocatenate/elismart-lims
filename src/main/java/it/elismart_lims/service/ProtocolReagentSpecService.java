package it.elismart_lims.service;

import it.elismart_lims.repository.ProtocolReagentSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for ProtocolReagentSpec operations.
 */
@Service
@RequiredArgsConstructor
public class ProtocolReagentSpecService {

    private final ProtocolReagentSpecRepository protocolReagentSpecRepository;

    /**
     * Collect the set of mandatory reagent IDs for a given protocol.
     */
    @Transactional(readOnly = true)
    public Set<Long> getMandatoryReagentIds(Long protocolId) {
        return protocolReagentSpecRepository.findByProtocolId(protocolId).stream()
                .filter(spec -> Boolean.TRUE.equals(spec.getIsMandatory()))
                .map(spec -> spec.getReagent().getId())
                .collect(Collectors.toSet());
    }
}
