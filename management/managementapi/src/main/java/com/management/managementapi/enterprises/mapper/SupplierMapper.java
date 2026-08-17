package com.management.managementapi.enterprises.mapper;

import com.management.managementapi.enterprises.dto.supplier.response.SupplierResponseDTO;
import com.management.managementapi.enterprises.model.Supplier;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    /**
     * O {@code invoiceCount} entra como segundo parâmetro por não ser um campo
     * da entidade: é contado nas faturas, numa query só para a lista toda (ver
     * {@code SupplierService#invoiceCountsByNif}).
     *
     * A escrita não passa por aqui de propósito — o NIF e o nome são
     * normalizados (trim) no service antes de gravar, porque é o valor
     * normalizado que a constraint de unicidade compara.
     */
    SupplierResponseDTO toResponse(Supplier supplier, long invoiceCount);
}
