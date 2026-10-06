package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.request.EmploymentTermDTO;
import com.management.managementapi.dto.attendance.request.EmploymentUpsertDTO;
import com.management.managementapi.dto.attendance.response.EmploymentResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.attendance.EmploymentMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.WorkScheduleRepository;

/**
 * os dados de emprego de um funcionário: admissão, horário atribuído e dias de
 * férias por ano.
 *
 * <p>As condições têm histórico: mudar o horário não reescreve o passado, fecha o
 * período em vigor no dia anterior e abre um novo. É o que faz um relatório de
 * janeiro continuar correto depois de uma mudança em março.
 */
@Service
@Transactional
public class EmploymentService {

    private final EmploymentRepository repository;
    private final WorkScheduleRepository workScheduleRepository;
    private final ProfileRepository profileRepository;
    private final EmploymentMapper mapper;

    public EmploymentService(EmploymentRepository repository,
                             WorkScheduleRepository workScheduleRepository,
                             ProfileRepository profileRepository,
                             EmploymentMapper mapper) {
        this.repository = repository;
        this.workScheduleRepository = workScheduleRepository;
        this.profileRepository = profileRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EmploymentResponseDTO> list() {
        return repository.findAllByOrderByHiredAtDesc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Employment getByProfileId(UUID profileId) {
        return repository.findByProfileId(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.EMPLOYMENT_NOT_FOUND,
                        "O funcionário " + profileId + " não tem dados de emprego registados"));
    }

    @Transactional(readOnly = true)
    public EmploymentResponseDTO getResponseByProfileId(UUID profileId) {
        return mapper.toResponse(getByProfileId(profileId));
    }

    public Employment create(EmploymentUpsertDTO dto) {
        if (repository.existsByProfileId(dto.profileId())) {
            throw new BusinessException(ErrorCode.EMPLOYMENT_ALREADY_EXISTS);
        }

        Employment employment = new Employment();
        employment.setProfile(resolveProfile(dto.profileId()));
        employment.setHiredAt(dto.hiredAt());
        employment.setEndedAt(dto.endedAt());

        EmploymentTerm firstTerm = new EmploymentTerm();
        firstTerm.setEmployment(employment);
        firstTerm.setWorkSchedule(resolveSchedule(dto.workScheduleId()));
        firstTerm.setVacationDaysPerYear(dto.vacationDaysPerYear());
        // O primeiro período começa na admissão: não há "antes" para cobrir.
        firstTerm.setValidFrom(dto.hiredAt());
        employment.getTerms().add(firstTerm);

        return repository.save(employment);
    }

    /** As datas do vínculo. As condições mudam-se por {@link #addTerm}, não aqui. */
    public Employment updateDates(UUID profileId, LocalDate hiredAt, LocalDate endedAt) {
        Employment employment = getByProfileId(profileId);

        if (endedAt != null && endedAt.isBefore(hiredAt)) {
            throw new BusinessException(ErrorCode.EMPLOYMENT_TERM_BEFORE_HIRE,
                    "A data de fim não pode ser antes da data de admissão");
        }
        boolean hasTermBeforeNewHire = employment.getTerms().stream()
                .anyMatch(term -> term.getValidFrom().isBefore(hiredAt));
        if (hasTermBeforeNewHire) {
            throw new BusinessException(ErrorCode.EMPLOYMENT_TERM_BEFORE_HIRE);
        }

        employment.setHiredAt(hiredAt);
        employment.setEndedAt(endedAt);
        return repository.save(employment);
    }

    /**
     * Muda as condições a partir de uma data. Fecha o período em vigor no dia
     * anterior e abre um novo — nunca altera um período já decorrido, que é o que
     * mantém os relatórios antigos corretos.
     */
    public Employment addTerm(UUID profileId, EmploymentTermDTO dto) {
        Employment employment = getByProfileId(profileId);

        if (dto.validFrom().isBefore(employment.getHiredAt())) {
            throw new BusinessException(ErrorCode.EMPLOYMENT_TERM_BEFORE_HIRE);
        }

        EmploymentTerm current = employment.currentTerm();
        if (current == null) {
            throw new BusinessException(ErrorCode.EMPLOYMENT_NO_CURRENT_TERM);
        }
        if (!dto.validFrom().isAfter(current.getValidFrom())) {
            throw new BusinessException(ErrorCode.EMPLOYMENT_TERM_NOT_AFTER_CURRENT);
        }

        current.setValidTo(dto.validFrom().minusDays(1));

        EmploymentTerm next = new EmploymentTerm();
        next.setEmployment(employment);
        next.setWorkSchedule(resolveSchedule(dto.workScheduleId()));
        next.setVacationDaysPerYear(dto.vacationDaysPerYear());
        next.setValidFrom(dto.validFrom());
        employment.getTerms().add(next);

        return repository.save(employment);
    }

    /**
     * As condições em vigor numa data — a porta de entrada do cálculo da fase 2.
     * Sem período que cubra a data, não há horário esperado e o dia não produz nem
     * atraso nem falta.
     */
    @Transactional(readOnly = true)
    public EmploymentTerm termOn(UUID profileId, LocalDate date) {
        return getByProfileId(profileId).termOn(date);
    }

    private Profile resolveProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_PROFILE_NOT_FOUND,
                        "Perfil " + profileId + " não encontrado"));
    }

    private WorkSchedule resolveSchedule(UUID scheduleId) {
        return workScheduleRepository.findByIdAndDeletedAtIsNull(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SCHEDULE_NOT_FOUND,
                        "Horário " + scheduleId + " não encontrado"));
    }
}
