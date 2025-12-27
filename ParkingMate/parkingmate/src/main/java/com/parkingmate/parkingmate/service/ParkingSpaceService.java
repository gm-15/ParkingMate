package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.ParkingSpace;
import com.parkingmate.parkingmate.domain.User;
import com.parkingmate.parkingmate.dto.*;
import com.parkingmate.parkingmate.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ParkingSpaceService {

    private final ParkingSpaceRepository parkingSpaceRepository;
    private final UserRepository userRepository;

    /**
     * 주차 공간 등록
     */
    @Transactional
    public Long createParkingSpace(ParkingSpaceCreateRequestDto requestDto, String userEmail) {
        // 1. 요청을 보낸 사용자의 엔티티를 찾습니다.
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. DTO와 사용자 엔티티를 바탕으로 새로운 ParkingSpace 엔티티를 생성합니다.
        ParkingSpace parkingSpace = new ParkingSpace(
                user,
                requestDto.getAddress(),
                requestDto.getPricePerHour(),
                requestDto.getDescription()
        );

        // 3. 생성된 엔티티를 DB에 저장합니다.
        parkingSpaceRepository.save(parkingSpace);

        return parkingSpace.getId();
    }

    /**
     * 모든 주차 공간 조회
     */
    public List<ParkingSpaceResponseDto> findAllParkingSpaces() {
        return parkingSpaceRepository.findAll().stream()
                .map(ParkingSpaceResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 특정 주차 공간 상세 조회
     */
    public ParkingSpaceResponseDto findParkingSpaceById(Long spaceId) {
        ParkingSpace parkingSpace = parkingSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + spaceId));

        return new ParkingSpaceResponseDto(parkingSpace);
    }

    /**
     * 주차 공간 정보 수정
     */
    @Transactional
    public void updateParkingSpace(Long spaceId, ParkingSpaceUpdateRequestDto requestDto, String userEmail) {
        // 1. 수정할 주차 공간을 찾습니다.
        ParkingSpace parkingSpace = parkingSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + spaceId));

        // 2. 요청을 보낸 사용자가 해당 주차 공간의 소유주인지 확인합니다.
        if (!parkingSpace.getUser().getEmail().equals(userEmail)) {
            // 소유주가 아니라면, 수정 권한이 없으므로 에러를 발생시킵니다.
            throw new IllegalStateException("주차 공간 정보를 수정할 권한이 없습니다.");
        }

        // 3. (권한 확인 완료) 정보를 수정합니다.
        parkingSpace.setAddress(requestDto.getAddress());
        parkingSpace.setPricePerHour(requestDto.getPricePerHour());
        parkingSpace.setDescription(requestDto.getDescription());
    }

    /**
     * 주차 공간 삭제
     */
    @Transactional
    public void deleteParkingSpace(Long spaceId, String userEmail) {
        // 1. 삭제할 주차 공간을 찾습니다.
        ParkingSpace parkingSpace = parkingSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + spaceId));

        // 2. 요청을 보낸 사용자가 해당 주차 공간의 소유주인지 확인합니다.
        if (!parkingSpace.getUser().getEmail().equals(userEmail)) {
            throw new IllegalStateException("주차 공간을 삭제할 권한이 없습니다.");
        }

        // 3. (권한 확인 완료) 정보를 삭제합니다.
        parkingSpaceRepository.delete(parkingSpace);
    }

    /**
     * 모든 주차 공간 조회 (주소 검색 기능 추가) - 기존 메서드 (하위 호환성)
     */
    public List<ParkingSpaceResponseDto> findAllParkingSpaces(String address) {
        List<ParkingSpace> parkingSpaces;

        if (address == null || address.trim().isEmpty()) {
            parkingSpaces = parkingSpaceRepository.findAll();
        } else {
            parkingSpaces = parkingSpaceRepository.findByAddressContaining(address);
        }

        return parkingSpaces.stream()
                .map(ParkingSpaceResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 주차 공간 검색 (페이징 및 정렬 지원)
     */
    public PageResponse<ParkingSpaceResponseDto> searchParkingSpaces(ParkingSpaceSearchRequest request) {
        Pageable pageable = createPageable(request);
        Page<ParkingSpace> page;

        if (request.hasAddress()) {
            // 주소 검색 + 정렬
            page = getPageWithAddressAndSort(request.getAddress(), request.getSortBy(), pageable);
        } else {
            // 전체 조회 + 정렬
            page = getPageWithSort(request.getSortBy(), pageable);
        }

        Page<ParkingSpaceResponseDto> dtoPage = page.map(ParkingSpaceResponseDto::new);
        return PageResponse.of(dtoPage);
    }

    private Pageable createPageable(ParkingSpaceSearchRequest request) {
        int page = Math.max(0, request.getPage() != null ? request.getPage() : 0);
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 10;
        size = Math.min(size, 100); // 최대 100개로 제한
        
        return PageRequest.of(page, size);
    }

    private Page<ParkingSpace> getPageWithSort(String sortBy, Pageable pageable) {
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "latest"; // 기본값: 최신순
        }

        return switch (sortBy) {
            case "price_asc" -> parkingSpaceRepository.findAllOrderByPriceAsc(pageable);
            case "price_desc" -> parkingSpaceRepository.findAllOrderByPriceDesc(pageable);
            case "latest" -> parkingSpaceRepository.findAllOrderByIdDesc(pageable);
            default -> parkingSpaceRepository.findAllOrderByIdDesc(pageable);
        };
    }

    private Page<ParkingSpace> getPageWithAddressAndSort(String address, String sortBy, Pageable pageable) {
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "latest"; // 기본값: 최신순
        }

        return switch (sortBy) {
            case "price_asc" -> parkingSpaceRepository.findByAddressContainingOrderByPriceAsc(address, pageable);
            case "price_desc" -> parkingSpaceRepository.findByAddressContainingOrderByPriceDesc(address, pageable);
            case "latest" -> parkingSpaceRepository.findByAddressContainingOrderByIdDesc(address, pageable);
            default -> parkingSpaceRepository.findByAddressContainingOrderByIdDesc(address, pageable);
        };
    }

    /**
     * 내가 등록한 주차 공간 목록 조회
     */
    public List<ParkingSpaceResponseDto> findMyParkingSpaces(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        return parkingSpaceRepository.findByUser_Id(user.getId()).stream()
                .map(ParkingSpaceResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 위치 기반 검색 (반경 내 주차 공간 검색)
     * Haversine 공식을 사용하여 두 지점 간의 거리를 계산
     */
    public PageResponse<ParkingSpaceResponseDto> searchByLocation(LocationSearchRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new IllegalArgumentException("위도와 경도는 필수입니다.");
        }

        Double radiusKm = request.getRadiusKm() != null && request.getRadiusKm() > 0 
                ? request.getRadiusKm() : 5.0; // 기본값: 5km

        // 모든 주차 공간 조회 (위치 정보가 있는 것만)
        List<ParkingSpace> allSpaces = parkingSpaceRepository.findAll().stream()
                .filter(space -> space.getLatitude() != null && space.getLongitude() != null)
                .collect(Collectors.toList());

        // 거리 계산 및 필터링
        List<ParkingSpaceWithDistance> spacesWithDistance = allSpaces.stream()
                .map(space -> {
                    double distance = calculateDistance(
                            request.getLatitude(),
                            request.getLongitude(),
                            space.getLatitude(),
                            space.getLongitude()
                    );
                    return new ParkingSpaceWithDistance(space, distance);
                })
                .filter(item -> item.distance <= radiusKm)
                .collect(Collectors.toList());

        // 정렬 적용
        if (request.getSortBy() != null) {
            switch (request.getSortBy()) {
                case "price_asc" -> spacesWithDistance.sort((a, b) -> 
                        Integer.compare(a.space.getPricePerHour(), b.space.getPricePerHour()));
                case "price_desc" -> spacesWithDistance.sort((a, b) -> 
                        Integer.compare(b.space.getPricePerHour(), a.space.getPricePerHour()));
                case "distance" -> spacesWithDistance.sort((a, b) -> 
                        Double.compare(a.distance, b.distance));
                case "latest" -> spacesWithDistance.sort((a, b) -> 
                        Long.compare(b.space.getId(), a.space.getId())); // ID 역순 = 최신순
            }
        } else {
            // 기본 정렬: 거리순
            spacesWithDistance.sort((a, b) -> Double.compare(a.distance, b.distance));
        }

        // 페이징 처리
        int page = Math.max(0, request.getPage() != null ? request.getPage() : 0);
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 10;
        size = Math.min(size, 100);

        int start = page * size;
        int end = Math.min(start + size, spacesWithDistance.size());
        List<ParkingSpaceWithDistance> pagedList = start < spacesWithDistance.size() 
                ? spacesWithDistance.subList(start, end) 
                : List.of();

        // DTO 변환
        List<ParkingSpaceResponseDto> content = pagedList.stream()
                .map(item -> new ParkingSpaceResponseDto(item.space))
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) spacesWithDistance.size() / size);

        return new PageResponse<>(
                content,
                page,
                size,
                spacesWithDistance.size(),
                totalPages,
                page == 0,
                page >= totalPages - 1
        );
    }

    /**
     * Haversine 공식을 사용하여 두 지점 간의 거리 계산 (킬로미터)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반지름 (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 거리 정보를 포함한 주차 공간 래퍼 클래스
     */
    private static class ParkingSpaceWithDistance {
        final ParkingSpace space;
        final Double distance;

        ParkingSpaceWithDistance(ParkingSpace space, Double distance) {
            this.space = space;
            this.distance = distance;
        }
    }
}