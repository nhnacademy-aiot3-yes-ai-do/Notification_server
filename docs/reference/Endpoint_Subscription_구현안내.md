# Endpoint·Subscription 구현 안내

현재 구현 담당: 호준(Notification Service)  
참고: 서영님은 현재 프론트엔드에 전념하며, 아래 내용은 Notification API와 프론트 연동을 위한 기준 문서다.  
작성 목적: Notification Service의 Endpoint·Subscription 구현 범위와 연동 기준 공유

이 문서는 `Notification_역할분담_및_개발실행계획.md`의 내용을 실제 구현 관점에서 정리한 안내 문서다. Endpoint·Subscription 구현은 Notification 담당자가 진행하고, 프론트엔드는 확정된 API 계약을 사용한다.

## 1. 담당 범위

### Endpoint

사용자가 실제 알림을 받을 경로를 등록·관리한다.

- Telegram Chat ID 등록
- Discord Webhook URL 등록
- Endpoint 목록 조회
- Endpoint 수정
- Endpoint 비활성화 또는 삭제
- 사용자의 Endpoint 소유권 검증
- 채널별 형식 검증

### Subscription

사용자가 어떤 알림을 받을지 설정한다.

- 구독 가능한 알림 종류 조회
- 재배별 알림 구독 생성
- 로그인 알림 구독 생성
- 문의 답변 알림 구독 생성
- 구독 목록 조회
- 구독 ON/OFF
- 구독 비활성화 또는 삭제
- 동일한 비삭제 구독 중복 방지
- 재배 권한 검증

## 2. 확정된 알림 정책

- 지원 채널은 Telegram과 Discord다.
- 이메일 인증번호 알림은 구현하지 않는다.
- 자동 제어 성공은 알림으로 보내지 않는다.
- 자동 제어 실패만 알림으로 보낸다.
- 사용자가 알림 설정 화면에서 직접 구독을 생성한다.
- 모든 사용자에게 구독을 자동 생성하지 않는다.
- 활성화된 구독만 발송 대상이다.
- 동일한 사용자·대상·알림 종류·Endpoint 조합의 비삭제 구독은 중복 생성하지 않는다.
- 일시정지된 동일 구독이 있으면 새로 생성하지 않고 기존 구독을 다시 활성화한다.
- 재배 알림 대상 유형은 `CULTIVATION`이다.
- 로그인·문의 답변 알림 대상 유형은 `USER`이다.

## 3. 기본 구현 기준

### API Base Path

```text
/api/v1/notifications
```

### 삭제·비활성화

과거 알림 이력과의 관계를 보존하기 위해 실제 삭제보다 비활성화를 우선한다.

- Endpoint: `is_deleted=true`
- Subscription: `is_deleted=true`
- 구독 수신 여부: `enabled=false`
- 기본 목록 조회: 삭제·비활성 데이터를 제외

### 소유권

- 사용자는 본인의 Endpoint만 조회·수정·삭제할 수 있다.
- 사용자는 본인의 Subscription만 조회·수정·삭제할 수 있다.
- 요청 body의 `userId`를 신뢰하지 말고 인증 정보에서 사용자 ID를 가져온다.

## 4. API 범위

실제 Controller·DTO 필드명은 구현 중 조정할 수 있지만, 기능 범위는 아래와 같다.

### Endpoint API

```text
POST   /api/v1/notifications/endpoints
GET    /api/v1/notifications/endpoints
PATCH  /api/v1/notifications/endpoints/{endpointId}
DELETE /api/v1/notifications/endpoints/{endpointId}
```

### Subscription API

```text
GET    /api/v1/notifications/subscription-types
GET    /api/v1/notifications/subscriptions
POST   /api/v1/notifications/subscriptions
PATCH  /api/v1/notifications/subscriptions/{subscriptionId}
DELETE /api/v1/notifications/subscriptions/{subscriptionId}
```

## 5. 관련 테이블

Endpoint·Subscription 구현과 프론트 연동에 사용되는 테이블은 다음과 같다.

- `notification_endpoint`
- `notification_subscription`
- `notification_subscription_type`
- `subscription_channel`
- `channel_type`
- `subscription_target_type`
- `notification_event_type`

### 관계 개념

```text
users
  └─ notification_endpoint
       └─ notification_subscription
            ├─ notification_subscription_type
            ├─ notification_event_type
            └─ channel_type
```

