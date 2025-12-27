package com.parkingmate.parkingmate.controller;

import com.parkingmate.parkingmate.dto.ApiResponse;
import com.parkingmate.parkingmate.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.dto.BookingResponseDto;
import com.parkingmate.parkingmate.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createBooking(
            @RequestBody @Valid BookingCreateRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userEmail = userDetails.getUsername();
        bookingService.createBooking(requestDto, userEmail);
        return ResponseEntity.ok(ApiResponse.success("예약이 성공적으로 완료되었습니다.", null));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<BookingResponseDto>>> getMyBookings(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<BookingResponseDto> myBookings = bookingService.findMyBookings(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(myBookings));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable("id") Long bookingId,
            @AuthenticationPrincipal UserDetails userDetails) {

        bookingService.cancelBooking(bookingId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("예약이 성공적으로 취소되었습니다.", null));
    }

}