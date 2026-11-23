package az.fitnest.user.service;

import az.fitnest.user.dto.PaginatedResponse;
import az.fitnest.user.dto.response.AdminUserResponse;
import az.fitnest.user.dto.response.UserStatisticsResponse;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    PaginatedResponse<AdminUserResponse> getAllUsers(Pageable pageable, Long packageId, Integer packageDuration, String subscriptionStatus, String sort, String search, java.util.List<String> roles);
    UserStatisticsResponse getUserStatistics();
    /** Drop list/statistics caches so freeze counts refresh immediately after admin actions. */
    void invalidateListCaches();
    az.fitnest.user.dto.response.AdminUserDetailResponse getUserDetail(Long userId);
}
