package az.fitnest.user.service;

import az.fitnest.user.dto.request.*;
import az.fitnest.user.dto.response.*;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {
    SummaryResponse getUserSummary();

    UserProfileResponse getUserMe();

    UserProfileV2Response getUserMeV2();

    LocationResponse updateMyLocation(UpdateLocationRequest request);

    void updateBody(UpdateBodyRequest request);

    UserProfileResponse updateUserMe(UpdateUserProfileRequest request);

    void updateProfileImage(MultipartFile file);

    void updateGoal(UpdateGoalsRequest request);

    BodyInfoResponse getBodyInfo(String language);

    GoalResponse getGoal(String language);

    void updatePreferences(UpdatePreferencesRequest request);

    void updateLanguage(UpdateLanguageRequest request);

    GoalsResponse getReferenceGoals();

    SetupResponse getSetupStatus();

    FitnessLevelResponse getFitnessLevel(String language);

    CompleteSetupResponse completeSetup();

    CompleteSetupResponse skipSetup();

    SetupResponse setupProfile(SetupRequest request);

    List<LanguageDto> getAllLanguages();

    LanguageDto getCurrentLanguage();

    BmiCalculatorResponse calculateBmi(BmiCalculatorRequest request);

    void deleteProfileImage();
}
