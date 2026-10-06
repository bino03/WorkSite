package com.management.managementapi.service.attendance;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.request.WorkScheduleDayDTO;
import com.management.managementapi.dto.attendance.request.WorkScheduleUpsertDTO;
import com.management.managementapi.dto.attendance.response.WorkScheduleResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.attendance.WorkScheduleMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentTermRepository;
import com.management.managementapi.repository.attendance.WorkScheduleRepository;

import java.time.DayOfWeek;

/**
 * catálogo de horários de trabalho. Um horário é definido pelo conjunto dos seus
 * dias; gravar substitui a lista inteira, porque "deixar de trabalhar à sexta" é
 * apagar o dia, não editá-lo.
 */
@Service
@Transactional
public class WorkScheduleService {

    private final WorkScheduleRepository repository;
    private final WorkScheduleMapper mapper;
    private final ProfileRepository profileRepository;
    private final EmploymentTermRepository employmentTermRepository;

    public WorkScheduleService(WorkScheduleRepository repository,
                               WorkScheduleMapper mapper,
                               ProfileRepository profileRepository,
                               EmploymentTermRepository employmentTermRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.profileRepository = profileRepository;
        this.employmentTermRepository = employmentTermRepository;
    }

    @Transactional(readOnly = true)
    public Page<WorkScheduleResponseDTO> list(Pageable pageable) {
        return repository.findByDeletedAtIsNullOrderByNameAsc(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<WorkScheduleResponseDTO> listDeleted() {
        return repository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkSchedule getById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SCHEDULE_NOT_FOUND,
                        "Horário com ID " + id + " não encontrado"));
    }

    @Transactional(readOnly = true)
    public WorkScheduleResponseDTO getResponseById(UUID id) {
        return mapper.toResponse(getById(id));
    }

    public WorkSchedule create(WorkScheduleUpsertDTO dto, UUID createdByProfileId) {
        validateDays(dto.days());

        if (repository.existsByNameIgnoreCaseAndDeletedAtIsNull(dto.name().trim())) {
            throw new BusinessException(ErrorCode.SCHEDULE_DUPLICATE_NAME);
        }

        WorkSchedule schedule = new WorkSchedule();
        schedule.setName(dto.name().trim());
        schedule.setNotes(dto.notes());
        schedule.setCreatedBy(resolveProfile(createdByProfileId));
        replaceDays(schedule, dto.days());

        return repository.save(schedule);
    }

    /**
     * Um horário já atribuído é imutável. O histórico vive na atribuição
     * ({@code employment_term.valid_from}), mas isso não protege nada se a coisa
     * atribuída puder mudar: editar "08–17" alteraria os cálculos de todos os meses
     * em que esteve em vigor, que é precisamente o que o histórico existe para
     * evitar. Para mudar, cria-se um horário novo e reatribui-se a partir de uma data.
     */
    public WorkSchedule update(UUID id, WorkScheduleUpsertDTO dto) {
        validateDays(dto.days());

        WorkSchedule schedule = getById(id);

        if (employmentTermRepository.existsByWorkScheduleId(id)) {
            throw new BusinessException(ErrorCode.SCHEDULE_IN_USE);
        }
        if (repository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(dto.name().trim(), id)) {
            throw new BusinessException(ErrorCode.SCHEDULE_DUPLICATE_NAME);
        }

        schedule.setName(dto.name().trim());
        schedule.setNotes(dto.notes());
        replaceDays(schedule, dto.days());

        return repository.save(schedule);
    }

    public void softDelete(UUID id) {
        WorkSchedule schedule = getById(id);
        schedule.setDeletedAt(OffsetDateTime.now());
        repository.save(schedule);
    }

    public WorkSchedule restore(UUID id) {
        WorkSchedule schedule = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SCHEDULE_NOT_FOUND,
                        "Horário com ID " + id + " não encontrado"));

        if (schedule.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_DELETED);
        }
        if (repository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(schedule.getName(), id)) {
            throw new BusinessException(ErrorCode.SCHEDULE_DUPLICATE_NAME);
        }

        schedule.setDeletedAt(null);
        return repository.save(schedule);
    }

    /**
     * As mesmas regras existem como CHECK na V42. Aqui existem outra vez para o erro
     * chegar ao cliente como um {@code SCHED_xxx} explicável, em vez de um 409 de
     * violação de integridade sem dizer qual das regras falhou.
     */
    private void validateDays(List<WorkScheduleDayDTO> days) {
        EnumSet<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);

        for (WorkScheduleDayDTO day : days) {
            if (!seen.add(day.weekday())) {
                throw new BusinessException(ErrorCode.SCHEDULE_DUPLICATE_WEEKDAY);
            }
            if (!day.endTime().isAfter(day.startTime())) {
                throw new BusinessException(ErrorCode.SCHEDULE_INVALID_HOURS);
            }
            Duration span = Duration.between(day.startTime(), day.endTime());
            if (day.breakMinutes() >= span.toMinutes()) {
                throw new BusinessException(ErrorCode.SCHEDULE_BREAK_TOO_LONG);
            }
        }
    }

    private void replaceDays(WorkSchedule schedule, List<WorkScheduleDayDTO> days) {
        schedule.getDays().clear();

        for (WorkScheduleDayDTO dto : days) {
            WorkScheduleDay day = new WorkScheduleDay();
            day.setSchedule(schedule);
            day.setDayOfWeek(dto.weekday());
            day.setStartTime(dto.startTime());
            day.setEndTime(dto.endTime());
            day.setBreakMinutes(dto.breakMinutes());
            schedule.getDays().add(day);
        }
    }

    private Profile resolveProfile(UUID profileId) {
        return profileId == null ? null : profileRepository.findById(profileId).orElse(null);
    }
}
