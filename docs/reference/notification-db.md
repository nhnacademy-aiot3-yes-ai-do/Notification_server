# Notification Database

## 1. 문서 기준

이 문서는 Notification Service의 실제 코드와 Flyway Migration `V1`~`V7`을 기준으로
작성한 최신 DB 명세다. ERD의 과거 초안보다 실행되는 Migration과 Entity를 우선한다.

- DBMS: PostgreSQL
- 스키마 관리: Flyway
- JPA 정책: `ddl-auto=validate`
- 시간 타입: PostgreSQL `TIMESTAMP` ↔ Java `LocalDateTime`
- 외부 서비스 ID: 실제 FK가 아닌 소프트 참조
- 지원 채널: Telegram, Discord
- WebSocket 알림: 지원하지 않음

### SSOT 원칙

최종 스키마의 SSOT(Single Source of Truth)는 Flyway `V1`~`V7`을 순서대로 적용한
PostgreSQL 상태다. ERD는 테이블 관계를 설명하는 설계 문서이며, ERD와 문서가 실제
Migration과 다르면 Flyway 최종 상태에 맞춰 갱신한다. 이미 적용된 Migration은 수정하지
않고 다음 버전 파일로 변경한다.

## 2. 데이터 흐름

```text
RabbitMQ DomainEvent
        │
        ▼
notification ─────────────── source_event_id로 중복 방지
        │ 1
        ▼ N
notification_delivery ───── 채널별 메시지·상태·재시도 결과
        │
        ├── notification_subscription
        │       └── notification_endpoint
        │               └── channel_type
        │
        └── notification_template
                ├── notification_event_type
                └── channel_type
```

이벤트 원본은 `notification`에 한 번 저장한다. 같은 이벤트를 Telegram과 Discord로
보내면 `notification_delivery`가 채널별로 생성된다. 따라서 한 채널만 실패해도 서로
독립적으로 상태를 기록할 수 있다.

### 핵심 DB 제약

| 대상 | 실제 제약 | 목적 |
|---|---|---|
| `notification` | `source_event_id UNIQUE` | 같은 RabbitMQ 이벤트의 중복 저장·발송 방지 |
| `notification_delivery` | `UNIQUE(notification_id, notification_subscription_id)` | 같은 원본 이벤트를 같은 구독으로 두 번 발송하지 않음 |
| `notification_delivery` | `CHECK(status IN ('PENDING', 'SENT', 'FAILED'))` | 허용되지 않은 발송 상태 저장 방지 |
| `notification_delivery` | `CHECK(attempt_count BETWEEN 0 AND 3)` | 확정된 최대 3회 발송 정책 강제 |
| `notification_subscription` | partial UNIQUE, `WHERE is_deleted = FALSE` | 비삭제 상태의 같은 구독 조합은 하나만 유지 |

`enabled=false`는 삭제가 아니라 일시정지다. 따라서 `enabled=false`인 구독도 위 partial
UNIQUE 대상에 포함되며, 재구독 요청은 새 행 생성 대신 기존 구독을 다시 활성화한다.

## 3. 테이블 요약

| 테이블 | 역할 |
|---|---|
| `channel_type` | Telegram·Discord 채널 기준 정보 |
| `subscription_target_type` | CULTIVATION·INQUIRY·USER 대상 기준 정보 |
| `notification_event_type` | 수신 가능한 이벤트 코드와 대상 유형 |
| `notification_subscription_type` | 사용자가 선택할 구독 카탈로그 |
| `subscription_channel` | 구독 종류에서 허용하는 채널 |
| `notification_template` | 이벤트×채널×버전별 메시지 템플릿 |
| `notification_endpoint` | 사용자의 Telegram Chat ID·Discord Webhook |
| `notification_subscription` | Endpoint와 이벤트 대상의 구독 연결 |
| `notification` | RabbitMQ에서 수신한 이벤트 원본 |
| `notification_delivery` | 구독별 실제 발송 및 재시도 결과 |

## 4. 최종 테이블 명세

### 4.1 `channel_type`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `code` | VARCHAR(30) | X | `TELEGRAM`, `DISCORD`, UNIQUE |
| `display_name` | VARCHAR(100) | X | 화면 표시명 |
| `is_deleted` | BOOLEAN | X | 기준 채널 소프트 삭제 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

### 4.2 `subscription_target_type`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `target_type` | VARCHAR(30) | X | `CULTIVATION`, `INQUIRY`, `USER`, UNIQUE |
| `display_name` | VARCHAR(100) | X | 화면 표시명 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

### 4.3 `notification_event_type`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `code` | VARCHAR(50) | X | 이벤트 코드, UNIQUE |
| `display_name` | VARCHAR(150) | X | 이벤트 표시명 |
| `description` | VARCHAR(500) | O | 설명 |
| `target_type` | BIGINT | X | `subscription_target_type.id` FK |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

`target_type`은 문자열이 아니라 FK ID를 저장한다. 컬럼명은 초기 Migration과의 호환을
위해 유지하며 의미는 `target_type_id`와 같다.

