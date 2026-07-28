# Notification Service 휴가기간 복습용 통합교재

> Notification Service를 처음 접하는 사람이 이 문서 하나로 목적, 용어, ERD, 코드, 이벤트 흐름, 테스트와 남은 일을 복습할 수 있도록 정리한 문서다.

## 1. 한 문장 요약

버섯 재배 중 다른 서비스에서 발생한 사건을 RabbitMQ로 받아, 사용자의 구독과 수신 채널을 확인하고 Telegram 또는 Discord로 발송하며 결과를 PostgreSQL에 기록하는 서비스다.

```text
센서/Rule/Cultivation/AI/Auth/Inquiry
              ↓ RabbitMQ 이벤트
       Notification Service Consumer
              ↓ 구독·Endpoint·템플릿 조회
       Telegram 또는 Discord 발송
              ↓
       notification_delivery 이력 저장
```

## 2. 왜 별도 서비스인가

Rule Engine이나 센서 서비스가 직접 Telegram API를 호출하면 채널 인증, 메시지 형식, 재시도, 사용자별 설정이 여러 서비스에 흩어진다. Notification Service를 별도로 두면 다른 서비스는 “무슨 일이 발생했는지”만 이벤트로 전달하고, 이 서비스는 “누구에게 어떤 채널로 보낼지와 발송 결과”를 전담한다.

중요한 경계는 다음과 같다.

- Rule Engine: 센서값을 판단하고 환경 이상·복구·센서 오류·제어 실패 이벤트를 발행
- Cultivation: 수확 완료·재배 종료 이벤트 발행
- AI: AI 일일 피드백 완료 이벤트 발행
- Auth: 로그인 성공 이벤트 발행
- Inquiry: 문의 답변 완료 이벤트 발행
- Notification: 이벤트 수신, 구독 필터링, 템플릿 렌더링, 외부 채널 발송, 이력 저장

## 3. 현재 확정 범위

공식 채널은 Telegram과 Discord다. WebSocket은 지원하지 않는다. AI 알림도 주간·월간이 아니라 `DAILY_FEEDBACK_COMPLETED` 일일 피드백만 사용한다.

현재 이벤트 코드는 다음 10개다.

| 코드 | 의미 |
|---|---|
| `ENVIRONMENT_THRESHOLD_BREACHED` | 환경값이 임계값을 벗어남 |
| `ENVIRONMENT_RECOVERED` | 환경값이 정상으로 복구됨 |
| `SENSOR_OFFLINE` | 센서 오프라인 |
| `SENSOR_ERROR` | 센서 오류 |
| `ACTUATOR_CONTROL_FAILED` | 물펌프 등 제어 실패 |
| `HARVEST_COMPLETED` | 수확 완료 |
| `CULTIVATION_FINISHED` | 재배 종료 |
| `DAILY_FEEDBACK_COMPLETED` | AI 일일 피드백 완료 |
| `LOGIN_SUCCEEDED` | 로그인 성공 |
| `INQUIRY_ANSWERED` | 문의 답변 완료 |

8월 3일 공동 회의에서 exchange, queue, routing key, 실제 JSON, JWT claim, 권한 API, 오류 형식, provider 테스트 방식을 최종 확정한다. 그 전에는 임의 값을 계약으로 고정하지 않는다.

## 4. 핵심 용어

### 이벤트(Event)
다른 서비스에서 발생한 사실이다. `SENSOR_OFFLINE`은 센서가 오프라인이 되었다는 사실이며, Notification Service가 판단하는 것이 아니다.

### 알림(Notification)
발생한 이벤트를 수신자에게 전달하기 위해 저장한 내부 알림 원본이다.

### 발송 이력(Delivery)
한 알림을 특정 구독·채널로 실제 발송한 기록이다. Telegram과 Discord로 각각 보내면 delivery도 두 건이다.

### Endpoint
실제 수신 주소다. Telegram Chat ID 또는 Discord Webhook을 저장한다.

### Subscription
특정 Endpoint가 어떤 이벤트를 받을지 정하는 설정이다.

### Template
이벤트와 채널에 맞는 메시지 형식이다.

### Channel
Telegram·Discord 같은 발송 매체다.

### Target
이벤트의 대상 종류다. `CULTIVATION`, `INQUIRY` 등이 있으며 `targetId`는 실제 재배 ID 또는 문의 ID다.

### Enabled와 Soft Delete
`enabled=false`는 잠시 끄는 일시정지다. `is_deleted=true`는 삭제 처리이며 목록에서 숨긴다. 삭제해도 과거 발송 이력을 보존하기 위해 행은 DB에 남긴다.

## 5. ERD 읽는 순서

알림 한 건을 이해할 때는 아래 순서로 읽는다.

1. `notification_event_type`: 무슨 사건인가?
2. `notification_subscription_type`: 어떤 사건·대상 조합을 구독하는가?
3. `notification_subscription`: 사용자가 실제로 그 구독을 켰는가?
4. `notification_endpoint`: 어디로 보낼 것인가?
5. `notification_template`: 채널별 문장을 어떻게 만들 것인가?
6. `notification`: 들어온 원본 이벤트를 어떻게 저장했는가?
7. `notification_delivery`: 실제 발송 결과는 무엇인가?

