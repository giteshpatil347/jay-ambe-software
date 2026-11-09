package com.materialbook.audit.service;

import com.materialbook.audit.dto.AuditLogResponse;
import com.materialbook.audit.entity.AuditLog;
import com.materialbook.audit.repository.AuditLogRepository;
import com.materialbook.audit.repository.AuditLogRepository.UserName;
import com.materialbook.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {
    private final AuditLogRepository repository;

    public AuditLogQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Newest first. {@code from}/{@code to} are inclusive UTC calendar dates. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String module, Long recordId, Long userId, String action,
                                                 LocalDate from, LocalDate to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (module != null) p.add(cb.equal(root.get("module"), module));
            if (recordId != null) p.add(cb.equal(root.get("recordId"), recordId));
            if (userId != null) p.add(cb.equal(root.get("userId"), userId));
            if (action != null) p.add(cb.equal(root.get("action"), action));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = repository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<Long> userIds = result.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = userIds.isEmpty() ? Map.of()
                : repository.findUserNames(userIds).stream().collect(Collectors.toMap(UserName::id, UserName::fullName));
        return PageResponse.of(result, a -> toResponse(a, names));
    }

    private static AuditLogResponse toResponse(AuditLog a, Map<Long, String> names) {
        return new AuditLogResponse(a.getId(), a.getUserId(), names.get(a.getUserId()), a.getAction(), a.getModule(),
                a.getRecordId(), a.getOldValues(), a.getNewValues(),
                a.getIpAddress() == null ? null : a.getIpAddress().getHostAddress(), a.getCreatedAt());
    }
}
