# EcoSphere / MushMush 프로젝트 컨텍스트

> 이 문서는 다른 AI(Grok 등)에게 프로젝트 맥락을 전달하기 위한 최신 요약본이다.
> 기준일: 2026-07-27
> 민감한 비밀번호, 토큰, 실제 Webhook URL은 포함하지 않는다.

## 1. 프로젝트 한 줄 요약

EcoSphere(서비스명 MushMush)는 IoT 센서와 AI를 활용해 사용자의 버섯 재배를 관리하는 자동화 플랫폼이다.

사용자는 회원가입·로그인 후 재배를 등록하고, 센서를 연결한다. 센서 데이터는 Rule 서비스가 환경 이상 여부와 제어 결과를 판단하며, AI 서비스는 일일 피드백과 성장 관련 분석을 생성한다. Notification 서비스는 여러 서비스가 발행한 이벤트를 받아 사용자의 구독 설정에 따라 Telegram·Discord로 알림을 보낸다.

## 2. 과제의 전체 방향

### 사용자 흐름

1. 회원가입 및 로그인
2. 재배(Cultivation) 생성
3. 버섯 종류와 생육 환경 설정
4. 센서 등록 및 센서 데이터 수집
5. Rule 서비스가 환경값을 임계값과 비교
6. 이상·복구·센서 오류·제어 실패 이벤트 발행
7. AI 서비스가 센서/재배 데이터를 이용해 일일 피드백 생성
8. 사용자가 재배 현황, 사진, 성장 기록, 수확 정보를 확인
9. Notification 서비스가 관련 이벤트를 Telegram·Discord로 전달
10. 사용자는 채널과 이벤트 종류를 직접 구독 설정

### 핵심 서비스

- Web/Frontend: 사용자 화면, 재배·센서·알림 설정 UI
- Gateway: 외부 요청의 진입점 및 서비스 라우팅
- Auth Service: 회원가입, 로그인, OAuth, 사용자 인증
- Cultivation Service: 재배, 재배 멤버, 센서 등록 연계, 사진, 수확
- Data Source Generator: 센서 데이터 생성/시뮬레이션
- Rule Service: 센서값 기반 임계값 판단과 제어 결과 판단
- AI Service: 일일 피드백, 성장 분석, 챗봇 등 AI 기능
- Notification Service: 이벤트 소비, 구독 확인, 알림 생성·발송·재시도

## 3. 팀 역할의 최신 상태

최신 인원 재배분은 다음과 같다.

- 인증: 재웅
- 경작: 진영, 동건, 동희
- AI: 민서, 진서
- 알림: 호준, 서영
- Rule: 상헌, 재웅
- Data Generator: 민서

현재 이 문서를 사용하는 주 담당자는 호준이며 Notification Service 구현을 담당한다. 서영은 Endpoint·Subscription API 및 권한·검증 영역을 담당한다.

## 4. Notification Service의 책임

Notification Service는 센서 데이터를 직접 판단하지 않는다. 다른 서비스가 판단한 결과를 이벤트로 받아 사용자 알림으로 변환한다.

### Notification이 하는 일

1. RabbitMQ Domain Event 수신
2. 공통 이벤트 형식 검증
3. `eventId` 기준 중복 방지
4. 이벤트 대상과 사용자의 활성 구독 확인
5. Notification 원본 생성
6. Telegram·Discord용 템플릿 렌더링
7. Notification Delivery 생성
8. 외부 채널 발송
9. 성공·실패 응답 및 발송 상태 저장
10. 실패 시 최대 3회 재시도

### Notification이 하지 않는 일

- 원시 센서값의 이상 여부 판단
- Rule 판단 대체
- 재배 데이터의 직접 조회·소유
- 사용자 인증 처리
- 사용자 비밀번호·토큰 보관
- 다른 서비스의 DB 직접 접근
- 이메일 인증번호 발송

다른 서비스는 알림을 직접 보내지 않고 이벤트만 발행한다.

## 5. 최신 알림 이벤트 목록

