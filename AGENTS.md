# Notification Service 작업 운영 지침

> 프로젝트: EcoSphere / MushMush 버섯 재배 자동화 플랫폼  
> 서비스: Notification Service  
> 기준일: 2026-07-26  
> 이 파일의 목적: 사람이든 Codex든 작업을 재개할 때, 현재 맥락·결정·구현 상태·다음 행동·검증 기준을 빠르게 복구한다.

이 문서는 단순한 코드 스타일 문서가 아니다. Notification Service 장기 작업의 **작업 메모리·운영 규칙·결정 기록**이다.

---

## 1. 이 프로젝트가 만드는 것

EcoSphere(서비스명 MushMush)는 IoT 센서와 AI를 이용해 개인의 버섯 재배를 관리하는 플랫폼이다.

사용자는 회원가입·로그인 후 재배를 만들고, 센서를 연결하며, 센서값과 AI 피드백을 확인한다. 환경 이상, 센서 오류, 수확 완료, 재배 종료, AI 일일 피드백 완료 등 중요한 사건이 생기면 사용자는 Telegram 또는 Discord로 알림을 받을 수 있다.

Notification Service는 다른 서비스가 발행한 이벤트를 받아 사용자의 구독 설정에 따라 외부 채널로 전달하고, 발송 상태와 실패 이력을 저장하는 서비스다.

### 핵심 한 문장

> Notification Service는 센서값을 직접 판단하지 않고, 다른 서비스가 판단·발행한 도메인 이벤트를 사용자별 구독과 수신 경로에 맞춰 Telegram·Discord 알림으로 바꾼다.

---

## 2. 전체 서비스 구조와 경계

| 서비스 | 책임 | Notification과의 관계 |
|---|---|---|
| Auth Service | 회원가입, 로그인, OAuth, 인증 | 로그인 성공 이벤트 발행, JWT 사용자 ID 제공 |
| Cultivation Service | 재배, 수확, 사진, 재배 멤버·권한 | 수확 완료·재배 종료 이벤트 발행, 재배 권한 확인 |
| Data Source Generator | 센서 데이터 생성/시뮬레이션 | Rule의 입력 데이터 제공 |
| Rule Service | 환경 임계값 판단, 센서 오류·오프라인·제어 실패 판단 | 알림 이벤트 발행 |
| AI Service | AI 일일 피드백, 챗봇, 성장 분석 | 일일 피드백 완료 이벤트 발행 |
| Inquiry 영역 | 사용자 문의와 답변 | 문의 답변 완료 이벤트 발행 |
| Gateway | Web 요청을 각 서비스로 라우팅 | Notification REST API의 진입점 |
| RabbitMQ | 서비스 간 비동기 이벤트 전달 | Notification이 이벤트를 소비하는 통로 |
| Notification Service | 구독 확인, 메시지 생성, 외부 발송, 재시도·이력 | 이 저장소의 구현 대상 |

### 반드시 지킬 서비스 경계

- Notification은 Rule의 판단을 대체하지 않는다.
- Notification은 Cultivation·Auth·AI의 DB를 직접 조회하지 않는다.
- 다른 서비스는 Telegram·Discord에 직접 발송하지 않는다. 이벤트만 발행한다.
- 다른 서비스의 DB ID는 FK가 아니라 필요한 경우 숫자 ID로만 보관하는 소프트 참조를 사용한다.
- Notification의 PostgreSQL은 Notification Service가 소유한다.

---

## 3. 팀 역할 분담

현재 팀 역할:

- 인증: 재웅
- 경작: 진영, 동건, 동희
- AI: 민서, 진서
- 알림: 호준, 서영
- Rule: 상헌, 재웅
- Data Generator: 민서

Notification 내부 역할:

| 담당 | 책임 |
|---|---|
| 호준 | 이벤트 계약, RabbitMQ Consumer, 중복 방지, 활성 구독 조회, Notification·Delivery 생성, 템플릿 렌더링, Telegram·Discord 발송, 재시도, 통합 테스트, 기준 Seed |
| 서영 | Endpoint·Subscription API, 소유권·권한 검증, 채널 형식 검증, 구독 UI 연동용 API |

공유 테이블·Migration·기준 Seed는 PR 전에 두 담당자가 함께 검토한다.

---

## 4. Notification의 책임과 비책임

### Notification이 하는 일

1. RabbitMQ 도메인 이벤트 수신
2. 공통 이벤트 형식 검증
3. `eventId` 기준 중복 방지
4. 활성 Subscription과 Endpoint 조회
5. Notification 원본 생성
6. 채널별 Template 렌더링
7. Notification Delivery 생성
8. Telegram·Discord 외부 발송
9. 성공·실패·Provider 응답 저장
10. 실패 시 최대 3회 재시도

### Notification이 하지 않는 일

