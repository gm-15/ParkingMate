# 🅿️ ParkingMate

신뢰 문제를 해결하여 시장을 혁신할 차세대 주차 공간 공유 플랫폼

## 📜 1. 프로젝트 핵심 요약 (Executive Summary)

'파킹메이트(ParkingMate)'는 도심의 만성적인 주차난과 개인의 유휴 주차 공간이라는 두 가지 문제를 동시에 해결하는 P2P(Peer-to-Peer) 주차 공간 공유 플랫폼입니다. 본 프로젝트는 백엔드의 동시성 제어와 클라우드의 IaC 기반 자동화 인프라 구축 경험을 통해, 엔지니어링 역량을 증명하는 것을 목표로 합니다.

기존 경쟁사들이 단순히 공간을 '중개'하는 데 그쳤다면, 파킹메이트는 **'사진 증거 시스템'**과 **'자체 보상 프로그램'**을 도입하여 공유 경제의 가장 큰 문제인 '신뢰'와 '오프라인 분쟁'을 해결함으로써 시장의 새로운 표준을 제시하고자 합니다.

## 💡 2. 프로젝트 배경 및 목적 (Background & Purpose)

### 문제 정의
- 도심 운전자의 주차 공간 탐색 시간 낭비
- 유휴 주차 공간의 비효율적 방치
- 전체 교통량의 약 30%가 주차 공간을 찾는 차량
- 주차 문제로 인한 교통체증, 대기오염, 에너지 소모

### 솔루션
- 사진 증거 시스템 기반의 P2P 주차 공유 매칭
- 클라우드 기반의 안정적인 서비스 제공
- AI 기반 최적 주차 공간 추천 및 동적 가격 제안
- JPA를 활용한 데이터 정합성 보장 및 Lock 메커니즘을 통한 동시성 문제 해결

## 🎯 3. 프로젝트 목표 (Project Goals)

### 백엔드 엔지니어링
- SOLID 원칙 준수
- JPA를 활용한 데이터 정합성 보장
- Lock 메커니즘을 통한 동시성 문제 해결
- RESTful API 설계 및 구현

### 클라우드/인프라 엔지니어링 (Advanced)
- **IaC 도입**: Terraform을 사용하여 AWS 인프라 전체를 코드로 관리 (재사용성 및 버전 관리 확보)
- **고가용성 설계**: Multi-AZ 기반의 ALB(Application Load Balancer) + ASG(Auto Scaling Group) 구성으로 무중단 서비스 지향
- **CI/CD 파이프라인**: GitHub Actions를 활용해 테스트-빌드-이미지 푸시(ECR)-배포(ECS/EB) 전 과정을 자동화
- **Observability 구축**: Prometheus와 Grafana를 연동하여 인프라 메트릭 모니터링 체계 구축

## ⚙️ 4. 핵심 기술 스택 (Tech Stack)

### Backend
- Java 21
- Spring Boot 3.x
- Spring Data JPA
- Spring Security
- JWT (JSON Web Token)

### Database
- MySQL (RDS) - 프로덕션
- H2 Database - 로컬 개발/테스트
- Redis - 캐시 및 분산 락

### Cloud & Infrastructure
- AWS (VPC, EC2, RDS, S3, ECR, ALB, ASG, ECS/EB)
- Terraform (IaC)

### DevOps
- Docker
- Docker Compose
- GitHub Actions
- Prometheus & Grafana (Monitoring)

### Frontend
- React 19
- Vite
- React Router

## 🗺️ 5. 주요 기능 및 아키텍처 (Features & Architecture)

### 주요 기능
- 사용자 관리 (회원가입, 로그인, JWT 인증)
- 주차 공간 CRUD (등록, 조회, 수정, 삭제)
- 예약 시스템 (동시성 제어 포함)
- 사진 업로드 시스템 (예정)

### 클라우드 아키텍처
- Public Subnet (ALB, NAT Gateway)
- Private Subnet (App Server, DB)
- Bastion Host를 통한 안전한 내부 인프라 접속 관리

### 동시성 제어 전략
- Optimistic Lock: `@Version` 어노테이션을 활용한 낙관적 락
- Pessimistic Lock: `LockModeType.PESSIMISTIC_WRITE`를 활용한 비관적 락
- Redis 분산 락: 분산 환경에서의 동시성 제어

## 🤝 6. 팀 구성 및 역할

### 역할
- 백엔드 설계 및 클라우드 인프라 아키텍트

### 협업 전략
- 팀원들이 Docker 환경에서 일관되게 개발할 수 있도록 인프라 환경을 표준화
- 기술적 의사결정 근거를 문서로 공유 (ADR 작성)

## 📁 프로젝트 구조

```
ParkingMate/
├── parkingmate/              # Backend (Spring Boot)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/parkingmate/parkingmate/
│   │   │   │   ├── config/      # 설정 클래스
│   │   │   │   ├── controller/  # REST API 컨트롤러
│   │   │   │   ├── domain/      # 도메인 엔티티
│   │   │   │   ├── dto/         # Data Transfer Objects
│   │   │   │   ├── repository/  # JPA Repository
│   │   │   │   ├── service/     # 비즈니스 로직
│   │   │   │   └── util/        # 유틸리티 클래스
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/
│   ├── build.gradle
│   └── Dockerfile
├── parking-mate-frontend/    # Frontend (React)
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── infrastructure/           # Terraform IaC
│   ├── main.tf
│   ├── variables.tf
│   └── outputs.tf
├── .github/
│   └── workflows/
│       └── ci-cd.yml         # CI/CD 파이프라인
├── docker-compose.yml        # 로컬 개발 환경
└── README.md

```

## 🚀 시작하기

### 전제 조건
- Java 17 이상
- Node.js 18 이상
- Docker & Docker Compose
- MySQL 8.0 이상
- Redis 7.0 이상

### 로컬 개발 환경 실행

1. 저장소 클론
```bash
git clone <repository-url>
cd ParkingMate
```

2. Docker Compose로 로컬 환경 실행
```bash
docker-compose up -d
```

3. 백엔드 실행 (별도 터미널)
```bash
cd ParkingMate/parkingmate
./gradlew bootRun
```

4. 프론트엔드 실행 (별도 터미널)
```bash
cd ParkingMate/parking-mate-frontend
npm install
npm run dev
```

## 📝 라이선스