- `notification_endpoint`: Telegram Chat ID, Discord Webhook URL 등 실제 수신 경로
- `notification_subscription`: 사용자가 어떤 대상의 어떤 이벤트를 어떤 Endpoint로 받을지 저장
- `notification_subscription_type`: 사용자가 선택할 수 있는 알림 종류
- `notification_event_type`: 이벤트 코드와 대상 유형의 기준 정보
- `channel_type`: Telegram·Discord 채널 기준 정보

## 6. 대상별 구독 기준

### 재배 알림

- `targetType=CULTIVATION`
- `targetId=cultivation_id`
- 사용자가 해당 재배의 소유자 또는 알림 설정 권한이 있는 멤버인지 확인

### 로그인 알림

- `targetType=USER`
- `targetId=user_id`
- 사용자의 계정 알림 설정을 기준으로 구독

### 문의 답변 알림

- `targetType=USER`
- `targetId=문의 작성자의 user_id`
- 문의 작성자가 답변 알림을 구독했는지 확인

## 7. 인증과 권한 확인

### JWT 사용자 ID

JWT의 실제 사용자 ID claim은 Auth/Gateway 구현과 맞춰야 한다. 현재 Notification 내부 구현 후보는 `userId`지만, 실제 토큰 명세가 확정되면 그 claim 이름을 사용한다.

비밀번호, access token, refresh token 등 민감정보는 Endpoint·Subscription 요청이나 이벤트 payload에 저장하지 않는다.

### 재배 권한

Notification이 Cultivation DB를 직접 조회하지 않는 구조를 유지한다.

권장 방향:

1. Cultivation Service의 권한 확인 API 호출
2. 또는 팀에서 정한 공통 권한 확인 방식 사용

실제 API 주소·요청 형식·응답 형식은 Cultivation 담당자와 확인한다.

## 8. 구현 전에 확인할 항목

아래 항목은 큰 정책을 다시 결정하는 것이 아니라, 실제 연동을 위해 구현 담당자 간 확인할 세부사항이다.

- JWT 사용자 ID claim의 최종 이름
- 재배 권한 확인 API의 주소와 요청 형식
- Endpoint 삭제 API가 비활성화 응답을 사용할지 여부
- Subscription 삭제 API가 `is_deleted=true`로 처리되는지 여부
- Telegram Chat ID와 Discord Webhook URL의 검증 규칙
- API 오류 응답 형식
- 페이지네이션 필요 여부
- Endpoint 등록 직후 테스트 메시지를 보낼지 여부

## 9. Notification Consumer 담당자에게 공유할 결과물

Endpoint·Subscription 구현 후 아래 내용을 공유한다.

- 최종 요청 JSON
- 최종 응답 JSON
- DTO 필드명
- 인증 사용자 ID를 가져오는 방식
- 활성 구독 판단 필드명
- 구독 생성 시 저장되는 필드
- 구독이 없을 때의 응답 형식
- 권한 오류와 중복 구독 오류 형식

이 정보가 있어야 Consumer가 활성 구독을 조회하고 Notification·Delivery를 생성할 때 정확하게 연동할 수 있다.

## 10. 구현 순서 제안

1. Entity·Repository 확인
2. Endpoint 생성·조회·수정·비활성화 구현
3. Telegram·Discord 형식 검증 추가
4. Subscription Type 목록 조회 구현
5. Subscription 생성·조회·ON/OFF·비활성화 구현
6. 본인 소유권 및 재배 권한 검증 추가
7. 중복 비삭제 구독 방지 및 일시정지 구독 재활성화
8. Controller 테스트 작성
9. 요청·응답 JSON을 Notification Consumer 담당자에게 공유

## 11. 현재 시점의 협업 방법

이 문서만으로 모든 외부 연동을 확정하는 것은 아니다. 우선 Notification 내부 구현을 진행하고, 프론트 연동 전에 최종 요청·응답 JSON과 인증·권한 정보를 확정한다.

## 12. 팀 공통 설명

Notification의 다른 서비스는 알림을 직접 발송하지 않고 이벤트만 발행한다. Endpoint·Subscription은 사용자의 알림 수신 설정을 관리하고, 실제 이벤트를 받아 구독 여부 확인·알림 생성·Telegram/Discord 발송·재시도·실패 이력 저장은 Notification Consumer가 담당한다.
