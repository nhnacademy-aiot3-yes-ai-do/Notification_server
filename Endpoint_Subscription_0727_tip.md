# Endpoint·Subscription 학습 가이드

이 문서는 Notification Service에 새로 합쳐진 Endpoint·Subscription 영역을 처음 공부하는 사람을 위한 안내서다. 코드를 읽을 때는 **사용자 설정을 저장하는 부분**과 **이벤트를 받아 실제 알림을 보내는 부분**을 구분해서 이해하면 된다.

## 1. 이 기능이 프로젝트에서 하는 일

우리 서비스는 버섯 재배 중 문제가 생기거나 중요한 변화가 발생하면 Telegram·Discord로 알려준다. 하지만 모든 사용자에게 모든 알림을 보내면 안 된다. 사용자가 먼저 “어디로 받을지”와 “무슨 알림을 받을지”를 설정해야 한다.

```text
사용자
  ├─ Endpoint: 받을 경로 등록
  │    ├─ Telegram Chat ID
  │    └─ Discord Webhook URL
  └─ Subscription: 받을 알림 종류와 대상을 선택
       ├─ 환경 이상
       ├─ 센서 오류·오프라인
       ├─ 수확 완료·재배 종료
       └─ 로그인·문의 답변
```

Endpoint와 Subscription은 알림을 즉시 발송하는 기능이 아니다. 이들은 “발송해도 되는 대상”을 저장한다. 나중에 Rule, Cultivation, AI, Auth, Inquiry 서비스가 이벤트를 발행하면 Notification Consumer가 이 설정을 조회한다.

## 2. Endpoint와 Subscription의 차이

### Endpoint

Endpoint는 실제 수신 주소다. 예를 들어 Telegram Chat ID가 `123456`이거나 Discord Webhook URL이 등록된 한 건이 Endpoint다.

```text
Endpoint = 어디로 보낼까?
Subscription = 어떤 알림을 보낼까?
```

같은 사용자가 Telegram과 Discord를 모두 등록하면 Endpoint가 2개 생긴다. 같은 알림을 두 채널로 받고 싶으면 각 Endpoint에 Subscription을 연결한다.

### Subscription

Subscription은 알림 종류와 대상, 수신 Endpoint를 연결한다. 예를 들어 “재배 12번의 센서 오류를 Telegram으로 받기”가 하나의 Subscription이다.

Subscription에는 보통 다음 정보가 필요하다.

- 어떤 Subscription Type인가
- 어느 Endpoint로 받을 것인가
- 어느 대상인가 (`targetId`)
- 현재 켜져 있는가 (`enabled`)
- 삭제된 설정인가 (`is_deleted`)

## 3. ERD를 코드로 읽는 법

### `notification_endpoint`

| 컬럼 | 의미 |
|---|---|
| `id` | Endpoint 식별자 |
| `user_id` | Endpoint 소유 사용자 |
| `channel_type_id` | Telegram 또는 Discord 유형 |
| `destination` | Chat ID 또는 Webhook URL |
| `display_name` | 사용자가 보는 이름 |
| `enabled` | 일시적으로 수신을 켜거나 끄는 값 |
| `is_deleted` | 삭제 여부를 표시하는 소프트 삭제 값 |

`enabled=false, is_deleted=false`는 잠시 꺼둔 상태다. 목록에 다시 나타나고 켤 수 있다. `is_deleted=true`는 삭제 상태다. 이력 보존을 위해 DB 행은 남기지만 기본 목록에는 숨긴다.

### `notification_subscription`

| 컬럼 | 의미 |
|---|---|
| `id` | 구독 식별자 |
| `notification_subscription_type_id` | 어떤 이벤트를 받을지 정의한 유형 |
| `notification_endpoint_id` | 어느 Telegram·Discord 경로로 받을지 |
| `target_id` | 재배 ID 또는 사용자 ID |
| `enabled` | 수신 ON/OFF |
| `is_deleted` | 구독 삭제 여부 |

현재 ERD의 `target_id`는 숫자 하나로 저장되며, 대상 유형은 Subscription Type이 가진 `subscription_target_type` 관계로 해석한다. 따라서 `target_id=12`라도 해당 Type이 `CULTIVATION`이면 재배 12번이고, `USER`이면 사용자 12번이다.

### 기준 테이블

- `channel_type`: TELEGRAM, DISCORD 같은 채널 목록
- `notification_event_type`: SENSOR_ERROR, HARVEST_COMPLETED 같은 이벤트 목록
- `subscription_target_type`: CULTIVATION, USER 같은 대상 종류
- `notification_subscription_type`: 이벤트 종류와 대상 종류를 묶어 사용자가 선택할 수 있게 만든 기준
- `subscription_channel`: 한 구독 유형에서 허용하는 채널

## 4. Entity 코드 읽기

### Endpoint Entity

파일: `src/main/java/com/ecosphere/notification/domain/NotificationEndpoint.java`

```java
@Entity
@Table(name = "notification_endpoint")
public class NotificationEndpoint extends AuditEntity {
```

