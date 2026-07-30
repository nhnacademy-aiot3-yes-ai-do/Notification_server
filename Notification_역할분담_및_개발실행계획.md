# Notification Service 역할 분담 및 개발 실행 계획

- 작성 기준일: 2026-07-23
- 담당자: 호준, 서영
- 기준: 최신 Notification ERD 및 2026-07-23 인수인계 내용
- 목적: 역할 충돌 없이 Notification Service를 바로 구현할 수 있도록 책임, 이벤트 계약, 구독 정책, 템플릿, 재시도, Seed 데이터와 완료 기준을 확정한다.

## 최신 설계 반영 사항 (2026-07-27)

### Delivery 상태

`notification_delivery.status`는 BOOLEAN이 아니라 `VARCHAR(20)`으로 관리한다. 허용 상태는 다음 세 가지다.

- `PENDING`: 발송 대기
- `SENT`: 발송 성공
- `FAILED`: 최대 재시도 후 최종 실패

DB CHECK 제약으로 허용되지 않은 상태값을 차단한다.

코드에서도 `PENDING` 상태에서만 발송 결과를 기록한다. 완료된 Delivery를 다시 변경하거나
3회 초과로 시도하면 `InvalidDeliveryStateException`으로 차단한다. API 공통 오류 응답은
Controller 구현과 팀 계약 확정 후 연결한다.

예외 처리 시 이벤트·알림·Delivery 식별자, 채널, 시도 횟수, 재시도 여부와 원인을 운영 로그에
남긴다. JWT·Webhook URL·Chat ID·토큰과 민감한 payload 원문은 로그에 기록하지 않는다.

Migration은 현재 V1부터 V6까지 적용한다. V6는 일시정지된 비삭제 구독을 새 행으로 중복
생성하지 않고 기존 구독을 다시 활성화할 수 있도록 UNIQUE 정책을 정리한다.

### 채널별 템플릿 관계

하나의 이벤트 원본(Notification)이 Telegram·Discord 등 여러 채널로 발송될 수 있고, 채널마다 템플릿이 다를 수 있으므로 템플릿 참조는 Delivery 단위로 관리한다.

```text
notification
  └─ source_event_id, event_payload (이벤트 원본)

notification_delivery
  ├─ notification_id
  ├─ notification_template_id (채널별 템플릿)
  └─ rendered_message (채널별 최종 문구)
```

기존 V1 Migration은 수정하지 않고 `V3__move_template_reference_to_delivery.sql`에서 기존 참조를 Delivery로 이관한 뒤 `notification.notification_template_id`를 제거한다. 이후 Consumer는 Delivery를 생성할 때 구독 Endpoint의 채널과 이벤트 유형에 맞는 Template을 선택한다.

---

## 1. 서비스 목표

Notification Service는 Rule, Cultivation, AI, Auth, Inquiry 서비스가 발행한 이벤트를 수신하고, 사용자가 설정한 구독과 수신 경로에 따라 Telegram 또는 Discord로 알림을 발송한다.

Notification Service는 센서값이나 재배 상태가 정상인지 직접 판단하지 않는다.

```text
Rule·Cultivation·AI·Auth·Inquiry
        │
        │ RabbitMQ Domain Event
        ▼
Notification Service
        │
        ├─ 이벤트 중복 여부 확인
        ├─ 대상에 연결된 활성 구독 조회
        ├─ 채널별 템플릿 렌더링
        ├─ notification / notification_delivery 저장
        ├─ Telegram·Discord 발송
        └─ 성공·실패·재시도 이력 저장
```

### 서비스 책임

- RabbitMQ 이벤트 소비
- 동일 이벤트 중복 처리 방지
- 사용자 Endpoint 관리
- 대상별·이벤트별 구독 관리
- 채널별 템플릿 관리 및 렌더링
- Telegram·Discord 발송
- 발송 성공·실패 이력 관리
- 실패 발송 재시도

### 서비스가 하지 않는 일

- 센서값이 정상인지 직접 판단
- 자동 제어 여부 판단
- 버섯 생육 점수 계산
- AI 피드백 생성
- 이메일 인증번호 생성·검증·발송

이메일 인증번호는 Auth Service의 책임으로 둔다.

---

## 2. 핵심 용어

| 용어 | 의미 | 예시 |
|---|---|---|
| 이벤트 유형 | 어떤 일이 발생했는지 나타내는 고정 코드 | `SENSOR_OFFLINE` |
| 대상 유형 | 이벤트가 무엇에 관한 것인지 구분 | `CULTIVATION`, `INQUIRY`, `USER` |
| 구독 종류 | 사용자가 선택할 수 있는 알림 항목 | 재배 센서 오류 알림 |
| Endpoint | 실제 알림 수신 경로 | Telegram Chat ID, Discord Webhook URL |
| Subscription | 특정 대상의 특정 알림을 특정 Endpoint로 받겠다는 설정 | 재배 12번의 센서 오류를 내 Discord로 수신 |
| Template | 이벤트를 사용자 메시지로 변환하는 채널별 양식 | Telegram용 환경 이상 문구 |
| Notification | 시스템에서 발생한 알림 원본 | 재배 12번 온도 이상 |
| Delivery | Notification을 특정 구독으로 발송한 결과 | Discord 발송 성공 |

### 한 건의 알림이 처리되는 예

