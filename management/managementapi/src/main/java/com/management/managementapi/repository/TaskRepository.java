package com.management.managementapi.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.management.managementapi.model.Task;
import com.management.managementapi.model.enums.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("""
        select t
        from Task t
        where (:status is null or t.status = :status)
          and (:assigneeId is null or exists (
                select 1 from TaskAssignee ta
                where ta.task = t and ta.profile.id = :assigneeId))
          and (:q is null or :q = '' or lower(t.name) like lower(concat('%', :q, '%')))
    """)
    Page<Task> search(
        @Param("status") TaskStatus status,
        @Param("assigneeId") UUID assigneeId,
        @Param("q") String q,
        Pageable pageable
    );
}
