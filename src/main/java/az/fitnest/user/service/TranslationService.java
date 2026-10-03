package az.fitnest.user.service;

import az.fitnest.user.model.entity.Translation;
import az.fitnest.user.repository.TranslationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class TranslationService {

    private final TranslationRepository translationRepository;

    public TranslationService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    public String getTranslatedValue(String entityType, String entityId, String fieldName, String userLanguage) {
        if (userLanguage == null || userLanguage.equalsIgnoreCase("AZ")) {
            return null;
        }

        String existingValue = translationRepository.findByEntityTypeAndEntityIdAndLanguageCodeAndFieldName(
                entityType,
                entityId,
                userLanguage.toUpperCase(),
                fieldName
        )
        .map(Translation::getFieldValue)
        .orElse(null);

        if (existingValue != null) {
            return existingValue;
        }

        if (entityType != null && (entityType.equalsIgnoreCase("GoalReference") || entityType.equalsIgnoreCase("Goal"))) {
            return null;
        }

        if (entityType != null && entityType.equalsIgnoreCase("Gender")) {
            // Manual fixed vocabulary: no machine translation.
            if (entityId != null && entityId.equalsIgnoreCase("MALE")) {
                if (userLanguage.equalsIgnoreCase("EN")) return "Male";
                if (userLanguage.equalsIgnoreCase("RU")) return "Мужчина";
            } else if (entityId != null && entityId.equalsIgnoreCase("FEMALE")) {
                if (userLanguage.equalsIgnoreCase("EN")) return "Female";
                if (userLanguage.equalsIgnoreCase("RU")) return "Женщина";
            }
            return null;
        }

        // Manual translations only: AZ lives on its own entity table, EN/RU live in
        // the translations table (admin-provided). No machine translation.
        return null;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TranslationService.class);

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void saveOrUpdateTranslation(String entityType, String entityId, String languageCode, String fieldName, String fieldValue) {
        String normalizedEntityType = entityType;
        String normalizedLanguageCode = languageCode.toUpperCase();

        log.info("Database Save: entityType={}, entityId={}, languageCode={}, fieldName={}, fieldValue='{}'", 
            normalizedEntityType, entityId, normalizedLanguageCode, fieldName, fieldValue);

        Translation existing = translationRepository.findByEntityTypeAndEntityIdAndLanguageCodeAndFieldName(
                normalizedEntityType, entityId, normalizedLanguageCode, fieldName
        ).orElse(null);

        if (existing != null) {
            log.info("Updating existing translation record ID={}", existing.getId());
            existing.setFieldValue(fieldValue);
            translationRepository.save(existing);
        } else {
            log.info("Creating new translation record");
            Translation translation = Translation.builder()
                    .entityType(normalizedEntityType)
                    .entityId(entityId)
                    .languageCode(normalizedLanguageCode)
                    .fieldName(fieldName)
                    .fieldValue(fieldValue)
                    .build();
            translationRepository.save(translation);
        }
    }
}