```text
재배 12번에서 온도 이상 발생
  → ENVIRONMENT_THRESHOLD_BREACHED 이벤트 수신
  → 재배 12번의 활성 환경 이상 구독 조회
  → Discord 구독 1건, Telegram 구독 1건 발견
  → notification 1건 생성
  → notification_delivery 2건 생성
  → Discord와 Telegram으로 각각 발송
```

---

## 3. 역할 분담

## 3.1 호준 담당: 이벤트 처리·알림 생성·발송 파이프라인

호준은 다른 서비스가 보낸 이벤트를 실제 사용자 알림으로 만드는 실행 영역을 담당한다.

### 주 담당 테이블

- `notification`
- `notification_delivery`
- `notification_template`
- `notification_event_type`
- Notification 기준 데이터 Seed

### 구현 기능

#### 1) RabbitMQ 이벤트 계약 관리

- 공통 이벤트 JSON DTO 작성
- 이벤트 코드 Enum 또는 상수 작성
- 대상 유형 Enum 작성
- 이벤트별 payload DTO 작성
- Producer 팀에 계약 문서 전달
- 잘못된 이벤트 타입·필수 필드 누락 검증

#### 2) RabbitMQ Consumer 구현

- Rule 이벤트 소비
- Cultivation 이벤트 소비
- AI 이벤트 소비
- Auth 이벤트 소비
- Inquiry 이벤트 소비
- 정상 처리 후 ACK
- 처리 불가능한 메시지 오류 기록

#### 3) 이벤트 중복 방지

- `eventId`를 `notification.source_event_id`에 저장
- 동일 `source_event_id`가 이미 존재하면 재생성하지 않음
- RabbitMQ가 같은 메시지를 재전달해도 사용자에게 한 번만 알림

#### 4) 활성 구독 조회 및 알림 생성

- `eventType + targetType`에 맞는 구독 종류 조회
- 동일한 `targetId`를 가진 활성 구독 조회
- Endpoint가 활성 상태인지 확인
- `notification` 원본 생성
- 수신 구독별 `notification_delivery` 생성

#### 5) 템플릿 렌더링

- Endpoint 채널에 맞는 템플릿 조회
- payload 값을 템플릿 변수에 대입
- 필수 변수가 누락되면 발송하지 않고 오류 기록
- 실제 발송 문구를 `notification_delivery.rendered_message`에 저장

#### 6) 외부 채널 발송

- Telegram Bot API 연동
- Discord Webhook 연동
- 성공 시 `SENT`, `sent_at`, `provider_message_id` 저장
- 실패 시 `FAILED`, `attempt_count`, `error` 저장

#### 7) 실패 재시도

- 최초 발송 포함 최대 3회 시도
- 1차 실패 후 1분 뒤 2차 시도
- 2차 실패 후 5분 뒤 3차 시도
- 최종 실패 상태와 원인 저장

#### 8) 통합 테스트

- 가짜 Rule 이벤트부터 실제 Telegram/Discord 수신까지 검증
- 같은 `eventId` 중복 수신 테스트
- 구독이 없을 때 미발송 테스트
- 비활성 구독·비활성 Endpoint 미발송 테스트
- 외부 API 실패와 재시도 테스트

### 호준 완료 산출물

- 이벤트 계약 문서
- RabbitMQ Consumer
- 알림 생성 서비스
- 템플릿 렌더러
- Telegram·Discord 발송 Adapter
- 재시도 로직
- 이벤트·발송 통합 테스트
- 기준 데이터 Seed

---

## 3.2 서영 담당: Endpoint·구독 설정 영역

서영은 사용자가 Telegram·Discord 수신 경로를 등록하고, 특정 대상에 대한 알림을 선택할 수 있는 설정 영역을 담당한다.

### 주 담당 테이블

- `channel_type`
- `notification_endpoint`
- `notification_subscription`
- `notification_subscription_type`
- `subscription_target_type`
- `subscription_channel`

### 구현 기능

#### 1) Endpoint 등록

- Telegram Chat ID 등록
- Discord Webhook URL 등록
- 사용자의 Endpoint 목록 조회
- Endpoint 표시 이름 관리
- Endpoint 활성화·비활성화
- Endpoint 수정·삭제 또는 소프트 삭제
- 사용자 본인의 Endpoint만 접근하도록 권한 검증

#### 2) Endpoint 유효성 확인

- Telegram Chat ID 기본 형식 검증
- Discord Webhook URL 기본 형식 검증
- 필요하면 테스트 메시지 전송 기능 제공
- 잘못되거나 사용할 수 없는 Endpoint 오류 반환

#### 3) 구독 종류 조회

- 대상 유형별 구독 가능 알림 목록 조회
- 구독 종류별 사용 가능한 채널 조회
- 재배·문의·계정 화면에서 필요한 목록 제공

#### 4) 구독 생성

- 대상 유형 선택
- 대상 ID 선택
- 구독 종류 선택
- Endpoint 선택
- `notification_subscription` 생성

#### 5) 구독 중복 방지

아래 조합의 삭제되지 않은 구독이 이미 있으면 새로 만들지 않는다.

```text
notification_subscription_type_id
+ target_id
+ endpoint_id
```

기존 구독이 일시정지 상태라면 새 행을 만들지 않고 다시 활성화한다. 소프트 삭제된 구독은
이력으로 남기고 새 구독을 생성할 수 있다.

