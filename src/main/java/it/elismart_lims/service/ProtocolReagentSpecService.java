package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolReagentSpecRequest;
import it.elismart_lims.dto.ProtocolReagentSpecResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ProtocolReagentSpecMapper;
import it.elismart_lims.model.ProtocolReagentSpec;
import it.elismart_lims.repository.ProtocolReagentSpecRepository;
import it.elismart_lims.repository.ProtocolRepository;
import it.elismart_lims.repository.ReagentCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for ProtocolReagentSpec operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolReagentSpecService {

    private final ProtocolReagentSpecRepository protocolReagentSpecRepository;
    private final ProtocolRepository protocolRepository;
    private final ReagentCatalogRepository reagentCatalogRepository;

    /**
     * Collect the set of mandatory reagent IDs for a given protocol.
     *
     * @param protocolId the protocol ID
     * @return set of mandatory reagent IDs
     */
    @Transactional(readOnly = true)
    public Set<Long> getMandatoryReagentIds(Long protocolId) {
        return protocolReagentSpecRepository.findByProtocolId(protocolId).stream()
                .filter(spec -> Boolean.TRUE.equals(spec.getIsMandatory()))
                .map(spec -> spec.getReagent().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Return all reagent specifications for a given protocol.
     *
     * @param protocolId the protocol ID
     * @return list of ProtocolReagentSpecResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ProtocolReagentSpecResponse> getByProtocolId(Long protocolId) {
        return protocolReagentSpecRepository.findByProtocolId(protocolId).stream()
                .map(ProtocolReagentSpecMapper::toResponse)
                .toList();
    }

    /**
     * Create a new protocol-reagent specification binding.
     *
     * @param request the creation request
     * @return the created ProtocolReagentSpecResponse DTO
     */
    @Transactional
    public ProtocolReagentSpecResponse create(ProtocolReagentSpecRequest request) {
        log.info("Linking reagent id: {} to protocol id: {} (mandatory={})",
                request.reagentId(), request.protocolId(), request.isMandatory());
        var protocol = protocolRepository.findById(request.protocolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Protocol not found with id: " + request.protocolId()));
        var reagent = reagentCatalogRepository.findById(request.reagentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reagent catalog not found with id: " + request.reagentId()));
        ProtocolReagentSpec saved = protocolReagentSpecRepository.save(
                ProtocolReagentSpec.builder()
                        .protocol(protocol)
                        .reagent(reagent)
                        .isMandatory(request.isMandatory())
                        .build());
        log.info("ProtocolReagentSpec created with id: {}", saved.getId());
        return ProtocolReagentSpecMapper.toResponse(saved);
    }
}
