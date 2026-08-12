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
| `user.login-attempted` | `userId`(number), `nickname`(string), `succeeded`(boolean), `loginLocation`(string), `occurredAt`(ISO-8601 offset datetime) |
| `user.password-change-attempted` | `userId`(number), `nickname`(string), `succeeded`(boolean), `occurredAt`(ISO-8601 offset datetime) |
| `user.account-reactivation-attempted` | `userId`(number), `nickname`(string), `succeeded`(boolean), `occurredAt`(ISO-8601 offset datetime) |

## Login 발행 예시

Headers:

```text
contentType: application/json
__TypeId__: user.login-attempted
```

Body:

```json
{
  "userId": 1,
  "nickname": "tester",
  "succeeded": true,
  "loginLocation": "Seoul",
  "occurredAt": "2026-08-11T12:00:00+09:00"
}
```

Notification은 type-id를 임의 Java class name이 아닌 위 alias로만 신뢰합니다.
