package com.parkingmate.parkingmate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 4: Redis GEO Cache Layer
 *
 * 구조 (CQRS):
 *   Write: MySQL parking_space (ACID, 마스터 데이터)
 *   Read:  Redis GEO (GEOSEARCH) → Miss 시 MySQL Spatial 쿼리 fallback
 *
 * Redis 명령:
 *   등록: GEOADD parkingspaces:geo longitude latitude "spaceId"
 *   검색: GEOSEARCH parkingspaces:geo FROMLONLAT lng lat BYRADIUS radiusKm km ASC COUNT 100
 *   삭제: ZREM parkingspaces:geo "spaceId"  (GEO key는 내부적으로 Sorted Set)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoCacheService {

    private static final String GEO_KEY = "parkingspaces:geo";
    private static final int MAX_RESULTS = 100;

    private final StringRedisTemplate stringRedisTemplate;

    public void addSpace(Long spaceId, double longitude, double latitude) {
        try {
            GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();
            geo.add(GEO_KEY,
                    new RedisGeoCommands.GeoLocation<>(
                            String.valueOf(spaceId),
                            new Point(longitude, latitude)
                    )
            );
            log.debug("GEO cache: added spaceId={} at ({}, {})", spaceId, longitude, latitude);
        } catch (Exception e) {
            log.warn("GEO cache add failed for spaceId={}: {}", spaceId, e.getMessage());
        }
    }

    public void removeSpace(Long spaceId) {
        try {
            // GEO key는 내부적으로 Sorted Set — ZREM으로 제거
            stringRedisTemplate.opsForZSet().remove(GEO_KEY, String.valueOf(spaceId));
            log.debug("GEO cache: removed spaceId={}", spaceId);
        } catch (Exception e) {
            log.warn("GEO cache remove failed for spaceId={}: {}", spaceId, e.getMessage());
        }
    }

    public List<Long> findNearbySpaceIds(double longitude, double latitude, double radiusKm) {
        GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geo.search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(longitude, latitude)),
                new Distance(radiusKm, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .limit(MAX_RESULTS)
                        .sortAscending()
        );

        if (results == null) return List.of();

        return results.getContent().stream()
                .map(r -> Long.parseLong(r.getContent().getName()))
                .collect(Collectors.toList());
    }
}
