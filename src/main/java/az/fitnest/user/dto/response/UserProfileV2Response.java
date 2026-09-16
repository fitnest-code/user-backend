package az.fitnest.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Builder
public record UserProfileV2Response(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("first_name") String firstName,
    @JsonProperty("last_name") String lastName,
    String mobile,
    String email,
    @JsonProperty("profile_image_url") String profileImageUrl,
    @JsonProperty("current_subscription") String currentSubscription,
    @JsonProperty("subscription_status") String subscriptionStatus,
    @JsonProperty("notifications_enabled") Boolean notificationsEnabled,
    @JsonProperty("has_local_password") Boolean hasLocalPassword,
    @JsonProperty("is_eligible_to_have_local_password") Boolean isEligibleToHaveLocalPassword,
    @JsonProperty("coin_balance") BigDecimal coinBalance,
    @JsonProperty("coin_azn_equivalent") BigDecimal coinAznEquivalent,
    @JsonProperty("coin_validity_date") String coinValidityDate
) {
    private static final DateTimeFormatter VALIDITY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static UserProfileV2Response from(
            UserProfileResponse profile,
            BigDecimal coinBalance,
            BigDecimal coinAznEquivalent,
            LocalDateTime coinValidityDate
    ) {
        return UserProfileV2Response.builder()
                .userId(profile.userId())
                .firstName(profile.firstName())
                .lastName(profile.lastName())
                .mobile(profile.mobile())
                .email(profile.email())
                .profileImageUrl(profile.profileImageUrl())
                .currentSubscription(profile.currentSubscription())
                .subscriptionStatus(profile.subscriptionStatus())
                .notificationsEnabled(profile.notificationsEnabled())
                .hasLocalPassword(profile.hasLocalPassword())
                .isEligibleToHaveLocalPassword(profile.isEligibleToHaveLocalPassword())
                .coinBalance(coinBalance != null ? coinBalance : BigDecimal.ZERO)
                .coinAznEquivalent(coinAznEquivalent != null ? coinAznEquivalent : BigDecimal.ZERO)
                .coinValidityDate(coinValidityDate != null ? coinValidityDate.format(VALIDITY_DATE_FORMAT) : null)
                .build();
    }
}
