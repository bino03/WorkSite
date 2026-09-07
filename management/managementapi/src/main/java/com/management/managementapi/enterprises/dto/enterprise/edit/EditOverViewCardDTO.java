package com.management.managementapi.enterprises.dto.enterprise.edit;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import java.util.UUID;

import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;
import com.management.managementapi.enterprises.model.enums.EnterPriseType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class EditOverViewCardDTO {

    private UUID id;
    private String name;
    private String internalReference;
    private EnterPriseType type;
    private EnterPriseStatus status;

    /** Nome da pasta da obra no vault Excel da Vilatro. Enviar "" limpa-o. */
    private String slug;

    /** Obra só de teste — excluída de exportações, importações e somas da empresa. */
    private Boolean isTest;

}