`@Entity`는 이 클래스가 DB 테이블과 연결된다는 뜻이고, `@Table`은 테이블 이름을 지정한다. `AuditEntity`를 상속하므로 생성 시간과 수정 시간이 공통으로 관리된다.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "channel_type_id", nullable = false)
private ChannelType channelType;
```

Endpoint 여러 개가 하나의 채널 유형을 사용할 수 있으므로 `ManyToOne`이다. `LAZY`는 실제 채널 정보가 필요할 때 조회한다는 의미다.

```java
public void delete() {
    this.enabled = false;
    this.deleted = true;
}
```

실제 DELETE SQL을 실행하지 않고 상태만 바꾸는 소프트 삭제다. 알림 발송 이력과의 관계를 보존하기 위해 사용한다.

### Subscription Entity

파일: `src/main/java/com/ecosphere/notification/domain/NotificationSubscription.java`

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "notification_endpoint_id", nullable = false)
private NotificationEndpoint endpoint;
```

Subscription은 Endpoint 없이 존재할 수 없다. 따라서 Endpoint를 삭제하거나 비활성화하면 그 Endpoint로 발송할 수 있는지 함께 확인해야 한다.

```java
public void changeEnabled(boolean enabled) {
    if (!deleted) {
        this.enabled = enabled;
    }
}
```

이미 삭제된 Subscription은 다시 ON으로 되돌리지 않도록 방어한다. 복구 기능이 필요해지면 별도 정책으로 추가해야 한다.

## 5. Repository란 무엇인가

Repository는 Entity를 DB에 저장하고 조회하는 객체다. 직접 SQL을 작성하지 않아도 Spring Data JPA가 메서드 이름을 분석해 쿼리를 만든다.

파일: `repository/NotificationEndpointRepository.java`

```java
List<NotificationEndpoint> findAllByUserIdAndDeletedFalse(Long userId);
```

다음 조건의 SQL을 자동으로 만든다.

```sql
SELECT *
FROM notification_endpoint
WHERE user_id = :userId
  AND is_deleted = false;
```

파일: `repository/NotificationSubscriptionRepository.java`

```java
List<NotificationSubscription>
findAllByEndpoint_UserIdAndDeletedFalse(Long userId);
```

`Endpoint_UserId`처럼 밑줄을 사용하면 연관 Entity를 따라가서 Endpoint의 user_id를 조건으로 사용할 수 있다.

```java
boolean existsBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndEnabledTrueAndDeletedFalse(...);
```

이 메서드는 같은 Subscription Type·Endpoint·Target 조합의 활성 구독이 이미 있는지 확인한다. 중복 구독을 막는 데 사용한다.

## 6. 삭제와 비활성화는 왜 나누는가

### Endpoint 일시정지

사용자가 “Discord 알림을 잠시 끄기”를 누르면 `enabled=false`로 바꾼다. 목록에는 보이므로 다시 켤 수 있다.

### Endpoint 삭제

사용자가 “Discord 연결 삭제”를 누르면 `enabled=false`와 `is_deleted=true`로 바꾼다. 기본 목록에서는 숨긴다. 실제 행을 지우지 않는 이유는 과거 `notification_delivery`가 이 Endpoint를 참조할 수 있기 때문이다.

### 조회 시 기본 조건

```text
목록 조회 = user_id 일치 AND is_deleted=false
발송 대상 = user_id 일치 AND enabled=true AND is_deleted=false
```

Subscription도 같은 방식으로 `enabled`와 `is_deleted`를 구분한다.

## 7. Migration 이해하기

파일: `src/main/resources/db/migration/V4__add_endpoint_soft_delete.sql`

```sql
ALTER TABLE notification_endpoint
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
```

Migration은 이미 만들어진 DB 구조를 안전하게 변경하는 파일이다. `V4`는 V1, V2, V3 다음에 실행된다. `IF NOT EXISTS`를 사용해 같은 파일을 다시 적용해도 중복 컬럼 오류를 줄인다.

추가 Migration인 `V5__fix_active_subscription_unique_index.sql`은 활성 구독 중복 방지 조건을 보완한다. `enabled=false`인 일시정지 구독은 중복 방지 대상에서 제외하고, `enabled=true AND is_deleted=false`인 구독만 같은 조합을 막는다.

```sql
CREATE INDEX IF NOT EXISTS idx_endpoint_user_active
    ON notification_endpoint (user_id, enabled, is_deleted);
```

사용자별 활성 Endpoint를 자주 조회하므로 이 조건에 맞춘 인덱스를 추가한다.

## 8. API를 만들 때의 흐름

아직 JWT claim명과 재배 권한 확인 API가 확정되지 않았으므로 Controller는 그 계약 확정 후 붙인다. 구현 순서는 아래가 안전하다.

1. 요청 DTO와 응답 DTO 작성
2. 인증 정보에서 userId 추출
3. Repository로 본인 소유 데이터 조회
4. 입력값과 채널 형식 검증
5. Entity 생성·변경
6. 저장 후 응답 변환
7. 타인의 ID를 요청해도 404 또는 권한 오류가 나는지 테스트

