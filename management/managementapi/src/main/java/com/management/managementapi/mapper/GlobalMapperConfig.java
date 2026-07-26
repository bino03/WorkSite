package com.management.managementapi.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig; // <-- ISTO é a ANNOTATION do MapStruct
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Configuração padrão para todos os mappers.
 * - componentModel spring: injecção como bean
 * - ignora nulls em updates (@MappingTarget)
 */
@MapperConfig(
  componentModel = "spring",
  injectionStrategy = InjectionStrategy.CONSTRUCTOR,
  nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
  nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface GlobalMapperConfig {}
