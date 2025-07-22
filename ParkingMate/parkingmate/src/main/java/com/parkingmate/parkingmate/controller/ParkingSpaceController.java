package com.parkingmate.parkingmate.controller;

import com.parkingmate.parkingmate.dto.ParkingSpaceCreateRequestDto;
import com.parkingmate.parkingmate.dto.ParkingSpaceResponseDto;
import com.parkingmate.parkingmate.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.parkingmate.parkingmate.dto.ParkingSpaceUpdateRequestDto;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class ParkingSpaceController {

    private final ParkingSpaceService parkingSpaceService;

    @PostMapping
    public ResponseEntity<String> createParkingSpace(
            @RequestBody ParkingSpaceCreateRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 현재 로그인한 사용자의 이메일을 가져옵니다.
        String userEmail = userDetails.getUsername();

        // 서비스 로직을 호출하여 주차 공간을 생성합니다.
        parkingSpaceService.createParkingSpace(requestDto, userEmail);

        return ResponseEntity.ok("주차 공간이 성공적으로 등록되었습니다.");
    }

    @GetMapping
    public ResponseEntity<List<ParkingSpaceResponseDto>> getAllParkingSpaces(
            @RequestParam(required = false) String address) {

        List<ParkingSpaceResponseDto> spaces = parkingSpaceService.findAllParkingSpaces(address);
        return ResponseEntity.ok(spaces);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParkingSpaceResponseDto> getParkingSpaceById(@PathVariable Long id) {
        ParkingSpaceResponseDto space = parkingSpaceService.findParkingSpaceById(id);
        return ResponseEntity.ok(space);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateParkingSpace(
            @PathVariable Long id,
            @RequestBody ParkingSpaceUpdateRequestDto requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {

        parkingSpaceService.updateParkingSpace(id, requestDto, userDetails.getUsername());
        return ResponseEntity.ok("주차 공간 정보가 성공적으로 수정되었습니다.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteParkingSpace(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        parkingSpaceService.deleteParkingSpace(id, userDetails.getUsername());
        return ResponseEntity.ok("주차 공간이 성공적으로 삭제되었습니다.");
    }


}