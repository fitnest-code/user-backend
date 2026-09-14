package az.fitnest.user.service.impl;

import az.fitnest.user.exception.BadRequestException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements az.fitnest.user.service.FileStorageService {

    private final az.fitnest.user.client.StorageGrpcClient storageGrpcClient;

    @Override
    public String saveFile(MultipartFile file) {
        return saveFile(file, "/uploads");
    }

    @Override
    public String saveFile(MultipartFile file, String directory) {
        return saveFile(file, directory, null);
    }

    @Override
    public String saveFile(MultipartFile file, String directory, String oldPath) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            String extractedOldPath = extractIdFromUrl(oldPath);
            az.fitnest.user.dto.response.StorageFileData data = storageGrpcClient.uploadFile(file, directory, extractedOldPath);
            return String.valueOf(data.getFsId());
        } catch (az.fitnest.user.exception.InternalServerException | az.fitnest.user.exception.BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw mapUploadError(e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return;
        }
        deleteFiles(List.of(fileUrl));
    }

    @Override
    public void deleteFiles(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }
        try {
            List<String> ids = fileUrls.stream()
                    .map(this::extractIdFromUrl)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            if (!ids.isEmpty()) {
                storageGrpcClient.deleteFiles(ids);
            }
        } catch (Exception e) {
        }
    }

    private String extractIdFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        if (url.contains("/")) {
            String[] parts = url.split("/");
            return parts[parts.length - 1];
        }
        return url;
    }

    private static BadRequestException mapUploadError(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof StatusRuntimeException sre) {
                String desc = sre.getStatus().getDescription();
                if ("error.file_too_large".equals(desc)) {
                    return new BadRequestException("error.file_size_limit");
                }
                if ("error.invalid_file_type".equals(desc)) {
                    return new BadRequestException("error.only_images_allowed");
                }
            }
        }
        return new BadRequestException("error.file_upload_failed");
    }
}
