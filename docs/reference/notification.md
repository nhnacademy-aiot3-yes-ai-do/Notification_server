# Notification Service

## 역할

Notification Service는 다른 서비스가 RabbitMQ로 발행한 이벤트를 받아 사용자의 구독과
수신 경로를 확인한 뒤 Telegram 또는 Discord로 알림을 발송하고, 발송 결과를 PostgreSQL에
저장하는 서비스다.

Rule Engine·Cultivation·AI·Auth·Inquiry가 사건을 판단하거나 생성하고,
Notification Service는 그 사건을 전달하는 역할을 맡는다. WebSocket은 지원하지 않는다.

## 책임

- Telegram·Discord 알림 발송
- 사용자 Endpoint 등록·조회·수정·소프트 삭제
- 사용자 Subscription 등록·조회·비활성화·소프트 삭제
- 이벤트·대상·채널별 템플릿 선택과 메시지 렌더링
- 발송 성공·실패·재시도 이력 저장

## 이벤트

현재 기준 이벤트는 다음 10개다.

| 코드 | 의미 |
|---|---|
| `ENVIRONMENT_THRESHOLD_BREACHED` | 재배 환경값이 임계 범위를 벗어남 |
| `ENVIRONMENT_RECOVERED` | 환경값이 정상 범위로 복구됨 |
| `SENSOR_OFFLINE` | 센서 오프라인 |
| `SENSOR_ERROR` | 센서 오류 |
| `ACTUATOR_CONTROL_FAILED` | 자동 제어 실패 |
| `HARVEST_COMPLETED` | 수확 완료 |
| `CULTIVATION_FINISHED` | 재배 종료 |
| `DAILY_FEEDBACK_COMPLETED` | AI 일일 피드백 완료 |
| `LOGIN_SUCCEEDED` | 로그인 성공 |
| `INQUIRY_ANSWERED` | 문의 답변 완료 |

주간·월간 피드백 이벤트는 사용하지 않는다.

## 처리 흐름

```text
Producer 서비스
  → RabbitMQ 이벤트
  → Notification Consumer
  → source_event_id 중복 확인
  → 활성 Subscription·Endpoint 조회
  → 이벤트×채널 Template 선택
  → notification 저장
  → 채널별 notification_delivery 생성
  → Telegram/Discord 발송
  → PENDING → SENT 또는 FAILED
```

구독자가 없으면 원본 `notification`만 남고 Delivery는 생성하지 않는다. 이는 오류가
아니라 발송 대상이 없는 정상 상황이다.

## API 초안

최종 Base Path는 팀 계약에서 확정한다. 현재 문서의 경로는 초안이다.

```text
POST   /api/v1/notification-endpoints
GET    /api/v1/notification-endpoints
PATCH  /api/v1/notification-endpoints/{endpointId}
PATCH  /api/v1/notification-endpoints/{endpointId}/enabled
DELETE /api/v1/notification-endpoints/{endpointId}

POST   /api/v1/notification-subscriptions
GET    /api/v1/notification-subscriptions
PATCH  /api/v1/notification-subscriptions/{subscriptionId}/enabled
DELETE /api/v1/notification-subscriptions/{subscriptionId}
```

DELETE는 소프트 삭제다. `enabled=false`는 일시정지이고 `is_deleted=true`는 삭제 처리다.
Auth Service의 JWT에서는 `sub` claim을 사용자 ID로 사용하며, API는 본인 소유 데이터만
조회·수정해야 한다.

## RabbitMQ 계약

이벤트의 실제 exchange, queue, routing key, JSON payload는 Producer 담당자들과 공동
회의에서 확정한다. 현재 이벤트 코드만 기준으로 관리하고, 미확정 필드명을 임의로
Consumer에 고정하지 않는다.

## 외부 채널

- Telegram: Bot API와 Chat ID 사용
- Discord: Webhook URL 사용

공통 발송 흐름은 같지만 요청 JSON과 응답 형식이 달라 채널별 Provider로 분리한다.

발송 상태는 도메인 메서드로만 변경한다. `SENT` 또는 `FAILED`로 확정된 Delivery를 다시
변경하거나 3회 초과로 시도하지 못하게 `InvalidDeliveryStateException`으로 차단한다.

## Database

자세한 테이블·FK·Migration 설명은 [notification-db.md](./notification-db.md)를 참고한다.

- `notification`: 수신한 원본 이벤트
- `notification_delivery`: 채널별 발송 결과
- `notification_endpoint`: 실제 Telegram/Discord 주소
- `notification_subscription`: Endpoint가 받을 이벤트 설정
- `notification_template`: 이벤트×채널별 메시지 양식

## 아직 확정할 항목

- RabbitMQ exchange·queue·routing key·vhost
- Producer별 실제 JSON payload
- 최종 API Base Path
- 공통 오류 응답 JSON
- Telegram·Discord 테스트 계정과 Provider 세부 방식

## CI/CD 현재 규칙

현재 저장소에는 Maven Wrapper(`mvnw`)가 없으므로 GitHub Actions와 로컬 검증은 설치된
Maven을 `mvn` 명령으로 실행한다. `./mvnw`로 변경하려면 Wrapper 파일을 먼저 저장소에
추가하고 팀 표준으로 합의해야 한다.

CI에서는 PostgreSQL 서비스 컨테이너를 실행한 뒤 통합 테스트 속성을 켜서 Migration과
Repository 테스트까지 실행한다. 로컬 통합 테스트는 Docker PostgreSQL의 `55432` 포트를
사용할 수 있다.

중앙 배포용 서비스명은 현재 `notification-server`를 임시 기준으로 사용하고 있다.
Config 저장소의 allowlist와 Kubernetes manifest가 아직 Notification에 대해 등록되지
않았으므로, 공식 repository·image·Deployment 이름은 인프라 담당자 등록 후 확정한다.

## JWT 사용자 ID 추출 구현

Auth 서비스의 JWT 계약상 사용자 ID는 `sub` claim에 문자열로 들어온다. Notification에는
`JwtUserIdExtractor` 컴포넌트를 추가해 Spring Security가 검증한 `Jwt`에서 `sub`를 양수
`Long` 사용자 ID로 변환한다.

현재 컴포넌트는 다음을 검사한다.

- 인증 객체가 존재하고 인증 상태인지
- principal이 검증된 `Jwt`인지
- `sub` claim이 존재하고 비어 있지 않은지
- `sub`가 양수 숫자 사용자 ID인지

아직 Controller에 보안 필터와 소유권 검증을 연결한 단계는 아니다. JWT 서명 알고리즘과
Secret은 운영 설정으로 확정한 뒤 Resource Server 설정에 연결한다. 임의의 Secret을 코드에
넣지 않는다.

현재 Controller와 공통 오류 응답 계약은 아직 확정 전이다. API 구현 시 JWT 오류, 소유권
오류, 중복 구독, 외부 발송 실패를 각각 HTTP 오류·재시도·Delivery 실패 기록으로 구분한다.

예외가 발생하면 운영 로그에는 이벤트·알림·발송을 추적할 수 있는 식별자와 재시도 정보를
남긴다. JWT·Webhook URL·Chat ID·토큰·민감한 payload 원문은 기록하지 않는다.
