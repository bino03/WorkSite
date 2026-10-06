package com.management.managementapi.mapper.attendance;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.management.managementapi.dto.attendance.response.WorkScheduleDayResponseDTO;
import com.management.managementapi.dto.attendance.response.WorkScheduleResponseDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.attendance.WorkScheduleDay;

@Mapper(config = GlobalMapperConfig.class)
public interface WorkScheduleMapper {

    @Mapping(target = "createdByName", source = "createdBy.name")
    @Mapping(target = "days", expression = "java(mapDays(entity.getDays()))")
    @Mapping(target = "weeklyMinutes", expression = "java(weeklyMinutes(entity.getDays()))")
    WorkScheduleResponseDTO toResponse(WorkSchedule entity);

    /** Ordenados por dia da semana: a lista da base de dados não tem ordem garantida. */
    default List<WorkScheduleDayResponseDTO> mapDays(List<WorkScheduleDay> days) {
        return days.stream()
                .sorted(Comparator.comparingInt(WorkScheduleDay::getWeekday))
                .map(this::mapDay)
                .toList();
    }

    default WorkScheduleDayResponseDTO mapDay(WorkScheduleDay day) {
        return new WorkScheduleDayResponseDTO(
                day.getId(),
                day.getDayOfWeek(),
                day.getStartTime(),
                day.getEndTime(),
                day.getBreakMinutes(),
                day.expectedWork().toMinutes());
    }

    default long weeklyMinutes(List<WorkScheduleDay> days) {
        return days.stream().mapToLong(day -> day.expectedWork().toMinutes()).sum();
    }
}
