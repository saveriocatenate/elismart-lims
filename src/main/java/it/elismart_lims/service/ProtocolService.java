package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ProtocolMapper;
import java.util.List;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.repository.ProtocolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for Protocol operations.
 */
@Slf4j
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
     *
     * <p>Rejects the insert if a protocol with the same name (case-insensitive),
     * number of calibration pairs, and number of control pairs already exists.
     * This prevents the accidental creation of functionally duplicate protocols.</p>
     *
     * @param protocol the entity to persist
     * @return the created ProtocolResponse DTO
     * @throws IllegalArgumentException if a duplicate protocol is detected
     */
    @Transactional
    public ProtocolResponse create(Protocol protocol) {
        if (protocolRepository.existsByNameIgnoreCaseAndNumCalibrationPairsAndNumControlPairs(
                protocol.getName(),
                protocol.getNumCalibrationPairs(),
                protocol.getNumControlPairs())) {
            throw new IllegalArgumentException(
                    "A protocol named '" + protocol.getName()
                    + "' with " + protocol.getNumCalibrationPairs() + " calibration pairs"
                    + " and " + protocol.getNumControlPairs() + " control pairs already exists.");
        }
        log.info("Creating protocol: {}", protocol.getName());
        ProtocolResponse response = ProtocolMapper.toResponse(protocolRepository.save(protocol));
        log.info("Protocol created with id: {}", response.id());
        return response;
    }

    /**
     * Search protocols by partial name match (case-insensitive).
     * Returns all protocols when {@code name} is {@code null} or blank.
     *
     * @param name the partial name to search for; {@code null} or blank returns all
     * @return list of matching ProtocolResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ProtocolResponse> search(String name) {
        List<Protocol> results = (name == null || name.isBlank())
                ? protocolRepository.findAll()
                : protocolRepository.findByNameContainingIgnoreCase(name);
        return results.stream().map(ProtocolMapper::toResponse).toList();
    }

    /**
     * Find a protocol response by its unique name.
     *
     * @param name the protocol name
     * @return the ProtocolResponse DTO
     * @throws ResourceNotFoundException if no protocol exists with the given name
     */
    @Transactional(readOnly = true)
    public ProtocolResponse getByName(String name) {
        var protocol = protocolRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Protocol not found with name: " + name));
        return ProtocolMapper.toResponse(protocol);
    }

    /**
     * Delete a protocol by its ID.
     *
     * @param id the protocol ID
     * @throws ResourceNotFoundException if no protocol exists with the given ID
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting protocol id: {}", id);
        if (!protocolRepository.existsById(id)) {
            throw new ResourceNotFoundException("Protocol not found with id: " + id);
        }
        protocolRepository.deleteById(id);
        log.info("Protocol deleted id: {}", id);
    }
}
