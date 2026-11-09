package com.materialbook.users;

import com.materialbook.common.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** The authenticated administrator, resolved fresh from the database (not from the JWT). */
public record Actor(Long id, boolean superAdmin, Set<String> permissions) {
    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", HttpStatus.FORBIDDEN, message);
    }

    /** Privilege-escalation guard: only a SUPER_ADMIN may hand out permissions it does not hold itself. */
    public void requireCanGrant(Set<String> codes) {
        if (!superAdmin && !permissions.containsAll(codes)) {
            throw forbidden("You cannot grant permissions you do not hold yourself");
        }
    }
}
