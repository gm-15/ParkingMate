package com.parkingmate.parkingmate.dto;

import com.parkingmate.parkingmate.domain.User;
import lombok.Getter;

@Getter
public class UserProfileResponseDto {
    private String email;
    private String name;

    public UserProfileResponseDto(User user) {
        this.email = user.getEmail();
        this.name = user.getName();
    }
}