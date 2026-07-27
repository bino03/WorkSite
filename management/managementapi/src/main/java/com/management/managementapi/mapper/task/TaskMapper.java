package com.management.managementapi.mapper.task;

import java.util.List;

import org.mapstruct.Mapper;

import com.management.managementapi.dto.task.response.TaskAssigneeDTO;
import com.management.managementapi.dto.task.response.TaskResponseDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.Task;
import com.management.managementapi.model.TaskAssignee;

@Mapper(config = GlobalMapperConfig.class)
public interface TaskMapper {

    TaskResponseDTO toResponse(Task entity);

    default TaskAssigneeDTO map(Profile profile) {
        return profile == null ? null : new TaskAssigneeDTO(profile.getId(), profile.getName());
    }

    default List<TaskAssigneeDTO> mapAssignees(List<TaskAssignee> assignees) {
        return assignees.stream().map(assignee -> map(assignee.getProfile())).toList();
    }
}
