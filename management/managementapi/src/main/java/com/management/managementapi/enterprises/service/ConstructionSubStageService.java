package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.substage.ConstructionSubStageResponseDTO;
import com.management.managementapi.enterprises.dto.substage.CreateConstructionSubStageDTO;
import com.management.managementapi.enterprises.mapper.ConstructionSubStageMapper;
import com.management.managementapi.enterprises.model.ConstructionStage;
import com.management.managementapi.enterprises.model.ConstructionSubStage;
import com.management.managementapi.enterprises.repository.ConstructionStageRepository;
import com.management.managementapi.enterprises.repository.ConstructionSubStageRepository;
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
public class ConstructionSubStageService {

    private final ConstructionSubStageRepository repository;
    private final ConstructionStageRepository stageRepository;
    private final ConstructionSubStageMapper mapper;

    @Transactional(readOnly = true)
    public List<ConstructionSubStageResponseDTO> listByStage(UUID stageId) {
        return mapper.toResponseList(repository.findByStageId(stageId));
    }

    @Transactional(readOnly = true)
    public ConstructionSubStage getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.constructionSubStage(id.toString()));
    }

    public ConstructionSubStage create(CreateConstructionSubStageDTO dto) {
        ConstructionStage stage = stageRepository.findById(dto.stageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSTAGE_STAGE_NOT_FOUND));

        ConstructionSubStage subStage = new ConstructionSubStage();
        subStage.setName(dto.name());
        subStage.setDescription(dto.description());
        subStage.setStage(stage);
        return repository.save(subStage);
    }

    public ConstructionSubStage update(UUID id, CreateConstructionSubStageDTO dto) {
        ConstructionSubStage subStage = getById(id);

        if (!subStage.getStage().getId().equals(dto.stageId())) {
            ConstructionStage stage = stageRepository.findById(dto.stageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SUBSTAGE_STAGE_NOT_FOUND));
            subStage.setStage(stage);
        }

        subStage.setName(dto.name());
        subStage.setDescription(dto.description());
        return repository.save(subStage);
    }

    public void delete(UUID id) {
        ConstructionSubStage subStage = getById(id);
        repository.delete(subStage);
    }
}