#### 6) 구독 조회·ON/OFF·해제

- 사용자별 구독 목록
- 재배별 구독 목록
- 계정 로그인 알림 구독
- `enabled`를 이용한 ON/OFF
- 구독 소프트 삭제
- 본인 또는 해당 대상의 권한 있는 사용자만 수정하도록 검증

#### 7) 프론트엔드 연동

- Endpoint 설정 화면 API
- 재배별 알림 설정 화면 API
- 계정 로그인 알림 설정 API
- Endpoint 미등록 상태 안내
- 구독 가능 이벤트와 채널 목록 제공

### 서영 완료 산출물

- Endpoint CRUD API
- Endpoint 권한·형식 검증
- 구독 종류 조회 API
- Subscription CRUD/ON/OFF API
- 중복 구독 방지 로직
- 프론트 연동 DTO와 API 명세
- Endpoint·Subscription 단위/통합 테스트

---

## 3.3 두 담당자의 코드 경계

```text
서영 영역
사용자 설정
  → Endpoint 생성
  → Subscription 생성·활성화

호준 영역
런타임 실행
  → 이벤트 수신
  → 서영 영역에서 만든 활성 Subscription 조회
  → Notification·Delivery 생성
  → 외부 채널 발송
```

### 충돌 방지 원칙

- Endpoint·Subscription 생성 규칙은 서영이 주도한다.
- Notification·Delivery 생성 규칙은 호준이 주도한다.
- 상대 담당 테이블은 조회할 수 있지만 생성·수정 책임을 임의로 가져가지 않는다.
- 공통 Entity 관계나 Migration 수정은 PR 전에 두 사람이 함께 검토한다.
- Seed 파일은 한 사람이 통합 관리하여 Merge 충돌을 방지한다.

---

## 4. 담당 테이블 정리

| 테이블 | 목적 | 주 담당 | 주요 사용 주체 |
|---|---|---|---|
| `channel_type` | Telegram·Discord 채널 기준 정보 | 서영 | 서영, 호준 |
| `subscription_target_type` | 재배·문의·사용자 대상 유형 | 서영 | 서영, 호준 |
| `notification_event_type` | 시스템 알림 이벤트 기준 정보 | 호준 | 호준, 서영 |
| `notification_subscription_type` | 이벤트 유형과 대상 유형을 조합한 구독 종류 | 서영 | 서영, 호준 |
| `subscription_channel` | 구독 종류에서 허용하는 채널 | 서영 | 서영, 호준 |
| `notification_template` | 이벤트·채널별 메시지 양식 | 호준 | 호준 |
| `notification_endpoint` | 사용자의 Telegram·Discord 수신 주소 | 서영 | 서영, 호준 |
| `notification_subscription` | 대상·구독 종류·Endpoint 연결 | 서영 | 서영, 호준 |
| `notification` | 수신 이벤트로 생성된 알림 원본 | 호준 | 호준 |
| `notification_delivery` | 구독별 실제 발송 결과 | 호준 | 호준 |

현재 ERD는 조회 편의를 위해 일부 관계가 중복된 비정규화 구조를 사용한다. 중복된 값의 일관성은 생성·수정 시 서비스 코드에서 검증한다.

---

## 5. 확정 사항 1: 이벤트 코드

## 5.1 확정 이벤트 목록

| 이벤트 코드 | 발행 서비스 | 대상 유형 | 설명 | 알림 여부 |
|---|---|---|---|---|
| `ENVIRONMENT_THRESHOLD_BREACHED` | Rule | `CULTIVATION` | 환경값이 정상 범위를 벗어남 | 구독 시 발송 |
| `ENVIRONMENT_RECOVERED` | Rule | `CULTIVATION` | 환경값이 정상 범위로 복귀 | 구독 시 발송 |
| `SENSOR_OFFLINE` | Rule 또는 Cultivation | `CULTIVATION` | 센서 연결 끊김 | 구독 시 발송 |
| `SENSOR_ERROR` | Rule 또는 Cultivation | `CULTIVATION` | 센서 측정·처리 오류 | 구독 시 발송 |
| `ACTUATOR_CONTROL_FAILED` | Rule | `CULTIVATION` | 자동 또는 수동 제어 실패 | 구독 시 발송 |
| `HARVEST_COMPLETED` | Cultivation | `CULTIVATION` | 수확 기록 완료 | 구독 시 발송 |
| `CULTIVATION_FINISHED` | Cultivation | `CULTIVATION` | 재배 종료 | 구독 시 발송 |
| `DAILY_FEEDBACK_COMPLETED` | AI | `CULTIVATION` | AI 일일 피드백 생성 완료 | 구독 시 발송 |
| `INQUIRY_ANSWERED` | Inquiry | `INQUIRY` | 사용자 문의 답변 완료 | 구독 시 발송 |
| `LOGIN_SUCCEEDED` | Auth | `USER` | 사용자 로그인 성공 | 구독 시 발송 |

이메일 인증번호 이벤트는 Notification에서 제외한다. Auth Service가 직접 이메일 인증 흐름을 담당한다.

## 5.2 환경 이상 발행 정책

환경값이 범위를 벗어난 상태에서 센서 데이터가 계속 들어와도 매번 알림을 발행하지 않는다.

