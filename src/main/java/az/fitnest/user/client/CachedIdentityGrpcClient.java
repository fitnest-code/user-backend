package az.fitnest.user.client;

import az.fitnest.user.dto.response.IdentityUserResponse;
import az.fitnest.user.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedIdentityGrpcClient {

    private final IdentityGrpcClient identityGrpcClient;

    @Cacheable(cacheNames = "identity_users", key = "#userId", sync = true)
    public IdentityUserResponse getUserById(Long userId) {
        try {
            UserResponse raw = identityGrpcClient.getUserById(userId);
            return toDto(raw);
        } catch (Exception e) {
            // Return minimal DTO instead of throwing - prevents cascade failures
            log.warn("Failed to fetch user {} from identity-backend: {}", userId, e.getMessage());
            return IdentityUserResponse.builder()
                    .userId(userId)
                    .language("AZ")
                    .build();
        }
    }

    @CacheEvict(cacheNames = "identity_users", key = "#userId")
    public IdentityUserResponse updateUserProfile(Long userId, String firstName, String lastName) {
        UserResponse raw = identityGrpcClient.updateUserProfile(userId, firstName, lastName);
        return toDto(raw);
    }

    public az.fitnest.user.grpc.OtpSendResponseProto requestEmailChange(Long userId, String newEmail) {
        return identityGrpcClient.requestEmailChange(userId, newEmail);
    }

    @CacheEvict(cacheNames = "identity_users", key = "#userId")
    public IdentityUserResponse confirmEmailChange(Long userId, String otpSessionId, String otpCode) {
        UserResponse raw = identityGrpcClient.confirmEmailChange(userId, otpSessionId, otpCode);
        return toDto(raw);
    }

    public az.fitnest.user.grpc.OtpSendResponseProto requestMobileChange(Long userId, String newMobile) {
        return identityGrpcClient.requestMobileChange(userId, newMobile);
    }

    @CacheEvict(cacheNames = "identity_users", key = "#userId")
    public IdentityUserResponse confirmMobileChange(Long userId, String otpSessionId, String otpCode) {
        UserResponse raw = identityGrpcClient.confirmMobileChange(userId, otpSessionId, otpCode);
        return toDto(raw);
    }

    @CacheEvict(cacheNames = "identity_users", key = "#userId")
    public void updateProfileImage(Long userId, String imageUrl) {
        identityGrpcClient.updateProfileImage(userId, imageUrl);
    }

    @CacheEvict(cacheNames = "identity_users", key = "#userId")
    public void updateLanguage(Long userId, String language) {
        identityGrpcClient.updateLanguage(userId, language);
    }

    @CacheEvict(cacheNames = "identity_users", key = "#userId")
    public void updateSetupRequired(Long userId, boolean required) {
        identityGrpcClient.updateSetupRequired(userId, required);
    }

    private IdentityUserResponse toDto(UserResponse r) {
        return IdentityUserResponse.builder()
                .userId(r.getUserId())
                .firstName(r.getFirstName())
                .lastName(r.getLastName())
                .mobile(r.getMobile())
                .email(r.getEmail())
                .profileImageUrl(r.getProfileImageUrl())
                .language(r.getLanguage())
                .setupRequired(r.getSetupRequired())
                .createdAt(r.getCreatedAt())
                .hasLocalPassword(r.getHasLocalPassword())
                .build();
    }
}
