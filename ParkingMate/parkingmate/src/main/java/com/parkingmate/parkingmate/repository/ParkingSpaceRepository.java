package com.parkingmate.parkingmate.repository;

import com.parkingmate.parkingmate.domain.ParkingSpace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    // 주소에 특정 문자열이 포함된 모든 주차 공간을 조회
    List<ParkingSpace> findByAddressContaining(String address);
    
    // 주소에 특정 문자열이 포함된 주차 공간을 페이징하여 조회
    Page<ParkingSpace> findByAddressContaining(String address, Pageable pageable);
    
    // 특정 사용자가 등록한 주차 공간 목록 조회
    List<ParkingSpace> findByUser_Id(Long userId);
    
    // 특정 사용자가 등록한 주차 공간 목록을 페이징하여 조회
    Page<ParkingSpace> findByUser_Id(Long userId, Pageable pageable);
    
    // 가격순으로 정렬 (오름차순)
    @Query("SELECT p FROM ParkingSpace p ORDER BY p.pricePerHour ASC")
    Page<ParkingSpace> findAllOrderByPriceAsc(Pageable pageable);
    
    // 가격순으로 정렬 (내림차순)
    @Query("SELECT p FROM ParkingSpace p ORDER BY p.pricePerHour DESC")
    Page<ParkingSpace> findAllOrderByPriceDesc(Pageable pageable);
    
    // 최신순으로 정렬 (등록일 기준)
    @Query("SELECT p FROM ParkingSpace p ORDER BY p.id DESC")
    Page<ParkingSpace> findAllOrderByIdDesc(Pageable pageable);
    
    // 주소 검색 + 가격순 정렬
    @Query("SELECT p FROM ParkingSpace p WHERE p.address LIKE %:address% ORDER BY p.pricePerHour ASC")
    Page<ParkingSpace> findByAddressContainingOrderByPriceAsc(@Param("address") String address, Pageable pageable);
    
    @Query("SELECT p FROM ParkingSpace p WHERE p.address LIKE %:address% ORDER BY p.pricePerHour DESC")
    Page<ParkingSpace> findByAddressContainingOrderByPriceDesc(@Param("address") String address, Pageable pageable);
    
    // 주소 검색 + 최신순 정렬
    @Query("SELECT p FROM ParkingSpace p WHERE p.address LIKE %:address% ORDER BY p.id DESC")
    Page<ParkingSpace> findByAddressContainingOrderByIdDesc(@Param("address") String address, Pageable pageable);
    
    // 위치 기반 검색 (Haversine 공식 사용 - 반경 내 주차 공간 검색)
    // Native Query는 EntityManager를 통해 직접 실행하도록 Service에서 처리
}