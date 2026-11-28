package az.fitnest.user.service.dashboard;

import az.fitnest.user.dto.dashboard.DashboardSavedViewRequest;
import az.fitnest.user.dto.dashboard.DashboardSavedViewResponse;
import az.fitnest.user.model.entity.DashboardSavedView;
import az.fitnest.user.repository.DashboardSavedViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardSavedViewService {

    private final DashboardSavedViewRepository repository;

    @Transactional(readOnly = true)
    public List<DashboardSavedViewResponse> list(Long adminUserId) {
        return repository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DashboardSavedViewResponse create(Long adminUserId, DashboardSavedViewRequest request) {
        DashboardSavedView view = new DashboardSavedView();
        view.setAdminUserId(adminUserId);
        view.setName(request.name().trim());
        view.setFiltersJson(request.filtersJson());
        return toResponse(repository.save(view));
    }

    @Transactional
    public void delete(Long adminUserId, Long viewId) {
        DashboardSavedView view = repository.findByIdAndAdminUserId(viewId, adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved view not found"));
        repository.delete(view);
    }

    private DashboardSavedViewResponse toResponse(DashboardSavedView view) {
        return new DashboardSavedViewResponse(
                view.getId(),
                view.getName(),
                view.getFiltersJson(),
                view.getCreatedAt() != null ? view.getCreatedAt().toString() : null
        );
    }
}
