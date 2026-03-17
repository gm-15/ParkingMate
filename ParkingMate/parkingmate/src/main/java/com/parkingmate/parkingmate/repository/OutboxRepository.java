package com.parkingmate.parkingmate.repository;

import com.parkingmate.parkingmate.domain.OutboxEvent;
import com.parkingmate.parkingmate.domain.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // PENDING 이벤트를 생성 시간 순으로 최대 10건 조회
    List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}
