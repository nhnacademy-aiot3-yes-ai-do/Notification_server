# Notification Service 핵심 용어·구조 초보자 가이드

이 문서는 Notification Service를 처음 보는 팀원이 이벤트, RabbitMQ, 알림 발송, DB 구조를 한 번에 이해할 수 있도록 정리한 학습 자료다.

## 1. 전체 흐름

우리 서비스는 센서·재배·AI·인증 등 다른 서비스에서 발생한 사건을 받아 사용자가 설정한 채널로 알림을 보낸다.

```text
Producer 서비스
  → RabbitMQ 이벤트 메시지
  → Notification Consumer
  → 이벤트 검증·구독 조회
  → Notification(알림 원본) 저장
  → 채널별 NotificationDelivery 생성
  → 템플릿 렌더링
  → Telegram/Discord Sender 호출
  → 성공·실패·재시도 이력 저장
```

핵심은 “알림을 판단하는 서비스”와 “알림을 실제로 전달하는 서비스”를 분리하는 것이다. Rule Engine이나 AI가 원인을 판단하고, Notification Service는 전달을 책임진다.

## 2. `ENVIRONMENT_THRESHOLD_BREACHED`

이벤트 코드이며, 뜻은 “재배 환경 측정값이 정해진 임계 범위를 벗어났다”이다. 예를 들어 느타리 생육 온도 기준이 18~22℃인데 센서가 25℃를 측정하면 발생한다.

```json
{
  "eventType": "ENVIRONMENT_THRESHOLD_BREACHED",
  "targetType": "CULTIVATION",
  "targetId": 101,
  "payload": {
    "sensorType": "TEMPERATURE",
    "value": 25.0,
    "min": 18.0,
    "max": 22.0,
    "unit": "C"
  }
}
```

Notification Service가 온도 기준을 계산하는 것이 아니라 Rule Engine이 기준을 비교한 뒤 이 이벤트를 발행한다. Notification은 이 메시지를 받아 구독자에게 “온도가 기준보다 높습니다”라고 전달한다.

## 3. `HARVEST_COMPLETED`

“재배 종류”가 아니라 “수확이 완료되었다”는 사건이다. 재배 종류는 `mushroom_name` 또는 cultivation의 품종 정보이고, 수확 완료 이벤트는 특정 재배 건의 생명주기에서 발생하는 상태 변화다.

```json
{
  "eventType": "HARVEST_COMPLETED",
  "targetType": "CULTIVATION",
  "targetId": 101,
  "payload": {
    "harvestId": 55,
    "quantity": 1200,
    "unit": "g",
    "harvestedAt": "2026-07-30T10:00:00+09:00"
  }
}
```

`targetId`가 어떤 재배 건인지 가리키고, payload 안의 `harvestId`·수확량은 알림 문구에 사용할 상세 데이터다.

## 4. RabbitMQ 공통 이벤트 계약

이벤트 계약은 Producer와 Consumer가 “메시지의 모양과 의미”를 미리 약속한 문서다. 한쪽이 필드명을 임의로 바꾸면 Consumer가 메시지를 읽지 못하므로 JSON 필드, 타입, 필수 여부, ID 의미를 함께 고정한다.

공통 envelope 예시는 다음과 같다.

```json
{
  "eventId": "2e7c2e2e-6c0a-4c9d-a1ad-123456789abc",
  "eventType": "ENVIRONMENT_THRESHOLD_BREACHED",
  "occurredAt": "2026-07-30T10:00:00Z",
  "producer": "rule-engine",
  "targetType": "CULTIVATION",
  "targetId": 101,
  "payload": {}
}
```

RabbitMQ에서 exchange는 메시지를 분류하고, routing key는 어떤 queue로 보낼지 결정하며, queue는 Consumer가 읽는 대기열이다. Notification은 이벤트를 직접 polling하지 않고 queue를 구독한다.

## 5. Producer와 payload

Producer는 이벤트를 만들어 RabbitMQ로 보내는 주체다. Rule Engine이 환경 이상 이벤트를 만들고, Cultivation Service가 수확 완료 이벤트를 만드는 식이다. Payload는 envelope 안에서 해당 사건의 상세 정보를 담는 JSON 부분이다.

