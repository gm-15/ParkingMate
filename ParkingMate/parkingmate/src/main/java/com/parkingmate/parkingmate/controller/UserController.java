package com.parkingmate.parkingmate.controller;

import com.parkingmate.parkingmate.dto.UserLoginRequestDto;
import com.parkingmate.parkingmate.dto.UserLoginResponseDto;
import com.parkingmate.parkingmate.dto.UserProfileResponseDto;
import com.parkingmate.parkingmate.dto.UserSignUpRequestDto;
import com.parkingmate.parkingmate.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.parkingmate.parkingmate.domain.User;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody UserSignUpRequestDto requestDto) {
        userService.signUp(requestDto.getEmail(), requestDto.getPassword(), requestDto.getName());
        return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponseDto> login(@RequestBody UserLoginRequestDto requestDto) {
        String token = userService.login(requestDto.getEmail(), requestDto.getPassword());
        return ResponseEntity.ok(new UserLoginResponseDto(token));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        // userDetails.getUsername() 에는 사용자의 이메일이 들어있습니다.
        // 이 이메일을 사용해 우리 DB에서 실제 User 객체를 찾아옵니다.
        User user = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(new UserProfileResponseDto(user));
    }
}