| 이벤트 코드 | 발행 서비스 | 대상 유형 | 의미 |
|---|---|---|---|
| `ENVIRONMENT_THRESHOLD_BREACHED` | Rule | CULTIVATION | 온도·습도·pH·조도·CO2 등이 임계값을 벗어남 |
| `ENVIRONMENT_RECOVERED` | Rule | CULTIVATION | 이상 상태가 정상 범위로 회복됨 |
| `SENSOR_OFFLINE` | Rule/Sensor 관련 서비스 | CULTIVATION | 센서가 오프라인 상태가 됨 |
| `SENSOR_ERROR` | Rule/Sensor 관련 서비스 | CULTIVATION | 센서 읽기 오류·통신 오류 등 |
| `ACTUATOR_CONTROL_FAILED` | Rule | CULTIVATION | 자동 제어 요청이 실패함 |
| `HARVEST_COMPLETED` | Cultivation | CULTIVATION | 수확 기록이 완료됨 |
| `CULTIVATION_FINISHED` | Cultivation | CULTIVATION | 재배가 종료됨 |
| `DAILY_FEEDBACK_COMPLETED` | AI | CULTIVATION | AI 일일 피드백 저장이 완료됨 |
| `LOGIN_SUCCEEDED` | Auth | USER | 로그인 성공 |
| `INQUIRY_ANSWERED` | Inquiry/Cultivation 영역 | USER | 문의 답변이 완료됨 |

이메일 인증번호 이벤트는 현재 알림 목록에서 제외한다. 일반적인 장치 제어 성공이나 정상 ON/OFF 상태도 알림으로 보내지 않고, 제어 실패만 알림으로 보낸다.

## 6. 이벤트 발행 규칙

환경 이상은 같은 상태가 계속되는 동안 매 센서 측정마다 반복 발행하지 않는다.

- 정상 → 이상: `ENVIRONMENT_THRESHOLD_BREACHED` 1회
- 이상 지속: 추가 발행 없음
- 이상 → 정상: `ENVIRONMENT_RECOVERED` 1회
- 센서 오프라인과 센서 오류는 서로 다른 이벤트로 구분
- 이벤트는 원천 데이터/판단 결과가 저장된 뒤 발행
- 동일 이벤트 재전달이 발생해도 Notification은 `eventId`로 한 번만 처리

## 7. 공통 RabbitMQ 이벤트 형식

```json
{
  "eventId": "UUID",
  "eventType": "SENSOR_ERROR",
  "producer": "rule-service",
  "targetType": "CULTIVATION",
  "targetId": 12,
  "occurredAt": "2026-07-24T10:30:00+09:00",
  "payload": {}
}
```

필드 의미:

- `eventId`: 이벤트 고유 ID. 중복 처리 방지의 기준
- `eventType`: 위 표의 이벤트 코드
- `producer`: 이벤트를 생성한 서비스
- `targetType`: `CULTIVATION`, `INQUIRY`, `USER` 중 하나
- `targetId`: 대상 레코드 ID. 재배 알림은 `cultivation_id`, 로그인·문의 알림은 사용자 식별 기준을 사용
- `occurredAt`: 이벤트 발생 시각. ISO-8601 timezone 포함
- `payload`: 이벤트별 상세 데이터

현재 권장 RabbitMQ 이름(팀 공통 규칙으로 최종 확인 필요):

```text
Exchange: domain.events
Type: topic
Queue: notification.events.queue
DLQ: notification.events.dlq
```

실제 host, port, virtual host, credentials, routing key는 RabbitMQ/인프라 담당자에게 최종 확인해야 한다. 현재 로컬 개발 기본값은 `localhost`이며, 운영 주소와 동일하다고 가정하면 안 된다.

## 8. 이벤트별 payload에 포함할 내용

### Rule 이벤트

```json
{
  "cultivationId": 12,
  "cultivationName": "느타리버섯 1차 재배",
  "sensorType": "TEMPERATURE",
  "deviceName": "온도센서-01",
  "currentValue": 28.5,
  "unit": "C",
  "thresholdMin": 18.0,
  "thresholdMax": 22.0,
  "violationDirection": "HIGH",
  "errorMessage": null
}
```