- 센서 원시값의 이상 여부 판단
- 자동 제어 결정
- 재배 생성·수확 저장
- 로그인·JWT 발급
- AI 일일 피드백 생성
- 이메일 인증번호 발송
- 다른 서비스 DB 직접 접근

---

## 5. 확정된 알림 정책

### 지원 채널

- Telegram
- Discord

### 구독 정책

- 사용자가 설정 화면에서 직접 Endpoint와 Subscription을 만든다.
- 모든 사용자에게 자동 구독을 만들지 않는다.
- 활성 상태인 구독만 발송 대상이다.
- 같은 사용자·대상·알림 종류·Endpoint 조합의 활성 구독은 중복 생성하지 않는다.
- Endpoint는 실제 수신 경로다. Telegram Chat ID 또는 Discord Webhook URL을 저장한다.
- Subscription은 "특정 대상의 특정 알림을 특정 Endpoint로 받겠다"는 설정이다.

### 발송 정책

- 제어 성공·일반적인 ON/OFF 성공은 알림으로 보내지 않는다.
- 제어 실패만 알림으로 보낸다.
- 이메일 인증번호 이벤트는 Notification 범위에서 제외한다.
- 발송 실패는 최대 3회 재시도한다.
- 최종 실패는 `FAILED` 상태와 오류 이력을 남긴다.

### 환경 이상 정책

- 정상 → 이상: `ENVIRONMENT_THRESHOLD_BREACHED` 1회 발행
- 이상 지속: 반복 발행하지 않음
- 이상 → 정상: `ENVIRONMENT_RECOVERED` 1회 발행
- 센서 오프라인과 센서 오류는 별도 이벤트로 구분

---

## 6. 현재 이벤트 목록

| 이벤트 코드 | Producer | 대상 유형(현재 방향) | 설명 |
|---|---|---|---|
| `ENVIRONMENT_THRESHOLD_BREACHED` | Rule | CULTIVATION | 환경값이 임계 범위를 벗어남 |
| `ENVIRONMENT_RECOVERED` | Rule | CULTIVATION | 환경값이 정상 범위로 회복 |
| `SENSOR_OFFLINE` | Rule/Sensor 관련 | CULTIVATION | 센서 연결 끊김 |
| `SENSOR_ERROR` | Rule/Sensor 관련 | CULTIVATION | 센서 측정·처리 오류 |
| `ACTUATOR_CONTROL_FAILED` | Rule | CULTIVATION | 자동 제어 요청 실패 |
| `HARVEST_COMPLETED` | Cultivation | CULTIVATION | 수확 기록 완료 |
| `CULTIVATION_FINISHED` | Cultivation | CULTIVATION | 재배 종료 |
| `DAILY_FEEDBACK_COMPLETED` | AI | CULTIVATION | AI 일일 피드백 저장 완료 |
| `LOGIN_SUCCEEDED` | Auth | USER | 로그인 성공 |
| `INQUIRY_ANSWERED` | Inquiry | USER 또는 INQUIRY 확인 필요 | 문의 답변 완료 |

### 문의 답변 이벤트 주의사항

회의에서는 "문의 답변은 문의 작성자 사용자에게 알림"이라는 방향으로 `targetType=USER`, `targetId=user_id`가 자연스럽다고 논의했다.

하지만 현재 Seed에는 `INQUIRY_ANSWERED → INQUIRY`가 들어가 있다. Consumer·Subscription API 구현 전에 팀과 아래 중 하나를 명확히 확정한다.

1. `INQUIRY`: `targetId=inquiry_id`, 특정 문의 단위 구독
2. `USER`: `targetId=user_id`, 계정 단위의 "내 문의 답변 알림" 구독

현재 사용자 경험 관점에서는 보통 `USER` 방식이 더 자연스럽지만, 임의로 Migration/Seed를 수정하지 않는다.

---

## 7. RabbitMQ 공통 이벤트 계약

모든 Producer는 다음 envelope를 기준으로 이벤트를 발행한다.

```json
{
  "eventId": "UUID",
  "eventType": "SENSOR_ERROR",
  "producer": "rule-service",
  "targetType": "CULTIVATION",
  "targetId": 12,
  "occurredAt": "2026-07-26T10:30:00+09:00",
  "payload": {}
}
```

| 필드 | 의미 | 필수 여부 |
|---|---|---|
| `eventId` | 이벤트 고유 ID. 중복 방지 기준 | 필수 |
| `eventType` | 위 이벤트 목록의 코드 | 필수 |
| `producer` | 이벤트를 발행한 서비스 이름 | 필수 |
| `targetType` | CULTIVATION, INQUIRY, USER 등 | 필수 |
| `targetId` | 대상의 실제 ID | 필수 |
| `occurredAt` | ISO-8601 timezone 포함 발생 시각 | 필수 |
| `payload` | 사건별 상세 데이터 | 필수 |

