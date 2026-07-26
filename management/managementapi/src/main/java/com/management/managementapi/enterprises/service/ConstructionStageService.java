package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.stage.ConstructionStageResponseDTO;
import com.management.managementapi.enterprises.dto.stage.CreateConstructionStageDTO;
import com.management.managementapi.enterprises.mapper.ConstructionStageMapper;
import com.management.managementapi.enterprises.model.ConstructionStage;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionStageRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionStageService {

    private final ConstructionStageRepository repository;
    private final EnterpriseRepository enterpriseRepository;
    private final ConstructionStageMapper mapper;

    @Transactional(readOnly = true)
    public List<ConstructionStageResponseDTO> listByEnterprise(UUID enterpriseId) {
        return mapper.toResponseList(repository.findByEnterpriseId(enterpriseId));
    }

    @Transactional(readOnly = true)
    public ConstructionStage getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.constructionStage(id.toString()));
    }

    public ConstructionStage create(CreateConstructionStageDTO dto) {
        Enterprise enterprise = enterpriseRepository.findById(dto.enterpriseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STAGE_ENTERPRISE_NOT_FOUND));

        ConstructionStage stage = new ConstructionStage();
        stage.setName(dto.name());
        stage.setDescription(dto.description());
        stage.setEnterprise(enterprise);
        return repository.save(stage);
    }

    public ConstructionStage update(UUID id, CreateConstructionStageDTO dto) {
        ConstructionStage stage = getById(id);

        if (!stage.getEnterprise().getId().equals(dto.enterpriseId())) {
            Enterprise enterprise = enterpriseRepository.findById(dto.enterpriseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.STAGE_ENTERPRISE_NOT_FOUND));
            stage.setEnterprise(enterprise);
        }

        stage.setName(dto.name());
        stage.setDescription(dto.description());
        return repository.save(stage);
    }

    public void delete(UUID id) {
        ConstructionStage stage = getById(id);
        repository.delete(stage);
    }
}
