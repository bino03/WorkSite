package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.request.HolidayUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.enums.HolidayScope;
import com.management.managementapi.repository.attendance.HolidayRepository;

/**
 * os feriados. Tabela editável e não biblioteca de código: os municipais variam
 * por concelho e mudam de ano para ano.
 */
@Service
@Transactional
public class HolidayService {

    private final HolidayRepository repository;

    public HolidayService(HolidayRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Holiday> list() {
        return repository.findAllByOrderByDateAsc();
    }

    @Transactional(readOnly = true)
    public List<Holiday> listBetween(LocalDate from, LocalDate to) {
        return repository.findByDateBetweenOrderByDateAsc(from, to);
    }

    @Transactional(readOnly = true)
    public Holiday getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.HOLIDAY_NOT_FOUND, "Feriado " + id + " não encontrado"));
    }

    public Holiday create(HolidayUpsertDTO dto) {
        validate(dto);

        Holiday holiday = new Holiday();
        apply(holiday, dto);
        return repository.save(holiday);
    }

    public Holiday update(UUID id, HolidayUpsertDTO dto) {
        validate(dto);

        Holiday holiday = getById(id);
        apply(holiday, dto);
        return repository.save(holiday);
    }

    public void delete(UUID id) {
        repository.delete(getById(id));
    }

    /**
     * O concelho é obrigatório num feriado municipal e não faz sentido num
     * nacional. A base de dados tem o mesmo `CHECK`; aqui o erro diz qual dos dois
     * casos falhou, em vez de uma violação de integridade.
     */
    private void validate(HolidayUpsertDTO dto) {
        boolean hasMunicipality = dto.municipality() != null && !dto.municipality().isBlank();

        if (dto.scope() == HolidayScope.MUNICIPAL && !hasMunicipality) {
            throw new BusinessException(ErrorCode.HOLIDAY_MUNICIPALITY_REQUIRED);
        }
        if (dto.scope() == HolidayScope.NATIONAL && hasMunicipality) {
            throw new BusinessException(ErrorCode.HOLIDAY_MUNICIPALITY_NOT_ALLOWED);
        }
    }

    private void apply(Holiday holiday, HolidayUpsertDTO dto) {
        holiday.setDate(dto.date());
        holiday.setName(dto.name().trim());
        holiday.setScope(dto.scope());
        holiday.setMunicipality(dto.scope() == HolidayScope.MUNICIPAL ? dto.municipality().trim() : null);
    }
}
