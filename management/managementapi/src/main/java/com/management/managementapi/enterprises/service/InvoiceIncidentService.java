package com.management.managementapi.enterprises.service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.incident.IncidentInvoiceRefDTO;
import com.management.managementapi.enterprises.dto.incident.InvoiceIncidentCreateDTO;
import com.management.managementapi.enterprises.dto.incident.InvoiceIncidentResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.InvoiceIncident;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoiceIncidentRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.model.Profile;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;

/**
 * Inconsistências — notas livres (markdown) sobre uma ou mais faturas que
 * ficaram por conciliar. A transferência sugere criar uma; a criação é sempre à
 * mão. Só ADMIN, como o resto do ciclo financeiro.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceIncidentService {

    private final InvoiceIncidentRepository repository;
    private final ConstructionInvoiceRepository invoiceRepository;
    private final ProfileRepository profileRepository;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<InvoiceIncidentResponseDTO> list() {
        return repository.findAllOrdered().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceIncidentResponseDTO get(UUID id) {
        return toResponse(require(id));
    }

    public InvoiceIncidentResponseDTO create(InvoiceIncidentCreateDTO dto) {
        List<UUID> wanted = List.copyOf(new LinkedHashSet<>(dto.invoiceIds()));
        List<ConstructionInvoice> invoices = invoiceRepository.findAllById(wanted);
        if (invoices.size() != wanted.size()) {
            throw new BusinessException(ErrorCode.INVOICE_INCIDENT_INVOICE_NOT_FOUND);
        }

        InvoiceIncident incident = new InvoiceIncident();
        incident.setTitle(dto.title().trim());
        incident.setBody(dto.body().trim());
        incident.setInvoices(new LinkedHashSet<>(invoices));
        authContext.currentProfileId().ifPresent(incident::setCreatedBy);

        return toResponse(repository.save(incident));
    }

    public InvoiceIncidentResponseDTO resolve(UUID id) {
        InvoiceIncident incident = require(id);
        if (incident.getResolvedAt() == null) {
            incident.setResolvedAt(OffsetDateTime.now());
            authContext.currentProfileId().ifPresent(incident::setResolvedBy);
            repository.save(incident);
        }
        return toResponse(incident);
    }

    private InvoiceIncident require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVOICE_INCIDENT_NOT_FOUND));
    }

    private InvoiceIncidentResponseDTO toResponse(InvoiceIncident incident) {
        return new InvoiceIncidentResponseDTO(
                incident.getId(),
                incident.getTitle(),
                incident.getBody(),
                incident.getResolvedAt(),
                incident.getResolvedBy(),
                profileName(incident.getResolvedBy()),
                incident.getCreatedBy(),
                profileName(incident.getCreatedBy()),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getInvoices().stream().map(this::toInvoiceRef).toList());
    }

    private IncidentInvoiceRefDTO toInvoiceRef(ConstructionInvoice invoice) {
        return new IncidentInvoiceRefDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getSupplierName(),
                invoice.getScope().name(),
                invoice.getEnterpriseId());
    }

    private String profileName(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId).map(Profile::getName).orElse(null);
    }
}