### 이벤트 계약 원칙

- Producer는 자신의 DB 저장이 성공한 뒤 이벤트를 발행한다.
- 비밀번호, access token, refresh token, 이메일 인증번호 같은 민감정보는 payload에 넣지 않는다.
- Template에 쓰는 변수 이름과 payload 키 이름을 반드시 맞춘다.
- 실제 payload 필드명은 Consumer 구현 전 각 Producer 담당자와 확정한다.

### 권장 RabbitMQ 이름: 아직 최종 확정 전

```text
Exchange: domain.events
Type: topic
Notification Queue: notification.events.queue
Dead Letter Queue: notification.events.dlq
```

실제 exchange, queue, routing key, vhost, credentials, ACK/NACK·DLQ 정책은 RabbitMQ/인프라 담당자와 최종 확인해야 한다. 이 문서의 이름은 임시 권장값이지 확정값이 아니다.

---

## 8. Producer별로 받아야 하는 계약 정보

Consumer를 실제 연결하기 전에 각 담당자로부터 아래 정보를 받는다.

### Rule 담당자

- `ENVIRONMENT_THRESHOLD_BREACHED`, `ENVIRONMENT_RECOVERED`, `SENSOR_OFFLINE`, `SENSOR_ERROR`, `ACTUATOR_CONTROL_FAILED`
- 정확한 발행 조건과 상태 전이 기준
- 센서 오프라인과 오류의 구분
- 제어 실패 payload
- `targetId=cultivation_id` 여부
- 실제 routing key와 JSON 샘플

환경 이상 payload 예시:

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
  "violationDirection": "HIGH"
}
```

### Cultivation 담당자

- `HARVEST_COMPLETED`, `CULTIVATION_FINISHED`
- 수확 기록·재배 종료 저장 후 발행하는지
- 수확량 단위는 `g`
- `targetId=cultivation_id` 여부
- 수확·재배 종료 JSON 샘플

### AI 담당자

- `DAILY_FEEDBACK_COMPLETED`
- AI 피드백 저장 완료 후 발행하는지
- `feedbackId`, `feedbackDate`, `feedbackSummary` 제공 여부
- `targetId=cultivation_id` 여부

### Auth 담당자

- `LOGIN_SUCCEEDED`
- 일반 로그인과 OAuth의 provider 표현 규칙
- `targetId=user_id` 여부
- JWT 사용자 ID claim 이름 (`userId`, `sub` 등)

### Inquiry 담당자

- `INQUIRY_ANSWERED`
- `targetType`·`targetId` 최종 기준
- 문의 작성자 식별 방식
- 답변 저장 완료 후 발행하는지

### RabbitMQ/인프라 담당자

- host, port, vhost
- exchange 이름·type·durable 여부
- queue 이름
- routing key 규칙
- DLQ 여부와 이름
- ACK/NACK, requeue, retry 정책
- 개발/운영 환경별 접근 정보

---

## 9. 현재 ERD / DB 설계

### Notification 전용 테이블

| 테이블 | 역할 | 상태 |
|---|---|---|
| `channel_type` | Telegram·Discord 같은 채널 기준 정보 | 구현됨 / Seed됨 |
| `subscription_target_type` | CULTIVATION·INQUIRY·USER 대상 종류 | 구현됨 / Seed됨 |
| `notification_event_type` | 시스템 이벤트 코드 기준 정보 | 구현됨 / Seed됨 |
| `notification_subscription_type` | 사용자가 구독하는 알림 항목 | 구현됨 / Seed됨 |
| `subscription_channel` | 구독 유형별 지원 채널 매핑 | 구현됨 / Seed됨 |
| `notification_template` | 이벤트·채널별 메시지 템플릿 | 구현됨 / Seed됨 |
| `notification_endpoint` | 사용자의 실제 수신 경로 | 구현됨 / API 미구현 |
| `notification_subscription` | 사용자별 알림 수신 설정 | 구현됨 / API 미구현 |
| `notification` | 수신 이벤트로 생성한 알림 원본 | 구현됨 / Consumer 미구현 |
| `notification_delivery` | 실제 채널 발송 시도·결과 | 구현됨 / Sender 미구현 |

### 핵심 데이터 관계

```text
사용자(Auth DB의 userId)
   └─ notification_endpoint
        └─ notification_subscription
             ├─ notification_subscription_type
             └─ target_id

RabbitMQ event
   └─ notification
        └─ notification_delivery
             └─ notification_subscription
                  └─ notification_endpoint