## 6. 테이블 상세

### `channel_type`
Telegram·Discord 기준 데이터다. `code`, `display_name`, `is_deleted`를 가진다.

### `subscription_target_type`
구독 대상 종류 사전이다. 현재 재배와 문의 같은 유형을 표현한다.

### `notification_event_type`
이벤트 코드, 표시 이름, 설명, 대상 유형 FK를 가진다. 코드는 화면 문구와 분리된 안정적인 프로그램 계약이다.

### `notification_subscription_type`
이벤트 유형과 대상 유형을 조합한 구독 종류다. 설명과 소프트 삭제 필드를 가진다.

### `subscription_channel`
구독 종류와 채널의 다대다 관계를 연결하는 중간 테이블이다.

### `notification_endpoint`
사용자 소유의 실제 주소다. `user_id`, `channel_type_id`, `destination`, `display_name`, `enabled`, `is_deleted`가 핵심이다. 기본 목록은 `enabled=true AND is_deleted=false`만 보여준다.

### `notification_subscription`
구독 종류와 Endpoint를 연결한다. `target_id`, `enabled`, `is_deleted`가 있다. 같은 활성 구독 중복 생성을 막는다.

### `notification_template`
`notification_event_type_id`, `channel_type_id`, `body_template`, `version`을 가진다. 같은 이벤트도 Telegram과 Discord의 표현 형식이 다르므로 채널별 템플릿이 필요하다.

### `notification`
RabbitMQ의 `source_event_id`, 원본 `event_payload`, 최종 `message`, 생성 시각을 저장한다. 채널별 발송 주소는 이 테이블에 넣지 않는다.

### `notification_delivery`
알림·구독·템플릿 FK, `status`, provider message ID, 렌더링 메시지, 시도 횟수, 오류, 발송 시각을 저장한다. 상태는 BOOLEAN이 아니라 `PENDING`, `SENT`, `FAILED`(필요 시 `SENDING`)이다.

## 7. 실제 처리 흐름

예를 들어 온도가 임계값을 넘었다면 다음 순서다.

1. 센서가 측정값을 발행한다.
2. Rule Engine이 기준 초과를 판단한다.
3. `ENVIRONMENT_THRESHOLD_BREACHED` 이벤트를 RabbitMQ에 발행한다.
4. Consumer가 메시지를 받는다.
5. `source_event_id`로 중복 여부를 확인한다.
6. 이벤트 유형을 찾는다.
7. 활성 Subscription과 Endpoint를 조회한다.
8. Endpoint 채널에 맞는 Template을 선택한다.
9. payload 변수로 메시지를 렌더링한다.
10. Delivery를 `PENDING`으로 저장한다.
11. Telegram/Discord API를 호출한다.
12. 성공하면 `SENT`, provider ID, `sent_at`을 기록한다.
13. 실패하면 횟수와 오류를 기록하고 3~5회 재시도한다.
14. 계속 실패하면 `FAILED`로 확정한다.

## 8. RabbitMQ 기초

- Producer: 이벤트를 보내는 서비스
- Consumer: 이벤트를 받는 Notification Service
- Exchange: 메시지를 queue로 분배하는 입구
- Queue: 소비 대기열
- Routing key: 메시지 종류를 구분하는 키
- ACK: 정상 처리했다는 확인
- DLQ: 반복 실패 메시지 보관 큐

DB 저장과 필요한 처리 전에 ACK하면 장애 때 메시지가 유실될 수 있다. 실제 이름과 ACK 시점은 공동 계약에서 맞춘다.

개념적 이벤트 예시는 다음과 같다.

```json
{
  "eventId": "uuid",
  "eventType": "ENVIRONMENT_THRESHOLD_BREACHED",
  "occurredAt": "2026-07-28T10:00:00Z",
  "targetType": "CULTIVATION",
  "targetId": 123,
  "userId": 45,
  "payload": { "sensorType": "TEMPERATURE", "value": 28.5, "thresholdMax": 25.0, "unit": "C" }
}
```

실제 필드명은 Producer별 계약을 받은 후 적용한다.

## 9. API 초안

Endpoint:

```text
POST   /api/v1/notification-endpoints
GET    /api/v1/notification-endpoints
PATCH  /api/v1/notification-endpoints/{endpointId}
PATCH  /api/v1/notification-endpoints/{endpointId}/enabled
DELETE /api/v1/notification-endpoints/{endpointId}
```

Subscription:

```text
POST   /api/v1/notification-subscriptions
GET    /api/v1/notification-subscriptions
PATCH  /api/v1/notification-subscriptions/{subscriptionId}/enabled
DELETE /api/v1/notification-subscriptions/{subscriptionId}
```