### 센서 오류 이벤트

```json
{
  "cultivationId": 12,
  "sensorId": 4,
  "sensorType": "CO2",
  "deviceName": "CO2센서-01",
  "errorCode": "READ_TIMEOUT",
  "errorMessage": "센서 응답 시간 초과"
}
```

### 제어 실패 이벤트

```json
{
  "cultivationId": 12,
  "actuatorType": "WATER_PUMP",
  "requestedState": "ON",
  "failureCode": "DEVICE_UNREACHABLE",
  "errorMessage": "장치 연결 실패"
}
```

### 수확·재배 종료 이벤트

```json
{
  "cultivationId": 12,
  "cultivationName": "느타리버섯 1차 재배",
  "harvestId": 30,
  "harvestAmount": 850,
  "harvestUnit": "g",
  "harvestedAt": "2026-07-24T15:00:00+09:00"
}
```

### AI 일일 피드백 이벤트

```json
{
  "cultivationId": 12,
  "cultivationName": "느타리버섯 1차 재배",
  "feedbackId": 55,
  "feedbackDate": "2026-07-24",
  "feedbackSummary": "오후 습도가 다소 낮았습니다."
}
```

### 로그인 이벤트

```json
{
  "userId": 101,
  "provider": "GOOGLE",
  "loginMethod": "OAUTH",
  "loggedInAt": "2026-07-24T09:30:00+09:00"
}
```

비밀번호, access token, refresh token, 인증번호는 payload에 포함하지 않는다.

### 문의 답변 이벤트

```json
{
  "inquiryId": 77,
  "userId": 101,
  "inquiryTitle": "느타리버섯 잎이 갈색으로 변합니다",
  "answerId": 88,
  "answeredAt": "2026-07-24T16:20:00+09:00"
}
```

## 9. 구독 및 수신 경로 정책

사용자가 알림 설정 화면에서 직접 Endpoint와 Subscription을 설정한다. 시스템이 모든 사용자에게 자동으로 구독을 생성하지 않는다.

---

# 2026-07-27 최신 변경사항

## A. 작업 범위 변경

서영님이 프론트 작업으로 바쁜 기간에는 호준이 서영님 담당이었던 Endpoint·Subscription 백엔드 기반까지 함께 진행한다. 동건님은 8월 13일경 이벤트 DTO·RabbitMQ 연동 작업에 합류한다. RabbitMQ 담당자 공동 회의는 8월 3일로 예정되어 있으므로, 그 전에는 임시 exchange·queue·routing key를 코드에 고정하지 않는다.

## B. ERD·Entity·Migration 변경

### Endpoint 소프트 삭제

`notification_endpoint`에 `is_deleted BOOLEAN NOT NULL DEFAULT FALSE`를 추가했다.

- `enabled=false, is_deleted=false`: 일시정지. 목록에 보이고 다시 켤 수 있다.
- `enabled=false, is_deleted=true`: 삭제. 기본 목록에서는 숨기지만 발송 이력 보존을 위해 DB 행은 남긴다.

반영 위치:

- `NotificationEndpoint` Entity
- `V4__add_endpoint_soft_delete.sql`
- `idx_endpoint_user_active` 인덱스

### Template 참조 위치

하나의 이벤트가 Telegram·Discord별 Delivery로 나뉘므로 Template은 Notification 원본이 아니라 `notification_delivery.notification_template_id`가 참조한다.

- `Notification` Entity에서 Template 관계 제거
- `NotificationDelivery` Entity에 Template 관계 추가
- `V3__move_template_reference_to_delivery.sql`에서 데이터 이동 후 기존 컬럼 제거
- `notification_template.channel_type_id`는 유지한다. 채널별 템플릿이 필요하기 때문이다.

### Delivery 상태

`notification_delivery.status`는 Boolean이 아니라 문자열 상태다.

```text
PENDING → SENT
PENDING → FAILED (최대 3회 재시도 후)
```

