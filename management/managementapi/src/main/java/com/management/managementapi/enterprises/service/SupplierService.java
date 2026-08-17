package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.supplier.request.SupplierUpsertDTO;
import com.management.managementapi.enterprises.dto.supplier.response.SupplierResponseDTO;
import com.management.managementapi.enterprises.dto.supplier.response.SupplierSaveResultDTO;
import com.management.managementapi.enterprises.dto.supplier.response.UnknownSupplierNifDTO;
import com.management.managementapi.enterprises.mapper.SupplierMapper;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Catálogo de fornecedores: dar nome às empresas que o QR da AT só identifica
 * por NIF.
 *
 * As duas metades do problema estão as duas aqui de propósito:
 *
 * <ol>
 *   <li>{@link #listUnknownNifs()} — os NIFs que aparecem nas faturas e ainda
 *       não têm empresa. É a lista de trabalho.</li>
 *   <li>{@link #create}/{@link #update} — ao gravar o par NIF→nome, o nome é
 *       escrito também nas faturas <b>sem nome</b> desse NIF. Sem este segundo
 *       passo o catálogo só valeria para o futuro e as faturas já carregadas
 *       ficavam anónimas para sempre.</li>
 * </ol>
 *
 * Nunca se sobrepõe um nome já escrito: quem o escreveu tinha o papel à frente
 * — ver {@code ConstructionInvoiceRepository#fillMissingSupplierName}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    private final SupplierRepository repository;
    private final ConstructionInvoiceRepository invoiceRepository;
    private final SupplierMapper mapper;
    private final AuthContext authContext;

    // ── leitura ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> list(String q) {
        List<Supplier> suppliers = repository.search(isBlank(q) ? null : q.trim());
        if (suppliers.isEmpty()) {
            return List.of();
        }

        Map<String, Long> counts = invoiceCountsByNif(suppliers.stream().map(Supplier::getNif).toList());
        return suppliers.stream()
                .map(supplier -> mapper.toResponse(supplier, counts.getOrDefault(supplier.getNif(), 0L)))
                .toList();
    }

    /** Os NIFs vistos nas faturas que ainda não têm empresa associada. */
    @Transactional(readOnly = true)
    public List<UnknownSupplierNifDTO> listUnknownNifs() {
        return invoiceRepository.findUnknownSupplierNifs().stream()
                .map(row -> new UnknownSupplierNifDTO(
                        row.getNif(), row.getInvoiceCount(), row.getSuggestedName(), row.getLastInvoiceDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Supplier getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.supplier(id.toString()));
    }

    // ── escrita ───────────────────────────────────────────────

    public SupplierSaveResultDTO create(SupplierUpsertDTO dto) {
        String nif = dto.nif().trim();
        if (repository.existsByNif(nif, null)) {
            throw new BusinessException(ErrorCode.SUPPLIER_NIF_ALREADY_EXISTS);
        }

        Supplier supplier = new Supplier();
        supplier.setNif(nif);
        supplier.setName(dto.name().trim());
        supplier.setNotes(trimToNull(dto.notes()));
        authContext.currentProfileId().ifPresent(supplier::setCreatedBy);

        return saveAndApplyName(supplier);
    }

    /**
     * Renomear também repõe o nome nas faturas que estejam sem ele — corrigir um
     * nome mal escrito e ver as faturas continuarem anónimas seria o mesmo bug
     * que a criação resolve.
     */
    public SupplierSaveResultDTO update(UUID id, SupplierUpsertDTO dto) {
        Supplier supplier = getById(id);

        String nif = dto.nif().trim();
        if (repository.existsByNif(nif, id)) {
            throw new BusinessException(ErrorCode.SUPPLIER_NIF_ALREADY_EXISTS);
        }

        supplier.setNif(nif);
        supplier.setName(dto.name().trim());
        supplier.setNotes(trimToNull(dto.notes()));

        return saveAndApplyName(supplier);
    }

    /**
     * Apaga a entrada do catálogo.
     *
     * <b>Não</b> limpa o nome das faturas: o nome já lá está escrito e é um
     * dado do documento, não uma referência viva a esta tabela. Apagar aqui só
     * significa "deixa de preencher sozinho a partir de agora".
     */
    public void delete(UUID id) {
        repository.delete(getById(id));
    }

    private SupplierSaveResultDTO saveAndApplyName(Supplier supplier) {
        Supplier saved = repository.save(supplier);
        int updated = invoiceRepository.fillMissingSupplierName(saved.getNif(), saved.getName());

        long invoiceCount = invoiceCountsByNif(List.of(saved.getNif()))
                .getOrDefault(saved.getNif(), 0L);
        return new SupplierSaveResultDTO(mapper.toResponse(saved, invoiceCount), updated);
    }

    /**
     * O nome da empresa deste NIF, se estiver no catálogo — é o que faz uma
     * fatura nova nascer já identificada (ver {@code ConstructionInvoiceService}).
     */
    @Transactional(readOnly = true)
    public String findNameByNif(String nif) {
        if (isBlank(nif)) {
            return null;
        }
        return repository.findByNif(nif.trim()).map(Supplier::getName).orElse(null);
    }

    // ── auxiliares ────────────────────────────────────────────

    private Map<String, Long> invoiceCountsByNif(List<String> nifs) {
        if (nifs.isEmpty()) {
            return Map.of();
        }
        return invoiceRepository.countBySupplierNifs(nifs).stream()
                .collect(Collectors.toMap(
                        ConstructionInvoiceRepository.SupplierNifCount::getNif,
                        ConstructionInvoiceRepository.SupplierNifCount::getInvoiceCount,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
