# Notification Database (최신 설계)

Notification Service는 알림 원본, 수신 경로, 구독 설정, 채널별 발송 결과를 분리해 관리한다.
현재 공식 발송 채널은 **Telegram과 Discord**이며 WebSocket은 지원하지 않는다.

## 테이블 구성

| 테이블 | 역할 |
|---|---|
| `notification` | RabbitMQ 이벤트에서 생성한 알림 원본 |
| `notification_delivery` | 채널별 실제 발송 결과와 재시도 이력 |
| `notification_endpoint` | 사용자의 Telegram Chat ID 또는 Discord Webhook |
| `notification_subscription` | 사용자가 어떤 이벤트를 받을지에 대한 설정 |
| `notification_event_type` | 이벤트 코드와 대상 유형 |
| `notification_subscription_type` | 이벤트·대상 조합별 구독 종류 |
| `subscription_target_type` | CULTIVATION·INQUIRY·USER 등의 대상 종류 |
| `channel_type` | TELEGRAM·DISCORD 채널 종류 |
| `subscription_channel` | 구독 종류와 발송 채널 연결 |
| `notification_template` | 이벤트·채널별 메시지 템플릿 |

## 핵심 관계

```text
notification (알림 원본 1개)
    └── notification_delivery (채널별 발송 여러 개)
             ├── notification_subscription
             └── notification_template

notification_endpoint (사용자 수신 경로)
    └── notification_subscription (이벤트 구독)
```

하나의 센서 오류 이벤트가 발생하면 `notification`은 한 건 생성되고,
Telegram과 Discord로 각각 발송될 경우 `notification_delivery`가 채널별로 생성된다.
따라서 Telegram은 성공하고 Discord만 실패하는 상황도 독립적으로 기록할 수 있다.

## 삭제·상태 정책

- Endpoint의 `enabled=false`: 일시정지
- Endpoint의 `is_deleted=true`: 소프트 삭제. 기본 목록에서 제외
- Subscription도 `enabled`와 `is_deleted`를 같은 의미로 구분
- Delivery `status`: `PENDING`, `SENT`, `FAILED`
- 발송 실패는 최대 3회 재시도한 뒤 실패 이력을 저장

## 기준 이벤트

- `ENVIRONMENT_THRESHOLD_BREACHED`
- `ENVIRONMENT_RECOVERED`
- `SENSOR_OFFLINE`
- `SENSOR_ERROR`
- `ACTUATOR_CONTROL_FAILED`
- `HARVEST_COMPLETED`
- `CULTIVATION_FINISHED`
- `DAILY_FEEDBACK_COMPLETED`
- `LOGIN_SUCCEEDED`
- `INQUIRY_ANSWERED`

주간·월간 리포트 알림은 사용하지 않고 `DAILY_FEEDBACK_COMPLETED`만 사용한다.

## Migration·Seed

현재 Migration은 `V1`부터 `V5`까지이며, 기준 Seed는 채널·이벤트·대상·구독 유형·템플릿을
멱등적으로 삽입한다. 자세한 실행 결과는
`docs/reference/2026-07-28_검증결과_및_회의준비.md`를 참고한다.