DB에는 `VARCHAR(20)`과 `PENDING/SENT/FAILED` CHECK 조건이 있다. Entity는 `DeliveryStatus`를 `EnumType.STRING`으로 매핑해 DB 값과 타입을 일치시킨다.

### 활성 구독 중복 방지 보완

초기 인덱스가 일시정지 구독까지 중복으로 막는 문제가 있어 `V5__fix_active_subscription_unique_index.sql`을 추가했다.

```sql
WHERE is_deleted = FALSE AND enabled = TRUE
```

따라서 활성 구독만 같은 `subscription_type + endpoint + target` 조합을 중복 생성할 수 없다.

## C. 새로 추가된 Java 코드

### Entity 기능

`NotificationEndpoint`:

- `update(destination, displayName)`
- `changeEnabled(boolean)`
- `softDelete()`

`NotificationSubscription`:

- `changeEnabled(boolean)`
- `softDelete()`

삭제된 객체는 다시 활성화되지 않도록 방어한다.

### Repository

- `NotificationEndpointRepository`
  - 사용자별 삭제되지 않은 Endpoint 조회
  - 본인 소유 Endpoint 단건 조회
- `NotificationSubscriptionRepository`
  - 사용자별 삭제되지 않은 Subscription 조회
  - 본인 소유 Subscription 단건 조회
  - 활성 구독 중복 확인

아직 JWT claim명과 재배 권한 API가 확정되지 않았기 때문에 Controller·Service의 인증/권한 연동은 계약 확정 후 구현한다. 내부 CRUD 기반은 그 전에 진행할 수 있다.

## D. 학습 문서

프로젝트 안에 `Endpoint_Subscription_0727_tip.md`를 추가했다. Endpoint·Subscription, ERD, Entity, Repository, Migration, 소프트 삭제, API 구현 순서, JWT·권한, RabbitMQ Consumer 연결, 용어 사전과 체크리스트를 초보자 기준으로 설명한다.

## E. 검증 상태

```text
mvn test: BUILD SUCCESS
실제 테스트 케이스: 아직 없음
실제 PostgreSQL/Flyway 실행: Docker 접근 권한 문제로 미검증
Controller/API: 아직 구현 전
```

따라서 현재 확인된 것은 컴파일과 Maven 테스트 실행 성공이며, 실제 DB Migration과 API 호출 검증은 PostgreSQL 및 Controller 구현 후 진행해야 한다.

## F. 다음 작업

1. Endpoint·Subscription 요청/응답 DTO 작성
2. Endpoint·Subscription Service 구현
3. 기본 Controller 구현
4. 소유권·중복 구독 단위 테스트 작성
5. 8월 3일 RabbitMQ 계약 확정 후 Consumer 연결
6. 8월 13일 동건님 합류 후 Producer별 실제 DTO와 통합 테스트 진행

- Endpoint: Telegram Chat ID 또는 Discord Webhook URL 등 실제 수신 경로
- Subscription: 특정 대상의 특정 알림 이벤트를 특정 Endpoint로 받겠다는 설정
- 사용자는 Telegram·Discord Endpoint를 등록할 수 있음
- 구독은 ON/OFF 및 삭제 가능
- 비활성 구독은 발송 대상에서 제외
- 중복 활성 구독은 생성하지 않음
- 재배 알림은 사용자가 권한을 가진 재배인지 확인
- 로그인 알림은 사용자 단위로 처리
- 문의 답변 알림은 문의 작성자 사용자에게 전달

예: 사용자가 재배 12번의 `SENSOR_ERROR`를 Discord로 구독하면, 해당 이벤트가 발생했을 때 활성 구독과 Endpoint를 확인한 뒤 Discord Delivery를 생성한다.

## 10. 현재 ERD 및 테이블 역할

최신 ERD 기준 Notification 관련 테이블은 다음과 같다.

