package az.fitnest.user.service.impl;

import az.fitnest.user.client.IdentityGrpcClient;
import az.fitnest.user.dto.response.InternalUserPageResponse;
import az.fitnest.user.dto.response.InternalUserSummary;
import az.fitnest.user.exception.BadRequestException;
import az.fitnest.user.model.entity.UserProfile;
import az.fitnest.user.repository.UserProfileRepository;
import az.fitnest.user.service.InternalUserDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalUserDirectoryServiceImpl implements InternalUserDirectoryService {

    private static final int MAX_LIMIT = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final UserProfileRepository userProfileRepository;
    private final IdentityGrpcClient identityGrpcClient;

    @Override
    @Transactional(readOnly = true)
    public List<InternalUserSummary> getUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, UserProfile> profiles = userProfileRepository
                .findAllByUserIdIn(ids, Pageable.unpaged())
                .getContent()
                .stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (left, right) -> left));

        Map<Long, az.fitnest.user.grpc.UserResponse> identities = fetchIdentities(ids);

        return ids.stream()
                .map(id -> {
                    UserProfile profile = profiles.get(id);
                    az.fitnest.user.grpc.UserResponse identity = identities.get(id);
                    return new InternalUserSummary(
                            id,
                            firstNonBlank(profile != null ? profile.getFirstName() : null,
                                    identity != null ? identity.getFirstName() : null),
                            firstNonBlank(profile != null ? profile.getLastName() : null,
                                    identity != null ? identity.getLastName() : null),
                            identity != null && !identity.getMobile().isBlank() ? identity.getMobile() : null,
                            profile != null ? profile.getEmail() : null);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> searchUserIds(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmed = query.trim();
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        Long numericQuery = parseLong(trimmed);

        Set<Long> matches = new LinkedHashSet<>();
        if (numericQuery != null) {
            matches.add(numericQuery);
        }
        matches.addAll(userProfileRepository.searchIdsByQuery(trimmed, numericQuery));

        // Mobile numbers live in the identity service, not in user_profiles.
        if (trimmed.matches(".*\\d.*")) {
            try {
                matches.addAll(identityGrpcClient.searchUserIdsByMobile(trimmed));
            } catch (Exception e) {
                log.warn("Mobile search failed for query '{}': {}", trimmed, e.getMessage());
            }
        }

        return matches.stream().limit(capped).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InternalUserPageResponse getUsersByMobilePrefix(String prefix, int page, int size) {
        String normalized = normalizePrefix(prefix);
        if (normalized == null) {
            throw new BadRequestException("prefix iki rəqəmli mobil prefiks olmalıdır (məs. 55)");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        az.fitnest.user.grpc.ListUsersByMobilePrefixResponse response =
                identityGrpcClient.listUsersByMobilePrefix(normalized, safePage, safeSize);
        List<az.fitnest.user.grpc.UserResponse> identities = response.getUsersList();
        List<Long> ids = identities.stream()
                .map(az.fitnest.user.grpc.UserResponse::getUserId)
                .toList();

        Map<Long, UserProfile> profiles = ids.isEmpty()
                ? Map.of()
                : userProfileRepository.findAllByUserIdIn(ids, Pageable.unpaged())
                        .getContent()
                        .stream()
                        .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (left, right) -> left));

        List<InternalUserSummary> users = identities.stream()
                .map(identity -> {
                    UserProfile profile = profiles.get(identity.getUserId());
                    return new InternalUserSummary(
                            identity.getUserId(),
                            firstNonBlank(profile != null ? profile.getFirstName() : null, identity.getFirstName()),
                            firstNonBlank(profile != null ? profile.getLastName() : null, identity.getLastName()),
                            identity.getMobile() != null && !identity.getMobile().isBlank() ? identity.getMobile() : null,
                            profile != null ? profile.getEmail() : null);
                })
                .toList();

        return new InternalUserPageResponse(
                users,
                response.getPage(),
                response.getSize(),
                response.getTotalElements(),
                response.getTotalPages(),
                response.getHasNext());
    }

    private static String normalizePrefix(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("994") && digits.length() > 3) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0") && digits.length() > 1) {
            digits = digits.substring(1);
        }
        return digits.matches("\\d{2}") ? digits : null;
    }

    private Map<Long, az.fitnest.user.grpc.UserResponse> fetchIdentities(List<Long> ids) {
        try {
            return identityGrpcClient.getUsersByIds(ids).stream()
                    .collect(Collectors.toMap(az.fitnest.user.grpc.UserResponse::getUserId,
                            Function.identity(), (left, right) -> left));
        } catch (Exception e) {
            log.warn("Identity lookup failed for {} user(s): {}", ids.size(), e.getMessage());
            return Map.of();
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback != null && !fallback.isBlank() ? fallback : null;
    }
}
