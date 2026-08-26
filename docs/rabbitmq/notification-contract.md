# Notification RabbitMQ 계약

## 공통

| 항목 | 값 |
|---|---|
| Exchange | `yes-nhn.notification.exchange` |
| Exchange 타입 | durable direct |
| Dead Letter Exchange | `yes-nhn.dlx` |
| Dead Letter Queue | `yes-nhn.dlq` |

Notification은 아래 큐를 선언하고 공통 Notification exchange에 바인딩합니다.
운영 routing key는 아직 팀 합의 전이므로, 큐 이름을 routing key로 사용하는 값은 로컬 기본값일 뿐 운영 계약으로 확정하지 않습니다.

| 출처 | Queue | DTO/이벤트 |
|---|---|---|
| Rule Engine | `yes-nhn.notification.threshold.queue` | `ThresholdStatusChangedEvent` |
| Rule Engine | `yes-nhn.notification.action.queue` | `AutomationStateChangedEvent` |
| AI | `yes-nhn.notification.daily.queue` | `DailyFeedbackGeneratedEvent` |
| AI | `yes-nhn.notification.cultivation-complete.queue` | `CultivationCompletedEvent` |
| Cultivation | `yes-nhn.notification.harvest.queue` | `HarvestCompletedEvent` |
| Cultivation | `yes-nhn.notification.sensor.queue` | `SensorDataUnavailableEvent` |
| Cultivation | `yes-nhn.notification.member.queue` | `CultivationMemberInvitedEvent` |
| User | `yes-nhn.notification.auth.queue` | 인증 이벤트 |
| User | `yes-nhn.notification.inquiry.queue` | `InquirySubmittedEvent` |

## Consumer 처리

- 역직렬화와 이벤트 처리에 성공하면 해당 delivery tag를 ACK합니다.
- 계약 오류, 처리 오류, 영속화 오류가 발생하면 오류 원인을 로그로 남기고 NACK합니다.
- 현재 listener 설정은 재배달하지 않고(`requeue=false`) DLX로 보내는 정책입니다.
- 중복 이벤트와 구독 없음은 정상 분기이며 오류로 기록하지 않습니다.
- DLQ는 Notification이 자동 소비하지 않으며, 관리자가 Management UI에서 원인을 확인합니다.

## 이벤트 문맥

이벤트 payload의 `eventId`, `occurredAt`, `targetId`(재배지 대상인 경우)는 Notification 저장 payload에 보존합니다.
외부 Webhook URL, 토큰, 민감한 payload 원문은 로그에 남기지 않습니다.

## 미확정 항목

- 운영 routing key와 vhost
- Producer별 최종 JSON 필드 및 헤더
- 재시도 횟수와 DLQ 재처리 절차

위 값은 각 Producer 담당자와 합의한 뒤 운영 설정에 반영합니다.