- `channel_type`: Telegram·Discord 같은 채널 기준
- `subscription_target_type`: CULTIVATION·INQUIRY·USER 대상 유형
- `notification_event_type`: 이벤트 코드와 대상 유형의 기준 정보
- `notification_subscription_type`: 사용자가 구독할 수 있는 알림 항목
- `subscription_channel`: 구독 유형과 채널의 매핑
- `notification_template`: 이벤트·채널별 메시지 템플릿
- `notification_endpoint`: 사용자의 Telegram Chat ID/Discord Webhook 등 수신 경로
- `notification_subscription`: 사용자·대상·구독 유형·Endpoint 연결 및 활성 상태
- `notification`: 수신 이벤트로 생성한 알림 원본과 최종 문구
- `notification_delivery`: 채널별 발송 시도·성공·실패 이력

최신 ERD의 `notification`은 `notification_template_id`, `source_event_id`, `event_payload`, `message`, `created_at`을 중심으로 사용한다. 오래된 문서의 단일 `notification` 테이블 구조나 WebSocket·WeeklyReport 설명을 현재 구현에 그대로 적용하지 않는다.

## 11. 현재 Notification 저장 정책

- 발송 성공 여부와 관계없이 Notification 원본과 Delivery 이력을 추적
- 최종 렌더링 문구를 저장하여 원본 이벤트 payload가 사라져도 이력 확인 가능
- `source_event_id` 또는 동등한 유니크 기준으로 동일 이벤트 중복 생성 방지
- Delivery 상태: `PENDING`, `SENT`, `FAILED`
- 최대 3회 재시도
- 최종 실패 시 실패 사유와 Provider 응답을 저장

## 12. 현재 코드 저장소와 파일

### Notification 저장소

```text
/Users/chosun-nhn02/Documents/버섯 프로젝트/Notification_service
```

GitHub:

```text
https://github.com/nhnacademy-aiot3-yes-ai-do/Notification_service
```

현재 브랜치: `feature/notification-db-seed`

### 참고 설계 저장소

```text
/Users/chosun-nhn02/Documents/버섯 프로젝트/poly-etilen-final-project
```

GitHub:

```text
https://github.com/Poly-Etilen/final-project/tree/second
```

참고 문서:

- `docs/01_Domain/notification.md`
- `docs/03_Database/notification-db.md`

단, 참고 저장소의 문서에는 과거 설계(WebSocket, WeeklyReport, 단일 notification 테이블)가 남아 있을 수 있다. 최신 회의 결정과 이 문서, 최신 ERD가 우선이다.

## 13. 현재 구현 완료 상태

Notification_service에 다음 작업이 완료되어 있다.

- Maven Spring Boot 프로젝트 생성
- Java 21 설정
- Spring Web, JPA, AMQP, Validation, Actuator 추가
- PostgreSQL 및 Flyway 추가
- Testcontainers 의존성 추가
- `NotificationServiceApplication` 생성
- `application.yml` 작성
- Flyway V1 Migration 작성
- Flyway V2 기준 Seed 작성
- Notification 관련 JPA Entity 작성
- FK·UNIQUE·핵심 인덱스 반영
- Seed 중복 실행 방지
- Docker PostgreSQL 빈 DB 검증
- Flyway Migration 성공 검증
- JPA Schema Validation 및 Spring Boot 기동 검증

현재 생성된 주요 파일:

```text
Notification_service/pom.xml
Notification_service/src/main/java/com/ecosphere/notification/NotificationServiceApplication.java
Notification_service/src/main/java/com/ecosphere/notification/domain/*.java
Notification_service/src/main/resources/application.yml
Notification_service/src/main/resources/db/migration/V1__create_notification_tables.sql
Notification_service/src/main/resources/db/migration/V2__seed_notification_reference_data.sql
Notification_service/Notification_역할분담_및_개발실행계획.md
```

로컬 검증용 PostgreSQL 컨테이너:

```text
Container: notification-db-verify
Database: notification_db
User: postgres
Host port: 55432
Container port: 5432
```

애플리케이션 기본 DB 주소는 팀 개발 기본값인 `localhost:5432`이고, 검증 컨테이너에 연결할 때는 `DB_URL=jdbc:postgresql://localhost:55432/notification_db`로 덮어쓴다.

## 14. 다음 구현 순서

