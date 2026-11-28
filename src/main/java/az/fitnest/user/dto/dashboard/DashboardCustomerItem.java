package az.fitnest.user.dto.dashboard;

public record DashboardCustomerItem(
        Long id,
        String fullName,
        String phoneNumber,
        String email,
        String userStatus,
        String subscriptionStatus,
        String role,
        String gymName,
        String platform,
        String registrationDate,
        String purchaseDate
) {}
