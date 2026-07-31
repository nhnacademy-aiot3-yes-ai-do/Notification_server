# Notification API

현재 공식 알림 채널은 Telegram·Discord이며 WebSocket은 지원하지 않는다.
현재 코드의 Base Path와 공통 오류 응답은 아래와 같다. 팀 공통 계약이 바뀌면
Controller와 Gateway Route를 함께 변경한다.
API Gateway가 JWT의 서명·만료를 검증하고 `sub` claim의 사용자 ID를
`X-User-Id` 요청 헤더로 전달한다. Notification API는 JWT를 다시 해석하지 않고 이 헤더를
Endpoint·Subscription 소유권 확인에 사용한다.

이 방식은 Notification Service가 외부에 직접 노출되지 않고, 모든 사용자 요청이 Gateway를
통과한다는 전제를 가진다. Gateway는 클라이언트가 임의로 보낸 `X-User-Id`를 제거하거나
검증한 JWT의 값으로 덮어써야 한다.

## Endpoint 관리

```text
POST   /api/v1/notification-endpoints
GET    /api/v1/notification-endpoints
PATCH  /api/v1/notification-endpoints/{endpointId}
PATCH  /api/v1/notification-endpoints/{endpointId}/enabled
DELETE /api/v1/notification-endpoints/{endpointId}
```

Endpoint는 Telegram Chat ID 또는 Discord Webhook을 저장한다.
`enabled=false`는 일시정지이고, DELETE는 `is_deleted=true` 소프트 삭제다.

## Subscription 관리

```text
POST   /api/v1/notification-subscriptions
GET    /api/v1/notification-subscriptions
PATCH  /api/v1/notification-subscriptions/{subscriptionId}/enabled
DELETE /api/v1/notification-subscriptions/{subscriptionId}
```

구독 생성 시 이벤트·대상·Endpoint를 연결한다. 동일한 비삭제 구독이 일시정지 상태라면
새 행을 만들지 않고 기존 구독을 다시 활성화한다.

## 구독 종류·알림 이력

```text
GET /api/v1/notification-subscription-types
GET /api/v1/notifications
```

## 요청 예시

```json
{
  "channelTypeId": 1,
  "destination": "123456789",
  "displayName": "내 Telegram"
}
```

## 공통 오류 응답

요청 사용자 소유의 데이터만 조회·수정할 수 있다.

```json
{
  "timestamp": "2026-07-31T10:00:00+09:00",
  "status": 404,
  "code": "NOTIFICATION_RESOURCE_NOT_FOUND",
  "message": "알림 수신 경로를 찾을 수 없습니다.",
  "path": "/api/v1/notification-endpoints/1"
}
```

| HTTP | 코드 | 의미 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 필수값·형식 오류 |
| 400 | `UNSUPPORTED_NOTIFICATION_CHANNEL` | 지원하지 않는 채널 조합 |
| 404 | `NOTIFICATION_RESOURCE_NOT_FOUND` | 없거나 사용자 소유가 아닌 리소스 |
| 409 | `NOTIFICATION_RESOURCE_CONFLICT` | Endpoint·구독 중복 |
| 502 | `NOTIFICATION_PROVIDER_FAILURE` | 외부 채널 요청 실패 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 |
