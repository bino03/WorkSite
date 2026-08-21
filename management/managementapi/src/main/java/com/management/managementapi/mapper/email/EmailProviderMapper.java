package com.management.managementapi.mapper.email;

import com.management.managementapi.dto.email.response.EmailProviderResponseDTO;
import com.management.managementapi.model.email.EmailProvider;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmailProviderMapper {

    /**
     * As duas flags são {@code Boolean} na entidade (a coluna {@code is_default} da
     * V7 nem default tem) e {@code boolean} na resposta — sem estas expressões um
     * registo antigo com null rebentava a serialização em vez de dizer "não".
     *
     * A password não aparece de propósito: só sai daqui se existe, nunca o valor.
     */
    @Mapping(target = "isDefault", expression = "java(Boolean.TRUE.equals(provider.getIsDefault()))")
    @Mapping(target = "isActive", expression = "java(Boolean.TRUE.equals(provider.getIsActive()))")
    @Mapping(target = "hasPassword", expression = "java(provider.getPassword() != null && !provider.getPassword().isBlank())")
    EmailProviderResponseDTO toResponse(EmailProvider provider);
}
