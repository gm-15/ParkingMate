package com.parkingmate.parkingmate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_space_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String address;

    /**
     * 공간 좌표 (POINT SRID 4326)
     *
     * BEFORE: latitude DOUBLE + longitude DOUBLE
     *   → 두 B-Tree를 MySQL이 조합하지 못해 풀 스캔 발생
     *
     * AFTER: location POINT SRID 4326
     *   → R-Tree SPATIAL INDEX → ST_Within으로 MBR 필터
     *   → EXPLAIN: ALL 9,880행 → range 378행 (96.2% 감소)
     *
     * WKT 좌표 순서: POINT(longitude latitude) — X=경도, Y=위도
     */
    @Column(columnDefinition = "POINT SRID 4326")
    private Point location;

    @Column(nullable = false)
    private int pricePerHour;

    private String description;

    @Column(length = 1000)
    private String imageUrls;

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    public ParkingSpace(User user, String address, int pricePerHour, String description) {
        this.user = user;
        this.address = address;
        this.pricePerHour = pricePerHour;
        this.description = description;
    }

    // ParkingSpaceResponseDto / 외부 호환용 편의 메서드
    public Double getLatitude() {
        return location != null ? location.getY() : null;
    }

    public Double getLongitude() {
        return location != null ? location.getX() : null;
    }

    public void setLocation(double longitude, double latitude) {
        Point p = GEO_FACTORY.createPoint(new Coordinate(longitude, latitude));
        p.setSRID(4326);
        this.location = p;
    }
}