| Producer | 대표 이벤트 | payload 예시 |
|---|---|---|
| Rule Engine | 환경 이상, 센서 오류·오프라인, 제어 실패 | 센서 종류, 측정값, 기준값, 오류 원인 |
| Cultivation Service | 수확 완료, 재배 종료 | harvestId, 수확량, 재배 종료 시각 |
| AI Service | AI 일일 피드백 완료 | feedbackId, 요약, 생성 시각 |
| Auth Service | 로그인 성공 | userId, 로그인 시각, 로그인 방식 |
| Inquiry Service | 문의 답변 완료 | inquiryId, 답변자, 답변 요약 |

Producer별 실제 exchange·queue·routing key와 payload는 3일 회의에서 최종 합의해야 한다. 지금은 Notification이 임의로 추측해 구현하면 안 된다.

## 6. `targetType`과 `targetId`

`targetType`은 이벤트가 어떤 종류의 대상을 가리키는지 나타낸다. `targetId`는 그 대상의 실제 식별자다.

```text
targetType = CULTIVATION
targetId   = 101
→ 101번 재배 건에 대한 이벤트
```

따라서 “`targetId=cultivation_id`인가?”라는 질문은 이벤트의 대상 ID에 cultivation 테이블의 PK를 넣을 것인지 묻는 것이다. 환경 이상, 수확 완료, 재배 종료는 특정 재배 건에 귀속되므로 `targetType=CULTIVATION`, `targetId=cultivation_id`로 정한다. 문의는 `targetType=INQUIRY`, `targetId=inquiry_id`가 된다.

로그인 성공처럼 특정 재배 건이 아닌 이벤트는 `targetType=USER`, `targetId=user_id`로 정할 수 있다.

## 7. “구현됨”과 “Seed됨”의 차이

구현은 애플리케이션 코드가 동작하도록 만드는 것이다. 예를 들어 Entity, Repository, Service, Consumer, Sender를 작성하는 것이 구현이다.

Seed는 실행에 필요한 기준 데이터를 DB에 미리 넣는 것이다. 예를 들어 `TELEGRAM`, `DISCORD`, `CULTIVATION`, `ENVIRONMENT_THRESHOLD_BREACHED` 같은 행을 INSERT한다.

Seed만 있으면 데이터는 있지만 처리 로직이 없고, 코드만 있으면 참조할 기준 행이 없어 실행 중 FK 오류나 조회 실패가 날 수 있다. 둘은 서로 보완 관계다.

## 8. Consumer·Sender·API 미구현의 의미

### Consumer 미구현

RabbitMQ queue에서 메시지를 실제로 읽고 검증하는 코드가 아직 없다는 뜻이다. 지금은 이벤트 계약과 DB 기반을 준비한 단계다.

### Sender 미구현

Telegram Bot API나 Discord Webhook을 호출해 실제 외부 채널로 전송하는 코드가 없다는 뜻이다. 발송 이력 테이블만 있어도 외부 사용자에게 알림이 전달되는 것은 아니다.

### API 미구현

프론트가 호출할 HTTP Controller가 아직 없다는 뜻이다. Endpoint 등록·삭제, 구독 생성·중지, 알림 목록 조회 같은 기능이 여기에 해당한다.

## 9. Template Renderer와 Telegram Sender

Template Renderer는 템플릿과 payload를 합쳐 최종 문자열을 만드는 역할이다.

```text
템플릿: "온도 {{value}}{{unit}}. 기준 범위 {{min}}~{{max}}{{unit}}"
payload: value=25, unit=℃, min=18, max=22
결과: "온도 25℃. 기준 범위 18~22℃"
```

Telegram Sender는 완성된 문자열을 Telegram Bot API에 전송하는 역할이다. Renderer는 문장 생성, Sender는 네트워크 전송이므로 분리해야 한다. 같은 템플릿 데이터라도 Telegram은 chat ID, Discord는 webhook URL을 사용한다.

## 10. 이벤트 Producer가 확정됐다는 뜻

각 이벤트를 누가 발행할지 결정했다는 뜻이다.

```text
환경 이상       → Rule Engine
수확 완료       → Cultivation Service
일일 피드백 완료 → AI Service
로그인 성공     → Auth Service
문의 답변 완료  → Inquiry Service
```

