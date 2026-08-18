package com.management.managementapi.service.employee;

import com.management.managementapi.dto.employee.*;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.profile.EmailUpdateRequest;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.service.ProfileService;

import jakarta.validation.ValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;


@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditorAware<UUID> auditorAware;
    private final ProfileService profileService;

    public EmployeeServiceImpl(NamedParameterJdbcTemplate jdbc, AuditorAware<UUID> auditorAware,
                               ProfileService profileService) {
        this.jdbc = jdbc;
        this.auditorAware = auditorAware;
        this.profileService = profileService;
    }

    // ---------- Helpers

    private EmployeeResponseDTO mapRow(Map<String, Object> row) {
        return new EmployeeResponseDTO(
                (UUID) row.get("id"),
                (UUID) row.get("auth_user_id"),
                (String) row.get("name"),
                (String) row.get("email"),
                (String) row.get("phone_number"),
                (String) row.get("photo_url"),
                (String) row.get("role"),
                (String) row.get("account_status"),
                toOffsetDateTime(row.get("created_at")),
                toOffsetDateTime(row.get("updated_at")),
                isSelf((UUID) row.get("id"))
        );
    }

    /**
     * O auditor traz o id do <b>profile</b> autenticado (não o de auth.users) e é
     * cacheado por request, por isso chamar isto por linha não custa consultas.
     */
    private boolean isSelf(@Nullable UUID profileId) {
        if (profileId == null) return false;
        return auditorAware.getCurrentAuditor().map(profileId::equals).orElse(false);
    }

    private String baseSelect() {
        return """
            select
              p.id,
              p.auth_user_id,
              p.name,
              p.phone_number,
              p.photo_url,
              p.role::text as role,
              p.account_status::text as account_status,
              p.created_at,
              p.updated_at,
              u.email
            from worksite.profile p
            left join auth.users u on u.id = p.auth_user_id
            """;
    }

    private void applyFilters(StringBuilder sql, MapSqlParameterSource params,
                          @Nullable String q, @Nullable String role, @Nullable String status) {
        sql.append(" where 1=1 ");
        sql.append(" and p.account_status != 'deleted' ");

        if (q != null && !q.isBlank()) {
            sql.append("""
                and (
                      p.name ilike concat('%%', :q, '%%')
                   or p.phone_number ilike concat('%%', :q, '%%')
                   or u.email ilike concat('%%', :q, '%%')
                )
            """);
            params.addValue("q", q);
        }
        if (role != null && !role.isBlank()) {
            sql.append(" and p.role = cast(:role as worksite.role_enum) ");
            params.addValue("role", role);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and p.account_status = cast(:status as worksite.account_status_enum) ");
            params.addValue("status", status);
        }
    }

    private String orderByCreatedAt(String dir) {
        String d = (dir == null) ? "desc" : dir.trim().toLowerCase(Locale.ROOT);
        return d.equals("asc") ? " order by p.created_at asc " : " order by p.created_at desc ";
    }

    // ---------- Queries

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponseDTO getById(UUID id) {
        String sql = baseSelect() + " where p.id = :id";
        try {
            Map<String, Object> row = jdbc.queryForMap(sql, new MapSqlParameterSource("id", id));
            return mapRow(row);
        } catch (EmptyResultDataAccessException ex) {
            throw new NotFoundException("Funcionário não encontrado: " + id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponseDTO> search(@Nullable String q, @Nullable String role, @Nullable String status,
                                            Pageable pageable, @Nullable String createdAtSortDir) {

        StringBuilder sql = new StringBuilder(baseSelect());
        MapSqlParameterSource params = new MapSqlParameterSource();
        applyFilters(sql, params, q, role, status);

        // total
        String countSql = "select count(*) from (" + sql + ") s";
        Long totalRaw = jdbc.queryForObject(countSql, params, Long.class);
        long total = totalRaw != null ? totalRaw : 0L;

        // paginação
        sql.append(orderByCreatedAt(createdAtSortDir));
        sql.append(" offset :offset limit :limit ");
        params.addValue("offset", (long) pageable.getPageNumber() * pageable.getPageSize());
        params.addValue("limit", pageable.getPageSize());

        List<Map<String, Object>> rows = Objects.requireNonNull(jdbc.queryForList(Objects.requireNonNull(sql.toString(), "sql"), params), "queryForList");
        List<EmployeeResponseDTO> content = Objects.requireNonNull(rows.stream().map(this::mapRow).toList(), "content");

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeAssignableDTO> listAssignable() {
        String sql = """
            select p.id, p.name, u.email, p.role::text as role
            from worksite.profile p
            left join auth.users u on u.id = p.auth_user_id
            where p.account_status != 'deleted'
            order by p.name asc
            """;

        return jdbc.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new EmployeeAssignableDTO(
                (UUID) rs.getObject("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("role")
        ));
    }

    @Transactional
    @Override
    public EmployeeResponseDTO update(UUID id, EmployeeUpdateRequestDTO dto) {

        // 1) buscar auth_user_id e email atual (para decidir se é preciso alterar o email)
        var row = jdbc.queryForObject("""
            select p.auth_user_id, u.email
              from worksite.profile p
              left join auth.users u on u.id = p.auth_user_id
             where p.id = :id
        """,
        new MapSqlParameterSource("id", id),
        (rs, i) -> new Object[] { rs.getObject("auth_user_id", java.util.UUID.class), rs.getString("email") });

        if (row == null) {
            throw new NotFoundException("Funcionário não encontrado: " + id);
        }

        UUID authUserId = (UUID) row[0];
        String currentEmail = (String) row[1];

        // 2) se o DTO trouxer email e for diferente do atual, chama o profileService.updateEmail
        if (dto.email() != null && !dto.email().equalsIgnoreCase(currentEmail)) {
            if (authUserId == null) {
                throw new ValidationException("Este funcionário não tem utilizador de autenticação associado.");
            }
            EmailUpdateRequest emailReq = new EmailUpdateRequest();
            emailReq.setEmail(dto.email().trim());
            profileService.updateEmail(authUserId, emailReq);
        }

        // 3) atualizar os restantes campos no perfil
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", dto.name())
                .addValue("phone", dto.phoneNumber())
                .addValue("role", dto.role());

        int updated = jdbc.update("""
            update worksite.profile set
              name = :name,
              phone_number = :phone,
              role = cast(:role as worksite.role_enum),
              updated_at = now()
            where id = :id
        """, params);

        if (updated == 0) throw new NotFoundException("Funcionário não encontrado: " + id);

        return getById(id);
    }

    @Override
    public EmployeeResponseDTO updateRole(UUID id, EmployeeRolePatchRequestDTO dto) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("role", dto.role());

        int updated = jdbc.update("""
            update worksite.profile set
              role = cast(:role as worksite.role_enum)
            where id = :id
            """, params);

        if (updated == 0) throw new NotFoundException("Funcionário não encontrado: " + id);

        log.debug("employee.patchRole byActor={} targetId={} newRole={}",
                currentActorOrNull(), id, dto.role());

        return getById(id);
    }

    @Override
    public EmployeeResponseDTO updateAvatar(UUID id, EmployeeAvatarUpdateRequestDTO dto) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("avatar", dto.avatarUrl());

        int updated = jdbc.update("""
            update worksite.profile set
              photo_url = :avatar
            where id = :id
            """, params);

        if (updated == 0) throw new NotFoundException("Funcionário não encontrado: " + id);

        log.debug("employee.updateAvatar byActor={} targetId={}", currentActorOrNull(), id);

        return getById(id);
    }

    private @Nullable UUID currentActorOrNull() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            try { return UUID.fromString(auth.getName()); } catch (Exception ignore) { return null; }
        } catch (Exception e) { return null; }
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (value instanceof java.util.Date d) return d.toInstant().atOffset(ZoneOffset.UTC);
        if (value instanceof String s) return OffsetDateTime.parse(s);
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Não consigo converter para OffsetDateTime: " + value.getClass());
    }

    // ---------- Recycle bin

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> getDeletedProfiles() {
        String sql = baseSelect() +
                " where p.account_status = cast('deleted' as worksite.account_status_enum)" +
                " order by p.updated_at desc";

        return jdbc.queryForList(sql, new MapSqlParameterSource())
                .stream()
                .map(this::mapRow)
                .toList();
    }

    @Override
    public EmployeeResponseDTO restoreProfile(UUID id) {
        int updated = jdbc.update("""
                update worksite.profile
                   set account_status = cast('unlocked' as worksite.account_status_enum),
                       updated_at     = now()
                 where id             = :id
                   and account_status = cast('deleted' as worksite.account_status_enum)
                """,
                new MapSqlParameterSource("id", id));

        if (updated == 0) {
            throw new BusinessException(ErrorCode.PROFILE_NOT_DELETED,
                    "O utilizador não existe ou não está no estado eliminado");
        }

        return getById(id);
    }

    @Override
    public EmployeeResponseDTO blockProfile(UUID id) {
        int updated = jdbc.update("""
                update worksite.profile
                   set account_status = cast('blocked' as worksite.account_status_enum),
                       updated_at     = now()
                 where id             = :id
                   and account_status = cast('unlocked' as worksite.account_status_enum)
                """,
                new MapSqlParameterSource("id", id));

        if (updated == 0) {
            throw new BusinessException(ErrorCode.PROFILE_CANNOT_BLOCK,
                    "O utilizador não pode ser bloqueado (já bloqueado, eliminado ou não existe)");
        }

        return getById(id);
    }

    @Override
    public EmployeeResponseDTO unblockProfile(UUID id) {
        int updated = jdbc.update("""
                update worksite.profile
                   set account_status = cast('unlocked' as worksite.account_status_enum),
                       updated_at     = now()
                 where id             = :id
                   and account_status = cast('blocked' as worksite.account_status_enum)
                """,
                new MapSqlParameterSource("id", id));

        if (updated == 0) {
            throw new BusinessException(ErrorCode.PROFILE_CANNOT_UNBLOCK,
                    "O utilizador não pode ser desbloqueado (não está bloqueado ou não existe)");
        }

        return getById(id);
    }

    @Override
    public void deleteProfile(UUID id) {
        // Um admin a eliminar-se a si próprio perdia o acesso sem que ninguém
        // pudesse repor a conta a não ser por outro admin — e pode não haver outro.
        if (isSelf(id)) {
            throw new BusinessException(ErrorCode.PROFILE_CANNOT_DELETE_SELF,
                    "Não pode eliminar a sua própria conta");
        }

        int updated = jdbc.update("""
                update worksite.profile
                   set account_status = cast('deleted' as worksite.account_status_enum),
                       updated_at     = now()
                 where id             = :id
                   and account_status != cast('deleted' as worksite.account_status_enum)
                """,
                new MapSqlParameterSource("id", id));

        if (updated == 0) {
            throw new BusinessException(ErrorCode.PROFILE_CANNOT_DELETE,
                    "O utilizador já está eliminado ou não existe");
        }
    }

}
