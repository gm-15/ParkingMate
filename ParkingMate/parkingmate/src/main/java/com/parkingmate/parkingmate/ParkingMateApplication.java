package com.parkingmate.parkingmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ParkingMateApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParkingMateApplication.class, args);
	}
}