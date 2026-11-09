package com.materialbook.audit.repository;

import com.materialbook.audit.entity.AuditLog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    record UserName(Long id, String fullName) {}

    /** One batched lookup of actor names for a page of rows (avoids N+1). */
    @Query("select new com.materialbook.audit.repository.AuditLogRepository$UserName(u.id, u.fullName) from User u where u.id in :ids")
    List<UserName> findUserNames(@Param("ids") Collection<Long> ids);
}
