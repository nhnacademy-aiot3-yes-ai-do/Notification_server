# Auth → Notification RabbitMQ 계약

Notification의 신규 Auth Consumer가 활성화되는 시점에 Auth 담당자가 따라야 할 wire 계약입니다.

## 전송 대상

- Exchange: `yes-nhn.notification.exchange`
- Routing key / Queue: `yes-nhn.notification.auth.queue`
- Content-Type: `application/json`
- Type header: `__TypeId__`

## 타입 헤더와 JSON payload

| `__TypeId__` | JSON 필드 |
| --- | --- |
| `user.login-attempted` | `eventId`(UUID string), `userId`(number), `nickname`(string), `succeeded`(boolean), `loginLocation`(string), `occurredAt`(ISO-8601 offset datetime) |
| `user.password-change-attempted` | `eventId`(UUID string), `userId`(number), `nickname`(string), `succeeded`(boolean), `occurredAt`(ISO-8601 offset datetime) |
| `user.account-reactivation-attempted` | `eventId`(UUID string), `userId`(number), `nickname`(string), `succeeded`(boolean), `occurredAt`(ISO-8601 offset datetime) |

`eventId`는 producer가 이벤트를 만들 때 한 번 생성한 UUID이며, 동일 이벤트를 재발행할 때도 반드시 같은 값을 사용해야 합니다. Notification은 이 값을 멱등성 키로 사용하므로 생략하거나 재발행마다 새 UUID를 만들면 안 됩니다.

## Login 발행 예시

Headers:

```text
contentType: application/json
__TypeId__: user.login-attempted
```

Body:

```json
{
  "eventId": "2c2fcd99-bbc3-4b85-ab4e-1df816b4f80b",
  "userId": 1,
  "nickname": "tester",
  "succeeded": true,
  "loginLocation": "Seoul",
  "occurredAt": "2026-08-11T12:00:00+09:00"
}
```

Notification은 type-id를 임의 Java class name이 아닌 위 alias로만 신뢰합니다.