```text
정상 → 이상
  ENVIRONMENT_THRESHOLD_BREACHED 1회

이상 상태 유지
  추가 발행 없음

이상 → 정상
  ENVIRONMENT_RECOVERED 1회
```

Rule Service가 상태 전이를 판단하고 이벤트를 발행한다. Notification Service는 이 판단을 반복하지 않는다.

## 5.3 제어 이벤트 정책

- 제어 성공 및 단순 장치 ON/OFF는 우선 이력·대시보드에서 확인한다.
- 사용자 알림은 `ACTUATOR_CONTROL_FAILED`를 우선 구현한다.
- 자동 제어 성공 알림이 공식 필수 요구사항으로 유지될 경우 `ACTUATOR_CONTROL_COMPLETED`를 추가한다.
- 제어 장치는 센서가 아니므로 코드와 문서에서 `SENSOR_CONTROL`이라는 이름을 사용하지 않는다.

---

## 6. 확정 사항 2: RabbitMQ 이벤트 계약

## 6.1 공통 JSON 형식

```json
{
  "eventId": "7f7a9186-6a68-45a2-b203-8c67e13d6c32",
  "eventType": "ENVIRONMENT_THRESHOLD_BREACHED",
  "producer": "rule-service",
  "targetType": "CULTIVATION",
  "targetId": 12,
  "occurredAt": "2026-07-23T16:00:00+09:00",
  "payload": {
    "cultivationName": "느타리 1호",
    "sensorType": "TEMPERATURE",
    "currentValue": 28.4,
    "unit": "°C",
    "thresholdMax": 25.0,
    "violationDirection": "ABOVE_MAX"
  }
}
```

## 6.2 공통 필드 규칙

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `eventId` | UUID 문자열 | O | 발행 서비스가 생성하며 전체 이벤트에서 유일 |
| `eventType` | 문자열 | O | 확정 이벤트 코드만 허용 |
| `producer` | 문자열 | O | 이벤트 발행 서비스 이름 |
| `targetType` | 문자열 | O | `CULTIVATION`, `INQUIRY`, `USER` |
| `targetId` | Long | O | 대상 서비스가 소유한 ID |
| `occurredAt` | ISO-8601 문자열 | O | 시간대 또는 UTC 오프셋 포함 |
| `payload` | JSON Object | O | 이벤트별 템플릿 렌더링 데이터 |

## 6.3 이벤트별 payload

### 환경 이상·복구

```json
{
  "cultivationName": "느타리 1호",
  "sensorType": "TEMPERATURE",
  "currentValue": 28.4,
  "unit": "°C",
  "thresholdMin": 18.0,
  "thresholdMax": 25.0,
  "violationDirection": "ABOVE_MAX"
}
```

`violationDirection` 값:

- `ABOVE_MAX`
- `BELOW_MIN`
- 정상 복귀 이벤트에서는 `RECOVERED`

### 센서 오류·오프라인

```json
{
  "cultivationName": "느타리 1호",
  "sensorId": 31,
  "sensorType": "HUMIDITY",
  "deviceName": "생육실 습도 센서",
  "errorCode": "NO_RESPONSE",
  "errorMessage": "센서 응답이 없습니다."
}
```

### 제어 실패

```json
{
  "cultivationName": "느타리 1호",
  "deviceName": "환풍기 1",
  "controlType": "FAN_ON",
  "reason": "DEVICE_TIMEOUT"
}
```

### 수확 완료

```json
{
  "cultivationName": "느타리 1호",
  "harvestAmount": 3200,
  "unit": "g"
}
```

수확량 단위는 `g`으로 통일한다.

### 재배 종료

```json
{
  "cultivationName": "느타리 1호",
  "finishedAt": "2026-08-05T15:00:00+09:00"
}
```

### AI 일일 피드백 완료

```json
{
  "cultivationName": "느타리 1호",
  "dailyFeedbackId": 75,
  "feedbackSummary": "오늘 습도가 다소 낮았습니다."
}
```

### 문의 답변 완료

```json
{
  "inquiryTitle": "센서 연결 문의",
  "answeredAt": "2026-07-23T16:30:00+09:00"
}
```

### 로그인 성공

```json
{
  "provider": "GOOGLE",
  "loginAt": "2026-07-23T16:30:00+09:00"
}
```

## 6.4 RabbitMQ 권장 설정

팀의 공통 RabbitMQ 이름 규칙이 아직 없다면 아래를 사용한다.

```text
Exchange: domain.events
Exchange Type: topic
Notification Queue: notification.events.queue
```

Routing Key 예시:

```text
rule.environment.threshold-breached
rule.environment.recovered
rule.sensor.offline
rule.sensor.error
rule.actuator.control-failed
cultivation.harvest.completed
cultivation.finished
ai.daily-feedback.completed
inquiry.answered
auth.login.succeeded
```

팀 전체에 이미 Exchange·Queue·Routing Key 규칙이 있으면 이름만 기존 규칙에 맞추고 JSON 계약은 유지한다.

---

## 7. 확정 사항 3: 대상 유형과 구독 정책

## 7.1 대상 유형

`subscription_target_type` Seed:

```text
CULTIVATION
INQUIRY
USER
```

| 대상 유형 | `targetId` 의미 |
|---|---|
| `CULTIVATION` | `cultivation.id` |
| `INQUIRY` | `inquiry.id` |
| `USER` | `users.id` |

