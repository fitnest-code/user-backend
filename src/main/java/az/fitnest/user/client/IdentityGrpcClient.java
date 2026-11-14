package az.fitnest.user.client;

import az.fitnest.user.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import net.devh.boot.grpc.client.inject.GrpcClient;

import java.util.concurrent.TimeUnit;

@Service
public class IdentityGrpcClient {

    @GrpcClient("identity-backend")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @Value("${grpc.identity.deadline-ms:10000}")
    private long deadlineMs;

    private UserServiceGrpc.UserServiceBlockingStub withDeadline() {
        return userServiceStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }

    public az.fitnest.user.grpc.UserResponse getUserById(Long userId) {
        GetUserByIdRequest request = GetUserByIdRequest.newBuilder()
                .setUserId(userId)
                .build();

        return withDeadline().getUserById(request);
    }

    public java.util.List<az.fitnest.user.grpc.UserResponse> getUsersByIds(java.util.List<Long> userIds) {
        az.fitnest.user.grpc.GetUsersByIdsRequest request = az.fitnest.user.grpc.GetUsersByIdsRequest.newBuilder()
                .addAllUserIds(userIds)
                .build();
        return withDeadline().getUsersByIds(request).getUsersList();
    }

    public az.fitnest.user.grpc.UserResponse updateUserProfile(Long userId, String firstName, String lastName) {
        az.fitnest.user.grpc.UpdateUserProfileRequest request = az.fitnest.user.grpc.UpdateUserProfileRequest.newBuilder()
                .setUserId(userId)
                .setFirstName(firstName != null ? firstName : "")
                .setLastName(lastName != null ? lastName : "")
                .build();

        return withDeadline().updateUserProfile(request);
    }

    public az.fitnest.user.grpc.OtpSendResponseProto requestEmailChange(Long userId, String newEmail) {
        RequestEmailChangeRequest request = RequestEmailChangeRequest.newBuilder()
                .setUserId(userId)
                .setNewEmail(newEmail)
                .build();

        return withDeadline().requestEmailChange(request);
    }

    public az.fitnest.user.grpc.UserResponse confirmEmailChange(Long userId, String otpSessionId, String otpCode) {
        ConfirmEmailChangeRequest request = ConfirmEmailChangeRequest.newBuilder()
                .setUserId(userId)
                .setOtpCode(otpCode)
                .setOtpSessionId(otpSessionId)
                .build();

        return withDeadline().confirmEmailChange(request);
    }

    public az.fitnest.user.grpc.OtpSendResponseProto requestMobileChange(Long userId, String newMobile) {
        RequestMobileChangeRequest request = RequestMobileChangeRequest.newBuilder()
                .setUserId(userId)
                .setNewMobile(newMobile)
                .build();

        return withDeadline().requestMobileChange(request);
    }

    public az.fitnest.user.grpc.UserResponse confirmMobileChange(Long userId, String otpSessionId, String otpCode) {
        ConfirmMobileChangeRequest request = ConfirmMobileChangeRequest.newBuilder()
                .setUserId(userId)
                .setOtpCode(otpCode)
                .setOtpSessionId(otpSessionId)
                .build();

        return withDeadline().confirmMobileChange(request);
    }

    public az.fitnest.user.grpc.UserResponse updateProfileImage(Long userId, String imageUrl) {
        az.fitnest.user.grpc.UpdateProfileImageRequest request = az.fitnest.user.grpc.UpdateProfileImageRequest.newBuilder()
                .setUserId(userId)
                .setImageUrl(imageUrl != null ? imageUrl : "")
                .build();

        return withDeadline().updateProfileImage(request);
    }

    public az.fitnest.user.grpc.UserResponse updateSetupRequired(Long userId, boolean setupRequired) {
        az.fitnest.user.grpc.UpdateSetupRequiredRequest request = az.fitnest.user.grpc.UpdateSetupRequiredRequest.newBuilder()
                .setUserId(userId)
                .setSetupRequired(setupRequired)
                .build();

        return withDeadline().updateSetupRequired(request);
    }

    public az.fitnest.user.grpc.UserResponse updateLanguage(Long userId, String language) {
        az.fitnest.user.grpc.UpdateLanguageRequest request = az.fitnest.user.grpc.UpdateLanguageRequest.newBuilder()
                .setUserId(userId)
                .setLanguage(language != null ? language : "")
                .build();

        return withDeadline().updateLanguage(request);
    }

    public java.util.List<Long> searchUserIdsByMobile(String query) {
        az.fitnest.user.grpc.SearchUserIdsByMobileRequest request = az.fitnest.user.grpc.SearchUserIdsByMobileRequest.newBuilder()
                .setQuery(query)
                .build();

        az.fitnest.user.grpc.SearchUserIdsByMobileResponse response = withDeadline().searchUserIdsByMobile(request);
        return response.getUserIdsList();
    }

    public az.fitnest.user.grpc.ListUsersByMobilePrefixResponse listUsersByMobilePrefix(String prefix, int page, int size) {
        az.fitnest.user.grpc.ListUsersByMobilePrefixRequest request =
                az.fitnest.user.grpc.ListUsersByMobilePrefixRequest.newBuilder()
                        .setPrefix(prefix)
                        .setPage(page)
                        .setSize(size)
                        .build();

        return withDeadline().listUsersByMobilePrefix(request);
    }

    public java.util.List<Long> getUserIdsByRoles(java.util.List<String> roleNames) {
        az.fitnest.user.grpc.GetUserIdsByRolesRequest request = az.fitnest.user.grpc.GetUserIdsByRolesRequest.newBuilder()
                .addAllRoleNames(roleNames)
                .build();
        return withDeadline().getUserIdsByRoles(request).getUserIdsList();
    }
}
