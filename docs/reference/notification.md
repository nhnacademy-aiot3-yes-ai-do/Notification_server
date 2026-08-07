# Notification Service

## 관련 문서

이 문서는 Notification Service의 전체 역할과 처리 흐름을 설명하는 상위 문서다.
세부 내용은 목적에 맞는 문서에서 확인한다.

| 확인할 내용 | 문서 |
|---|---|
| HTTP API 경로·요청·응답·오류 형식 | [`notification-api.md`](./notification-api.md) |
| Flyway 기준 테이블·제약조건·인덱스 | [`notification-db.md`](./notification-db.md) |
| Endpoint·Subscription 구현 절차 | [`Endpoint_Subscription_구현안내.md`](./Endpoint_Subscription_구현안내.md) |
| 코드 구조와 기술 학습 | [`Notification_Service_coad.md`](./Notification_Service_coad.md) |

API나 DB의 세부 정의가 이 문서와 다를 경우, API는 `notification-api.md`, 스키마는
`notification-db.md`를 우선 확인한다. 최종 DB 스키마의 기준은 Flyway Migration이다.

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
  → PENDING → SENDING 선점(조건부 DB UPDATE)
  → Telegram/Discord 발송
  → SENT 또는 FAILED
```

`SENDING`은 Consumer와 복구 스케줄러, 또는 서버 인스턴스 여러 개가 같은 Delivery를 동시에
발송하지 못하게 하는 내부 선점 상태다. `PENDING → SENDING`은 조건부 UPDATE로 한 작업만
성공한다. 이미 다른 작업이 선점했거나 완료한 Delivery는 발송을 건너뛴다.

DB 저장 직후 Consumer 프로세스가 중단되어 `PENDING` Delivery만 남는 경우를 대비해,
일정 시간 이상 오래된 미완료 Delivery를 스케줄러가 다시 발송한다. `SENDING` 상태에서
프로세스가 비정상 종료된 경우에는 별도 선점 만료 시간(기본 5분) 뒤 복구한다. 시도 횟수가
남아 있으면 `PENDING`으로 되돌리지만, 3회를 모두 소진한 상태라면 `FAILED`와 DLQ로 최종
처리한다. 같은 RabbitMQ 이벤트의 `source_event_id`는 UNIQUE이므로 원본 이벤트와 Delivery가
중복 생성되지는 않는다.

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
GET    /api/v1/notifications?page=0&size=20
```

DELETE는 소프트 삭제다. `enabled=false`는 일시정지이고 `is_deleted=true`는 삭제 처리다.
Endpoint DELETE는 해당 Endpoint의 비삭제 Subscription도 함께 소프트 삭제한다. Endpoint 응답의
Chat ID와 Discord Webhook은 외부로 그대로 반환하지 않고 마스킹한다.
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
복구 배치는 Delivery 한 건의 발송에서 오류가 나도 나머지 대상을 계속 처리한다.

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
- Cultivation·Inquiry 대상 소유권 확인 API와 공동 재배자 구독 범위
- Telegram·Discord 테스트 계정·Secret 전달 방식과 Provider 세부 방식
- Config 저장소·Kubernetes의 Notification Service 등록 값

## 2026-08-05 현재 구현·연동 상태

### 이미 정리된 범위

- Notification 교환기는 `yes-nhn.notification.exchange`를 **direct exchange**로 사용한다.
- Notification Consumer용 큐는 임계값, 제어 결과, 일일 피드백, 로그인, 문의/답변,
  수확, 재배 종료 흐름으로 분리한다.
- 실패 메시지는 `yes-nhn.dlx`와 `yes-nhn.dlq`로 보내고, 운영자가 확인 후 수동 처리한다.
- 서비스 내부에는 Endpoint·Subscription API, 이벤트 수신 뼈대, 템플릿/발송 이력 모델,
  중복 이벤트 방지와 최대 3회 재시도 정책이 구현되어 있다.

### 회의 후 실제 값으로 연결할 범위

- 각 큐의 최종 routing key, RabbitMQ vhost, ACK/NACK·재전달 규칙
- Rule·Cultivation·AI·Auth·Inquiry Producer가 보내는 최종 JSON 값과 필수 payload 필드
- Telegram Bot Token, Discord Webhook 등 Secret의 운영 전달·관리 방식
- 재배/문의 대상에 대한 소유권 확인 API와 가족 공동 재배자의 구독 가능 범위
- Gateway route, Config 저장소 allowlist, Kubernetes Deployment·Service 이름 및 환경 변수

즉, 현재는 Notification 내부 구조와 로컬 검증은 가능한 상태이며, 외부 서비스와의
실제 연결 값만 공통 회의 결과에 맞춰 바꾸면 된다.

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

