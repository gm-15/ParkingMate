package com.parkingmate.parkingmate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

/**
 * AWS S3 파일 업로드 서비스
 * 실제 AWS 리소스 없이도 코드 구조는 완성
 */
@Slf4j
@Service
public class S3Service {

    @Value("${aws.s3.bucket:parkingmate-images}")
    private String bucketName;

    @Value("${aws.s3.region:ap-northeast-2}")
    private String region;

    @Value("${aws.s3.enabled:false}")
    private boolean s3Enabled;

    private final S3Client s3Client;

    public S3Service() {
        // 실제 사용 시에는 환경변수나 설정에서 가져와야 함
        // 실제 AWS 리소스 없이 코드 구조만 작성
        // 실제 AWS 사용 시 아래 주석 해제 및 수정:
        // this.s3Client = S3Client.builder()
        //     .region(Region.of("ap-northeast-2"))
        //     .build();
        this.s3Client = null;
    }

    /**
     * 이미지 파일을 S3에 업로드
     * 
     * @param file 업로드할 파일
     * @param folder 폴더 경로 (예: "parking-spaces", "users")
     * @return S3 URL
     */
    public String uploadImage(MultipartFile file, String folder) {
        if (!s3Enabled) {
            log.warn("S3 is not enabled. Returning placeholder URL.");
            return generatePlaceholderUrl(folder, file.getOriginalFilename());
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어있습니다.");
        }

        // 파일 확장자 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isImageFile(originalFilename)) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }

        try {
            // 고유한 파일명 생성
            String fileName = generateFileName(folder, originalFilename);
            
            // S3에 업로드
            if (s3Client != null) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .contentType(file.getContentType())
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                        file.getInputStream(), file.getSize()));
            }

            // 업로드된 파일의 URL 반환
            String fileUrl = generateS3Url(fileName);
            log.info("File uploaded to S3: {}", fileUrl);
            return fileUrl;

        } catch (S3Exception e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("파일 업로드에 실패했습니다: " + e.getMessage());
        } catch (IOException e) {
            log.error("Error reading file", e);
            throw new RuntimeException("파일을 읽는 중 오류가 발생했습니다.");
        }
    }

    /**
     * 여러 이미지를 일괄 업로드
     */
    public java.util.List<String> uploadImages(java.util.List<MultipartFile> files, String folder) {
        return files.stream()
                .map(file -> uploadImage(file, folder))
                .toList();
    }

    /**
     * S3에서 파일 삭제
     */
    public void deleteImage(String fileUrl) {
        if (!s3Enabled || s3Client == null) {
            log.warn("S3 is not enabled. Skipping delete operation.");
            return;
        }

        try {
            String key = extractKeyFromUrl(fileUrl);
            s3Client.deleteObject(b -> b.bucket(bucketName).key(key));
            log.info("File deleted from S3: {}", fileUrl);
        } catch (S3Exception e) {
            log.error("Error deleting file from S3", e);
            throw new RuntimeException("파일 삭제에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 고유한 파일명 생성
     */
    private String generateFileName(String folder, String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uniqueId = UUID.randomUUID().toString();
        return String.format("%s/%s%s", folder, uniqueId, extension);
    }

    /**
     * S3 URL 생성
     */
    private String generateS3Url(String fileName) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
    }

    /**
     * 플레이스홀더 URL 생성 (S3 비활성화 시)
     */
    private String generatePlaceholderUrl(String folder, String originalFilename) {
        return String.format("/placeholder/%s/%s", folder, originalFilename);
    }

    /**
     * URL에서 S3 키 추출
     */
    private String extractKeyFromUrl(String url) {
        // URL에서 키 부분 추출
        // 예: https://bucket.s3.region.amazonaws.com/folder/file.jpg -> folder/file.jpg
        int keyStartIndex = url.indexOf(".amazonaws.com/") + ".amazonaws.com/".length();
        return url.substring(keyStartIndex);
    }

    /**
     * 이미지 파일인지 확인
     */
    private boolean isImageFile(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return java.util.Set.of("jpg", "jpeg", "png", "gif", "webp").contains(extension);
    }
}