### 단계 1: 공동 계약 확정

- 각 Producer의 최종 payload 확인
- RabbitMQ exchange·queue·routing key 확인
- `targetType`·`targetId` 의미 확정
- ACK/NACK와 DLQ 정책 확인

### 단계 2: Consumer

- 공통 Event DTO 작성
- 이벤트 코드 검증
- RabbitMQ Listener/Consumer 작성
- 잘못된 이벤트 처리
- `eventId` 중복 방지

### 단계 3: 알림 생성

- 활성 Subscription 조회
- 권한이 있는 재배·사용자 대상인지 확인
- Notification 생성
- 활성 구독 수만큼 Delivery 생성
- 구독이 없으면 Delivery 미생성

### 단계 4: 채널 발송

- Template Renderer
- Telegram Sender
- Discord Sender
- Provider 응답 저장
- 성공·실패 상태 변경

### 단계 5: 재시도·통합 테스트

- 최대 3회 재시도
- 최종 실패 이력 저장
- 동일 `eventId` 재전달 테스트
- 구독 없음 테스트
- Telegram·Discord 발송 테스트
- RabbitMQ → Consumer → DB → Provider 통합 테스트

## 15. 현재 당장 필요한 협업 정보

DB 작업 직후 Consumer를 구현하기 전까지는 다음 네 가지를 각 담당자에게 받아야 한다.

1. 최종 이벤트 payload JSON
2. RabbitMQ exchange·queue·routing key 이름
3. `targetType`·`targetId` 확정 기준
4. 이벤트 발행 조건과 발행 시점

현재는 DB·Entity·Seed 작업을 먼저 진행하고, Producer 구현이 진행된 뒤 실제 계약값을 맞춘다. Consumer 뼈대는 임시 테스트 이벤트로 먼저 만들 수 있지만, 운영 연결 전에는 반드시 실제 계약을 반영해야 한다.

## 16. 팀에 공유할 때의 핵심 설명

> 우리는 IoT 센서와 AI를 이용해 버섯 재배를 관리하는 플랫폼을 만들고 있습니다. Rule 서비스가 센서값을 판단하고, Cultivation·AI·Auth·Inquiry 서비스가 각자의 도메인 이벤트를 발행하면 Notification 서비스가 RabbitMQ로 이를 받아 사용자의 구독 설정을 확인한 뒤 Telegram과 Discord로 알림을 전송합니다. 각 서비스는 알림을 직접 보내지 않고 이벤트만 발행하며, Notification이 중복 방지·템플릿·발송·재시도·실패 이력을 책임집니다.

## 17. Grok에게 질문할 때의 주의사항

Grok에게 구현을 요청할 때는 다음 기준을 함께 알려야 한다.

- 최신 회의 결정이 오래된 참고 문서보다 우선
- Notification은 센서 판단 서비스가 아님
- PostgreSQL은 Notification Service가 소유하며 다른 서비스 DB를 직접 조회하지 않음
- 실제 RabbitMQ 이름과 payload가 확정되기 전에는 설정값을 추측하지 않음
- 변경 전에는 현재 ERD와 `Notification_역할분담_및_개발실행계획.md`를 확인
- 기존 파일을 덮어쓰기 전에 변경 이유와 영향 범위를 설명
- 비밀번호·토큰·Webhook URL을 코드나 문서에 하드코딩하지 않음
- 구현 후 `mvn test`, Migration 검증, 중복 이벤트 테스트를 수행

## 18. 현재 미확정 또는 추후 확인 항목

- 실제 운영 RabbitMQ host·port·vhost
- 최종 exchange·queue·routing key 이름
- 각 Producer의 최종 payload 필드명
- Gateway가 전달하는 JWT claim 이름
- 실제 Telegram·Discord provider 연동 방식과 secrets 관리
- Telegram·Discord 알림 목록·읽음 처리 API를 제공할지와 범위
- 자동 제어 성공 알림을 향후 추가할지

이 항목들은 현재 DB 설계를 막는 요소는 아니지만, Consumer·외부 발송·API 통합 전에 확정해야 한다.
