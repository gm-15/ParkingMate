package com.parkingmate.parkingmate.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ImageUploadResponse {
    private List<String> imageUrls;
    private int uploadedCount;
}

