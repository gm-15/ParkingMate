package com.parkingmate.parkingmate.space.repository;

import com.parkingmate.parkingmate.space.domain.ParkingSpace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    List<ParkingSpace> findByAddressContaining(String address);

    Page<ParkingSpace> findByAddressContaining(String address, Pageable pageable);

    List<ParkingSpace> findByUser_Id(Long userId);

    Page<ParkingSpace> findByUser_Id(Long userId, Pageable pageable);

    @Query("SELECT p FROM ParkingSpace p ORDER BY p.pricePerHour ASC")
    Page<ParkingSpace> findAllOrderByPriceAsc(Pageable pageable);

    @Query("SELECT p FROM ParkingSpace p ORDER BY p.pricePerHour DESC")
    Page<ParkingSpace> findAllOrderByPriceDesc(Pageable pageable);

    @Query("SELECT p FROM ParkingSpace p ORDER BY p.id DESC")
    Page<ParkingSpace> findAllOrderByIdDesc(Pageable pageable);

    @Query("SELECT p FROM ParkingSpace p WHERE p.address LIKE %:address% ORDER BY p.pricePerHour ASC")
    Page<ParkingSpace> findByAddressContainingOrderByPriceAsc(@Param("address") String address, Pageable pageable);

    @Query("SELECT p FROM ParkingSpace p WHERE p.address LIKE %:address% ORDER BY p.pricePerHour DESC")
    Page<ParkingSpace> findByAddressContainingOrderByPriceDesc(@Param("address") String address, Pageable pageable);

    @Query("SELECT p FROM ParkingSpace p WHERE p.address LIKE %:address% ORDER BY p.id DESC")
    Page<ParkingSpace> findByAddressContainingOrderByIdDesc(@Param("address") String address, Pageable pageable);

    /**
     * R-Tree Spatial Index 기반 반경 검색
     *
     * - :polygon : 바운딩 박스 WKT (POLYGON, SRID 4326) → SPATIAL INDEX 활용
     * - ST_Distance_Sphere : 중심점 기준 실제 거리(미터) 계산 → 원형 최종 필터
     *
     * EXPLAIN: type=range, key=idx_location → 스캔 행수 96.2% 감소
     */
    @Query(value = """
            SELECT * FROM parking_space
            WHERE location IS NOT NULL
              AND ST_Within(location, ST_GeomFromText(:polygon, 4326))
              AND ST_Distance_Sphere(location, ST_GeomFromText(:center, 4326)) <= :radiusMeters
            ORDER BY ST_Distance_Sphere(location, ST_GeomFromText(:center, 4326)) ASC
            LIMIT :lim OFFSET :off
            """, nativeQuery = true)
    List<ParkingSpace> findWithinRadius(
            @Param("polygon") String boundingBoxPolygon,
            @Param("center") String centerPoint,
            @Param("radiusMeters") double radiusMeters,
            @Param("lim") int limit,
            @Param("off") int offset
    );

    @Query(value = """
            SELECT COUNT(*) FROM parking_space
            WHERE location IS NOT NULL
              AND ST_Within(location, ST_GeomFromText(:polygon, 4326))
              AND ST_Distance_Sphere(location, ST_GeomFromText(:center, 4326)) <= :radiusMeters
            """, nativeQuery = true)
    long countWithinRadius(
            @Param("polygon") String boundingBoxPolygon,
            @Param("center") String centerPoint,
            @Param("radiusMeters") double radiusMeters
    );
}
