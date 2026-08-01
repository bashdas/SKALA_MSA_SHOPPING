# Gateway MSA E2E 테스트

## 목적

`gateway-service`, `user-service`, `order-service`를 실제로 함께 실행해 Gateway 라우팅, 회원가입, JWT 인증 헤더 전달, 상품 및 주문, 포인트 차감·환불, 주문 소유권, 재고 원상복구까지 서비스 간 전체 흐름을 검증한다. 운영 코드는 변경하지 않는다.

외부 요청 구조는 다음과 같다.

```text
Client
  ↓
gateway-service :8080
  ├── /api/customers/** → user-service :8081
  ├── /api/products/**  → order-service :8082
  └── /api/orders/**    → order-service :8082
```

내부 서비스 통신은 Gateway를 거치지 않는다.

```text
order-service :8082
  → user-service :8081
```

Gateway는 외부 요청의 논리적 단일 진입점이며 `Authorization` 헤더를 하위 서비스로 전달한다. Gateway 자체는 JWT를 검증하지 않는다. JWT 검증과 JWT 고객 ID 기반 주문 소유권 검증은 `order-service`가 담당한다. `/internal/**`는 Gateway Route에 등록하지 않아 논리적으로 외부에 노출하지 않는다.

## 실행 전제

- macOS 또는 일반 Linux의 Bash 환경
- `bash`, `curl`, `java`
- JSON 파싱용 `jq` 또는 `python3` 중 하나
- 8080, 8081, 8082 포트가 모두 비어 있어야 함
- 각 서비스의 Gradle Wrapper가 실행 가능해야 함(시스템 Gradle은 불필요)

## 실행

모노레포 루트에서 다음 명령을 실행한다.

```bash
./scripts/msa-e2e-test.sh
```

스크립트는 다음 순서로 기동과 readiness polling을 수행한다.

1. `user-service` 8081 실행 및 준비 확인
2. `order-service` 8082 실행 및 준비 확인
3. `gateway-service` 8080 실행 및 `/actuator/health` 확인
4. 모든 외부 API 요청을 Gateway 8080으로 실행

각 준비 단계에는 최대 120초 timeout이 있으며 고정된 긴 sleep이나 무한 대기를 사용하지 않는다.

`user-service`의 JWT 발급과 `order-service`의 JWT 검증에는 같은 대칭키가 필요하다. 스크립트가 실행할 때마다 임시 E2E 전용 `JWT_SECRET`을 만들고 두 서비스에 동일하게 전달한다. Gateway에는 JWT secret을 전달하지 않는다. `order-service`에는 `USER_SERVICE_BASE_URL=http://localhost:8081`, Gateway에는 `USER_SERVICE_URL=http://localhost:8081`과 `ORDER_SERVICE_URL=http://localhost:8082`를 전달한다. 운영 secret은 저장하거나 사용하지 않는다.

## 검증 시나리오

1. 세 서비스 실행과 Gateway health `UP`
2. Gateway에서 `/internal/**` 조회·포인트 API가 404이며 포인트가 변하지 않음
3. Gateway를 통한 고객 A 회원가입, 초기 10,000포인트와 로그인/JWT
4. Gateway를 통한 정수 가격 상품 두 개 등록 및 조회
5. 미인증 주문의 401/`UNAUTHORIZED`
6. JWT가 Gateway를 통해 전달되는 신규 주문, 주문 내용, 포인트 및 재고 차감
7. 기존 주문 수량 누적, 동일 주문 ID, 증가분 포인트 차감
8. 고객 B의 주문 조회·부분 취소·전체 취소 차단과 무변경 확인
9. 부분 취소, 일부 포인트 환불, 일부 재고 복구
10. 전체 취소, 전액 환불, 주문·포인트·재고 원상복구
11. 미등록 Gateway 경로 `/unknown`의 404

고객·상품·주문 관련 외부 API는 모두 Gateway 8080을 사용한다. 포인트 잔액은 공개 고객 응답만으로 매 단계 확인할 수 없으므로, 테스트 검증에 한해 `user-service`의 `/internal/customers/{id}`를 8081로 직접 호출한다. `/internal/**`를 Gateway로 우회 호출하지 않는다.

H2 인메모리 DB와 `create-drop` 설정을 사용하므로 서비스를 다시 실행할 때마다 데이터가 초기화된다.

## 로컬 네트워크 한계

로컬 실행에서는 Gateway뿐 아니라 하위 서비스의 8081, 8082 포트도 열려 있다. Gateway 코드만으로 이 포트의 직접 접근을 차단할 수 없다. 운영 환경에서는 private network, firewall, security group 등으로 하위 서비스 접근을 차단해야 한다. `/internal/**`를 Gateway Route에 등록하지 않은 것은 논리적 미노출이며 물리적 네트워크 차단과는 별개다.

## 실패 로그

실패하면 마지막 출력에 `/tmp` 또는 시스템 임시 디렉터리 아래의 `shopping-msa-e2e.*` 경로가 표시된다. 그 안의 `user-service.log`, `order-service.log`, `gateway-service.log`, 각 PID 및 마지막 HTTP 응답을 확인할 수 있다. 성공하면 이 임시 디렉터리를 자동 삭제한다. 성공·실패와 관계없이 trap이 세 서비스와 자식 Gradle·Java 프로세스를 종료한다.

## 검증하지 않는 범위

- 실제 운영 DB
- 네트워크 분리 환경
- Circuit Breaker
- Service Discovery
- 메시지 기반 Saga
- 다중 인스턴스 환경
- 부하 테스트
- Gateway 고가용성
- Gateway 로드밸런싱
- Gateway Rate Limiting
- 하위 서비스 포트의 네트워크 차단
- TLS 및 HTTPS
- 운영용 인증서
