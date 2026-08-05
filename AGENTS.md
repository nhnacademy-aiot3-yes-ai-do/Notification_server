# Notification Service 개발 규칙

## 서비스 책임

Notification Service는 다른 서비스가 RabbitMQ로 발행한 이벤트를 소비해 사용자의 구독과
수신 경로를 조회하고, Telegram 또는 Discord로 알림을 발송한 뒤 결과를 저장한다.

- 환경 이상 판단은 Rule Service가 담당한다.
- 재배·수확 정보는 Cultivation Service가 담당한다.
- 사용자 인증과 JWT 발급은 Auth Service가 담당한다.
- Notification Service는 다른 서비스의 DB를 직접 조회하지 않는다.
- 외부 서비스의 ID는 FK가 아닌 숫자 ID로 보관한다.

## 확정 정책

- 지원 채널은 Telegram과 Discord다.
- WebSocket 알림은 지원하지 않는다.
- `enabled=false`는 일시정지, `is_deleted=true`는 소프트 삭제다.
- 삭제되지 않은 같은 구독 조합은 하나만 유지하고, 재구독 시 기존 구독을 활성화한다.
- 장치 ON/OFF 제어는 성공과 실패를 모두 알림으로 발송한다. 같은 제어 결과 이벤트는 한
  번만 발행하며 `source_event_id`로 중복 발송을 막는다.
- 발송은 `NotificationDelivery.MAX_ATTEMPT_COUNT` 기준으로 최대 3회 시도하며 최종 결과는 `SENT` 또는 `FAILED`로 저장한다. 환경 설정으로는 backoff만 조절한다.
- `notification.source_event_id`로 중복 이벤트를 방지한다. 동시 Consumer의 UNIQUE 충돌은 이미 저장된 이벤트가 확인되면 정상 중복으로 처리한다.
- Gateway가 JWT를 검증하고 전달한 `X-User-Id` 헤더를 사용자 ID로 사용한다.

## RabbitMQ 연동 계약

- Notification 계열 이벤트는 Durable Direct Exchange인
  `yes-nhn.notification.exchange`로 받는다.
- Notification은 다음 Queue를 선언하고 동일 Exchange에 Binding한다.
  - `yes-nhn.notification.threshold.queue`
  - `yes-nhn.notification.action.queue`
  - `yes-nhn.notification.daily.queue`
  - `yes-nhn.notification.login.queue`
  - `yes-nhn.notification.question.queue`
  - `yes-nhn.notification.answer.queue`
  - `yes-nhn.notification.harvest.queue`
  - `yes-nhn.notification.cultivation-finished.queue`
- 운영 Routing Key는 아직 팀 합의 전이다. `application.yml`의 Queue명과 같은 기본
  Routing Key는 로컬 실행용이며 운영 계약으로 간주하지 않는다.
- 공용 Dead Letter Exchange와 Queue는 각각 `yes-nhn.dlx`, `yes-nhn.dlq`를 사용한다.
- Notification은 공용 DLQ를 자동 소비하지 않는다. 관리자가 RabbitMQ Management UI에서
  원인을 확인한 뒤 메시지를 수동 처리·삭제한다.
- 센서 계열의 `yes-nhn.sensor.exchange`만 여러 Consumer에게 같은 데이터를 전달하기 위해
  Topic Exchange를 사용한다. Notification과 Harvest 계열은 Direct Exchange를 사용한다.

## 코드 구조

- 기본 Java 패키지는 `site.yesaido.notification_server`를 사용한다.
- Spring Application과 중앙 배포 서비스명은 `notification-server`를 사용한다.
- Controller는 HTTP 요청·응답과 입력 검증을 담당한다.
- Service는 트랜잭션과 도메인 규칙을 담당한다.
- Repository는 데이터 조회·저장에 집중한다.
- RabbitMQ Consumer는 역직렬화와 Service 호출만 담당한다.
- Telegram·Discord 연동은 채널별 Provider로 분리한다.
- Entity를 API 요청·응답이나 메시지 계약에 직접 노출하지 않는다.
- Entity 상태는 Setter가 아니라 의미 있는 도메인 메서드로 변경한다.
- 연관관계는 특별한 이유가 없으면 LAZY를 사용한다.

## DB 변경

