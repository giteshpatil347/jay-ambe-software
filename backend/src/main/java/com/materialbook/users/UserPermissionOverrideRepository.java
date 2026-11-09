package com.materialbook.users;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UserPermissionOverride.Key> {
    @Query("select o from UserPermissionOverride o join fetch o.permission where o.id.userId = :userId")
    List<UserPermissionOverride> findByUserId(@Param("userId") Long userId);

    @Query("select o from UserPermissionOverride o join fetch o.permission where o.id.userId in :userIds")
    List<UserPermissionOverride> findByUserIds(@Param("userIds") Collection<Long> userIds);
}
