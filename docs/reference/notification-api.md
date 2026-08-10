# Notification API

현재 공식 알림 채널은 Telegram·Discord이며 WebSocket은 지원하지 않는다.
현재 코드의 Base Path와 공통 오류 응답은 아래와 같다. 팀 공통 계약이 바뀌면
Controller와 Gateway Route를 함께 변경한다.

## 프론트 연동 전제

- Base Path는 `/api/v1`이다.
- 본 문서의 API는 Gateway를 거쳐 호출한다. 프론트는 `X-User-Id`를 직접 만들거나 저장하지
  않고, 평소처럼 인증 정보를 Gateway로 보낸다.
- Gateway가 JWT의 서명·만료를 검증한 뒤 `sub` claim의 사용자 ID를 `X-User-Id` 요청 헤더로
  추가한다. Notification API는 이 헤더를 Endpoint·Subscription 소유권 확인에 사용한다.
- `GET /api/v1/notification-subscription-types`만 사용자별 데이터가 아니므로
  `X-User-Id`가 필요 없다. 나머지 API는 모두 필요하다.
- JSON 요청에는 `Content-Type: application/json`을 사용한다. 생성·수정·조회 성공 응답은
  `application/json`, 오류 응답은 Spring 표준 `application/problem+json` 형식이다.

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
DELETE 시 해당 Endpoint에 연결된 비삭제 Subscription도 함께 소프트 삭제한다. Endpoint 조회·생성·수정
응답의 `destination`은 Chat ID나 Webhook Token을 노출하지 않도록 마스킹한다.

### Endpoint 요청·응답

```http
POST /api/v1/notification-endpoints HTTP/1.1
Content-Type: application/json

{
  "channelTypeId": 1,
  "destination": "123456789",
  "displayName": "내 Telegram"
}
```

성공하면 `201 Created`와 `Location: /api/v1/notification-endpoints/{id}`를 반환한다.
응답의 `destination`은 보안상 마스킹되므로, 수정 화면에서 원문을 다시 표시하거나 재사용하면 안 된다.

```json
{
  "id": 10,
  "channelTypeId": 1,
  "channelCode": "TELEGRAM",
  "channelName": "Telegram",
  "destination": "*****6789",
  "displayName": "내 Telegram",
  "enabled": true,
  "createdAt": "2026-08-06T13:00:00",
  "updatedAt": "2026-08-06T13:00:00"
}
```

`PATCH /{endpointId}`는 `destination`, `displayName`을 모두 받는다.
`PATCH /{endpointId}/enabled`는 아래처럼 일시정지 여부만 바꾼다.

```json
{ "enabled": false }
```

## Subscription 관리

```text
POST   /api/v1/notification-subscriptions
GET    /api/v1/notification-subscriptions
PATCH  /api/v1/notification-subscriptions/{subscriptionId}/enabled
DELETE /api/v1/notification-subscriptions/{subscriptionId}
```

구독 생성 시 이벤트·대상·Endpoint를 연결한다. 동일한 비삭제 구독이 일시정지 상태라면
새 행을 만들지 않고 기존 구독을 다시 활성화한다.

### 구독 생성 요청·응답

`subscriptionTypeId`는 프론트가 아래의 “구독 종류 조회” API에서 받은 값을 사용한다.
`targetId`는 Notification DB의 ID가 아니라 재배·문의·사용자 서비스가 가진 원래 대상 ID다.

```http
POST /api/v1/notification-subscriptions HTTP/1.1
Content-Type: application/json

{
  "subscriptionTypeId": 5,
  "endpointId": 10,
  "targetId": 101
}
```

성공하면 `201 Created`를 반환한다.

```json
{
  "id": 20,
  "subscriptionTypeId": 5,
  "subscriptionName": "센서 오류 알림",
  "eventType": "SENSOR_ERROR",
  "targetType": "CULTIVATION",
  "targetId": 101,
  "endpointId": 10,
  "channelCode": "TELEGRAM",
  "enabled": true,
  "createdAt": "2026-08-06T13:00:00",
  "updatedAt": "2026-08-06T13:00:00"
}
```

`PATCH /{subscriptionId}/enabled`의 요청 본문은 `{ "enabled": false }`이고, DELETE는
소프트 삭제한다.

## 구독 종류·알림 이력

```text
GET /api/v1/notification-subscription-types
GET /api/v1/notifications?page=0&size=20
```

알림 이력은 최신 생성일 순으로 반환한다. `page`는 0 이상, `size`는 1~100이다.
프론트는 첫 화면에서 `page=0&size=20`을 사용하고 `hasNext=true`일 때만 다음 페이지를
요청하면 된다. `message`는 해당 채널에 실제로 렌더링된 알림 문구이므로 화면에 바로 표시할 수 있다.

```json
{
  "content": [
    {
      "id": 11,
      "notificationId": 7,
      "subscriptionId": 20,
      "channelCode": "TELEGRAM",
      "message": "[센서 오류] 느타리 1번의 온도 센서 오류가 발생했습니다.",
      "status": "SENT",
      "attemptCount": 1,
      "sentAt": "2026-08-06T13:00:00",
      "createdAt": "2026-08-06T13:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

`status`는 `PENDING`, `SENDING`, `SENT`, `FAILED` 중 하나다. `SENDING`은 외부 발송을
한 작업만 수행하도록 내부에서 선점한 상태이며, 화면에서는 보통 “발송 중”으로 표시하면 된다.
`attemptCount`는 최대 3이다.

## 공통 오류 응답

요청 사용자 소유의 데이터만 조회·수정할 수 있다.

```json
{
  "type": "about:blank",
  "timestamp": "2026-07-31T10:00:00+09:00",
  "status": 404,
  "title": "Not Found",
  "detail": "알림 수신 경로를 찾을 수 없습니다.",
  "code": "NOTIFICATION_RESOURCE_NOT_FOUND",
  "path": "/api/v1/notification-endpoints/1"
}
```

프론트는 오류 문구를 `detail`, 프로그램 분기는 `code`로 처리한다. `message` 필드는 반환하지
않으므로 사용하면 안 된다.

| HTTP | 코드                                   | 의미                            |
|-----:|----------------------------------------|---------------------------------|
|  400 | `INVALID_REQUEST`                      | 필수값·형식 오류                |
|  400 | `UNSUPPORTED_NOTIFICATION_CHANNEL`     | 지원하지 않는 채널 조합         |
|  404 | `NOTIFICATION_RESOURCE_NOT_FOUND`      | 없거나 사용자 소유가 아닌 리소스 |
|  409 | `NOTIFICATION_RESOURCE_CONFLICT`       | Endpoint·구독 중복              |
|  502 | `NOTIFICATION_PROVIDER_FAILURE`        | 외부 채널 요청 실패             |
|  500 | `INTERNAL_SERVER_ERROR`                | 예상하지 못한 서버 오류         |