DELETE는 소프트 삭제다. 사용자는 자기 Endpoint와 Subscription만 조회·수정해야 한다. Base Path, JWT claim, 오류 JSON은 공동 회의 후 확정한다.

## 10. 코드 구조

```text
src/main/java/com/ecosphere/notification
├─ domain       Entity와 공통 AuditEntity
├─ messaging    DomainEvent 메시지 모델
└─ repository   Endpoint/Subscription DB 조회
```

`@Entity`는 테이블 매핑, `@Id`는 PK, `@ManyToOne`은 다대일 관계, `@JoinColumn`은 FK 컬럼이다. Repository는 DB 조회를 담당하며 사용자 ID와 `is_deleted` 조건을 함께 적용해야 한다. Controller와 Service는 API 계약 확정 뒤 구현한다.

## 11. Flyway와 Seed

```text
V1__create_notification_tables.sql
V2__seed_notification_reference_data.sql
V3__move_template_reference_to_delivery.sql
V4__add_endpoint_soft_delete.sql
V5__fix_active_subscription_unique_index.sql
```

V1은 구조, V2는 기준 데이터, V3은 템플릿 FK 정리, V4는 Endpoint 소프트 삭제, V5는 활성 구독 중복 방지다. Flyway는 파일을 버전 순서로 한 번만 실행한다. 이미 적용된 migration 파일을 함부로 수정하면 checksum 오류가 날 수 있으므로 새 변경은 V6으로 만든다.

Seed는 Telegram·Discord, 이벤트·대상·구독 유형, 템플릿 같은 기준 데이터를 넣는다. 재실행해도 중복되지 않게 `ON CONFLICT DO NOTHING` 또는 UNIQUE code를 사용한다.

## 12. 테스트

```bash
mvn test
mvn -Dnotification.integration.enabled=true test
```

첫 명령은 일반 테스트다. 두 번째는 PostgreSQL이 실행된 환경에서 Migration·Repository까지 검증한다. 현재 Docker API 호환 문제를 피하기 위해 Repository 통합 테스트는 외부 PostgreSQL 접속 방식으로 gated 되어 있다.

JaCoCo 리포트는 `target/site/jacoco/index.html`에서 확인한다. 커버리지 숫자만으로 외부 RabbitMQ나 실제 Telegram 발송 성공을 보장할 수는 없다.

현재 완료된 것은 Entity, Migration V1~V5, Seed, Endpoint/Subscription Repository, Entity 테스트, PostgreSQL Migration·Seed 검증, Repository 통합 테스트, API 초안 문서다.

## 13. 남은 작업

1. 8월 3일 RabbitMQ 공동 회의
2. Producer별 실제 JSON, exchange·queue·routing key 수령
3. Consumer와 중복 이벤트 처리
4. Subscription/Endpoint 필터링 서비스
5. 채널별 템플릿 렌더링
6. Telegram·Discord Provider 발송
7. 3~5회 재시도와 실패 확정
8. DTO·Service·Controller 및 검증
9. JWT 사용자 소유권과 Cultivation 권한 연동
10. 다른 서비스와 통합 테스트

## 14. 회의 질문

- `eventId`, `eventType`, `targetId`의 실제 필드명과 타입은?
- targetId는 재배 ID·문의 ID 중 무엇을 의미하는가?
- RabbitMQ exchange·queue·routing key·vhost·host·port는?
- JWT 사용자 ID claim은?
- Cultivation 권한 API 주소와 응답 형식은?
- Telegram Chat ID와 Discord Webhook 테스트 계정은?
- 실패 재시도 횟수·간격·ACK 시점은?
- 알림 목록과 읽음 처리 API가 공식 요구사항인가?

## 15. 자주 하는 오해

- Rule Engine이 알림까지 발송하는 것이 아니다. 판단 후 이벤트만 보낸다.
- Endpoint는 주소, Subscription은 수신 설정, Delivery는 발송 결과다.
- `enabled=false`와 `is_deleted=true`는 다르다.
- 알림 하나에 채널 템플릿 하나만 있는 것이 아니다. 채널별 delivery가 각 템플릿을 선택한다.
- 주간 피드백과 WebSocket은 현재 범위가 아니다.
- 커버리지 100%가 곧 외부 연동 완료를 의미하지 않는다.

## 16. 휴가 중 학습 순서

1. 1~4장을 읽고 서비스 목적과 용어를 말로 설명한다.
2. 5~6장을 보며 ERD에서 각 FK 선을 찾는다.
3. 7장의 환경 이상 예시를 직접 종이에 그린다.
4. `PENDING → SENT/FAILED` 상태 흐름을 설명한다.
5. V1~V5의 목적을 한 문장씩 요약한다.
6. Entity와 Repository 코드를 테이블 설명과 대조한다.
7. 14장 질문에 답을 적고 8월 3일 회의에서 확인한다.

## 17. 최종 기억 문장

**Notification Service는 이벤트를 판단하는 곳이 아니라, 이벤트를 받아 구독자를 찾아 Telegram·Discord로 안전하게 발송하고 그 결과를 남기는 곳이다.**
