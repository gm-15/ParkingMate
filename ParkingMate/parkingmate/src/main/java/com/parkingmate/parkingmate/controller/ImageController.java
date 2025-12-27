package com.parkingmate.parkingmate.controller;

import com.parkingmate.parkingmate.dto.ApiResponse;
import com.parkingmate.parkingmate.dto.ImageUploadResponse;
import com.parkingmate.parkingmate.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final S3Service s3Service;

    /**
     * 주차 공간 이미지 업로드
     */
    @PostMapping("/parking-spaces")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadParkingSpaceImages(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("업로드할 파일이 없습니다."));
        }

        // 파일 개수 제한 (최대 5개)
        if (files.size() > 5) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("이미지는 최대 5개까지 업로드 가능합니다."));
        }

        List<String> imageUrls = s3Service.uploadImages(files, "parking-spaces");
        ImageUploadResponse response = new ImageUploadResponse(imageUrls, imageUrls.size());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 단일 이미지 업로드
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            @AuthenticationPrincipal UserDetails userDetails) {

        String imageUrl = s3Service.uploadImage(file, folder);
        return ResponseEntity.ok(ApiResponse.success(imageUrl));
    }

    /**
     * 이미지 삭제
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @RequestParam("url") String imageUrl,
            @AuthenticationPrincipal UserDetails userDetails) {

        s3Service.deleteImage(imageUrl);
        return ResponseEntity.ok(ApiResponse.success("이미지가 성공적으로 삭제되었습니다.", null));
    }
}

