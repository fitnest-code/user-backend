package az.fitnest.user.service.impl;

import az.fitnest.user.client.CachedIdentityGrpcClient;

import az.fitnest.user.dto.request.*;
import az.fitnest.user.dto.response.*;
import az.fitnest.user.mapper.UserProfileMapper;
import az.fitnest.user.client.OrderGrpcClient;
import az.fitnest.user.exception.BadRequestException;
import az.fitnest.user.exception.ConflictException;
import az.fitnest.user.exception.ResourceNotFoundException;
import az.fitnest.user.model.entity.UserLocation;
import az.fitnest.user.model.entity.UserProfile;
import az.fitnest.user.model.entity.GoalReference;
import az.fitnest.user.model.enums.Gender;
import az.fitnest.user.repository.UserLocationRepository;
import az.fitnest.user.repository.UserProfileRepository;
import az.fitnest.user.service.*;
import az.fitnest.user.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import az.fitnest.catalog.grpc.GymMainPage;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final CachedIdentityGrpcClient cachedIdentityClient;
    private final UserProfileRepository userProfileRepository;
    private final FileStorageService fileStorageService;
    private final UserLocationRepository userLocationRepository;
    private final az.fitnest.user.repository.GoalReferenceRepository goalReferenceRepository;
    private final TranslationService translationService;
    private final az.fitnest.user.client.StorageGrpcClient storageGrpcClient;
    private final CatalogGrpcClient catalogGrpcClient;
    private final OrderGrpcClient orderGrpcClient;
    private final LanguageService languageService;
    private final org.springframework.context.MessageSource messageSource;
    private final az.fitnest.user.client.NotificationsGrpcClient notificationsGrpcClient;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserProfileServiceImpl.class);

    private Long currentUserId() {
        return UserContext.getCurrentUserId();
    }

    @Cacheable(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()", sync = true)
    @Transactional(readOnly = true)
    @Override
    public SummaryResponse getUserSummary() {
        Long userId = UserContext.getCurrentUserId();
        IdentityUserResponse identityUser = cachedIdentityClient.getUserById(userId);
        UserProfile profile = getOrCreateProfile(userId);
        String profileImageUrl = formatProfileImageUrl(profile.getProfileImageUrl());
        String currentSubscription = null;
        String subscriptionStatus = null;
        String langCode = identityUser.language() != null && !identityUser.language().isBlank() ? identityUser.language() : "AZ";
        try {
            az.fitnest.order.grpc.ActiveSubscriptionResponse r = orderGrpcClient.getActiveSubscription(userId);
            if (r.getPackageName() != null && !r.getPackageName().isEmpty()) {
                currentSubscription = r.getPackageName();
            } else {
                currentSubscription = messageSource.getMessage("no_plan", null, new java.util.Locale(langCode.toLowerCase()));
            }
            if (r.getSubscriptionStatus() != null && !r.getSubscriptionStatus().isEmpty()
                    && !r.getSubscriptionStatus().equals("none")) {
                if (r.getSubscriptionStatus().equals("no_limits")) {
                    subscriptionStatus = "No Entry Limits Left";
                } else {
                    subscriptionStatus = r.getSubscriptionStatus();
                }
            }
        } catch (Exception e) {
            currentSubscription = messageSource.getMessage("no_plan", null, new java.util.Locale(langCode.toLowerCase()));
        }
        UserProfileResponse user = UserProfileMapper.toUserProfileResponse(identityUser, profile, profileImageUrl, currentSubscription, subscriptionStatus);

        CountersResponse counters = CountersResponse.builder()
                .favorite_gyms(0L)
                .favorite_stores(0L)
                .build();

        return SummaryResponse.builder()
                .user(user)
                .counters(counters)
                .unreadNotifications(0)
                .build();
    }

    @Cacheable(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()", sync = true)
    @Override
    public UserProfileResponse getUserMe() {
        Long userId = UserContext.getCurrentUserId();
        IdentityUserResponse identityUser = cachedIdentityClient.getUserById(userId);
        UserProfile profile = getOrCreateProfile(userId);
        String profileImageUrl = formatProfileImageUrl(profile.getProfileImageUrl());
        String currentSubscription = null;
        String subscriptionStatus = null;
        String langCode = identityUser.language() != null && !identityUser.language().isBlank() ? identityUser.language() : "AZ";
        try {
            logger.debug("Calling orderGrpcClient.getActiveSubscription for userId={}", userId);
            az.fitnest.order.grpc.ActiveSubscriptionResponse r = orderGrpcClient.getActiveSubscription(userId);
            logger.debug("Received ActiveSubscriptionResponse: package_name={}, subscription_status={}", r.getPackageName(), r.getSubscriptionStatus());
            if (r.getPackageName() != null && !r.getPackageName().isEmpty()) {
                currentSubscription = r.getPackageName();
            } else {
                logger.warn("No package_name returned for userId={}, defaulting to localized 'No Plan'", userId);
                currentSubscription = messageSource.getMessage("no_plan", null, new java.util.Locale(langCode.toLowerCase()));
            }
            if (r.getSubscriptionStatus() != null && !r.getSubscriptionStatus().isEmpty()
                    && !r.getSubscriptionStatus().equals("none")) {
                subscriptionStatus = r.getSubscriptionStatus();
            } else {
                logger.warn("No valid subscription_status returned for userId={}, defaulting to 'none'", userId);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch subscription for userId={}: {}", userId, e.getMessage(), e);
            currentSubscription = messageSource.getMessage("no_plan", null, new java.util.Locale(langCode.toLowerCase()));
        }
        Boolean notificationsEnabled = notificationsGrpcClient.getUserDeviceNotificationEnabled(userId);
        logger.debug("UserProfileResponse for userId={}: currentSubscription={}, subscriptionStatus={}, notificationsEnabled={}", userId, currentSubscription, subscriptionStatus, notificationsEnabled);
        return UserProfileMapper.toUserProfileResponse(identityUser, profile, profileImageUrl, currentSubscription, subscriptionStatus, notificationsEnabled);
    }

    private UserProfile getOrCreateProfile(Long userId) {
        return userProfileRepository.findById(userId).orElseGet(() -> {
            UserProfile newProfile = new UserProfile();
            newProfile.setUserId(userId);
            return userProfileRepository.save(newProfile);
        });
    }

    @Transactional
    @Override
    public LocationResponse updateMyLocation(UpdateLocationRequest request) {
        Long userId = UserContext.getCurrentUserId();

        var existingOpt = userLocationRepository.findById(userId);
        UserLocation location = existingOpt.orElseGet(() ->
                new UserLocation(userId, request.lat(), request.lng(), LocalDateTime.now())
        );

        boolean isNew = existingOpt.isEmpty();
        boolean latChanged = !Objects.equals(location.getLat(), request.lat());
        boolean lngChanged = !Objects.equals(location.getLng(), request.lng());

        if (isNew || latChanged || lngChanged) {
            location.setLat(request.lat());
            location.setLng(request.lng());
            location.setUpdatedAt(LocalDateTime.now());

            UserLocation saved = userLocationRepository.save(location);
            return LocationResponse.builder()
                    .lat(saved.getLat())
                    .lng(saved.getLng())
                    .updatedAt(saved.getUpdatedAt())
                    .build();
        }

        return LocationResponse.builder()
                .lat(location.getLat())
                .lng(location.getLng())
                .updatedAt(location.getUpdatedAt())
                .build();
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Transactional
    @Override
    public void updateBody(UpdateBodyRequest request) {
        checkSetupNotRequired();
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = getOrCreateProfile(userId);
        boolean dirty = false;
        if (request.heightCm() != null) {
            Double newHeight = request.heightCm().doubleValue();
            if (!Objects.equals(profile.getHeightCm(), newHeight)) {
                profile.setHeightCm(newHeight);
                dirty = true;
            }
        }
        if (request.weightKg() != null) {
            if (!Objects.equals(profile.getWeightKg(), request.weightKg())) {
                profile.setWeightKg(request.weightKg());
                dirty = true;
            }
        }
        if (request.gender() != null) {
            logger.info("[updateBody] Setting gender to: {} (was: {})", request.gender(), profile.getGender());
            if (!Objects.equals(profile.getGender(), request.gender())) {
                profile.setGender(request.gender());
                dirty = true;
            }
        }
        if (request.birthDate() != null) {
            if (!Objects.equals(profile.getBirthDate(), request.birthDate())) {
                profile.setBirthDate(request.birthDate());
                dirty = true;
            }
        }
        if (dirty) {
            logger.info("[updateBody] Saving profile for user {} with gender: {}", userId, profile.getGender());
            userProfileRepository.save(profile);
        }
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "identity_users", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "admin-users", allEntries = true)
    })
    @Override
    public UserProfileResponse updateUserMe(UpdateUserProfileRequest request) {
        Long userId = UserContext.getCurrentUserId();
        IdentityUserResponse updated = cachedIdentityClient.updateUserProfile(
                userId, request.firstName(), request.lastName()
        );
        UserProfile profile = getOrCreateProfile(userId);
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        userProfileRepository.save(profile);

        String profileImageUrl = formatProfileImageUrl(profile.getProfileImageUrl());
        String currentSubscription = null;
        String subscriptionStatus = null;
        String langCode = updated.language() != null && !updated.language().isBlank() ? updated.language() : "AZ";
        try {
            az.fitnest.order.grpc.ActiveSubscriptionResponse r = orderGrpcClient.getActiveSubscription(userId);
            if (r.getPackageName() != null && !r.getPackageName().isEmpty()) {
                currentSubscription = r.getPackageName();
            } else {
                currentSubscription = messageSource.getMessage("no_plan", null, new java.util.Locale(langCode.toLowerCase()));
            }
            if (r.getSubscriptionStatus() != null && !r.getSubscriptionStatus().isEmpty()
                    && !r.getSubscriptionStatus().equals("none")) {
                if (r.getSubscriptionStatus().equals("no_limits")) {
                    subscriptionStatus = "No Entry Limits Left";
                } else {
                    subscriptionStatus = r.getSubscriptionStatus();
                }
            }
        } catch (Exception e) {
            currentSubscription = messageSource.getMessage("no_plan", null, new java.util.Locale(langCode.toLowerCase()));
        }

        return UserProfileMapper.toUserProfileResponse(updated, profile, profileImageUrl, currentSubscription, subscriptionStatus);
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Override
    public void updateProfileImage(MultipartFile file) {
        validateImage(file);
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = getOrCreateProfile(userId);
        String oldImageUrl = profile.getProfileImageUrl();

        String fsId = fileStorageService.saveFile(file, "/profiles", oldImageUrl);
        String newImageUrl = "/api/v1/me/profile/images/" + fsId;
        profile.setProfileImageUrl(newImageUrl);
        userProfileRepository.save(profile);
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "identity_users", key = "#userId"),
        @CacheEvict(cacheNames = "user_me", key = "#userId"),
        @CacheEvict(cacheNames = "user_summaries", key = "#userId")
    })
    @Transactional
    public void updateProfileImageDirect(Long userId, String newImageUrl) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setProfileImageUrl(newImageUrl);
        userProfileRepository.save(profile);
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Transactional
    @Override
    public void updateGoal(UpdateGoalsRequest request) {
        checkSetupNotRequired();
        Long userId = UserContext.getCurrentUserId();

        goalReferenceRepository.findById(request.goalCode())
                .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));

        UserProfile profile = getOrCreateProfile(userId);

        if (!Objects.equals(profile.getGoalCode(), request.goalCode())) {
            profile.setGoalCode(request.goalCode());
            userProfileRepository.save(profile);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public BodyInfoResponse getBodyInfo(String language) {
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });

        String translatedGender = null;
        if (profile.getGender() != null) {
            translatedGender = translationService.getTranslatedValue("Gender", profile.getGender().name(), "label", language);
            if (translatedGender == null || translatedGender.isBlank()) {
                translatedGender = profile.getGender().name();
            }
        }

        return BodyInfoResponse.builder()
                .heightCm(profile.getHeightCm() != null ? profile.getHeightCm().intValue() : null)
                .weightKg(profile.getWeightKg())
                .gender(translatedGender)
                .birthDate(profile.getBirthDate())
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public GoalResponse getGoal(String language) {
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });

        String goalCode = profile.getGoalCode();
        if (goalCode == null || goalCode.isBlank()) {
            return null;
        }

        var reference = goalReferenceRepository.findById(goalCode)
                .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));

        String title = translationService.getTranslatedValue("GoalReference", goalCode, "title", language);
        if (title == null || title.isBlank()) {
            title = reference.getTitle();
        }
        String subtitle = translationService.getTranslatedValue("GoalReference", goalCode, "subtitle", language);
        if (subtitle == null || subtitle.isBlank()) {
            subtitle = reference.getSubtitle();
        }
        return UserProfileMapper.toGoalResponse(reference, goalCode, title, subtitle);
    }

    @Transactional(readOnly = true)
    @Override
    public GoalsResponse getReferenceGoals() {
        var items = goalReferenceRepository.findAllByOrderByGoalCodeAsc().stream()
                .map(goal -> {
                    String title = goal.getTitle();
                    String subtitle = goal.getSubtitle();
                    return GoalItemResponse.builder()
                            .code(goal.getGoalCode())
                            .title(title)
                            .subtitle(subtitle)
                            .imageUrl(goal.getImageUrl())
                            .build();
                })
                .toList();

        return GoalsResponse.builder().items(items).build();
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Override
    public void updatePreferences(UpdatePreferencesRequest request) {
        Long userId = UserContext.getCurrentUserId();
        Boolean notificationsEnabled = request.notificationsEnabled();
        notificationsGrpcClient.setUserNotificationPreference(userId, notificationsEnabled);
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "identity_users", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "users", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Override
    public void updateLanguage(UpdateLanguageRequest request) {
        String upperLang = request.language().toUpperCase();
        Long userId = UserContext.getCurrentUserId();
        
        IdentityUserResponse identityUser = cachedIdentityClient.getUserById(userId);
        String currentLang = identityUser.language();
        if (currentLang == null || currentLang.isBlank()) {
            currentLang = "AZ";
        }
        
        if (upperLang.equals(currentLang.toUpperCase())) {
            return;
        }
        
        try {
            languageService.getLanguageByCode(upperLang);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("error.invalid_language_code");
        }
        
        cachedIdentityClient.updateLanguage(userId, upperLang);
    }

    @Transactional(readOnly = true)
    @Override
    public SetupResponse getSetupStatus() {
        Long userId = UserContext.getCurrentUserId();
        IdentityUserResponse identityUser = cachedIdentityClient.getUserById(userId);

        return SetupResponse.builder()
                .setupRequired(identityUser.setupRequired())
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public FitnessLevelResponse getFitnessLevel(String language) {
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });

        Double bmiValue = null;
        if (profile.getHeightCm() != null && profile.getWeightKg() != null
                && profile.getGender() != null && profile.getBirthDate() != null) {
            double heightM = profile.getHeightCm() / 100.0;
            double bmi = profile.getWeightKg() / (heightM * heightM);
            bmi = Math.round(bmi * 10.0) / 10.0;
            bmiValue = bmi;
        }

        String goalTitle = null;
        if (profile.getGoalCode() != null && !profile.getGoalCode().isBlank()) {
            goalTitle = translationService.getTranslatedValue("GoalReference", profile.getGoalCode(), "title", language);
            if (goalTitle == null || goalTitle.isBlank()) {
                var refOpt = goalReferenceRepository.findById(profile.getGoalCode());
                if (refOpt.isPresent()) {
                    goalTitle = refOpt.get().getTitle();
                }
            }
        }

        return FitnessLevelResponse.builder()
                .bmi(bmiValue)
                .goal(goalTitle)
                .build();
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "identity_users", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Transactional
    @Override
    public CompleteSetupResponse completeSetup() {
        Long userId = UserContext.getCurrentUserId();

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.profile_not_found"));

        if (profile.getHeightCm() == null ||
                profile.getWeightKg() == null ||
                profile.getGoalCode() == null ||
                profile.getGender() == null ||
                profile.getBirthDate() == null) {
            throw new ConflictException("error.setup_not_finished");
        }

        cachedIdentityClient.updateSetupRequired(userId, false);

        return CompleteSetupResponse.builder()
                .setupRequired(false)
                .next(CompleteSetupResponse.NextSteps.builder()
                        .workoutPlanReady(true)
                        .nutritionPlanReady(true)
                        .build())
                .build();
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Transactional
    @Override
    public SetupResponse setupProfile(SetupRequest request) {
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = getOrCreateProfile(userId);

        if (request.profile() != null) {
            ProfileInfo info = request.profile();

            if (info.heightCm() != null) profile.setHeightCm(info.heightCm().doubleValue());
            if (info.weightKg() != null) profile.setWeightKg(info.weightKg());
            if (info.gender() != null) {
                logger.info("[setupProfile] Setting gender to: {} (was: {})", info.gender(), profile.getGender());
                try {
                    profile.setGender(Gender.valueOf(info.gender().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("error.invalid_gender");
                }
            }
            if (info.birthDate() != null) profile.setBirthDate(info.birthDate());
            if (info.goal() != null) {
                goalReferenceRepository.findById(info.goal())
                        .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));
                profile.setGoalCode(info.goal());
            }
        }
        logger.info("[setupProfile] Saving profile for user {} with gender: {}", userId, profile.getGender());
        userProfileRepository.save(profile);

        return SetupResponse.builder()
                .setupRequired(false)
                .build();
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "identity_users", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Transactional
    @Override
    public CompleteSetupResponse skipSetup() {
        Long userId = UserContext.getCurrentUserId();

        cachedIdentityClient.updateSetupRequired(userId, false);

        return CompleteSetupResponse.builder()
                .setupRequired(false)
                .next(CompleteSetupResponse.NextSteps.builder()
                        .workoutPlanReady(false)
                        .nutritionPlanReady(false)
                        .build())
                .build();
    }

    @Override
    public List<LanguageDto> getAllLanguages() {
        return languageService.getAllLanguages();
    }

    @Override
    public LanguageDto getCurrentLanguage() {
        Long userId = UserContext.getCurrentUserId();
        IdentityUserResponse identityUser = cachedIdentityClient.getUserById(userId);
        String langCode = identityUser.language();
        if (langCode == null || langCode.isBlank()) {
            langCode = "AZ";
        }
        return languageService.getLanguageByCode(langCode);
    }

    public List<GymMainPage> getMainPageGymsFromCatalog() {
        return catalogGrpcClient.getMainPageGyms().getItemsList();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("error.file_required");
        }
    }

    private void checkSetupNotRequired() {
        Long userId = UserContext.getCurrentUserId();
        IdentityUserResponse identityUser = cachedIdentityClient.getUserById(userId);
        if (Boolean.TRUE.equals(identityUser.setupRequired())) {
            throw new BadRequestException("error.setup_not_finished");
        }
    }

    @Override
    public BmiCalculatorResponse calculateBmi(BmiCalculatorRequest request) {
        double heightCm = request.height();
        double weightKg = request.weight();
        if (heightCm <= 0 || weightKg <= 0) {
            throw new BadRequestException("Height and weight must be positive");
        }
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        String category;
        if (bmi < 18.5) {
            category = "Underweight";
        } else if (bmi < 25) {
            category = "Normal weight";
        } else if (bmi < 30) {
            category = "Overweight";
        } else {
            category = "Obesity";
        }
        return new BmiCalculatorResponse(bmi, category);
    }

    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(cacheNames = "user_me", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()"),
        @CacheEvict(cacheNames = "user_summaries", key = "T(az.fitnest.user.util.UserContext).getCurrentUserId()")
    })
    @Override
    public void deleteProfileImage() {
        Long userId = UserContext.getCurrentUserId();
        UserProfile profile = getOrCreateProfile(userId);
        String oldImageUrl = profile.getProfileImageUrl();

        if (oldImageUrl != null && !oldImageUrl.isBlank()) {
            try {
                fileStorageService.deleteFile(oldImageUrl);
            } catch (Exception e) {
                logger.error("Failed to delete profile image from storage: {}", oldImageUrl, e);
            }
            profile.setProfileImageUrl(null);
            userProfileRepository.save(profile);
            logger.info("Deleted profile image for user {}", userId);
        }
    }

    private String formatProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }
        if (profileImageUrl.startsWith("/")) {
            return profileImageUrl;
        }
        return "/api/v1/me/profile/images/" + profileImageUrl;
    }

}
