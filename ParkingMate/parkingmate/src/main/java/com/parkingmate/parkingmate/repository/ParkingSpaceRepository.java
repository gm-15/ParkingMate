package com.parkingmate.parkingmate.repository;

import com.parkingmate.parkingmate.domain.ParkingSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    // 주소에 특정 문자열이 포함된 모든 주차 공간을 조회
    List<ParkingSpace> findByAddressContaining(String address);
}