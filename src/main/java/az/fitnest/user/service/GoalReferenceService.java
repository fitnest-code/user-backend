package az.fitnest.user.service;

import az.fitnest.user.model.entity.GoalReference;
import az.fitnest.user.dto.response.GoalItemResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

public interface GoalReferenceService {
    List<GoalItemResponse> getAllGoals();

    GoalItemResponse getGoalByCode(String code);

    List<GoalItemResponse> getPublicGoals(String language);

    GoalItemResponse getPublicGoalByCode(String code, String language);

    StreamingResponseBody streamGoalImage(String fsId);

    GoalReference createGoal(String code, String title, String subtitle, MultipartFile image);

    GoalReference updateGoal(String code, String title, String subtitle, MultipartFile image);

    void deleteGoal(String code);
}
