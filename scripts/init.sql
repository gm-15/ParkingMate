-- ParkingMate 데이터베이스 초기화 스크립트
-- docker-compose로 MySQL이 시작될 때 자동으로 실행됩니다.

CREATE DATABASE IF NOT EXISTS parkingmate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE parkingmate;

-- 필요한 경우 초기 데이터를 여기에 추가할 수 있습니다.