```

### 중요한 제약조건

- `channel_type.code` UNIQUE
- `notification_event_type.code` UNIQUE
- `notification.source_event_id` UNIQUE: 같은 `eventId` 중복 처리 방지
- `notification_delivery`는 `PENDING`, `SENT`, `FAILED` 상태만 허용
- `attempt_count`는 0~3만 허용
- 활성 Subscription 조합은 partial unique index로 중복 방지

---

## 10. 현재 코드·파일 상태

### 실제 프로젝트 경로

```text
/Users/chosun-nhn02/Documents/버섯 프로젝트/Notification_service
```

> 주의: `/Users/chosun-nhn02/Notification_service`에 있던 빈 IntelliJ 폴더는 2026-07-26에 휴지통으로 이동했다. 앞으로 반드시 위 Documents 경로의 프로젝트를 열고 작업한다.

### Git 정보

```text
GitHub: https://github.com/nhnacademy-aiot3-yes-ai-do/Notification_service
Local branch: feature/notification-db-seed
```

아직 커밋·푸시 여부는 작업 전 `git status`, `git log`로 반드시 확인하고 추정하지 않는다.

### 현재 구현된 파일

```text
pom.xml
src/main/java/com/ecosphere/notification/NotificationServiceApplication.java
src/main/java/com/ecosphere/notification/domain/AuditEntity.java
src/main/java/com/ecosphere/notification/domain/ChannelType.java
src/main/java/com/ecosphere/notification/domain/SubscriptionTargetType.java
src/main/java/com/ecosphere/notification/domain/NotificationEventType.java
src/main/java/com/ecosphere/notification/domain/NotificationSubscriptionType.java
src/main/java/com/ecosphere/notification/domain/SubscriptionChannel.java
src/main/java/com/ecosphere/notification/domain/NotificationTemplate.java
src/main/java/com/ecosphere/notification/domain/NotificationEndpoint.java
src/main/java/com/ecosphere/notification/domain/NotificationSubscription.java
src/main/java/com/ecosphere/notification/domain/Notification.java
src/main/java/com/ecosphere/notification/domain/NotificationDelivery.java
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_notification_tables.sql
src/main/resources/db/migration/V2__seed_notification_reference_data.sql
```

### 현재 추가된 문서

| 파일 | 목적 |
|---|---|
| `Notification_역할분담_및_개발실행계획.md` | Notification 역할·정책·단계별 계획의 기준 문서 |
| `Notification_초보자_학습가이드.md` | 초보자용 개념·코드·DB·RabbitMQ 학습 문서 |
| `GROK_PROJECT_CONTEXT.md` | 다른 AI에게 프로젝트 맥락을 전달하기 위한 통합 문서 |
| `docs/서영님_Endpoint_Subscription_구현안내.md` | 서영님의 Endpoint·Subscription 구현 안내 |
| `docs/reference/notification.md` | 기존 참고 문서. 일부 내용은 오래된 설계일 수 있음 |
| `docs/reference/notification-db.md` | 기존 DB 참고 문서. 최신 ERD보다 우선하지 않음 |

### 현재 의존성

- Spring Boot 4.0.7
- Java 21
- Spring Web
- Spring Data JPA
- Spring AMQP
- Flyway
- PostgreSQL
- Lombok
- Actuator
- Testcontainers

---

## 11. 현재 검증 상태

### 완료된 검증

- `mvn compile` 성공
- Docker PostgreSQL 빈 DB에서 Flyway V1/V2 성공
- JPA `ddl-auto=validate` 성공
- Seed 재실행 시 중복 미발생 확인
- Spring Boot 기동 확인

### 로컬 DB

```text
Container: notification-db-verify
Database: notification_db
User: postgres
Host port: 55432
Container port: 5432
```

### 로컬 실행 시 주의

`application.yml` 기본 DB 포트는 팀 일반 설정을 위한 `5432`다. 현재 로컬 Docker 검증 DB는 `55432`를 사용한다.

검증용 실행 예시:

```bash
DB_URL='jdbc:postgresql://localhost:55432/notification_db' \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
java -jar target/notification-service-0.0.1-SNAPSHOT.jar
```

현재 개발 단계에서는 RabbitMQ Consumer가 아직 없지만 AMQP health indicator가 `localhost:5672`의 RabbitMQ 연결을 확인한다. RabbitMQ를 띄우지 않으면 전체 `/actuator/health`는 DOWN일 수 있다. 그러나 DB 연결과 애플리케이션 기동 여부는 liveness/readiness 및 로그로 별도 확인한다.

### 실행 확인 기준

아래 로그가 나오면 Spring Boot와 DB Migration은 정상이다.

```text
Successfully validated 2 migrations
Schema "public" is up to date
Started NotificationServiceApplication
```

---

## 12. 현재 작업 단계

### 완료

1. Notification Service 프로젝트 골격 생성
2. Maven·Spring Boot·Java 21 설정
3. PostgreSQL·Flyway 설정
4. Notification ERD 기반 V1 Migration 작성
5. 기준 코드·Template V2 Seed 작성
6. Entity 작성
7. Docker PostgreSQL Migration·Seed 검증
8. 역할분담·개발 계획·학습 문서·서영님 안내 문서 작성

### 아직 구현하지 않은 것

1. Repository
2. Endpoint API
3. Subscription API
4. JWT 인증 연동
5. 재배 권한 검증 연동
6. RabbitMQ Event DTO
7. RabbitMQ Consumer
8. 이벤트 유효성 검증
9. 이벤트 중복 방지 서비스 로직
10. 활성 Subscription 조회
11. Notification·Delivery 생성 로직
12. Template Renderer
13. Telegram Sender
14. Discord Sender
15. 발송 상태 업데이트
16. Retry 정책 구현
17. 단위 테스트·통합 테스트
18. Gateway·Producer 실제 연결

---

## 13. 앞으로의 구현 순서

### 단계 1: 계약 최종 확인

구현 전에 각 담당자의 실제 payload와 RabbitMQ 정보를 받는다.

완료 기준:

- 이벤트별 Producer가 확정됨
- eventType, targetType, targetId 의미가 정리됨
- JSON 샘플을 관련 담당자가 확인함
- exchange·queue·routing key를 확인함

### 단계 2: Repository

다음 Repository를 우선 만든다.

- `NotificationRepository`
- `NotificationSubscriptionRepository`
- `NotificationEventTypeRepository`
- `NotificationTemplateRepository`
- `NotificationDeliveryRepository`
- 필요 시 Endpoint/Channel/SubscriptionType Repository

완료 기준:

- 활성 Subscription을 event type·target ID·enabled·deleted 조건으로 찾을 수 있음
- 동일 `source_event_id` 존재 여부를 확인할 수 있음

### 단계 3: Event DTO와 Consumer

예상 DTO:

```java
public record DomainEvent(
    UUID eventId,
    String eventType,
    String producer,
    String targetType,
    Long targetId,
    OffsetDateTime occurredAt,
    Map<String, Object> payload
) {}
```

Consumer 순서:

1. RabbitMQ JSON 수신
2. `DomainEvent` 변환
3. 필수 필드 검증
4. event type·target type 검증
5. `eventId` 중복 확인
6. 활성 Subscription 조회
7. 구독이 없으면 정상 종료
8. Notification·Delivery 생성

완료 기준:

- 활성 구독 수만큼 Delivery 생성
- 구독이 없으면 Delivery 생성 없음
- 같은 eventId를 두 번 보내도 Notification은 하나만 생성

### 단계 4: Template·외부 발송

- Template variable 치환
- Telegram Sender
- Discord Sender
- Provider 응답 저장
- 상태 `PENDING → SENT` 또는 `FAILED` 변경

완료 기준:

- 채널별 적절한 템플릿으로 발송됨
- 최종 문구와 Provider 응답이 Delivery에 저장됨

### 단계 5: 재시도·통합 테스트

- 최대 3회 재시도
- 실패 이력 저장
- 중복 이벤트 테스트
- 구독 없음 테스트
- Telegram/Discord adapter 테스트
- RabbitMQ → Consumer → DB → Sender 통합 테스트

---

## 14. 서영님 작업과의 연동 규칙

서영님은 Endpoint·Subscription API를 구현한다.

### 서영님이 구현할 주요 API 범위

```text
POST   /api/v1/notifications/endpoints
GET    /api/v1/notifications/endpoints
PATCH  /api/v1/notifications/endpoints/{endpointId}
DELETE /api/v1/notifications/endpoints/{endpointId}

