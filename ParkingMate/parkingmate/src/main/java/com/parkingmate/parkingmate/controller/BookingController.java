package com.parkingmate.parkingmate.controller;

import com.parkingmate.parkingmate.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.parkingmate.parkingmate.dto.BookingResponseDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<String> createBooking(
            @RequestBody BookingCreateRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userEmail = userDetails.getUsername();
        bookingService.createBooking(requestDto, userEmail);

        return ResponseEntity.ok("예약이 성공적으로 완료되었습니다.");
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponseDto>> getMyBookings(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<BookingResponseDto> myBookings = bookingService.findMyBookings(userDetails.getUsername());
        return ResponseEntity.ok(myBookings);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelBooking(
            @PathVariable("id") Long bookingId,
            @AuthenticationPrincipal UserDetails userDetails) {

        bookingService.cancelBooking(bookingId, userDetails.getUsername());
        return ResponseEntity.ok("예약이 성공적으로 취소되었습니다.");
    }

}