로그인 성공 알림을 구현하기 위해 `USER` 대상 유형을 추가한다.

## 7.2 구독 생성 방식

사용자가 알림 설정에서 직접 구독한다.

```text
1. Telegram 또는 Discord Endpoint 등록
2. 대상 선택
3. 알림 종류 선택
4. 수신 Endpoint 선택
5. notification_subscription 생성
```

정책:

- 기본 구독 자동 생성 없음
- Endpoint가 없으면 구독 생성 불가
- 활성 구독이 없으면 이벤트가 발생해도 외부 발송하지 않음
- 같은 구독 종류·대상 ID·Endpoint의 중복 비삭제 구독 금지
- 사용자는 구독을 ON/OFF 할 수 있음
- 비활성 Endpoint에 연결된 구독은 발송 대상에서 제외
- 재배 알림은 재배별 알림 설정에서 관리
- 로그인 알림은 계정 알림 설정에서 관리
- 문의 답변 알림은 문의 대상 구독에서 관리

## 7.3 권한 검증

DB의 다형적 `targetId`는 모든 대상 테이블에 물리적 FK를 걸기 어렵기 때문에 서비스 코드에서 검증한다.

- `CULTIVATION`: 사용자가 해당 재배의 소유자 또는 알림 설정 권한이 있는 멤버인지 확인
- `INQUIRY`: 사용자가 해당 문의의 작성자인지 확인
- `USER`: `targetId`가 로그인한 사용자 ID와 같은지 확인
- `notification_endpoint`: Endpoint 소유자가 로그인한 사용자인지 확인

---

## 8. 확정 사항 4: Telegram·Discord 템플릿

## 8.1 템플릿 정책

- 이벤트 의미는 같아도 Telegram과 Discord 템플릿 행은 분리한다.
- 초기 구현은 두 채널 모두 일반 텍스트로 시작할 수 있다.
- 이후 Discord Embed 형식으로 확장 가능하다.
- 이벤트 발송 당시 최종 문구를 `notification_delivery.rendered_message`에 보관한다.
- 템플릿이 수정되어도 과거 발송 문구는 변경되지 않는다.
- 누락된 필수 변수가 있으면 잘못된 문구를 보내지 않고 Delivery 실패로 기록한다.

## 8.2 공통 변수 이름

| 변수 | 의미 |
|---|---|
| `cultivationName` | 재배 이름 |
| `sensorType` | 센서 종류 |
| `currentValue` | 현재 측정값 |
| `unit` | 단위 |
| `thresholdMin` | 최소 임계값 |
| `thresholdMax` | 최대 임계값 |
| `violationDirection` | 상한 초과·하한 미달·복구 구분 |
| `deviceName` | 센서 또는 제어 장치 표시 이름 |
| `controlType` | 제어 종류 |
| `harvestAmount` | 수확량(g) |
| `feedbackSummary` | AI 피드백 요약 |
| `inquiryTitle` | 문의 제목 |
| `occurredAt` | 이벤트 발생 시각 |

Producer의 payload 필드명과 템플릿 변수명은 반드시 일치해야 한다.

## 8.3 초기 템플릿

### 환경 이상

```text
[환경 이상]
[{{cultivationName}}] 환경 이상이 감지되었습니다.
항목: {{sensorType}}
현재값: {{currentValue}}{{unit}}
정상 범위: {{thresholdMin}} ~ {{thresholdMax}}{{unit}}
발생 시각: {{occurredAt}}
```

### 환경 정상 복귀

```text
[환경 복구]
[{{cultivationName}}] {{sensorType}} 값이 정상 범위로 돌아왔습니다.
현재값: {{currentValue}}{{unit}}
복구 시각: {{occurredAt}}
```

### 센서 오프라인

```text
[센서 연결 오류]
[{{cultivationName}}] {{deviceName}} 센서가 오프라인 상태입니다.
발생 시각: {{occurredAt}}
```

### 센서 오류

```text
[센서 오류]
[{{cultivationName}}] {{deviceName}} 센서 오류가 발생했습니다.
오류: {{errorMessage}}
발생 시각: {{occurredAt}}
```

### 제어 실패

```text
[제어 실패]
[{{cultivationName}}] {{deviceName}} 제어에 실패했습니다.
제어 종류: {{controlType}}
발생 시각: {{occurredAt}}
```

### 수확 완료

```text
[수확 완료]
[{{cultivationName}}] 수확이 완료되었습니다.
수확량: {{harvestAmount}}g
```

### 재배 종료

```text
[재배 종료]
[{{cultivationName}}] 재배가 종료되었습니다.
종료 시각: {{occurredAt}}
```

### AI 일일 피드백

```text
[AI 일일 피드백]
[{{cultivationName}}] 오늘의 AI 피드백이 생성되었습니다.
{{feedbackSummary}}
```

### 문의 답변

```text
[문의 답변]
문의 '{{inquiryTitle}}'에 답변이 등록되었습니다.
```

### 로그인 성공

```text
[로그인 알림]
계정 로그인이 완료되었습니다.
로그인 방식: {{provider}}
로그인 시각: {{occurredAt}}
본인이 아니라면 계정 정보를 확인해주세요.
```

---

## 9. 확정 사항 5: 발송 실패와 중복 방지