- 이미 적용된 Flyway Migration은 수정하지 않는다.
- DB 변경은 다음 버전의 Migration으로 추가한다.
- FK, UNIQUE, CHECK, INDEX가 필요한지 함께 검토한다.
- 기준 Seed는 재실행 시 중복되거나 의도치 않게 증가하지 않아야 한다.
- Migration 추가 후 PostgreSQL에서 Flyway와 JPA validate를 확인한다.

## 보안과 예외

- JWT 검증은 API Gateway가 담당하며 Notification Service는 JWT를 다시 해석하지 않는다.
- Controller는 Gateway가 전달한 `X-User-Id`를 받아 본인 소유 Endpoint·Subscription만 처리한다.
- 외부 요청이 Notification Service에 직접 접근하지 못하도록 배포 경계를 구성해야 한다.
- Gateway는 클라이언트가 보낸 `X-User-Id`를 신뢰하지 않고, 검증한 JWT의 `sub` 값으로 덮어써야 한다.
- 비밀번호, 토큰, Telegram Chat ID, Discord Webhook 전체 값을 로그에 남기지 않는다.
- 도메인 규칙 위반은 의미가 드러나는 전용 예외로 표현한다.
- Controller 구현 시 `@RestControllerAdvice`에서 공통 오류 응답으로 변환한다.
- Repository의 제약조건 예외만 사용자에게 그대로 노출하지 않는다.

## 예외와 운영 로그

- 예외를 삼키지 말고, 처리하거나 다시 던질 때 원인과 처리 결과를 남긴다.
- 이벤트 처리 로그에는 `eventId`, `eventType`, `targetType`, `targetId`를 기록한다.
- 발송 로그에는 `notificationId`, `deliveryId`, 채널, 시도 횟수, 재시도 여부와 실패 원인을 기록한다.
- 중복 이벤트·구독 없음은 오류가 아닌 정상 분기이므로 `DEBUG` 또는 필요한 수준의 `INFO`로 남긴다.
- Consumer 실패는 계약 오류, 템플릿·기준 설정 오류, 영속화 오류, 시스템 오류로 구분해 남긴다.
- 외부 Provider 실패, 재시도, 최종 실패, DLQ 이동은 `WARN` 또는 `ERROR`로 남긴다.
- JWT·비밀번호·Webhook URL·Chat ID·access token·refresh token·민감 payload 원문은 로그에 남기지 않는다.
- stack trace가 필요한 경우 원인 예외를 함께 전달하되, 사용자 응답에는 내부 정보를 노출하지 않는다.

## 테스트 기준

기능 변경 후 최소한 다음을 확인한다.

```text
mvn clean verify
```

DB 또는 Repository 변경이 있으면 Docker PostgreSQL 통합 테스트도 실행한다.

```text
mvn \
  -Dnotification.integration.enabled=true \
  -Dnotification.integration.db-url=jdbc:postgresql://localhost:55432/notification_migration_test \
  -Dnotification.integration.db-username=postgres \
  -Dnotification.integration.db-password=postgres \
  test
```

- 정상 경로뿐 아니라 중복, 권한 오류, 삭제 데이터, 잘못된 상태 전이를 테스트한다.
- 테스트를 건너뛰었다면 완료로 보고하지 않고 이유를 남긴다.
- CI와 로컬 테스트가 같은 조건으로 실행되는지 확인한다.

## Git 협업

- 작업 전 `git status --short`로 기존 변경을 확인한다.
- `.idea`, `target`, `.env`, 개인 학습자료는 커밋하지 않는다.
- 기능별 변경 범위를 작게 유지한다.
- `develop`이나 `main`에 직접 push하지 않는다.
- 사용자 확인 전에는 `git add`, commit, push, PR 생성, merge를 수행하지 않는다.
- 커밋 전 변경 파일, 테스트 결과, 미확정 외부 계약을 먼저 보고한다.

## 아직 외부 합의가 필요한 항목

- RabbitMQ routing key, vhost, ACK/NACK와 Consumer 재시도 세부 방식
- Producer별 실제 이벤트 JSON payload
- Config allowlist와 Kubernetes Deployment의 최종 서비스명
- Gateway의 `X-User-Id` 전달 및 외부 직접 접근 차단 방식
- 최종 API Base Path와 공통 오류 응답 형식

미확정 값을 운영 코드에 임의로 고정하지 않는다.
