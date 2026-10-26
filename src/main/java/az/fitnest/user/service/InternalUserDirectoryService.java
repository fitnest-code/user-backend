package az.fitnest.user.service;

import az.fitnest.user.dto.response.InternalUserSummary;

import java.util.Collection;
import java.util.List;

/**
 * Read-only user directory used by other backend services (payment, notifications, ...)
 * to label rows with a human identity instead of a bare userId.
 */
public interface InternalUserDirectoryService {

    /**
     * Resolve identities for the given ids. Missing users are skipped, order follows the input.
     */
    List<InternalUserSummary> getUsers(Collection<Long> userIds);

    /**
     * Resolve ids matching a free-text query (id, name, email, mobile).
     */
    List<Long> searchUserIds(String query, int limit);
}