권한 검증에서 요청 body의 `userId`를 믿으면 안 된다. 로그인한 사용자의 JWT에서 얻은 userId를 기준으로 조회해야 한다.

## 9. 예상 API 범위

```text
POST   /api/v1/notifications/endpoints
GET    /api/v1/notifications/endpoints
PATCH  /api/v1/notifications/endpoints/{endpointId}
DELETE /api/v1/notifications/endpoints/{endpointId}

GET    /api/v1/notifications/subscription-types
GET    /api/v1/notifications/subscriptions
POST   /api/v1/notifications/subscriptions
PATCH  /api/v1/notifications/subscriptions/{subscriptionId}
DELETE /api/v1/notifications/subscriptions/{subscriptionId}
```

## 10. 예시 요청과 처리

### Endpoint 등록

```json
{
  "channelTypeCode": "TELEGRAM",
  "destination": "123456789",
  "displayName": "내 텔레그램"
}
```

처리 순서:

1. JWT에서 userId를 얻는다.
2. `channelTypeCode`로 Telegram 기준 데이터를 찾는다.
3. Chat ID 형식을 검증한다.
4. userId와 함께 Endpoint를 저장한다.
5. 저장된 id와 상태를 응답한다.

### 재배 알림 구독

```json
{
  "subscriptionTypeId": 3,
  "endpointId": 10,
  "targetId": 12
}
```

처리 순서:

1. 요청한 Endpoint가 로그인 사용자 소유인지 확인한다.
2. Subscription Type이 `CULTIVATION` 대상인지 확인한다.
3. 사용자가 재배 12번의 알림 설정 권한이 있는지 확인한다.
4. 같은 Type·Endpoint·Target의 활성 중복을 확인한다.
5. Subscription을 저장한다.

## 11. Notification Consumer와의 연결

Endpoint·Subscription API는 설정을 저장하고, Consumer는 그 설정을 사용한다.

```text
Rule/Cultivation/AI/Auth/Inquiry
              │ RabbitMQ 이벤트
              ▼
Notification Consumer
              │ 이벤트 대상 + 활성 구독 조회
              ▼
Notification 생성
              │ Endpoint별 Delivery 생성
              ▼
Telegram / Discord 발송
```

Consumer가 필요한 정보는 다음과 같다.

- 활성 구독 조회 메서드 또는 Service
- targetType과 targetId의 의미
- Endpoint의 channelType과 destination
- Subscription의 enabled·is_deleted 조건
- 중복 구독이 없다는 보장

## 12. 지금 구현하지 않고 기다려야 하는 것

- 실제 RabbitMQ exchange·queue·routing key
- JWT 사용자 ID claim의 최종 이름
- Cultivation 권한 확인 API 주소와 응답 형식
- Telegram·Discord 테스트 발송 방식
- 최종 API 오류 응답 형식

이 값들을 임의로 확정하면 나중에 다른 서비스와 연동할 때 다시 고쳐야 한다. 대신 Entity·Repository·Migration처럼 계약에 영향을 덜 받는 기반부터 만든다.

## 13. 공부 순서 체크리스트

- [ ] `NotificationEndpoint`의 각 필드와 ERD 컬럼을 비교한다.
- [ ] `enabled`와 `deleted`의 차이를 설명할 수 있다.
- [ ] `NotificationSubscription`이 Endpoint를 참조하는 이유를 설명할 수 있다.
- [ ] Repository 메서드 이름을 SQL 조건으로 바꿔 읽어본다.
- [ ] V4 Migration이 왜 필요한지 설명한다.
- [ ] 본인 소유권 검증이 필요한 이유를 이해한다.
- [ ] Subscription 중복 방지 조건을 설명한다.
- [ ] RabbitMQ 이벤트가 오면 Consumer가 이 설정을 어떻게 사용하는지 설명한다.
- [ ] JWT·RabbitMQ·권한 계약이 확정된 뒤 Controller를 구현한다.

## 14. 용어 사전

| 용어 | 쉬운 뜻 |
|---|---|
| Endpoint | 알림을 받을 실제 경로 |
| Subscription | 어떤 알림을 받을지에 대한 설정 |
| Channel | Telegram·Discord 같은 발송 수단 |
| Target | 알림의 대상인 재배 또는 사용자 |
| Soft Delete | 행을 지우지 않고 삭제 상태만 기록하는 방식 |
| Repository | DB 조회·저장을 담당하는 Spring 객체 |
| Entity | DB 테이블과 연결된 Java 클래스 |
| Migration | DB 구조를 버전별로 변경하는 SQL 파일 |
| DTO | API나 메시지로 주고받는 데이터 묶음 |
| Consumer | RabbitMQ 메시지를 받는 컴포넌트 |
| JWT Claim | 토큰 안에 들어 있는 사용자 식별 정보 |
| Idempotency | 같은 요청을 여러 번 처리해도 결과가 중복되지 않는 성질 |
