package az.fitnest.user.service.impl;

import az.fitnest.user.client.IdentityGrpcClient;
import az.fitnest.user.client.OrderGrpcClient;
import az.fitnest.user.client.PaymentCoinGrpcClient;
import az.fitnest.user.dto.PaginatedResponse;
import az.fitnest.user.dto.response.AdminUserResponse;
import az.fitnest.user.dto.response.UserStatisticsResponse;
import az.fitnest.user.model.entity.UserProfile;
import az.fitnest.user.repository.UserProfileRepository;
import az.fitnest.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserProfileRepository userProfileRepository;
    private final IdentityGrpcClient identityGrpcClient;
    private final OrderGrpcClient orderGrpcClient;
    private final az.fitnest.user.repository.GoalReferenceRepository goalReferenceRepository;
    private final az.fitnest.user.client.DevicePlatformGrpcClient devicePlatformGrpcClient;
    private final CatalogGrpcClient catalogGrpcClient;
    private final PaymentCoinGrpcClient paymentCoinGrpcClient;

    private static final java.util.concurrent.ExecutorService grpcExecutor = 
        java.util.concurrent.Executors.newFixedThreadPool(16, new java.util.concurrent.ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "grpc-client-pool-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

    @Override
    @Transactional(readOnly = true)
    public az.fitnest.user.dto.response.AdminUserDetailResponse getUserDetail(Long userId) {
        log.info("Fetching detailed user info for admin. User ID: {}", userId);

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found: " + userId));

        String fullName = (profile.getFirstName() != null ? profile.getFirstName() : "") +
                         (profile.getLastName() != null ? " " + profile.getLastName() : "");

        String registerDate = "";
        String phoneNumber = "";
        String email = profile.getEmail();
        String role = "";
        try {
            var identityUser = identityGrpcClient.getUserById(userId);
            registerDate = identityUser.getCreatedAt();
            phoneNumber = identityUser.getMobile();
            role = identityUser.getRole();
            if (email == null || email.isEmpty()) {
                email = identityUser.getEmail();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch identity info for user detail {}: {}", userId, e.getMessage());
        }

        String platform = "N/A";
        try {
            platform = devicePlatformGrpcClient.getUserPlatform(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch platform info for user detail {}: {}", userId, e.getMessage());
        }

        String goalTitle = "N/A";
        if (profile.getGoalCode() != null) {
            goalTitle = goalReferenceRepository.findById(profile.getGoalCode())
                    .map(az.fitnest.user.model.entity.GoalReference::getTitle)
                    .orElse("N/A");
        }

        Double bmi = null;
        if (profile.getHeightCm() != null && profile.getWeightKg() != null && profile.getHeightCm() > 0) {
            double heightMeters = profile.getHeightCm() / 100.0;
            bmi = profile.getWeightKg() / (heightMeters * heightMeters);
            bmi = Math.round(bmi * 10.0) / 10.0;
        }

        java.math.BigDecimal coinBalance = java.math.BigDecimal.ZERO;
        java.math.BigDecimal coinAznEquivalent = java.math.BigDecimal.ZERO;
        try {
            PaymentCoinGrpcClient.CoinWalletSnapshot coins = paymentCoinGrpcClient.getCoinWallet(userId);
            if (coins.coinBalance() != null) {
                coinBalance = coins.coinBalance();
            }
            if (coins.aznEquivalent() != null) {
                coinAznEquivalent = coins.aznEquivalent();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch coin wallet for user detail {}: {}", userId, e.getMessage());
        }

        return az.fitnest.user.dto.response.AdminUserDetailResponse.builder()
                .userId(userId)
                .fullName(fullName.trim())
                .registrationDate(registerDate)
                .platform(platform)
                .phoneNumber(phoneNumber)
                .email(email)
                .birthDate(profile.getBirthDate())
                .goalTitle(goalTitle)
                .height(profile.getHeightCm())
                .weight(profile.getWeightKg())
                .bmiIndex(bmi)
                .role(role)
                .coinBalance(coinBalance)
                .coinAznEquivalent(coinAznEquivalent)
                .build();
    }

    // Caching strategy note: Use a short TTL (2-5 minutes) for admin-users cache in your cache configuration (e.g., Redis, Ehcache).
    // For statistics, prefer a scheduled refresh (every 10 minutes) and return cached value immediately.
    @Override
    @Cacheable(value = "admin-users", key = "{#pageable.pageNumber, #pageable.pageSize, #packageId, #packageDuration, #subscriptionStatus, #sort, #search, #roles}")
    public PaginatedResponse<AdminUserResponse> getAllUsers(Pageable pageable, Long packageId, Integer packageDuration, String subscriptionStatus, String sort, String search, List<String> roles) {
        log.info("Fetching all users with filters: packageId={}, duration={}, status={}, sort={}, search={}, roles={}", packageId, packageDuration, subscriptionStatus, sort, search, roles);

        List<Long> filteredUserIds = null;
        String orderSort = null;
        org.springframework.data.domain.Sort localSort = org.springframework.data.domain.Sort.unsorted();

        if (sort != null) {
            if (sort.equalsIgnoreCase("finishDate_asc")) orderSort = "FINISH_DATE_ASC";
            else if (sort.equalsIgnoreCase("finishDate_desc")) orderSort = "FINISH_DATE_DESC";
            else if (sort.equalsIgnoreCase("newest")) localSort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "userId");
            else if (sort.equalsIgnoreCase("name_asc")) localSort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "firstName");
            else if (sort.equalsIgnoreCase("name_desc")) localSort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "firstName");
            else if (sort.equalsIgnoreCase("registrationDate_desc")) localSort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "userId");
            else if (sort.equalsIgnoreCase("registrationDate_asc")) localSort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "userId");
        }

        if (localSort.isSorted()) {
            pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), localSort);
        }

        if (packageId != null || packageDuration != null || subscriptionStatus != null || orderSort != null) {
            try {
                filteredUserIds = orderGrpcClient.getFilteredUserIds(packageId, packageDuration, subscriptionStatus, orderSort);
                log.info("Filtered user IDs from order-backend: {}", filteredUserIds.size());
            } catch (Exception e) {
                log.error("Failed to fetch filtered user IDs from order-backend", e);
            }
        }

        if (roles != null && !roles.isEmpty()) {
            try {
                List<Long> roleUserIds = identityGrpcClient.getUserIdsByRoles(roles);
                filteredUserIds = intersect(filteredUserIds, roleUserIds);
                log.info("Filtered user IDs by roles {}: {}", roles, roleUserIds.size());
            } catch (Exception e) {
                log.error("Failed to fetch user IDs by roles from identity-backend", e);
            }
        }

        boolean hasSearch = search != null && !search.trim().isEmpty();

        Page<UserProfile> profiles;
        if (filteredUserIds != null) {
            if (filteredUserIds.isEmpty()) {
                return new PaginatedResponse<>(List.of(), 0, pageable.getPageNumber() + 1, pageable.getPageSize());
            }

            if (hasSearch) {
                Long searchUserId = parseUserId(search);
                List<Long> mobileUserIds = searchMobileUserIds(search);

                java.util.Set<Long> searchMatchIds = getSearchMatchedUserIds(search, searchUserId, mobileUserIds);
                filteredUserIds = filteredUserIds.stream()
                        .filter(searchMatchIds::contains)
                        .collect(Collectors.toList());

                if (filteredUserIds.isEmpty()) {
                    return new PaginatedResponse<>(List.of(), 0, pageable.getPageNumber() + 1, pageable.getPageSize());
                }
            }

            boolean isIdBasedSort = sort == null 
                    || sort.equalsIgnoreCase("newest") 
                    || sort.equalsIgnoreCase("registrationDate_desc") 
                    || sort.equalsIgnoreCase("registrationDate_asc");

            if (orderSort != null || isIdBasedSort) {
                List<Long> mutableIds = new java.util.ArrayList<>(filteredUserIds);
                if (isIdBasedSort) {
                    if (sort == null || sort.equalsIgnoreCase("newest") || sort.equalsIgnoreCase("registrationDate_desc")) {
                        mutableIds.sort(java.util.Comparator.reverseOrder());
                    } else {
                        mutableIds.sort(java.util.Comparator.naturalOrder());
                    }
                }

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), mutableIds.size());
                if (start >= mutableIds.size()) {
                    return new PaginatedResponse<>(List.of(), mutableIds.size(), pageable.getPageNumber() + 1, pageable.getPageSize());
                }

                // Only fetch user profiles for the current page of user IDs
                List<Long> pageIds = mutableIds.subList(start, end);
                List<UserProfile> profileList = new ArrayList<>(userProfileRepository.findAllByUserIdIn(pageIds, Pageable.unpaged()).getContent());
                // Maintain the order of pageIds
                profileList.sort(java.util.Comparator.comparingInt(p -> pageIds.indexOf(p.getUserId())));

                List<AdminUserResponse> items = mapToResponse(profileList, roles);
                return new PaginatedResponse<>(items, mutableIds.size(), pageable.getPageNumber() + 1, pageable.getPageSize());
            } else {
                // Name-based sort: sort in-memory using lightweight name projections
                List<UserProfileRepository.UserIdNameProjection> nameList = fetchNamesInBatches(filteredUserIds);
                final boolean asc = sort != null && sort.equalsIgnoreCase("name_asc");
                nameList.sort((a, b) -> {
                    String nameA = (a.getFirstName() != null ? a.getFirstName() : "") + " " + (a.getLastName() != null ? a.getLastName() : "");
                    String nameB = (b.getFirstName() != null ? b.getFirstName() : "") + " " + (b.getLastName() != null ? b.getLastName() : "");
                    return asc ? nameA.compareToIgnoreCase(nameB) : nameB.compareToIgnoreCase(nameA);
                });

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), nameList.size());
                if (start >= nameList.size()) {
                    return new PaginatedResponse<>(List.of(), nameList.size(), pageable.getPageNumber() + 1, pageable.getPageSize());
                }
                List<Long> pageIds = nameList.subList(start, end).stream()
                        .map(UserProfileRepository.UserIdNameProjection::getUserId)
                        .collect(Collectors.toList());

                List<UserProfile> profileList = new ArrayList<>(userProfileRepository.findAllByUserIdIn(pageIds, Pageable.unpaged()).getContent());
                profileList.sort(java.util.Comparator.comparingInt(p -> pageIds.indexOf(p.getUserId())));

                List<AdminUserResponse> items = mapToResponse(profileList, roles);
                return new PaginatedResponse<>(items, nameList.size(), pageable.getPageNumber() + 1, pageable.getPageSize());
            }
        } else if (hasSearch) {
            Long searchUserId = parseUserId(search);
            List<Long> mobileUserIds = searchMobileUserIds(search);

            if (mobileUserIds != null && !mobileUserIds.isEmpty()) {
                profiles = userProfileRepository.searchByQueryOrUserIds(search.trim(), searchUserId, mobileUserIds, pageable);
            } else {
                profiles = userProfileRepository.searchByQuery(search.trim(), searchUserId, pageable);
            }
        } else {
            profiles = userProfileRepository.findAll(pageable);
        }

        List<AdminUserResponse> items = mapToResponse(profiles.getContent(), roles);

        return new PaginatedResponse<>(
                items,
                profiles.getTotalElements(),
                profiles.getNumber() + 1,
                profiles.getSize()
        );
    }

    private Long parseUserId(String search) {
        try {
            return Long.parseLong(search.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> searchMobileUserIds(String search) {
        String trimmed = search.trim();
        if (!trimmed.matches(".*\\d.*")) {
            return List.of();
        }
        try {
            return identityGrpcClient.searchUserIdsByMobile(trimmed);
        } catch (Exception e) {
            log.warn("Failed to search users by mobile via gRPC: {}", e.getMessage());
            return List.of();
        }
    }

    private java.util.Set<Long> getSearchMatchedUserIds(String search, Long searchUserId, List<Long> mobileUserIds) {
        List<Long> localMatches = userProfileRepository.searchIdsByQuery(search.trim(), searchUserId);

        java.util.Set<Long> matchedIds = new java.util.HashSet<>(localMatches);
        if (mobileUserIds != null) {
            matchedIds.addAll(mobileUserIds);
        }
        return matchedIds;
    }

    private List<AdminUserResponse> mapToResponse(List<UserProfile> profiles, List<String> roles) {
        if (profiles.isEmpty()) return List.of();

        List<Long> userIds = profiles.stream().map(UserProfile::getUserId).collect(Collectors.toList());

        boolean fetchSubscriptions = (roles == null || roles.isEmpty() || roles.contains("ROLE_USER"));
        boolean fetchGymAdmins = (roles == null || roles.isEmpty() || roles.contains("ROLE_GYM_ADMIN") || roles.contains("ROLE_GYM_SUPER_ADMIN"));

        // Fetch identity, order and catalog data in parallel using dedicated grpcExecutor pool
        java.util.concurrent.CompletableFuture<java.util.Map<Long, az.fitnest.user.grpc.UserResponse>> identityFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return identityGrpcClient.getUsersByIds(userIds).stream()
                        .collect(Collectors.toMap(u -> u.getUserId(), u -> u));
            } catch (Exception e) {
                log.warn("Identity gRPC failed", e);
                return java.util.Collections.<Long, az.fitnest.user.grpc.UserResponse>emptyMap();
            }
        }, grpcExecutor);

        java.util.concurrent.CompletableFuture<java.util.Map<Long, az.fitnest.order.grpc.ActiveSubscriptionResponse>> orderFuture;
        if (fetchSubscriptions) {
            orderFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return orderGrpcClient.getActiveSubscriptions(userIds);
                } catch (Exception e) {
                    log.warn("Order gRPC failed", e);
                    return java.util.Collections.<Long, az.fitnest.order.grpc.ActiveSubscriptionResponse>emptyMap();
                }
            }, grpcExecutor);
        } else {
            orderFuture = java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyMap());
        }

        java.util.concurrent.CompletableFuture<java.util.List<az.fitnest.catalog.grpc.GymAdminDetail>> catalogFuture;
        if (fetchGymAdmins) {
            catalogFuture = identityFuture.thenApplyAsync(identityMap -> {
                List<String> phoneNumbers = identityMap.values().stream()
                        .map(az.fitnest.user.grpc.UserResponse::getMobile)
                        .filter(p -> p != null && !p.isBlank())
                        .collect(Collectors.toList());
                List<String> emails = new ArrayList<>(profiles.stream()
                        .map(UserProfile::getEmail)
                        .filter(e -> e != null && !e.isBlank())
                        .collect(Collectors.toList()));
                identityMap.values().stream()
                        .map(az.fitnest.user.grpc.UserResponse::getEmail)
                        .filter(e -> e != null && !e.isBlank())
                        .forEach(emails::add);
                List<String> finalEmails = emails.stream().distinct().collect(Collectors.toList());

                try {
                    return catalogGrpcClient.getGymAdminsByUsers(userIds, phoneNumbers, finalEmails).getAdminsList();
                } catch (Exception e) {
                    log.warn("Catalog gRPC failed", e);
                    return java.util.Collections.emptyList();
                }
            }, grpcExecutor);
        } else {
            catalogFuture = java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }

        // Wait for all to finish
        java.util.concurrent.CompletableFuture.allOf(identityFuture, orderFuture, catalogFuture).join();
        java.util.Map<Long, az.fitnest.user.grpc.UserResponse> identityUsersMap = identityFuture.join();
        java.util.Map<Long, az.fitnest.order.grpc.ActiveSubscriptionResponse> subscriptionMap = orderFuture.join();
        java.util.List<az.fitnest.catalog.grpc.GymAdminDetail> gymAdminsList = catalogFuture.join();

        return profiles.stream()
                .map(profile -> {
                    String userStatus = "UNKNOWN";
                    String phoneNumber = "";
                    String createdAt = "";
                    String role = "";

                    var identityUser = identityUsersMap.get(profile.getUserId());
                    if (identityUser != null) {
                        userStatus = identityUser.getStatus();
                        phoneNumber = identityUser.getMobile();
                        createdAt = identityUser.getCreatedAt();
                        role = identityUser.getRole();
                    }

                    String subscriptionStatus = "";
                    var sub = subscriptionMap.get(profile.getUserId());
                    if (sub != null) {
                        subscriptionStatus = sub.getSubscriptionStatus();
                    }

                    String fullName = (profile.getFirstName() != null ? profile.getFirstName() : "") +
                                     (profile.getLastName() != null ? " " + profile.getLastName() : "");

                    String gymName = null;
                    final Long currentUserId = profile.getUserId();
                    final String currentPhone = identityUser != null ? identityUser.getMobile() : "";
                    final String currentEmail = profile.getEmail() != null ? profile.getEmail() : (identityUser != null ? identityUser.getEmail() : "");

                    az.fitnest.catalog.grpc.GymAdminDetail gymAdmin = gymAdminsList.stream()
                            .filter(admin -> {
                                if (admin.getUserId() > 0 && admin.getUserId() == currentUserId) {
                                    return true;
                                }
                                if (currentPhone != null && !currentPhone.isBlank() && currentPhone.equalsIgnoreCase(admin.getPhoneNumber())) {
                                    return true;
                                }
                                if (currentEmail != null && !currentEmail.isBlank() && currentEmail.equalsIgnoreCase(admin.getEmail())) {
                                    return true;
                                }
                                return false;
                            })
                            .findFirst()
                            .orElse(null);

                    if (gymAdmin != null) {
                        gymName = gymAdmin.getGymName();
                        String gymRole = gymAdmin.getRole();
                        if ("Super admin".equalsIgnoreCase(gymRole)) {
                            role = "ROLE_GYM_SUPER_ADMIN";
                        } else if ("Admin".equalsIgnoreCase(gymRole)) {
                            role = "ROLE_GYM_ADMIN";
                        }
                    }

                    return new AdminUserResponse(
                            profile.getUserId(),
                            fullName.trim(),
                            phoneNumber,
                            profile.getEmail(),
                            userStatus,
                            subscriptionStatus,
                            role,
                            gymName
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "user-statistics", key = "'all'")
    public UserStatisticsResponse getUserStatistics() {
        long totalUsers = 0;
        List<Long> roleUserIds = List.of();
        try {
            roleUserIds = identityGrpcClient.getUserIdsByRoles(List.of("ROLE_USER"));
            totalUsers = roleUserIds.isEmpty() ? 0 : userProfileRepository.countByUserIdIn(roleUserIds);
        } catch (Exception e) {
            log.error("Failed to fetch count of ROLE_USER from identity-backend", e);
            totalUsers = userProfileRepository.count(); // fallback to all
        }

        long usersWithLast7Days = 0;
        long finishedSubscriptions = 0;
        long activeOrFrozenSubscriptions = 0;

        if (!roleUserIds.isEmpty()) {
            try {
                final java.util.Set<Long> roleUserIdSet = new java.util.HashSet<>(roleUserIds);
                List<Long> last7DaysIds = orderGrpcClient.getFilteredUserIds(null, null, "LAST_7_DAYS", null);
                List<Long> finishedIds  = orderGrpcClient.getFilteredUserIds(null, null, "FINISHED", null);
                List<Long> activeIds    = orderGrpcClient.getFilteredUserIds(null, null, "ACTIVE", null);

                usersWithLast7Days          = last7DaysIds.stream().filter(roleUserIdSet::contains).distinct().count();
                finishedSubscriptions       = finishedIds.stream().filter(roleUserIdSet::contains).distinct().count();
                activeOrFrozenSubscriptions = activeIds.stream().filter(roleUserIdSet::contains).distinct().count();
            } catch (Exception e) {
                log.error("Failed to fetch filtered subscription statistics from order-backend", e);
            }
        }

        return UserStatisticsResponse.builder()
                .totalUsers(totalUsers)
                .usersWithLast7Days(usersWithLast7Days)
                .finishedSubscriptions(finishedSubscriptions)
                .activeOrFrozenSubscriptions(activeOrFrozenSubscriptions)
                .build();
    }

    private List<UserProfileRepository.UserIdNameProjection> fetchNamesInBatches(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<UserProfileRepository.UserIdNameProjection> result = new ArrayList<>();
        int batchSize = 1000;
        for (int i = 0; i < userIds.size(); i += batchSize) {
            List<Long> batch = userIds.subList(i, Math.min(i + batchSize, userIds.size()));
            result.addAll(userProfileRepository.findNamesByUserIds(batch));
        }
        return result;
    }

    private List<Long> intersect(List<Long> list1, List<Long> list2) {
        if (list1 == null)
            return list2;
        if (list2 == null)
            return list1;
        List<Long> mutable = new ArrayList<>(list1);
        mutable.retainAll(list2);
        return mutable;
    }
}
