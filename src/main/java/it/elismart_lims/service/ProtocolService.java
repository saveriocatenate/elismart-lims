package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ProtocolMapper;
import java.util.List;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.repository.ProtocolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for Protocol operations.
 */
@Service
@RequiredArgsConstructor
public class ProtocolService {

    private final ProtocolRepository protocolRepository;

    /**
     * Return all protocols.
     *
     * @return list of all ProtocolResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ProtocolResponse> getAll() {
        return protocolRepository.findAll().stream()
                .map(ProtocolMapper::toResponse)
                .toList();
    }

    /**
     * Find a protocol by its ID.
     */
    @Transactional(readOnly = true)
    public ProtocolResponse getById(Long id) {
        var protocol = getEntityById(id);
        return ProtocolMapper.toResponse(protocol);
    }

    /**
     * Find a protocol entity by its ID.
     * Used internally by other services to avoid cross-repository injection.
     *
     * @param id the protocol ID
     * @return the found Protocol entity
     * @throws ResourceNotFoundException if no protocol exists with the given ID
     */
    public Protocol getEntityById(Long id) {
        return protocolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Protocol not found with id: " + id));
    }

    /**
     * Create a new protocol.
     */
    @Transactional
    public ProtocolResponse create(Protocol protocol) {
        return ProtocolMapper.toResponse(protocolRepository.save(protocol));
    }

    /**
     * Delete a protocol by its ID.
     */
    @Transactional
    public void delete(Long id) {
        if (!protocolRepository.existsById(id)) {
            throw new ResourceNotFoundException("Protocol not found with id: " + id);
        }
        protocolRepository.deleteById(id);
    }
}