## 9.1 재시도 정책

```text
1차: 이벤트 처리 직후 즉시 발송
2차: 1차 실패 후 1분 뒤
3차: 2차 실패 후 5분 뒤
최종 실패: FAILED 상태와 원인 저장
```

- 최초 시도를 포함하여 총 3회
- `attempt_count`는 실제 외부 API 호출마다 1 증가
- 성공하면 추가 재시도 취소
- 최종 실패 후 자동으로 무한 재시도하지 않음

## 9.2 Delivery 상태

최소 상태:

```text
PENDING
SENT
FAILED
```

필요하면 발송 중 상태인 `SENDING`을 추가할 수 있다.

## 9.3 재시도하지 않을 오류

- 잘못된 Discord Webhook URL
- 권한이 제거된 Webhook
- 잘못된 Telegram Chat ID
- Telegram Bot 차단
- 템플릿 필수 변수 누락

이처럼 재시도해도 성공할 가능성이 없는 오류는 즉시 최종 실패 처리할 수 있다.

## 9.4 재시도 가능한 오류

- 일시적인 네트워크 연결 실패
- 외부 API 5xx 오류
- 외부 API 시간 초과
- 일시적인 Rate Limit

Rate Limit 응답에 재시도 가능 시간이 포함되면 해당 값을 우선 사용한다.

## 9.5 이벤트 중복과 발송 재시도의 차이

| 구분 | 기준 필드 | 목적 |
|---|---|---|
| 이벤트 중복 방지 | `notification.source_event_id` | 같은 Domain Event로 Notification을 여러 번 만들지 않음 |
| 외부 발송 재시도 | `notification_delivery.attempt_count` | 같은 Delivery의 외부 API 호출을 최대 3회 시도 |

## 9.6 MVP 재시도 구현

초기 구현에서는 Spring Retry 또는 별도 비동기 실행기를 사용할 수 있다.

```text
maxAttempts = 3
첫 backoff = 60초
두 번째 backoff = 300초
```

서비스 재시작 후에도 재시도가 반드시 복구되어야 한다면 이후 RabbitMQ Retry Queue 또는 DB 기반 Retry Scheduler로 확장한다.

---

## 10. 확정 사항 6: 초기 Seed 데이터

## 10.1 Seed 담당

### 호준

Notification 기준 데이터 Seed 파일을 통합 관리한다.

- `channel_type`
- `subscription_target_type`
- `notification_event_type`
- `notification_subscription_type`
- `subscription_channel`
- `notification_template`

### 서영

실제 Endpoint와 Subscription은 운영 Seed로 넣지 않는다.

- 개발·테스트 코드에서 Endpoint Fixture 작성
- 개발·테스트 코드에서 Subscription Fixture 작성
- Endpoint/Subscription API 테스트 데이터 관리

## 10.2 `channel_type`

```text
TELEGRAM
DISCORD
```

## 10.3 `subscription_target_type`

```text
CULTIVATION
INQUIRY
USER
```

## 10.4 `notification_event_type`

```text
ENVIRONMENT_THRESHOLD_BREACHED
ENVIRONMENT_RECOVERED
SENSOR_OFFLINE
SENSOR_ERROR
ACTUATOR_CONTROL_FAILED
HARVEST_COMPLETED
CULTIVATION_FINISHED
DAILY_FEEDBACK_COMPLETED
INQUIRY_ANSWERED
LOGIN_SUCCEEDED
```

## 10.5 `notification_subscription_type`

```text
재배 환경 이상 알림
재배 환경 복구 알림
재배 센서 오프라인 알림
재배 센서 오류 알림
재배 제어 실패 알림
재배 수확 완료 알림
재배 종료 알림
재배 AI 일일 피드백 알림
문의 답변 완료 알림
로그인 성공 알림
```

## 10.6 `subscription_channel`

초기에는 모든 구독 종류에서 Telegram과 Discord를 허용한다.

```text
각 notification_subscription_type
  ├─ TELEGRAM
  └─ DISCORD
```

향후 특정 알림을 특정 채널에만 제공해야 할 때 이 매핑을 변경한다.

## 10.7 `notification_template`

각 이벤트마다 아래 2개를 생성한다.

```text
이벤트 × TELEGRAM 템플릿
이벤트 × DISCORD 템플릿
```

이벤트 10개를 기준으로 초기 템플릿은 총 20개다.

## 10.8 Seed 실행 원칙

- 여러 번 실행해도 중복 데이터가 생기지 않아야 한다.
- `code` 또는 복합 Unique Key 기준으로 Upsert하거나 존재 여부를 확인한다.
- 운영 사용자 Endpoint, Chat ID, Webhook URL은 Seed에 포함하지 않는다.
- 비밀값과 토큰을 Git에 저장하지 않는다.
- 이벤트 코드 변경 시 Producer·Consumer·Seed·Template을 함께 수정한다.

---

## 11. API 구현 범위

## 11.1 서영 담당 API 권장안

### Endpoint

```text
POST   /api/v1/notification-endpoints
GET    /api/v1/notification-endpoints
PATCH  /api/v1/notification-endpoints/{endpointId}
DELETE /api/v1/notification-endpoints/{endpointId}
POST   /api/v1/notification-endpoints/{endpointId}/test
```

### 구독 가능 목록

```text
GET /api/v1/notification-subscription-types?targetType=CULTIVATION
```

