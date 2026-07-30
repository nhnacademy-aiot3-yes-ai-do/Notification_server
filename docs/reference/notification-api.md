# Notification API 초안

현재 공식 알림 채널은 Telegram·Discord이며 WebSocket은 지원하지 않는다.
최종 Base Path와 공통 오류 응답은 8월 3일 공동 회의에서 확정한다.
Auth Service 코드 확인 결과 JWT의 `sub` claim에 사용자 ID가 문자열로 들어간다.
Notification API에서는 `sub`를 Long으로 변환해 Endpoint·Subscription 소유권 확인에 사용한다.
현재 구현은 Spring Security가 이미 검증한 `Jwt`에서 사용자 ID를 읽는 단계다. JWT 서명·만료
검증을 수행하는 Resource Server 설정은 Auth·인프라 계약 확정 후 별도로 연결한다.

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
