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

현재 기준 이벤트는 다음 11개다.

| 코드 | 의미 |
|---|---|
| `ENVIRONMENT_THRESHOLD_BREACHED` | 재배 환경값이 임계 범위를 벗어남 |
| `ENVIRONMENT_RECOVERED` | 환경값이 정상 범위로 복구됨 |
| `SENSOR_OFFLINE` | 센서 오프라인 |
| `SENSOR_ERROR` | 센서 오류 |
| `ACTUATOR_CONTROL_SUCCEEDED` | 장치 ON/OFF 제어 성공 |
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

## REST API

현재 구현 경로는 다음과 같다. 최종 팀 계약에서 Base Path가 바뀌면 Gateway Route와 함께
변경한다.

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

GET    /api/v1/notification-subscription-types
GET    /api/v1/notifications
```

DELETE는 소프트 삭제다. `enabled=false`는 일시정지이고 `is_deleted=true`는 삭제 처리다.
API Gateway가 JWT를 검증한 뒤 전달하는 `X-User-Id`를 사용자 ID로 사용하며, API는
본인 소유 데이터만 조회·수정해야 한다.

## RabbitMQ 계약

2026년 8월 4일 공동 회의에서 Notification 계열 이벤트는 Durable Direct Exchange인
`yes-nhn.notification.exchange`로 모으기로 했다. Producer는 Exchange에 이벤트를
발행하고, Notification이 다음 Queue를 선언·Binding한 뒤 소비한다.

| 용도 | Queue |
|---|---|
| 환경 임계값 초과·복구 | `yes-nhn.notification.threshold.queue` |
| 장치 제어 성공·실패 | `yes-nhn.notification.action.queue` |
| AI 일일 피드백 완료 | `yes-nhn.notification.daily.queue` |
| 로그인 성공 | `yes-nhn.notification.login.queue` |
| 문의 등록 | `yes-nhn.notification.question.queue` |
| 문의 답변 완료 | `yes-nhn.notification.answer.queue` |
| 수확 완료 | `yes-nhn.notification.harvest.queue` |
| 재배 종료 | `yes-nhn.notification.cultivation-finished.queue` |

Routing Key는 아직 최종 합의하지 않았다. `application.yml`은 로컬 실행을 위해 Queue명과
같은 값을 기본 Routing Key로 사용하지만, 이 값은 Producer 계약 확정 후 교체해야 한다.
Producer별 payload 필드명도 최종 합의 전까지 공통 Parser에서 과도하게 제한하지 않는다.

공용 Dead Letter Exchange와 Queue는 `yes-nhn.dlx`, `yes-nhn.dlq`를 사용한다.
Notification은 공용 DLQ를 자동으로 소비하지 않으며, 관리자가 RabbitMQ Management
화면에서 원인을 확인한 뒤 수동으로 처리·삭제한다.

계약 확정 전 준비 작업으로 `DomainEventParser`가 공통 envelope의 JSON 역직렬화와
필수값을 검증한다. 임시 수확 완료 JSON, 필수 필드 누락, 잘못된 `targetId`, 잘못된 JSON을
테스트하지만, 아직 미확정인 Producer별 payload 구조는 공통 Parser에서 제한하지 않는다.
RabbitMQ Listener와 Direct Exchange·다중 Queue·공용 DLX·DLQ 선언은 구현했다.

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

- RabbitMQ routing key·vhost·ACK/NACK와 Consumer 재시도 세부 방식
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

중앙 배포용 서비스명과 Spring Application 이름은 `notification-server`로 사용한다.
Config 저장소의 allowlist와 Kubernetes manifest가 아직 Notification에 대해 등록되지
않았으므로, Deployment와 Service도 같은 이름으로 인프라 담당자가 등록해야 한다.

## Gateway 사용자 ID 전달

Auth Service가 JWT의 `sub` claim에 사용자 ID를 넣고, API Gateway가 JWT 서명·만료를
검증한다. 검증에 성공하면 Gateway가 사용자 ID를 `X-User-Id` 헤더로 Notification
Service에 전달한다.

Notification Service는 JWT를 다시 검증하거나 claim을 직접 해석하지 않는다. Controller는
`X-User-Id`를 `Long`으로 받아 본인 소유 Endpoint·Subscription만 조회·변경한다.

이 구조가 안전하려면 Notification Service를 외부에 직접 노출하지 않고 사용자 요청이
반드시 Gateway를 거치도록 해야 한다. 또한 Gateway는 클라이언트가 임의로 넣은
`X-User-Id`를 신뢰하지 않고, 검증한 JWT의 `sub` 값으로 덮어써야 한다.

Controller와 공통 `ErrorResponse`, `@RestControllerAdvice`가 구현되어 있다. 사용자 ID
형식 오류, 소유 데이터 없음, 중복 리소스, 지원하지 않는 채널, 외부 Provider 실패를 서로
다른 HTTP 상태와 오류 코드로 구분한다. 최종 팀 공통 오류 형식이 확정되면 필드명을 맞춘다.

예외가 발생하면 운영 로그에는 이벤트·알림·발송을 추적할 수 있는 식별자와 재시도 정보를
남긴다. JWT·Webhook URL·Chat ID·토큰·민감한 payload 원문은 기록하지 않는다.