이 결정이 있어야 Notification 팀이 “어느 exchange를 구독할지”, “payload를 누구에게 요청할지”, “targetId를 무엇으로 해석할지” 정할 수 있다.

## 11. `eventType`, `targetType`, `targetId`

- `eventType`: 무슨 일이 발생했는가. 예: `HARVEST_COMPLETED`
- `targetType`: 그 일이 어떤 대상에 관한 것인가. 예: `CULTIVATION`
- `targetId`: 그 대상의 실제 ID. 예: `101`

세 필드를 합치면 “101번 재배 건에서 수확 완료가 발생했다”가 된다. `eventType`만 있으면 어느 대상인지 알 수 없고, `targetId`만 있으면 무슨 사건인지 알 수 없다.

## 12. Repository를 먼저 만드는 이유

Repository는 DB에 저장·조회하는 통로다. Spring Data JPA Repository를 사용하면 SQL을 직접 반복 작성하지 않고 Entity 조회 메서드를 정의할 수 있다.

```java
public interface NotificationEndpointRepository
        extends JpaRepository<NotificationEndpoint, Long> {
    List<NotificationEndpoint> findByUserIdAndEnabledTrueAndDeletedFalse(Long userId);
}
```

Repository를 먼저 만들면 Service가 사용할 DB 계약이 생긴다. 그 다음 Service는 “중복 확인 → 저장 → 조회” 같은 업무 규칙에 집중하고, Controller는 HTTP 입력·응답에 집중할 수 있다.

권장 계층은 다음과 같다.

```text
Controller: HTTP 요청/응답
  ↓
Service: 업무 규칙·트랜잭션
  ↓
Repository: DB 조회·저장
  ↓
PostgreSQL
```

## 13. 현재 프로젝트에서 이미 된 것과 남은 것

현재 완료된 기반 작업은 Migration V1~V6, Notification 관련 Entity, Repository, 기준 Seed, Repository 통합 테스트, Docker PostgreSQL 검증, 예외·재시도 정책, JWT 사용자 ID 추출 보조 코드다.

공통 이벤트의 최소 역직렬화·검증 기반도 추가되어 있다. `DomainEventParser`는 RabbitMQ에서 받을 문자열 JSON을 `DomainEvent`로 변환하고, `eventId`, `eventType`, `producer`, `targetType`, `targetId`, `occurredAt`, `payload`의 필수 여부를 확인한다. 잘못된 JSON이나 양수가 아닌 `targetId`는 `InvalidDomainEventException`으로 거부한다.

Parser 테스트는 임시 `HARVEST_COMPLETED` JSON을 정상 변환하는지와 필수값 누락·잘못된 JSON을 거부하는지를 확인한다. 아직 회의에서 확정하지 않은 이벤트 코드와 Producer별 payload 필드는 Parser에 고정하지 않았다. 따라서 이 코드는 Consumer 전체가 아니라 나중에 RabbitMQ Listener가 재사용할 수 있는 공통 입력 검증 단계다.

아직 외부 계약 확정 전이라 본격적으로 만들지 않은 것은 RabbitMQ Consumer, 실제 Telegram/Discord Sender, Controller/API, Producer별 최종 payload 연결이다. 3일 회의에서 exchange·queue·routing key, payload, JWT claim, 권한 API를 확정한 뒤 구현한다.

## 14. 개발 순서 요약

1. Producer와 RabbitMQ 이벤트 계약 확정
2. Consumer가 envelope과 payload를 검증하도록 구현
3. 구독·Endpoint를 조회해 수신 대상 결정
4. Notification과 channel별 Delivery 생성
5. Renderer로 메시지 생성
6. Sender로 Telegram/Discord 전송
7. 성공·실패·재시도와 구조화 로그 저장
8. Controller/API와 통합 테스트 구현

이 순서를 지키면 메시지 형식이 바뀌어 DB와 발송 코드가 다시 깨지는 일을 줄일 수 있다.

## 15. 한 문장으로 정리

Producer가 “무슨 일이 발생했다”는 공통 JSON 이벤트를 RabbitMQ로 보내면, Notification Service의 Consumer가 이를 받아 `eventType·targetType·targetId`를 해석하고, 구독과 템플릿을 조회해 채널별 Delivery를 만든 뒤 Renderer와 Sender를 통해 실제 알림을 발송한다.