### 4.4 `notification_subscription_type`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `notification_event_type_id` | BIGINT | X | 이벤트 유형 FK |
| `subscription_target_type_id` | BIGINT | X | 대상 유형 FK |
| `notification_subscription_name` | VARCHAR(20) | X | 구독 표시명 |
| `description` | VARCHAR(500) | O | 구독 설명 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

`(notification_event_type_id, subscription_target_type_id)`는 UNIQUE다.

### 4.5 `subscription_channel`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `notification_subscription_type_id` | BIGINT | X | 구독 종류 FK |
| `channel_type_id` | BIGINT | X | 채널 FK |
| `created_at` | TIMESTAMP | X | 생성 시각 |

`(notification_subscription_type_id, channel_type_id)`는 UNIQUE다. 구독 생성 Service는
이 테이블을 확인해 해당 구독이 Endpoint 채널을 지원하는지 검증한다.

### 4.6 `notification_template`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `notification_event_type_id` | BIGINT | X | 이벤트 유형 FK |
| `channel_type_id` | BIGINT | X | 채널 FK |
| `body_template` | TEXT | X | `{{variable}}` 형식 메시지 |
| `version` | INTEGER | X | 템플릿 버전 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

`(notification_event_type_id, channel_type_id, version)`는 UNIQUE다. 발송 생성 시 같은
이벤트·채널의 가장 높은 버전을 선택한다.

### 4.7 `notification_endpoint`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `user_id` | BIGINT | X | Auth 사용자 ID, 소프트 참조 |
| `channel_type_id` | BIGINT | X | 채널 FK |
| `display_name` | VARCHAR(100) | X | 사용자 지정 이름 |
| `destination` | VARCHAR(500) | X | Telegram Chat ID 또는 Discord Webhook |
| `enabled` | BOOLEAN | X | 일시 사용 여부 |
| `is_deleted` | BOOLEAN | X | 소프트 삭제 여부 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

`enabled=false`는 다시 켤 수 있는 일시정지다. `is_deleted=true`는 일반 목록에서 제외하는
삭제다. 발송 대상은 두 값이 모두 활성 상태여야 한다.

### 4.8 `notification_subscription`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `notification_subscription_type_id` | BIGINT | X | 구독 종류 FK |
| `notification_endpoint_id` | BIGINT | X | 사용자 Endpoint FK |
| `target_id` | BIGINT | X | cultivationId·inquiryId·userId |
| `enabled` | BOOLEAN | X | 일시 구독 여부 |
| `is_deleted` | BOOLEAN | X | 소프트 삭제 여부 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

`target_id`는 다른 MSA DB의 PK이므로 물리 FK를 만들지 않는다. 대상 의미는
`notification_subscription_type.subscription_target_type_id`로 판단한다.

`notification_event_type`에도 대상 유형이 있고 구독 유형에도 대상 유형이 있는 이유는
역할이 다르기 때문이다. 전자는 이벤트 계약상 원래 대상이고, 후자는 사용자가 선택하는
구독 카탈로그의 대상이다. 현재 Seed에서는 둘이 같은 값을 사용하며, 관리자 기능으로
구독 유형을 추가하게 되면 두 대상 유형의 일치 여부를 검증해야 한다.

`CULTIVATION`, `INQUIRY`의 `target_id` 존재 여부와 사용자의 소유권은 Notification DB만으로
판단하지 않는다. 구독 생성 시 해당 서비스의 권한 확인 API 계약이 확정되면 연동한다.

비삭제 구독에는 다음 partial UNIQUE index를 적용한다.

```sql
UNIQUE (
  notification_subscription_type_id,
  notification_endpoint_id,
  target_id
) WHERE is_deleted = FALSE
```

따라서 일시정지된 구독을 다시 신청하면 새 행을 만들지 않고 기존 행의 `enabled=true`로
복구한다. 소프트 삭제된 구독은 과거 이력을 유지하면서 같은 조건으로 새 구독을 만들 수
있다.

### 4.9 `notification`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `source_event_id` | UUID | X | Producer eventId, UNIQUE |
| `event_payload` | JSONB | X | 이벤트 상세 데이터 |
| `created_at` | TIMESTAMP | X | 생성 시각 |

`source_event_id` UNIQUE가 RabbitMQ 재전송에 따른 중복 발송을 방지한다. 동시에 같은
이벤트가 들어와 UNIQUE 충돌이 나더라도, 이미 저장된 eventId가 확인되면 Consumer는 이를
정상 중복 이벤트로 처리하고 DLQ로 보내지 않는다.
사용자에게 실제로 발송된 문구는 `notification_delivery.rendered_message`에만 보관한다.

