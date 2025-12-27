package com.parkingmate.parkingmate.controller;

import com.parkingmate.parkingmate.dto.*;
import com.parkingmate.parkingmate.service.BookingService;
import com.parkingmate.parkingmate.service.ParkingSpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class ParkingSpaceController {

    private final ParkingSpaceService parkingSpaceService;
    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createParkingSpace(
            @RequestBody @Valid ParkingSpaceCreateRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userEmail = userDetails.getUsername();
        parkingSpaceService.createParkingSpace(requestDto, userEmail);
        return ResponseEntity.ok(ApiResponse.success("주차 공간이 성공적으로 등록되었습니다.", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllParkingSpaces(
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        // 페이징 파라미터가 있으면 페이징 응답, 없으면 리스트 응답 (하위 호환성)
        if (page != null || size != null || sortBy != null) {
            ParkingSpaceSearchRequest searchRequest = new ParkingSpaceSearchRequest();
            searchRequest.setAddress(address);
            searchRequest.setSortBy(sortBy);
            searchRequest.setPage(page != null ? page : 0);
            searchRequest.setSize(size);
            
            PageResponse<ParkingSpaceResponseDto> pageResponse = parkingSpaceService.searchParkingSpaces(searchRequest);
            return ResponseEntity.ok(ApiResponse.success(pageResponse));
        } else {
            // 기존 방식 (하위 호환성)
            List<ParkingSpaceResponseDto> spaces = parkingSpaceService.findAllParkingSpaces(address);
            return ResponseEntity.ok(ApiResponse.success(spaces));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ParkingSpaceResponseDto>> getParkingSpaceById(@PathVariable Long id) {
        ParkingSpaceResponseDto space = parkingSpaceService.findParkingSpaceById(id);
        return ResponseEntity.ok(ApiResponse.success(space));
    }

    @GetMapping("/{id}/available-slots")
    public ResponseEntity<ApiResponse<List<AvailableTimeSlotDto>>> getAvailableTimeSlots(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "1") int slotDurationHours) {

        List<AvailableTimeSlotDto> slots = bookingService.getAvailableTimeSlots(
                id, startDate, endDate, slotDurationHours);
        return ResponseEntity.ok(ApiResponse.success(slots));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<PageResponse<ParkingSpaceResponseDto>>> searchNearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        LocationSearchRequest request = new LocationSearchRequest();
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setRadiusKm(radiusKm);
        request.setSortBy(sortBy);
        request.setPage(page != null ? page : 0);
        request.setSize(size);

        PageResponse<ParkingSpaceResponseDto> result = parkingSpaceService.searchByLocation(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ParkingSpaceResponseDto>>> getMyParkingSpaces(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ParkingSpaceResponseDto> spaces = parkingSpaceService.findMyParkingSpaces(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(spaces));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateParkingSpace(
            @PathVariable Long id,
            @RequestBody @Valid ParkingSpaceUpdateRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {

        parkingSpaceService.updateParkingSpace(id, requestDto, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("주차 공간 정보가 성공적으로 수정되었습니다.", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteParkingSpace(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        parkingSpaceService.deleteParkingSpace(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("주차 공간이 성공적으로 삭제되었습니다.", null));
    }
}
