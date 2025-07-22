package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.ParkingSpace;
import com.parkingmate.parkingmate.domain.User;
import com.parkingmate.parkingmate.dto.ParkingSpaceCreateRequestDto;
import com.parkingmate.parkingmate.dto.ParkingSpaceResponseDto;
import com.parkingmate.parkingmate.dto.ParkingSpaceUpdateRequestDto;
import com.parkingmate.parkingmate.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
     * 모든 주차 공간 조회 (주소 검색 기능 추가)
     */
    public List<ParkingSpaceResponseDto> findAllParkingSpaces(String address) {
        List<ParkingSpace> parkingSpaces;

        if (address == null || address.trim().isEmpty()) {
            // 검색어가 없으면, 모든 주차 공간을 조회
            parkingSpaces = parkingSpaceRepository.findAll();
        } else {
            // 검색어가 있으면, 주소에 검색어가 포함된 주차 공간만 조회
            parkingSpaces = parkingSpaceRepository.findByAddressContaining(address);
        }

        return parkingSpaces.stream()
                .map(ParkingSpaceResponseDto::new)
                .collect(Collectors.toList());
    }
}