### Subscription

```text
POST   /api/v1/notification-subscriptions
GET    /api/v1/notification-subscriptions
GET    /api/v1/notification-subscriptions?targetType=CULTIVATION&targetId=12
PATCH  /api/v1/notification-subscriptions/{subscriptionId}
DELETE /api/v1/notification-subscriptions/{subscriptionId}
```

구독 생성 요청 예:

```json
{
  "subscriptionTypeId": 3,
  "targetId": 12,
  "endpointId": 5
}
```

구독 ON/OFF 요청 예:

```json
{
  "enabled": false
}
```

## 11.2 호준 담당 내부 기능

알림 생성은 REST API가 아니라 RabbitMQ 이벤트로 수행한다.

```text
RabbitMQ Consumer
  → EventValidationService
  → DuplicateEventGuard
  → SubscriptionResolver
  → NotificationCreator
  → TemplateRenderer
  → ChannelSender
  → DeliveryResultRecorder
```

테스트·운영 확인을 위해 Delivery 조회 API가 필요하면 다음을 추가할 수 있다.

```text
GET /api/v1/notification-deliveries
GET /api/v1/notification-deliveries/{deliveryId}
```

관리자 전용인지 사용자용인지 먼저 확정한다.

---

## 12. 공동 검토 및 개발 전 최종 확인 항목

아래 항목은 두 사람이 먼저 검토하고, 관련 서비스 담당자와 확인해야 한다.

## 12.1 이벤트 발행 주체

- `SENSOR_OFFLINE`, `SENSOR_ERROR`를 Rule과 Cultivation 중 누가 최종 발행하는가?
- 동일 사건을 두 서비스가 동시에 발행하지 않도록 한 곳만 소유해야 한다.
- `INQUIRY_ANSWERED`를 발행할 Inquiry 담당 서비스 이름은 무엇인가?

## 12.2 RabbitMQ 공통 인프라 이름

- 기존 팀 공통 Exchange가 있는가?
- Queue와 Routing Key 이름 규칙이 있는가?
- Notification Queue가 Durable로 생성되는가?
- DLQ 또는 Retry Queue를 이번 범위에 포함하는가?

## 12.3 `USER` 대상 유형

- 최신 ERD `subscription_target_type`에 `USER`를 실제 추가한다.
- Auth Service가 `LOGIN_SUCCEEDED` 이벤트를 발행할 수 있는지 확인한다.
- 계정 알림 설정 화면을 프론트 범위에 포함한다.

## 12.4 환경 이상과 제어 이벤트 수준

- Rule Service가 정상→이상→복구 상태 전이를 관리할 수 있는지 확인한다.
- 공식 요구사항에 자동 제어 성공 알림이 계속 포함된다면 `ACTUATOR_CONTROL_COMPLETED`를 추가한다.
- 제어 성공까지 알림으로 보낼 경우 사용자가 별도로 구독할 수 있게 한다.

## 12.5 템플릿 변수

- 각 Producer가 필요한 payload 값을 실제로 제공할 수 있는지 확인한다.
- `cultivationName`을 이벤트 payload에 포함할지, Notification이 다른 서비스에 조회할지 확정한다.
- 권장 방식은 이벤트 시점의 이름을 payload에 포함하여 동기 API 의존성을 없애는 것이다.

## 12.6 기존 문서와 최신 ERD 차이

기존 프로젝트 문서에는 WebSocket과 웹 읽음 처리 내용이 남아 있을 수 있으나, 최신 결정에서는
WebSocket을 공식 지원 범위에서 제외한다. 현재 ERD와 구현 범위는 Telegram·Discord
Endpoint·Subscription·Delivery 중심으로 유지한다. 주간·월간 피드백 알림도 사용하지 않고
AI 일일 피드백 완료 이벤트만 사용한다.

---

## 13. 권장 구현 순서

## 1단계: 공동 계약 확정

- 이 문서 검토
- 이벤트 발행 주체 확정
- RabbitMQ 이름 확인
- `USER` 대상 추가 확정
- Telegram·Discord만 공식 채널로 유지하는지 확인

완료 조건:

- 이벤트별 Producer가 정해짐
- JSON 샘플을 관련 담당자가 확인함
- 최신 ERD와 구현 문서의 차이가 없음

## 2단계: DB Entity·Migration·Seed

- 최신 ERD 기준 Entity 작성
- FK·Unique·Index 작성
- 기준 데이터 Seed 작성
- Repository 테스트

완료 조건:

- 빈 DB에서 Migration과 Seed가 성공함
- Seed를 두 번 실행해도 중복이 없음

## 3단계: 서영의 Endpoint 구현

- Endpoint CRUD
- 권한 검증
- Telegram·Discord 형식 검증
- Endpoint 테스트

완료 조건:

- 사용자 A가 사용자 B의 Endpoint에 접근할 수 없음
- 유효한 Endpoint가 DB에 저장됨

## 4단계: 서영의 Subscription 구현

- 구독 가능 목록 조회
- 구독 생성·조회·ON/OFF·삭제
- 대상 권한 검증
- 중복 구독 방지

완료 조건:

- 재배·문의·사용자 대상으로 구독 생성 가능
- 비활성 구독은 활성 조회에서 제외됨

## 5단계: 호준의 이벤트 Consumer·알림 생성 구현

