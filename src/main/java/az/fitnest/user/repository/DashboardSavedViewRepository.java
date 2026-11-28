package az.fitnest.user.repository;

import az.fitnest.user.model.entity.DashboardSavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DashboardSavedViewRepository extends JpaRepository<DashboardSavedView, Long> {
    List<DashboardSavedView> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);

    Optional<DashboardSavedView> findByIdAndAdminUserId(Long id, Long adminUserId);
}