### 4.10 `notification_delivery`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---:|---|
| `id` | BIGINT IDENTITY | X | PK |
| `notification_id` | BIGINT | X | 알림 원본 FK |
| `notification_subscription_id` | BIGINT | X | 실제 수신 구독 FK |
| `notification_template_id` | BIGINT | X | 채널별 사용 템플릿 FK |
| `status` | VARCHAR(20) | X | `PENDING`, `SENT`, `FAILED` |
| `provider_message_id` | VARCHAR(200) | O | Telegram·Discord 응답 ID |
| `rendered_message` | TEXT | X | 변수 치환이 끝난 최종 메시지 |
| `attempt_count` | SMALLINT | X | 발송 시도 수, 0~3 |
| `error` | TEXT | O | 최종 실패 원인 |
| `sent_at` | TIMESTAMP | O | 발송 성공 시각 |
| `created_at` | TIMESTAMP | X | 생성 시각 |
| `updated_at` | TIMESTAMP | X | 수정 시각 |

`(notification_id, notification_subscription_id)`는 UNIQUE다. 템플릿 FK는
`notification`이 아니라 `notification_delivery`에 둔다. 한 원본 이벤트에서도
Telegram·Discord가 서로 다른 템플릿을 선택하기 때문이다.

`status`와 `attempt_count`는 애플리케이션 규칙일 뿐 아니라 DB CHECK 제약으로도 보호한다.
따라서 잘못된 상태 문자열이나 0~3 범위를 벗어난 발송 시도 횟수는 PostgreSQL이 저장을
거부한다.

## 5. 상태·발송 정책

```text
PENDING
  ├── 외부 발송 성공 ──> SENT
  └── 실패
       ├── attempt_count < 3 ──> 다시 시도
       └── 3회 소진 ──> FAILED + DLQ 메시지
```

- 성공할 때 `provider_message_id`, `sent_at`을 저장한다.
- 최종 실패할 때 `error`를 저장하고 Delivery 실패 메시지를 DLQ로 보낸다.
- RabbitMQ 메시지 자체가 파싱되지 않으면 원본 메시지가 Queue의 DLX 정책에 따라 DLQ로
  이동한다.
- 제어 성공·일반 ON/OFF 성공은 알림으로 만들지 않는다.
- `ACTUATOR_CONTROL_FAILED`만 제어 관련 알림으로 발송한다.

## 6. 기준 Seed

### 채널

- `TELEGRAM`
- `DISCORD`

### 대상

- `CULTIVATION`
- `INQUIRY`
- `USER`

### 이벤트

- `ENVIRONMENT_THRESHOLD_BREACHED`
- `ENVIRONMENT_RECOVERED`
- `SENSOR_OFFLINE`
- `SENSOR_ERROR`
- `ACTUATOR_CONTROL_FAILED`
- `HARVEST_COMPLETED`
- `CULTIVATION_FINISHED`
- `DAILY_FEEDBACK_COMPLETED`
- `INQUIRY_ANSWERED`
- `LOGIN_SUCCEEDED`

Seed는 `ON CONFLICT`를 사용해 재실행해도 중복되지 않는다. 현재 이벤트 코드와 payload
변수는 서비스 간 RabbitMQ 계약 확정 전까지의 기준안이다. 계약이 바뀌면 이미 적용된
Migration을 수정하지 않고 새 Migration을 추가한다.

## 7. Index

| Index | 목적 |
|---|---|
| `uq_notification_source_event` | 동일 eventId 중복 처리 방지 |
| `uq_non_deleted_notification_subscription` | 비삭제 구독 중복 방지 |
| `idx_notification_created` | 최신 알림 조회 |
| `idx_notification_delivery_status` | 상태·재시도 대상 조회 |
| `idx_endpoint_user_enabled` | 사용자 활성 Endpoint 조회 |
| `idx_endpoint_user_active` | 삭제 여부까지 포함한 Endpoint 조회 |
| `idx_delivery_template` | 템플릿별 발송 이력 조회 |

## 8. 보안과 MSA 경계

- `user_id`와 `target_id`에는 다른 서비스 테이블 FK를 만들지 않는다.
- 사용자 API는 Gateway가 검증한 `X-User-Id`로 Endpoint·Subscription 소유권을 확인한다.
- 클라이언트가 임의로 보낸 `X-User-Id`는 Gateway가 제거하거나 검증한 JWT 값으로
  덮어써야 한다.
- Telegram Chat ID, Discord Webhook, Bot Token, payload 원문은 로그에 남기지 않는다.
- Cultivation·Inquiry 대상 접근 권한 확인은 해당 서비스의 내부 API 계약이 확정된 뒤
  연동한다. Endpoint 소유권 검사는 현재 코드에 구현되어 있다.

## 9. Migration 이력

| 버전 | 내용 |
|---|---|
| `V1` | 10개 테이블·기본 FK·제약·Index 생성 |
| `V2` | 채널·대상·이벤트·구독 종류·템플릿 Seed |
| `V3` | Template FK를 Notification에서 Delivery로 이동 |
| `V4` | Endpoint `is_deleted`와 활성 조회 Index 추가 |
| `V5` | 활성 구독 partial UNIQUE 정책 조정 |
| `V6` | 일시정지 구독 재사용을 위해 비삭제 구독 UNIQUE로 최종 보정 |
| `V7` | `notification.message` 제거. 채널별 최종 발송 문구는 Delivery에만 보관 |

Flyway가 적용한 Migration 파일은 수정하지 않는다. 변경이 필요하면 `V7` 이후 파일로
추가한다.
