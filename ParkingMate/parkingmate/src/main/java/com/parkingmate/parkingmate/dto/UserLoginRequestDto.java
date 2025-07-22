package com.parkingmate.parkingmate.dto;

import lombok.Getter;

@Getter
public class UserLoginRequestDto {
    private String email;
    private String password;
}