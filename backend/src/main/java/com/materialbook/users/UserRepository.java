package com.materialbook.users;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByMobile(String mobile);

    boolean existsByMobileAndIdNot(String mobile, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** {@code pattern} is a lower-cased LIKE pattern (see UserService); {@code status} may be null for "any". */
    @Query("""
            select u from User u
            where (:status is null or u.status = :status)
              and (lower(u.fullName) like :pattern escape '\\' or u.mobile like :pattern escape '\\')
            """)
    Page<User> search(@Param("pattern") String pattern, @Param("status") UserStatus status, Pageable pageable);

    @Query("select count(u) from User u join u.roles r where r.roleCode = :roleCode and u.status = com.materialbook.users.UserStatus.ACTIVE")
    long countActiveWithRole(@Param("roleCode") String roleCode);

    /** Row lock so concurrent failed logins cannot under-count towards lockout. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.mobile = :mobile")
    Optional<User> findByMobileForUpdate(@Param("mobile") String mobile);

    /** Transaction-scoped advisory lock: serialises "is this the last SUPER_ADMIN?" decisions across different target rows. */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(7019001)) t", nativeQuery = true)
    long lockSuperAdminChanges();

    @Query("select case when count(u) > 0 then true else false end from User u join u.roles r where r.roleCode = :roleCode")
    boolean existsWithRole(@Param("roleCode") String roleCode);

    @Query("select distinct p.permissionCode from User u join u.roles r join r.permissions p where u.id = :userId")
    Set<String> findRolePermissionCodes(@Param("userId") Long userId);
}
