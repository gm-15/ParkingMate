# ParkingMate Infrastructure as Code (Terraform)

이 디렉토리는 AWS 인프라를 Terraform으로 관리하는 코드를 포함합니다.

## 구조

- `main.tf`: 주요 인프라 리소스 정의 (VPC, Subnets, Security Groups, ALB, ECR 등)
- `variables.tf`: 변수 정의
- `outputs.tf`: 출력 값 정의
- `terraform.tfvars.example`: 변수 값 예시 파일 (실제 사용 시 terraform.tfvars로 복사)

## 사용 방법

### 1. 초기 설정

```bash
cd infrastructure
terraform init
```

### 2. 변수 파일 생성

```bash
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars 파일을 열어 필요한 값 수정 (특히 비밀번호)
```

### 3. 계획 확인

```bash
terraform plan
```

### 4. 인프라 생성

```bash
terraform apply
```

### 5. 인프라 삭제

```bash
terraform destroy
```

## 주요 리소스

### 네트워킹
- VPC (10.0.0.0/16)
- Public Subnets (2개, Multi-AZ)
- Private Subnets (2개, Multi-AZ)
- Internet Gateway
- NAT Gateway (2개)
- Route Tables

### 보안
- Security Groups (ALB, App, RDS, Redis, Bastion)
- VPC 엔드포인트 (선택 사항)

### 로드 밸런싱
- Application Load Balancer (ALB)
- Target Group

### 스토리지
- S3 Bucket (Artifacts)
- ECR Repositories (Backend, Frontend)

### 데이터베이스 (추가 필요)
- RDS MySQL (변수로 정의되어 있지만 main.tf에 리소스 추가 필요)
- ElastiCache Redis (변수로 정의되어 있지만 main.tf에 리소스 추가 필요)

### 컴퓨팅 (추가 필요)
- ECS Cluster & Service
- Auto Scaling Group
- EC2 Bastion Host

## 참고사항

- 실제 운영 환경에서는 Terraform State를 S3 백엔드로 관리하는 것을 권장합니다.
- RDS와 ElastiCache 리소스는 비용을 고려하여 필요 시 추가하세요.
- ECS/EC2 인스턴스 설정은 추가로 구현이 필요합니다.
- Bastion Host의 SSH 접근은 특정 IP로 제한하는 것을 권장합니다.

