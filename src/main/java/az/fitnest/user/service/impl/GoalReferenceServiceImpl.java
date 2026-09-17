package az.fitnest.user.service.impl;

import az.fitnest.user.client.CachedIdentityGrpcClient;
import az.fitnest.user.client.StorageGrpcClient;
import az.fitnest.user.dto.response.GoalItemResponse;
import az.fitnest.user.exception.BadRequestException;
import az.fitnest.user.exception.ConflictException;
import az.fitnest.user.exception.ResourceNotFoundException;
import az.fitnest.user.model.entity.GoalReference;
import az.fitnest.user.model.entity.Translation;
import az.fitnest.user.repository.GoalReferenceRepository;
import az.fitnest.user.repository.TranslationRepository;
import az.fitnest.user.service.FileStorageService;
import az.fitnest.user.service.GoalReferenceService;
import az.fitnest.user.service.TranslationService;
import az.fitnest.user.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalReferenceServiceImpl implements GoalReferenceService {

    private final GoalReferenceRepository goalReferenceRepository;
    private final TranslationRepository translationRepository;
    private final FileStorageService fileStorageService;
    private final CachedIdentityGrpcClient cachedIdentityGrpcClient;
    private final TranslationService translationService;
    private final StorageGrpcClient storageGrpcClient;

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "goals-all", key = "#root.target.resolveLanguageForCache()")
    public List<GoalItemResponse> getAllGoals() {
        String userLanguage = getUserLanguage();
        List<GoalReference> goals = goalReferenceRepository.findAllByOrderByGoalCodeAsc();
        return goals.stream().map(goal -> mapToResponse(goal, userLanguage)).collect(Collectors.toList());
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "goal-by-code", key = "{#code, #root.target.resolveLanguageForCache()}")
    public GoalItemResponse getGoalByCode(String code) {
        GoalReference goal = goalReferenceRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));
        return mapToResponse(goal, getUserLanguage());
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "public-goals-all", key = "#root.target.resolvePublicLanguage(#language)")
    public List<GoalItemResponse> getPublicGoals(String language) {
        String publicLanguage = resolvePublicLanguage(language);
        List<GoalReference> goals = goalReferenceRepository.findAllByOrderByGoalCodeAsc();
        return goals.stream().map(goal -> mapPublicResponse(goal, publicLanguage)).collect(Collectors.toList());
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "public-goal-by-code", key = "{#code, #root.target.resolvePublicLanguage(#language)}")
    public GoalItemResponse getPublicGoalByCode(String code, String language) {
        GoalReference goal = goalReferenceRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));
        return mapPublicResponse(goal, resolvePublicLanguage(language));
    }

    @Override
    public StreamingResponseBody streamGoalImage(String fsId) {
        return outputStream -> {
            outputStream.write(downloadGoalImage(fsId));
            outputStream.flush();
        };
    }

    @Override
    public byte[] downloadGoalImage(String fsId) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        storageGrpcClient.downloadFile(fsId, response -> {
            if (response.hasFileData()) {
                try {
                    buffer.write(response.getFileData().toByteArray());
                } catch (IOException e) {
                }
            }
        });
        return buffer.toByteArray();
    }

    @Transactional
    @Override
    @org.springframework.cache.annotation.CacheEvict(value = {"goals-all", "goal-by-code", "public-goals-all", "public-goal-by-code"}, allEntries = true)
    public GoalReference createGoal(String code, String title, String subtitle, MultipartFile image) {
        if (goalReferenceRepository.existsById(code)) {
            throw new ConflictException("error.resource_already_exists");
        }
        GoalReference goal = new GoalReference();
        goal.setGoalCode(code);
        goal.setTitle(title);
        goal.setSubtitle(subtitle);

        if (image != null && !image.isEmpty()) {
            validateImage(image);
            String fsId = fileStorageService.saveFile(image, "/goals");
            goal.setImageUrl("/api/v1/goals/images/" + fsId);
        }

        goalReferenceRepository.save(goal);

        translationService.autoTranslateAndSave("GoalReference", code, "title", title);
        translationService.autoTranslateAndSave("GoalReference", code, "subtitle", subtitle);

        return goal;
    }

    @Transactional
    @Override
    @org.springframework.cache.annotation.CacheEvict(value = {"goals-all", "goal-by-code", "public-goals-all", "public-goal-by-code"}, allEntries = true)
    public GoalReference updateGoal(String code, String title, String subtitle, MultipartFile image) {
        GoalReference goal = goalReferenceRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));

        goal.setTitle(title);
        goal.setSubtitle(subtitle);

        if (image != null && !image.isEmpty()) {
            validateImage(image);
            String fsId = fileStorageService.saveFile(image, "/goals", goal.getImageUrl());
            goal.setImageUrl("/api/v1/goals/images/" + fsId);
        }

        goalReferenceRepository.save(goal);

        translationService.autoTranslateAndSave("GoalReference", code, "title", title);
        translationService.autoTranslateAndSave("GoalReference", code, "subtitle", subtitle);

        return goal;
    }

    @Transactional
    @Override
    @org.springframework.cache.annotation.CacheEvict(value = {"goals-all", "goal-by-code", "public-goals-all", "public-goal-by-code"}, allEntries = true)
    public void deleteGoal(String code) {
        GoalReference goal = goalReferenceRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.goal_reference_not_found"));

        if (goal.getImageUrl() != null && !goal.getImageUrl().isBlank()) {
            try {
                fileStorageService.deleteFile(goal.getImageUrl());
            } catch (Exception e) {
            }
        }

        goalReferenceRepository.deleteById(code);
    }

    private GoalItemResponse mapToResponse(GoalReference goal, String userLanguage) {
        String title = translationService.getTranslatedValue("GoalReference", goal.getGoalCode(), "title", userLanguage);
        if (title == null || title.isBlank()) {
            title = goal.getTitle();
        }
        String subtitle = translationService.getTranslatedValue("GoalReference", goal.getGoalCode(), "subtitle", userLanguage);
        if (subtitle == null || subtitle.isBlank()) {
            subtitle = goal.getSubtitle();
        }
        return GoalItemResponse.builder()
                .code(goal.getGoalCode())
                .title(title)
                .subtitle(subtitle)
                .imageUrl(getFullImageUrl(goal.getImageUrl()))
                .build();
    }

    private GoalItemResponse mapPublicResponse(GoalReference goal, String language) {
        GoalItemResponse item = mapToResponse(goal, language);
        return GoalItemResponse.builder()
                .code(item.code())
                .title(item.title())
                .subtitle(item.subtitle())
                .imageUrl(getPublicImageUrl(item.imageUrl()))
                .build();
    }

    private void updateOrSaveTranslation(String entityId, String lang, String field, String value) {
        translationRepository.findByEntityTypeAndEntityIdAndLanguageCodeAndFieldName("GoalReference", entityId, lang, field)
                .ifPresentOrElse(t -> {
                    t.setFieldValue(value);
                    translationRepository.save(t);
                }, () -> {
                    Translation t = Translation.builder()
                            .entityType("GoalReference")
                            .entityId(entityId)
                            .languageCode(lang)
                            .fieldName(field)
                            .fieldValue(value)
                            .build();
                    translationRepository.save(t);
                });
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("error.file_required");
        if (file.getSize() > 5 * 1024 * 1024) throw new BadRequestException("error.file_size_limit");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            throw new BadRequestException("error.only_images_allowed");
    }

    private void createTranslationIfNotFound(String goalCode, String languageCode, String title, String subtitle) {
        if (!translationRepository.existsByEntityTypeAndEntityIdAndLanguageCodeAndFieldName("GoalReference", goalCode, languageCode, "title")) {
            translationRepository.save(Translation.builder().entityType("GoalReference").entityId(goalCode).languageCode(languageCode).fieldName("title").fieldValue(title).build());
        }
        if (!translationRepository.existsByEntityTypeAndEntityIdAndLanguageCodeAndFieldName("GoalReference", goalCode, languageCode, "subtitle")) {
            translationRepository.save(Translation.builder().entityType("GoalReference").entityId(goalCode).languageCode(languageCode).fieldName("subtitle").fieldValue(subtitle).build());
        }
    }

    public String resolvePublicLanguage(String requestedLanguage) {
        String explicit = normalizeLanguage(requestedLanguage);
        if (explicit != null) {
            return explicit;
        }
        return resolveRequestLanguage();
    }

    public String resolveLanguageForCache() {
        return getUserLanguage();
    }

    private String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String token = value.trim().split("[,;]")[0].trim();
        int separator = Math.max(token.indexOf('-'), token.indexOf('_'));
        String code = separator > 0 ? token.substring(0, separator) : token;
        if (code.length() >= 2) {
            String lang = code.substring(0, 2).toUpperCase();
            if (lang.equals("EN") || lang.equals("RU") || lang.equals("AZ")) {
                return lang;
            }
        }
        return null;
    }

    private String getUserLanguage() {
        // 1. First check if user is authenticated and get language from their profile (Authorization / JWT)
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            try {
                String profileLang = cachedIdentityGrpcClient.getUserById(userId).language();
                if (profileLang != null && !profileLang.trim().isEmpty()) {
                    return profileLang.toUpperCase();
                }
            } catch (Exception e) {
            }
        }

        // 2. Fallback to Accept-Language header (unauthenticated / anonymous requests)
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = 
                        ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage != null && !acceptLanguage.trim().isEmpty()) {
                    String localeLang = org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage()
                            .toUpperCase();
                    if (localeLang.equals("EN") || localeLang.equals("RU") || localeLang.equals("AZ")) {
                        return localeLang;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "AZ";
    }

    private String resolveRequestLanguage() {
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request =
                        ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                String fromHeader = normalizeLanguage(request.getHeader("Accept-Language"));
                if (fromHeader != null) {
                    return fromHeader;
                }
                String localeLang = normalizeLanguage(
                        org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
                if (localeLang != null) {
                    return localeLang;
                }
            }
        } catch (Exception ignored) {
        }

        return "AZ";
    }

    private String getPublicImageUrl(String imageUrl) {
        String full = getFullImageUrl(imageUrl);
        if (full == null) return null;
        String fileId = extractGoalImageId(full);
        if (fileId != null) {
            return "/api/v1/public/landing/goals/images/" + fileId;
        }
        return full;
    }

    private String getFullImageUrl(String fsId) {
        if (fsId == null || fsId.trim().isEmpty()) return null;
        String value = fsId.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
            return value;
        }
        return "/api/v1/goals/images/" + value;
    }

    private String extractGoalImageId(String imageUrl) {
        String marker = "/goals/images/";
        int index = imageUrl.indexOf(marker);
        if (index >= 0) {
            String id = imageUrl.substring(index + marker.length());
            int query = id.indexOf('?');
            if (query >= 0) {
                id = id.substring(0, query);
            }
            int hash = id.indexOf('#');
            if (hash >= 0) {
                id = id.substring(0, hash);
            }
            return id.isBlank() ? null : id;
        }
        if (!imageUrl.contains("/") && !imageUrl.contains("://")) {
            return imageUrl;
        }
        return null;
    }
}
