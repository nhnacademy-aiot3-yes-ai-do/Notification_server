# Notification API 초안

현재 공식 알림 채널은 Telegram·Discord이며 WebSocket은 지원하지 않는다.
최종 Base Path, JWT claim, 공통 오류 응답은 8월 3일 공동 회의에서 확정한다.

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

구독 생성 시 이벤트·대상·Endpoint를 연결한다. 동일한 활성 구독은 중복 생성하지 않는다.

## 요청 예시

```json
{
  "channelTypeId": 1,
  "destination": "123456789",
  "displayName": "내 Telegram"
}
```

## 응답·오류

요청 사용자 소유의 데이터만 조회·수정할 수 있다. 오류 코드와 공통 JSON 형식은 팀 계약 확정 후
적용하며, 현재는 API 초안으로 관리한다.
