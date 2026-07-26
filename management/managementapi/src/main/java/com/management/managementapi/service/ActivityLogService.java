package com.management.managementapi.service;

import com.management.managementapi.dto.activity.ActivityLogCreateDTO;
import com.management.managementapi.dto.activity.ActivityLogFilterDTO;
import com.management.managementapi.dto.activity.ActivityLogListDTO;
import com.management.managementapi.dto.activity.ActivityLogResponseDTO;
import com.management.managementapi.mapper.activity.ActivityLogMapper;
import com.management.managementapi.model.ActivityLog;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ActivityLogService {

    private final ActivityLogRepository repository;
    private final ActivityLogMapper mapper;

    /** Regista uma nova atividade. */
    public ActivityLogResponseDTO createActivity(ActivityLogCreateDTO dto) {
        ActivityLog entity = mapper.toEntity(dto);
        ActivityLog saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    /** Atividades de um utilizador específico, paginadas. */
    @Transactional(readOnly = true)
    public Page<ActivityLogListDTO> getUserActivities(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                         .map(mapper::toListDTO);
    }

    /** Atividades mais recentes do sistema (para o dashboard geral). */
    @Transactional(readOnly = true)
    public Page<ActivityLogListDTO> getRecentActivities(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable)
                         .map(mapper::toListDTO);
    }

    /** Histórico completo de uma entidade específica. */
    @Transactional(readOnly = true)
    public List<ActivityLogResponseDTO> getEntityActivities(EntityType entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                         .stream()
                         .map(mapper::toResponse)
                         .toList();
    }

    /** Filtro combinado de atividades. */
    @Transactional(readOnly = true)
    public Page<ActivityLogListDTO> filterActivities(ActivityLogFilterDTO filter, Pageable pageable) {
        return repository.findWithFilters(
                filter.userId(),
                filter.activityType(),
                filter.entityType(),
                filter.dateFrom(),
                filter.dateTo(),
                pageable
        ).map(mapper::toListDTO);
    }
}