- 공통 Event DTO
- 이벤트 검증
- 중복 방지
- 구독 조회
- Notification·Delivery 생성

완료 조건:

- 활성 구독 수만큼 Delivery가 생성됨
- 구독이 없으면 Delivery가 생성되지 않음
- 같은 `eventId`를 두 번 보내도 Notification은 한 번만 생성됨

## 6단계: 호준의 템플릿·외부 발송 구현

- Template Renderer
- Telegram Sender
- Discord Sender
- 성공·실패 이력

완료 조건:

- 채널별 올바른 템플릿으로 발송됨
- 최종 문구와 Provider 응답이 Delivery에 저장됨

## 7단계: 재시도·통합 테스트

- 1분·5분 재시도
- 최종 실패 처리
- Rule·Cultivation·AI·Auth·Inquiry 연동
- 프론트 알림 설정 연동

완료 조건:

- 외부 API 일시 실패 후 재시도 성공
- 영구 오류는 최종 실패 기록
- 전체 이벤트가 올바른 대상·채널로 발송됨

---

## 14. 테스트 시나리오

### 정상 발송

1. 사용자 A가 Discord Endpoint를 등록한다.
2. 사용자 A가 재배 12번 환경 이상 알림을 구독한다.
3. Rule이 `ENVIRONMENT_THRESHOLD_BREACHED`를 발행한다.
4. Notification과 Delivery가 생성된다.
5. Discord로 메시지가 도착한다.
6. Delivery 상태가 `SENT`가 된다.

### 중복 이벤트

1. 동일한 `eventId`의 메시지를 두 번 발행한다.
2. Notification은 한 번만 생성되어야 한다.
3. 사용자에게도 한 번만 발송되어야 한다.

### 구독 없음

1. 대상 재배에 활성 구독이 없다.
2. 이벤트를 발행한다.
3. 외부 채널 발송이 발생하지 않아야 한다.

### 비활성 Endpoint

1. 구독은 활성 상태지만 Endpoint가 비활성이다.
2. 이벤트를 발행한다.
3. 발송 대상에서 제외되어야 한다.

### 발송 실패

1. 외부 API가 일시적 5xx를 반환한다.
2. 1분 뒤 재시도한다.
3. 다시 실패하면 5분 뒤 마지막 재시도한다.
4. 최종 실패 시 `FAILED`, `attempt_count = 3`, 오류 메시지가 저장된다.

### 권한

1. 사용자 B가 사용자 A의 Endpoint를 수정하려고 한다.
2. 접근이 거부되어야 한다.
3. 사용자 B가 권한 없는 재배 12번 구독을 생성하려고 한다.
4. 접근이 거부되어야 한다.

---

## 15. 개발 완료 정의

다음 조건을 모두 만족하면 Notification Service 1차 구현이 완료된 것으로 본다.

- 최신 ERD 테이블과 관계 구현
- Endpoint CRUD와 권한 검증
- Subscription CRUD·ON/OFF·중복 방지
- 확정된 이벤트 Consumer 구현
- `source_event_id` 기반 중복 방지
- 채널별 템플릿 렌더링
- Telegram·Discord 실제 발송
- Delivery 성공·실패 이력 저장
- 최대 3회 재시도
- 핵심 통합 테스트 통과
- 이벤트 계약 및 API 문서 최신화
- 비밀 토큰·Webhook URL이 Git에 포함되지 않음

---

## 16. 서영에게 전달할 요약

```text
Notification은 사용자 설정 영역과 실제 발송 영역으로 나눕니다.

서영님:
- Telegram/Discord Endpoint CRUD
- 재배·문의·계정별 구독 CRUD 및 ON/OFF
- 구독 중복 방지와 권한 검증
- 프론트 알림 설정 API 연동

호준:
- RabbitMQ 이벤트 계약과 Consumer
- 구독 조회 후 Notification·Delivery 생성
- 중복 이벤트 방지
- 템플릿 렌더링
- Telegram/Discord 발송
- 실패 재시도 및 통합 테스트

개발 전에 같이 확인할 것:
- SENSOR_OFFLINE/ERROR 발행 서비스
- 기존 RabbitMQ Exchange/Queue/Route 규칙
- USER 대상 및 로그인 알림 범위
- 자동 제어 성공 알림 유지 여부
- Producer별 payload 변수 제공 가능 여부
- Telegram·Discord 알림 목록·읽음 처리 API를 이번 범위에 포함할지
```

---

## 17. 일정 변경 기준 (2026-07-27)

- 8월 3일: RabbitMQ 사용자 공동 회의에서 실제 메시지 계약과 인프라 이름을 확정한다.
- 8월 3일 전: 호준이 Notification 백엔드 기반 작업을 단독 진행한다. 서영은 프론트 우선 일정으로 Notification 백엔드 병렬 작업을 전제로 하지 않는다.
- 이 기간에는 서영님 담당이었던 Endpoint·Subscription API의 Entity·Repository·기본 CRUD 기반도 호준이 함께 진행한다.
- 8월 13일: 동건님이 이벤트 DTO·RabbitMQ 연동 작업에 합류한다.
- 따라서 Consumer는 지금 임시 설정으로 완성하지 않고, 8월 3일 계약 확정 후 뼈대를 맞추고 8월 13일부터 Producer 통합을 진행한다.
