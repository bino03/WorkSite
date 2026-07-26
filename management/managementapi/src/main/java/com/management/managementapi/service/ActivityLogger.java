package com.management.managementapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.managementapi.dto.activity.ActivityLogCreateDTO;
import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Helper de registo de atividades assíncrono.
 *
 * IMPORTANTE: o HttpServletRequest é lido de forma SÍNCRONA antes de passar à
 * thread async — o Tomcat pode reciclar o objeto request após enviar a resposta,
 * o que causaria exceções silenciosas e perda de logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogger {

    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    public void logView(UUID userId, String userName,
                        EntityType entityType, UUID entityId, String entityName,
                        HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        logAsync(userId, userName, ActivityType.VIEW, entityType, entityId, entityName, null, null, ip, ua);
    }

    public void logCreate(UUID userId, String userName,
                          EntityType entityType, UUID entityId, String entityName,
                          HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        logAsync(userId, userName, ActivityType.CREATE, entityType, entityId, entityName, null, null, ip, ua);
    }

    public void logEdit(UUID userId, String userName,
                        EntityType entityType, UUID entityId, String entityName,
                        Map<String, Object> changes,
                        HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        String metadata = toJson(changes);
        logAsync(userId, userName, ActivityType.EDIT, entityType, entityId, entityName, null, metadata, ip, ua);
    }

    public void logDelete(UUID userId, String userName,
                          EntityType entityType, UUID entityId, String entityName,
                          HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        logAsync(userId, userName, ActivityType.DELETE, entityType, entityId, entityName, null, null, ip, ua);
    }

    public void logRestore(UUID userId, String userName,
                           EntityType entityType, UUID entityId, String entityName,
                           HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        logAsync(userId, userName, ActivityType.RESTORE, entityType, entityId, entityName, null, null, ip, ua);
    }

    public void logLogin(UUID userId, String userName, HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        logAsync(userId, userName, ActivityType.LOGIN, EntityType.USER, userId, userName, null, null, ip, ua);
    }

    public void logLogout(UUID userId, String userName, HttpServletRequest request) {
        String ip = extractIpAddress(request);
        String ua = extractUserAgent(request);
        logAsync(userId, userName, ActivityType.LOGOUT, EntityType.USER, userId, userName, null, null, ip, ua);
    }

    // -------------------------------------------------------------------------
    // Async interno — só recebe tipos simples (Strings/UUIDs), sem HttpServletRequest
    // -------------------------------------------------------------------------

    @Async
    protected void logAsync(UUID userId, String userName,
                            ActivityType activityType,
                            EntityType entityType, UUID entityId, String entityName,
                            String description, String metadata,
                            String ipAddress, String userAgent) {
        try {
            ActivityLogCreateDTO dto = new ActivityLogCreateDTO(
                userId, userName, activityType,
                entityType, entityId, entityName,
                description, metadata,
                ipAddress, userAgent
            );
            activityLogService.createActivity(dto);
        } catch (Exception e) {
            log.warn("Falha ao registar atividade [{} {} {}]: {}",
                     activityType, entityType, entityId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private String extractIpAddress(HttpServletRequest request) {
        if (request == null) return null;
        String[] headers = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        return request != null ? request.getHeader("User-Agent") : null;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Não foi possível serializar metadata para JSON: {}", e.getMessage());
            return null;
        }
    }
}