모든 Controller는 `ResponseEntity`로 HTTP 상태를 명시한다. 생성은 `201 Created`, 조회·수정은
`200 OK`, 삭제는 `204 No Content`를 사용한다. 공통 예외는 `@RestControllerAdvice`와 Spring의
`ProblemDetail`로 응답하며, `title`, `detail`, `status`에 더해 `code`, `timestamp`, `path`를
제공한다. 사용자 ID 형식 오류, 소유 데이터 없음, 중복 리소스, 지원하지 않는 채널, 외부
Provider 실패를 서로 다른 HTTP 상태와 오류 코드로 구분한다. 팀 공통 오류 형식이 확정되면
이 확장 필드 이름만 맞춘다.

예외가 발생하면 운영 로그에는 이벤트·알림·발송을 추적할 수 있는 식별자와 재시도 정보를
남긴다. JWT·Webhook URL·Chat ID·토큰·민감한 payload 원문은 기록하지 않는다.

### Provider 가짜 서버 테스트와 운영 상태 확인

Telegram·Discord Provider는 실제 Bot Token이나 Webhook을 사용하지 않고
`MockRestServiceServer`로 HTTP 요청을 검증한다. Telegram은 HTTP 200이라도 응답의
`ok=false`이면 실패로 처리하며, Discord는 5xx 응답을 `NotificationProviderException`으로
감싼다. 따라서 채널 발송 코드의 요청 형식과 실패 분기를 실제 외부 서비스 없이 확인할 수
있다.

운영 환경에는 Actuator의 `/actuator/health`와 `/actuator/info`만 노출한다. Health 상세 정보는
공개하지 않아 DB·RabbitMQ 연결 정보가 응답에 섞이지 않도록 한다. 발송 로그에는
`deliveryId`, 채널, 시도 횟수, 실패 예외 종류만 남긴다. Provider 예외의 전체 stack trace나
원문 메시지를 로그에 기록하지 않아 Discord Webhook·Telegram Chat ID가 노출되지 않도록 한다.

### 로컬 인프라 연결 확인

로컬에서 PostgreSQL과 RabbitMQ를 함께 실행하면 Notification의 전체 저장·소비 흐름을
검증할 수 있다. PostgreSQL은 Endpoint, Subscription, Notification, Delivery와 발송 상태를
저장하고, RabbitMQ는 Rule·Cultivation·AI·Auth·Inquiry 서비스가 발행한 이벤트를 Notification
Consumer까지 전달한다.

```text
Producer → RabbitMQ Exchange/Queue → Notification Consumer
         → PostgreSQL 저장 → Delivery 생성·발송 상태 갱신
```

이 연결 자체가 홈페이지 화면을 자동으로 바꾸는 것은 아니다. 프론트가 Notification API를
호출하면 PostgreSQL에 저장된 Endpoint·구독·알림 이력이 화면에 표시되고, 다른 서비스가
실제 이벤트를 발행하면 RabbitMQ를 통해 새 알림이 생성된다. 따라서 화면에서 변화가 보이려면
프론트 API 연결과 실제 Producer 이벤트가 모두 필요하다.

현재 로컬 검증 환경은 다음과 같다.

- 애플리케이션 DB: `localhost:5432/notification_db`
- Migration 검증 DB: `localhost:55432/notification_migration_test`
- RabbitMQ: `localhost:5672`
- 통합 테스트: Docker PostgreSQL 연결 상태에서 Repository 6개 테스트, 실패 0건

## 2026-08-07 전체 검증 결과

원본 `develop` 브랜치에서 전체 검증을 다시 실행했다.

| 검증 항목 | 결과 |
|---|---|
| `mvn clean verify` 기본 테스트 | 77개 실행, 실패 0, 오류 0, 스킵 6개 |
| PostgreSQL Repository 통합 테스트 | 6개 실행, 실패 0, 오류 0 |
| Flyway Migration | V1~V10 검증·적용 성공, 검증 DB 버전 10 |
| Controller·Service·Consumer·Provider 테스트 | 전체 통과 |
| Telegram·Discord 가짜 HTTP Provider 테스트 | 통과 |

통합 테스트는 다음 Docker PostgreSQL 검증 DB에 연결해 실행했다.

```text
jdbc:postgresql://localhost:55432/notification_migration_test
```

기본 `mvn clean verify`에서는 외부 DB 의존성을 피하기 위해 Repository 통합 테스트가 스킵되며,
`notification.integration.enabled=true`를 지정하면 실제 PostgreSQL에서 실행된다. 이번에는
해당 플래그를 켜서 통합 테스트까지 별도로 통과시켰다.

이번 결과는 Notification 내부의 DB·HTTP·이벤트 처리 흐름이 정상임을 의미한다. 실제 팀 서비스와의
RabbitMQ 종단 간 연결, 최종 routing key와 Producer payload, 운영 Secret, Gateway·Kubernetes 설정은
공통 계약 확정 후 별도 검증이 필요하다. 따라서 외부 서비스까지 운영 환경에서 연결 완료되었다는
의미는 아니다.
