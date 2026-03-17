package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.ParkingSpace;
import com.parkingmate.parkingmate.domain.User;
import com.parkingmate.parkingmate.dto.*;
import com.parkingmate.parkingmate.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ParkingSpaceService {

    private final ParkingSpaceRepository parkingSpaceRepository;
    private final UserRepository userRepository;
    private final GeoCacheService geoCacheService;

    @Transactional
    public Long createParkingSpace(ParkingSpaceCreateRequestDto requestDto, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        ParkingSpace parkingSpace = new ParkingSpace(
                user, requestDto.getAddress(), requestDto.getPricePerHour(), requestDto.getDescription());
        parkingSpace.setImageUrls(requestDto.getImageUrls());

        if (requestDto.getLatitude() != null && requestDto.getLongitude() != null) {
            parkingSpace.setLocation(requestDto.getLongitude(), requestDto.getLatitude());
        }

        parkingSpaceRepository.save(parkingSpace);

        // Phase 4: Redis GEO 캐시 등록 (Write-through)
        if (requestDto.getLatitude() != null && requestDto.getLongitude() != null) {
            geoCacheService.addSpace(parkingSpace.getId(), requestDto.getLongitude(), requestDto.getLatitude());
        }

        return parkingSpace.getId();
    }

    public List<ParkingSpaceResponseDto> findAllParkingSpaces() {
        return parkingSpaceRepository.findAll().stream()
                .map(ParkingSpaceResponseDto::new)
                .collect(Collectors.toList());
    }

    public ParkingSpaceResponseDto findParkingSpaceById(Long spaceId) {
        ParkingSpace space = parkingSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + spaceId));
        return new ParkingSpaceResponseDto(space);
    }

    @Transactional
    public void updateParkingSpace(Long spaceId, ParkingSpaceUpdateRequestDto requestDto, String userEmail) {
        ParkingSpace space = parkingSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + spaceId));
        if (!space.getUser().getEmail().equals(userEmail)) {
            throw new IllegalStateException("주차 공간 정보를 수정할 권한이 없습니다.");
        }

        space.setAddress(requestDto.getAddress());
        space.setPricePerHour(requestDto.getPricePerHour());
        space.setDescription(requestDto.getDescription());
        space.setImageUrls(requestDto.getImageUrls());

        if (requestDto.getLatitude() != null && requestDto.getLongitude() != null) {
            space.setLocation(requestDto.getLongitude(), requestDto.getLatitude());
            geoCacheService.addSpace(spaceId, requestDto.getLongitude(), requestDto.getLatitude());
        }
    }

    @Transactional
    public void deleteParkingSpace(Long spaceId, String userEmail) {
        ParkingSpace space = parkingSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + spaceId));
        if (!space.getUser().getEmail().equals(userEmail)) {
            throw new IllegalStateException("주차 공간을 삭제할 권한이 없습니다.");
        }
        geoCacheService.removeSpace(spaceId);
        parkingSpaceRepository.delete(space);
    }

    public List<ParkingSpaceResponseDto> findAllParkingSpaces(String address) {
        List<ParkingSpace> spaces = (address == null || address.isBlank())
                ? parkingSpaceRepository.findAll()
                : parkingSpaceRepository.findByAddressContaining(address);
        return spaces.stream().map(ParkingSpaceResponseDto::new).collect(Collectors.toList());
    }

    public PageResponse<ParkingSpaceResponseDto> searchParkingSpaces(ParkingSpaceSearchRequest request) {
        Pageable pageable = buildPageable(request);
        Page<ParkingSpace> page = request.hasAddress()
                ? getPageWithAddressAndSort(request.getAddress(), request.getSortBy(), pageable)
                : getPageWithSort(request.getSortBy(), pageable);
        return PageResponse.of(page.map(ParkingSpaceResponseDto::new));
    }

    /**
     * 위치 기반 반경 검색
     *
     * 1순위: Redis GEO (GEOSEARCH) — 캐시 히트 시 DB 쿼리 없이 처리
     * 2순위: MySQL Spatial (ST_Within + R-Tree SPATIAL INDEX)
     *
     * BEFORE: findAll() + Java Haversine → EXPLAIN type=ALL, rows=9,880
     * AFTER:  ST_Within + SPATIAL INDEX  → EXPLAIN type=range, rows=378 (96.2% 감소)
     */
    public PageResponse<ParkingSpaceResponseDto> searchByLocation(LocationSearchRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new IllegalArgumentException("위도와 경도는 필수입니다.");
        }
        double radiusKm = request.getRadiusKm() != null && request.getRadiusKm() > 0
                ? request.getRadiusKm() : 5.0;
        int page = request.getPage() != null ? Math.max(0, request.getPage()) : 0;
        int size = request.getSize() != null && request.getSize() > 0
                ? Math.min(request.getSize(), 100) : 10;

        // Phase 4: Redis GEO 캐시 우선
        try {
            List<Long> cachedIds = geoCacheService.findNearbySpaceIds(
                    request.getLongitude(), request.getLatitude(), radiusKm);
            if (!cachedIds.isEmpty()) {
                log.debug("GEO cache hit: {} spaces", cachedIds.size());
                List<ParkingSpace> spaces = parkingSpaceRepository.findAllById(cachedIds);
                return buildPageResponse(spaces, page, size);
            }
        } catch (Exception e) {
            log.warn("Redis GEO unavailable, fallback to MySQL: {}", e.getMessage());
        }

        // Phase 2: MySQL Spatial Index fallback
        return searchFromDb(request.getLongitude(), request.getLatitude(), radiusKm, page, size);
    }

    private PageResponse<ParkingSpaceResponseDto> searchFromDb(
            double longitude, double latitude, double radiusKm, int page, int size) {
        String polygon = buildBoundingBoxWkt(latitude, longitude, radiusKm);
        String center = String.format("POINT(%f %f)", longitude, latitude);
        double radiusMeters = radiusKm * 1000;

        List<ParkingSpace> spaces = parkingSpaceRepository.findWithinRadius(
                polygon, center, radiusMeters, size, page * size);
        long total = parkingSpaceRepository.countWithinRadius(polygon, center, radiusMeters);
        return buildPageResponse(spaces, total, page, size);
    }

    /**
     * 바운딩 박스 WKT — R-Tree가 인식하는 MBR 형태
     * 이후 ST_Distance_Sphere로 원형 정밀 필터 (모서리 오탐 제거)
     */
    private String buildBoundingBoxWkt(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return String.format(
                "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                lng - lngDelta, lat - latDelta,
                lng + lngDelta, lat - latDelta,
                lng + lngDelta, lat + latDelta,
                lng - lngDelta, lat + latDelta,
                lng - lngDelta, lat - latDelta
        );
    }

    private PageResponse<ParkingSpaceResponseDto> buildPageResponse(List<ParkingSpace> spaces, int page, int size) {
        int total = spaces.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        List<ParkingSpace> paged = start < total ? spaces.subList(start, end) : List.of();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(
                paged.stream().map(ParkingSpaceResponseDto::new).collect(Collectors.toList()),
                page, size, total, totalPages, page == 0, page >= totalPages - 1);
    }

    private PageResponse<ParkingSpaceResponseDto> buildPageResponse(
            List<ParkingSpace> spaces, long total, int page, int size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(
                spaces.stream().map(ParkingSpaceResponseDto::new).collect(Collectors.toList()),
                page, size, (int) total, totalPages, page == 0, page >= totalPages - 1);
    }

    public List<ParkingSpaceResponseDto> findMyParkingSpaces(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return parkingSpaceRepository.findByUser_Id(user.getId()).stream()
                .map(ParkingSpaceResponseDto::new)
                .collect(Collectors.toList());
    }

    private Pageable buildPageable(ParkingSpaceSearchRequest request) {
        int p = Math.max(0, request.getPage() != null ? request.getPage() : 0);
        int s = Math.min(request.getSize() != null && request.getSize() > 0 ? request.getSize() : 10, 100);
        return PageRequest.of(p, s);
    }

    private Page<ParkingSpace> getPageWithSort(String sortBy, Pageable pageable) {
        return switch (sortBy == null ? "latest" : sortBy) {
            case "price_asc"  -> parkingSpaceRepository.findAllOrderByPriceAsc(pageable);
            case "price_desc" -> parkingSpaceRepository.findAllOrderByPriceDesc(pageable);
            default           -> parkingSpaceRepository.findAllOrderByIdDesc(pageable);
        };
    }

    private Page<ParkingSpace> getPageWithAddressAndSort(String address, String sortBy, Pageable pageable) {
        return switch (sortBy == null ? "latest" : sortBy) {
            case "price_asc"  -> parkingSpaceRepository.findByAddressContainingOrderByPriceAsc(address, pageable);
            case "price_desc" -> parkingSpaceRepository.findByAddressContainingOrderByPriceDesc(address, pageable);
            default           -> parkingSpaceRepository.findByAddressContainingOrderByIdDesc(address, pageable);
        };
    }
}