GET    /api/v1/notifications/subscription-types
GET    /api/v1/notifications/subscriptions
POST   /api/v1/notifications/subscriptions
PATCH  /api/v1/notifications/subscriptions/{subscriptionId}
DELETE /api/v1/notifications/subscriptions/{subscriptionId}
```

### Consumer 구현 전 서영님에게 받을 것

- Endpoint·Subscription 최종 DTO 필드명
- 요청·응답 JSON
- 활성 구독 판단 규칙
- 삭제·비활성화 방식
- JWT 사용자 ID를 가져오는 방식
- 재배 권한 검증 방식
- 오류 응답 형식

### 현재 Endpoint 정책의 확인 필요 사항

설계 논의에서는 Endpoint도 `is_deleted=true` 소프트 삭제를 제안했다. 그러나 현재 `notification_endpoint` Migration에는 `is_deleted` 컬럼이 없고 `enabled`만 있다.

Endpoint API 구현 전에 다음 중 하나를 확정한다.

1. Endpoint는 `enabled=false`만 사용하고 물리 삭제 API는 제공하지 않음
2. Endpoint에도 `is_deleted`를 추가하는 **새 V3 Migration**을 작성

이미 공유·실행된 V1을 수정하지 않는다. 변경이 필요하면 `V3__...sql` 파일을 추가한다.

---

## 15. 설계상 확인해야 할 항목

### 15.1 `notification`과 채널별 Template의 관계

기존 설계에서는 `notification`이 `notification_template_id` 하나를 가졌지만, 하나의 이벤트가 Telegram·Discord 두 채널로 가면 채널별 Template이 달라질 수 있다. 이 문제를 해결하기 위해 V3 Migration에서 Template FK를 `notification_delivery`로 이동했다.

현재 `notification_delivery.rendered_message`는 이미 채널별 최종 문구를 저장할 수 있다.

현재 확정된 설계는 아래와 같다.

1. Notification은 사건 원본과 payload를 저장한다.
2. Delivery마다 채널에 맞는 Template과 rendered message를 저장한다.
3. 기존 DB에도 적용할 수 있도록 V3 Migration에서 기존 template 참조를 Delivery로 이관한 뒤 Notification의 Template FK를 제거한다.

현재 Seed의 양 채널 문구가 거의 같아서 당장 문제가 드러나지 않지만, 채널별 Markdown 문법 등을 적용하면 중요해진다.

### 15.2 `updated_at` 자동 변경

현재 SQL은 insert 시 `DEFAULT CURRENT_TIMESTAMP`를 넣는다. 하지만 Entity 수정 시 `updated_at`을 자동 변경하는 `@PreUpdate`, JPA Auditing 또는 DB Trigger는 아직 없다.

Endpoint·Subscription 수정 API 구현 전에 자동 갱신 방식을 정한다.

### 15.3 최신 문서 우선순위

기존 참고 문서에는 WebSocket, WeeklyReport, 단일 notification 테이블처럼 오래된 설계가 남아 있을 수 있다.

우선순위는 아래와 같다.

1. 최신 팀 회의 결정
2. 최신 ERD
3. `Notification_역할분담_및_개발실행계획.md`
4. 이 `AGENTS.md`
5. 기존 `docs/reference/*` 참고 문서

오래된 문서만 보고 최신 설계를 되돌리지 않는다.

---

## 16. 코드 변경 규칙

### 파일 작업 전

1. `git status --short`로 기존 변경을 확인한다.
2. 최신 ERD·역할분담·현재 Migration과 충돌하는지 확인한다.
3. 다른 담당자의 파일을 불필요하게 수정하지 않는다.
4. 변경 범위가 불명확하면 먼저 설명하고 방향을 확인한다.

### DB 변경

- 이미 공유된 Migration 파일을 덮어쓰지 않는다.
- 변경은 새 버전 파일(`V3__...sql`)로 추가한다.
- FK, UNIQUE, CHECK, Index 필요성을 함께 검토한다.
- 빈 DB Migration과 Seed 재실행을 검증한다.

### 코드 작성

- Controller는 HTTP 요청·응답에 집중한다.
- Service는 도메인 규칙과 Transaction을 담당한다.
- Repository는 DB 조회에 집중한다.
- Consumer는 메시지 수신·역직렬화·서비스 호출에 집중한다.
- Sender는 Telegram/Discord 호출을 각각 분리한다.
- Event DTO와 외부 API DTO는 Entity를 직접 노출하지 않는다.
- 예외 메시지에는 비밀번호, token, Webhook URL 전체를 남기지 않는다.

### Git

- 사용자 의도 없이 `git reset --hard`, 강제 checkout, 광범위 삭제를 하지 않는다.
- `.idea`, `target`, `.env`, 민감한 설정 파일은 Git에 올리지 않는다.
- 커밋 전 변경 파일과 테스트 결과를 요약한다.
- 커밋·푸시는 사용자가 요청하거나 승인한 경우에만 수행한다.

### 작업 완료 후 Git 협업 절차

기능 구현과 검증이 끝나면 먼저 사용자에게 변경 파일·검증 결과·남은 위험을 설명하고 확인을 받는다. 사용자의 확인 전에는 아래 Git 작업을 진행하지 않는다.

```text
git add
git commit
git push origin <feature-branch>
GitHub에서 develop 대상 Pull Request 생성
리뷰 후 develop 병합
```

현재 Notification 작업 브랜치는 `feature/notification-contract`다. 앞으로도 커밋·push·PR·merge는 각 단계 전 사용자 확인을 받고 진행한다. 특히 `develop`이나 `main`에 직접 push하지 않고, feature 브랜치와 Pull Request를 기본으로 사용한다.

---

## 17. 장기 작업 운영 방식

이 프로젝트는 단발성 질문이 아니라 장기 개발 작업이다. Codex는 단순 답변 도구가 아니라, 맥락·결정·검증·다음 행동을 계속 이어가는 **작업 운영체제**처럼 사용한다.

### 17.1 이 스레드를 Notification 작업의 본진으로 유지

- Notification Service 관련 중요한 질문, 구현, 오류, 회의 결정은 가능하면 이 스레드에서 이어간다.
- 매번 새 대화에서 처음부터 설명하게 만들지 않는다.
- 일회성 일반 질문은 새 스레드를 써도 되지만, 서비스 설계·코드·DB·통합 작업은 이 스레드에 누적한다.

### 17.2 채팅만 믿지 않고 파일에 남긴다

중요한 결정은 반드시 파일에 기록한다.

- 역할·구현 계획: `Notification_역할분담_및_개발실행계획.md`
- 초보자 학습·개념: `Notification_초보자_학습가이드.md`
- 다른 AI 전달용 맥락: `GROK_PROJECT_CONTEXT.md`
- 서영님 담당 구현 안내: `docs/서영님_Endpoint_Subscription_구현안내.md`
- 현재 장기 작업 메모리와 기본 지침: `AGENTS.md`

회의로 결정이 바뀌면 관련 문서와 이 파일의 "확정 정책", "미확정 항목", "다음 단계"를 함께 갱신한다.

### 17.3 작업을 단발 요청이 아니라 검증 루프로 수행

코드 작업 요청을 받으면 가능한 한 다음 순서를 따른다.

```text
1. 현재 상태·문제·제약 파악
2. 수정 범위와 영향을 설명
3. 구현
4. Build / Test / Migration 등 적절한 검증
5. 실패하면 원인 분석
6. 안전한 범위에서 수정·재검증
7. 파일별 변경 내용과 검증 결과 요약
8. 남은 위험·다음 행동 기록
```

"코드만 작성하고 검증하지 않는 것"을 완료로 간주하지 않는다.

### 17.4 요청은 검증 가능한 완료 기준을 갖게 한다

막연한 "잘 만들어줘"보다 아래 요소를 명확히 한다.

- 무엇을 바꾸는가?
- 무엇은 깨지면 안 되는가?
- 어떤 테스트 또는 검증을 통과해야 하는가?
- 어떤 파일이 바뀌어야 하는가?
- 리뷰할 사람이 무엇을 확인하면 되는가?

예시:

```text
RabbitMQ Consumer를 구현한다.
- 동일 eventId는 Notification을 한 번만 생성해야 한다.
- 구독이 없으면 Delivery를 만들지 않아야 한다.
- 활성 구독 수만큼 Delivery를 생성해야 한다.
- 단위 테스트와 PostgreSQL 통합 테스트를 통과해야 한다.
- 변경 파일과 검증 결과를 마지막에 요약한다.
```

### 17.5 사용자는 작업 중에도 방향을 계속 조정한다

- 회의 결정이 바뀌면 바로 알려준다.
- AI의 제안이 실제 팀 방향과 다르면 즉시 수정한다.
- 새로운 ERD, 아키텍처 이미지, 전사문, 요구사항 문서를 받으면 기존 결정과 비교한다.
- Codex는 자동 조종 보조자이며, 최종 목적지·우선순위·외부 협업 결정은 사용자가 잡는다.

### 17.6 음성·중얼거림도 요구사항으로 활용한다

사용자가 정제되지 않은 말이나 긴 음성 전사문을 주더라도 다음을 추출해 정리한다.

- 결정된 사항
- 열린 질문
- 담당자
- 우선순위
- 영향받는 코드·ERD·아키텍처
- 다음 행동

완벽한 프롬프트를 요구하지 않는다. 맥락을 구조화해 되돌려 주는 것이 역할이다.

### 17.7 긴 작업 후 반드시 상태를 갱신한다

의미 있는 구현이나 회의 반영 후에는 다음을 갱신한다.

- 완료된 작업
- 새로 생긴 파일/테이블/API
- 검증 결과
- 아직 미확정인 외부 의존성
- 다음 구현 순서
- 필요한 담당자 확인 사항

이 `AGENTS.md`를 업데이트할 때는 "현재 상태"와 "향후 계획"을 섞지 않고 구분한다.

---

## 18. 작업 시작 체크리스트

매 작업 시작 시 다음을 확인한다.

- [ ] 실제 프로젝트 경로가 `/Users/chosun-nhn02/Documents/버섯 프로젝트/Notification_service`인가?
- [ ] `AGENTS.md`와 관련 계획 문서를 읽었는가?
- [ ] `git status --short`로 기존 변경을 확인했는가?
- [ ] 이번 작업이 호준·서영 중 누구의 책임인지 확인했는가?
- [ ] 외부 담당자의 payload/API/RabbitMQ 정보가 필요한 작업인가?
- [ ] DB 변경이 필요하면 새 Migration 버전 파일을 만들 계획인가?
- [ ] 완료 기준과 검증 방법이 명확한가?

---

## 19. 작업 완료 체크리스트

코드·DB·문서 작업이 끝나면 다음을 확인한다.

- [ ] 요구사항과 최신 ERD·이벤트 정책에 맞는가?
- [ ] 기존 기능·Migration을 불필요하게 깨지 않았는가?
- [ ] 적절한 Build/Test/Migration 검증을 했는가?
- [ ] 실패했다면 원인과 미해결 상태를 명확히 기록했는가?
- [ ] 변경 파일별 역할을 요약했는가?
- [ ] 다른 담당자에게 전달하거나 받아야 할 계약이 있는가?
- [ ] 문서·AGENTS의 현재 상태를 갱신해야 하는가?
- [ ] 커밋·푸시·PR은 사용자의 요청 또는 승인 범위인가?

---

## 20. 다음 즉시 행동

현재 가장 합리적인 다음 행동 순서는 아래와 같다.

1. 서영님이 Endpoint·Subscription 구현을 시작하도록 이미 공유한 안내 문서를 기준으로 지원한다.
2. Producer 담당자들이 구현을 진행하는 동안 이벤트 payload·RabbitMQ 이름을 요청할 준비를 한다.
3. Repository를 구현한다.
4. 공통 `DomainEvent` DTO와 Consumer 뼈대를 구현한다.
5. 실제 RabbitMQ 계약을 반영한다.
6. 활성 Subscription 조회 → Notification·Delivery 생성까지 구현한다.
7. Template·Sender·Retry·통합 테스트를 구현한다.

현재 2026-07-27 준비 작업으로 `messaging/DomainEvent.java` 공통 이벤트 DTO와 `docs/이벤트계약_수집현황.md` 계약 수집표를 추가했다. 실제 Producer·RabbitMQ 정보가 확정되면 이 DTO를 기준으로 Consumer를 연결한다.

### 2026-07-27 이후 확정된 일정 기준

- **8월 3일:** RabbitMQ를 사용하는 담당자들이 모여 실제 exchange, queue, routing key, ACK/NACK 규칙과 공통 DTO를 확정한다. 그 전에는 임시 이름을 코드에 고정하지 않는다.
- **8월 3일 전까지:** 호준은 서영님 담당이었던 Endpoint·Subscription API 범위까지 포함해 DB 모델·Repository·구독 조회/알림 생성에 필요한 내부 구조를 단독으로 정리한다. 서영은 프론트 작업으로 바쁜 상태라 Notification 백엔드의 병렬 작업을 전제로 하지 않는다.
- **8월 13일:** 동건님이 Notification의 이벤트 DTO·RabbitMQ 작업에 합류한다. 이때부터 실제 Producer와의 연결·통합을 본격 진행한다.
- GitHub Project의 #11은 7/27~8/3, #12~#18은 기존 주차 흐름을 유지한다. #14(8/10~8/14)는 동건님 합류일을 포함한다.

추가 점검 결과, 활성 구독 중복 방지 인덱스가 일시정지(`enabled=false`) 구독까지 막고 있던 문제를 `V5__fix_active_subscription_unique_index.sql`에서 수정했다. 이제 `enabled=true AND is_deleted=false`인 구독만 중복 방지한다.

### 지금 당장 외부에 다시 물어볼 필요 없는 것

알림 이벤트 목록, 구독 정책, 지원 채널, 재시도 기본 정책, 담당 역할 분담은 이미 큰 방향이 정해져 있다.

### Consumer 구현 전에 반드시 필요한 것

- 실제 Producer payload JSON
- 실제 RabbitMQ exchange·queue·routing key
- `targetType`·`targetId` 최종 의미
- JWT claim 이름
- 재배 권한 확인 방법

---

## 21. 중요 참고 파일

| 파일 | 읽는 시점 |
|---|---|
| `AGENTS.md` | 모든 Notification 작업 시작 전 |
| `Notification_역할분담_및_개발실행계획.md` | 역할·이벤트·정책·구현 순서 확인 시 |
| `Notification_초보자_학습가이드.md` | 개념, DB, 코드 구조 학습 시 |
| `GROK_PROJECT_CONTEXT.md` | 다른 AI에게 맥락 전달 시 |
| `docs/서영님_Endpoint_Subscription_구현안내.md` | 서영님 작업과 연동 시 |
| `src/main/resources/db/migration/V1__create_notification_tables.sql` | DB 구조 확인 시 |
| `src/main/resources/db/migration/V2__seed_notification_reference_data.sql` | 이벤트·채널·템플릿 기준 확인 시 |
| `src/main/resources/application.yml` | 로컬 실행·DB·RabbitMQ 설정 확인 시 |

---

## 22. 최종 원칙

> 중요 작업은 이 스레드와 문서에 맥락을 축적한다.  
> 코드 변경은 항상 설계·계약·검증 기준과 연결한다.  
> 모르는 외부 계약은 추측으로 구현하지 않는다.  
> 완료는 "코드를 썼다"가 아니라 "요구사항에 맞게 검증되었다"이다.
