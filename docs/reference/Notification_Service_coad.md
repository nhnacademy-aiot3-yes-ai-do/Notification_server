# Notification Service 코드 구조 초보자 학습 가이드

> 이 문서는 현재 `Notification_service/src/main`에 실제로 있는 코드를 기준으로 작성했다.
> 목표는 “파일 이름을 아는 것”이 아니라, **이 서비스가 왜 존재하고, Spring Boot가 어떤 순서로 실행되며, 각 클래스·SQL·설정이 무엇을 책임지는지** 이해하는 것이다.

---

## 0. 이 프로젝트는 무엇을 만드는가

우리 팀의 프로젝트는 **EcoSphere / MushMush 버섯 재배 자동화 플랫폼**이다.

사용자는 버섯 재배를 등록하고, 온도·습도·CO₂ 등 센서 정보를 확인한다. 센서값이 기준을 벗어나거나, 센서가 끊기거나, 수확이 완료되거나, AI 일일 피드백이 생성되는 등 중요한 일이 발생하면 사용자가 알아야 한다.

그런데 Rule, Cultivation, AI, Auth 같은 모든 서비스가 Telegram·Discord 발송 코드를 각각 가지면 다음 문제가 생긴다.

- 서비스마다 같은 발송 코드가 중복된다.
- 발송 실패와 재시도 정책이 제각각이 된다.
- 사용자의 알림 설정을 여러 서비스가 나눠 관리하게 된다.
- Telegram/Discord 토큰과 Webhook을 여러 서비스가 알게 되어 보안상 좋지 않다.

그래서 Notification Service를 별도로 둔다.

```text
Rule / Cultivation / AI / Auth / Inquiry
  → “이런 일이 발생했다”는 이벤트 발행
  → RabbitMQ
  → Notification Service
  → 사용자의 구독 설정 확인
  → Telegram / Discord 발송
  → 발송 이력 저장
```

### Notification Service의 한 문장 역할

> 다른 서비스가 판단한 사건을 받아서, 사용자의 구독 설정과 수신 채널에 맞게 알림으로 전달하고 결과를 기록하는 서비스다.

### 이 서비스가 하지 않는 일

Notification은 일을 “판단”하지 않는다.

| 하지 않는 일 | 하는 서비스 |
|---|---|
| 센서값이 이상인지 판단 | Rule Service |
| 재배 생성·종료·수확 저장 | Cultivation Service |
| 로그인·JWT 발급 | Auth/User Service |
| AI 피드백 생성 | AI Service |
| 다른 서비스 DB를 직접 조회 | 하지 않음 |

Notification은 이미 발생한 일을 이벤트로 받아 **전달**하는 역할이다.

---

## 1. 현재 코드가 있는 위치와 전체 구조

현재 핵심 폴더는 다음과 같다.

```text
src/main
├── java/site/yesaido/notification_server
│   ├── NotificationServiceApplication.java
│   ├── config/
│   ├── controller/
│   ├── domain/
│   ├── dto/
│   ├── exception/
│   ├── messaging/
│   ├── provider/
│   ├── repository/
│   ├── service/
│   └── template/
└── resources
    ├── application.yml
    └── db/migration/
```

각 폴더의 책임을 먼저 한 문장으로 보면 다음과 같다.

| 위치 | 쉬운 설명 |
|---|---|
| `NotificationServiceApplication` | Spring Boot를 시작하는 전원 버튼 |
| `config` | RabbitMQ와 타입 안전 설정 객체를 만드는 곳 |
| `controller` | Gateway를 거쳐 들어온 HTTP 요청을 받는 곳 |
| `domain` | 알림 서비스가 다루는 핵심 개념과 DB 테이블의 Java 표현 |
| `dto` | API 요청·응답 모양을 정의하는 곳 |
| `exception` | 오류 종류와 공통 오류 응답을 정의하는 곳 |
| `messaging` | RabbitMQ 이벤트 수신과 DLQ 발행을 담당하는 곳 |
| `provider` | Telegram·Discord 외부 API 발송을 담당하는 곳 |
| `repository` | Java가 DB에 조회·저장 요청을 보내는 창구 |
| `service` | 트랜잭션과 업무 흐름을 구현하는 곳 |
| `template` | 이벤트 payload를 최종 메시지로 변환하는 곳 |
| `db/migration` | DB 테이블과 기준 데이터를 버전 순서대로 만드는 SQL |
| `application.yml` | DB·RabbitMQ·포트·Flyway·로그 같은 실행 설정 |

현재는 Endpoint·Subscription API, RabbitMQ Consumer, 템플릿 렌더링,
Telegram·Discord 발송, 최대 3회 재시도, 최종 실패 DLQ 처리까지 구현된 상태다.

---

## 2. Spring Boot란 무엇인가

Spring Boot는 Java로 웹 서버와 DB 연결, 설정 읽기, 객체 생성 등을 편하게 만드는 프레임워크다.

원래 Java 웹 서버를 만들려면 다음을 사람이 일일이 준비해야 한다.

- HTTP 서버 실행
- 객체 생성과 연결
- DB 연결 관리
- JSON 변환
- 설정 파일 읽기
- 예외 처리 기본 구조

Spring Boot는 필요한 라이브러리와 설정을 보고 이를 자동으로 준비한다. 이를 **자동 구성(Auto Configuration)** 이라고 한다.

예를 들어 이 프로젝트에 `spring-boot-starter-data-jpa`가 있고 DB URL이 설정돼 있으면 Spring Boot는 PostgreSQL 연결과 JPA 사용 준비를 자동으로 한다.

---

## 3. NotificationServiceApplication: 애플리케이션의 시작점

파일: `src/main/java/site/yesaido/notification_server/NotificationServiceApplication.java`

```java
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

### `main` 메서드

Java 프로그램은 보통 `main` 메서드에서 시작한다.

```java
public static void main(String[] args)
```

여기서 `SpringApplication.run(...)`을 호출하면 Spring Boot가 서버를 준비한다.

### `@SpringBootApplication`

이 어노테이션은 “이 클래스가 Spring Boot의 시작점이다”라고 알려주는 표시다. 실제로는 다음 세 기능을 묶은 것이다.

| 포함 기능 | 역할 |
|---|---|
| `@Configuration` | Java 코드로 Spring 설정을 만들 수 있게 함 |
| `@EnableAutoConfiguration` | 의존성과 설정에 맞는 기능을 자동 준비 |
| `@ComponentScan` | 같은 패키지와 하위 패키지의 Spring 객체를 찾음 |

현재 시작 클래스의 패키지는 `site.yesaido.notification_server`다.
따라서 Spring은 이 아래의 `config`, `controller`, `domain`, `service`,
`repository`, `messaging`, `provider`, `template` 등을 찾아 관리한다.

### 실행할 때 실제 순서

```text
1. main() 실행
2. SpringApplication.run() 실행
3. application.yml 읽기
4. DataSource(PostgreSQL 연결) 준비
5. Flyway가 V1~V7 Migration 확인·실행
6. JPA가 Entity와 실제 테이블 구조 비교(validate)
7. Repository 구현체 생성
8. RabbitMQ 연결 설정 준비
9. 내장 웹 서버가 8080 포트에서 대기
```

Controller가 구현되어 있으므로 Gateway가 전달한 `X-User-Id`를 이용해
Endpoint·Subscription·발송 이력 API를 호출할 수 있다.

---

## 4. `domain` 폴더: 서비스의 핵심 명사들

### Domain이란

도메인은 “이 서비스가 해결하는 업무 세계”를 말한다.

알림 서비스의 도메인 명사는 다음과 같다.

```text
알림(Notification)
발송 이력(Delivery)
수신 경로(Endpoint)
구독(Subscription)
알림 이벤트 유형(Event Type)
채널 유형(Channel Type)
템플릿(Template)
```

현재 `domain` 클래스 대부분은 JPA Entity다. Entity는 **DB 테이블의 한 행을 Java 객체로 표현하는 클래스**다.

```text
PostgreSQL table notification_endpoint
        ↕ JPA가 변환
Java class NotificationEndpoint
```

### Entity와 DTO를 혼동하지 않기

| 구분 | Entity | DTO |
|---|---|---|
| 목적 | DB 테이블과 연결 | API·메시지 데이터 전달 |
| 예시 | `NotificationEndpoint` | `DomainEvent` |
| DB 저장 책임 | 있음 | 보통 없음 |
| 변경 시 영향 | 테이블과 연관 | 요청·응답 형식과 연관 |

현재 `DomainEvent`는 DB Entity가 아니라 RabbitMQ 메시지 DTO에 가깝다.

---

## 5. Entity에서 반복해서 보이는 어노테이션

### `@Entity`

```java
@Entity
public class NotificationEndpoint { ... }
```

이 클래스가 DB 테이블과 연결된 Entity라는 뜻이다. JPA가 객체를 저장하거나 조회할 때 사용한다.

### `@Table(name = "...")`

```java
@Table(name = "notification_endpoint")
```

Java 클래스명과 실제 DB 테이블명을 연결한다.

```text
NotificationEndpoint 클래스 → notification_endpoint 테이블
```

### `@Id`

```java
@Id
private Long id;
```

테이블의 기본 키(PK)를 뜻한다. 한 행을 구분하는 고유 번호다.

### `@GeneratedValue(strategy = GenerationType.IDENTITY)`

```java
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

ID를 Java가 직접 정하지 않고 PostgreSQL이 자동 생성하게 한다.

```text
새 Endpoint 저장
→ DB가 id=1 생성
→ JPA가 생성된 id를 Java 객체에 반영
```

### `@Column`

```java
@Column(name = "display_name", nullable = false, length = 100)
private String displayName;
```

필드와 컬럼의 연결 규칙이다.

| 옵션 | 뜻 |
|---|---|
| `name` | 실제 DB 컬럼명 |
| `nullable = false` | NULL을 허용하지 않음 |
| `unique = true` | 중복을 허용하지 않음 |
| `length` | 문자열 길이 제한 |
| `columnDefinition = "TEXT"` | DB 타입을 명시적으로 지정 |

### `@ManyToOne`과 `@JoinColumn`

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "channel_type_id", nullable = false)
private ChannelType channelType;
```

“여러 Endpoint가 하나의 ChannelType을 참조한다”는 뜻이다.

```text
여러 Endpoint
 ├─ Telegram 개인 채팅
 ├─ Telegram 가족 채팅
 └─ Discord 재배방
       ↓
ChannelType(TELEGRAM 또는 DISCORD)
```

`@JoinColumn`의 `channel_type_id`는 DB의 외래 키(FK) 컬럼명이다.

### `FetchType.LAZY`

LAZY는 “당장 필요하지 않은 연관 객체는 늦게 가져오자”는 뜻이다.

Endpoint를 조회할 때 ChannelType 내용까지 무조건 한 번에 가져오지 않고, 실제로 `getChannelType()`을 쓸 때 조회한다.

장점은 필요 없는 DB 조회를 줄이는 것이다. 단, Service/Controller에서 LAZY 객체를 무심코 접근하면 추가 쿼리나 세션 종료 문제가 생길 수 있어서 설계가 필요하다.

### `optional = false`

연관 객체가 반드시 있어야 한다는 뜻이다. 예를 들어 Endpoint는 채널 유형 없이 존재할 수 없다.

### `@Getter`

Lombok이 getter를 자동 생성한다.

```java
private String destination;
```

위 필드에 대해 컴파일 시 아래 메서드를 자동으로 만든다.

```java
public String getDestination()
```

### `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

JPA는 Entity를 DB에서 불러올 때 기본 생성자가 필요하다. 하지만 아무 코드나 `new Entity()`로 빈 객체를 만들지 못하게 `protected`로 제한했다.

```text
JPA는 사용 가능
외부 코드의 무분별한 빈 객체 생성은 제한
```

### Setter를 쓰지 않는 이유

현재 Entity는 `@Setter`를 달지 않았다. 대신 의미 있는 메서드를 제공한다.

```java
endpoint.changeEnabled(false);
endpoint.softDelete();
delivery.markSent(providerMessageId);
```

이 방식은 “무엇을 바꾸는지”와 “어떤 규칙으로 바꾸는지”가 코드에 드러난다.

---

## 6. `AuditEntity`: 생성·수정 시간의 공통 부모

파일: `domain/AuditEntity.java`

```java
@Getter
@MappedSuperclass
public abstract class AuditEntity {
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

여러 테이블에는 생성 시각과 수정 시각이 반복된다. 각 Entity마다 똑같은 코드를 쓰지 않으려고 공통 부모 클래스로 만들었다.

### `abstract`

`AuditEntity` 자체를 단독으로 `new`해서 쓰는 것이 아니라, 자식 Entity가 상속받도록 만든 클래스다.

### `@MappedSuperclass`

이 클래스는 자신의 테이블을 만들지 않는다. 대신 상속한 Entity의 테이블에 `created_at`, `updated_at` 컬럼을 포함시킨다.

```text
AuditEntity 자체 테이블: 없음
notification_endpoint 테이블: created_at, updated_at 포함
notification_subscription 테이블: created_at, updated_at 포함
```

### `@CreationTimestamp`

Hibernate가 Entity를 처음 INSERT할 때 생성 시각을 자동으로 넣는다.

### `@UpdateTimestamp`

Hibernate가 Entity UPDATE를 감지해 DB에 반영할 때 수정 시각을 자동으로 갱신한다.

이 때문에 매번 `updatedAt = now()`를 직접 쓰지 않아도 된다.

---

## 7. 기준 코드 Entity: Channel·Target·Event

기준 코드 테이블은 자주 바뀌는 사용자 데이터가 아니라, 시스템이 공통으로 알아야 하는 종류 목록이다.

### 7.1 `ChannelType`

파일: `domain/ChannelType.java`

```text
channel_type
├─ TELEGRAM
└─ DISCORD
```

| 필드 | 의미 |
|---|---|
| `id` | 내부 PK |
| `code` | 프로그램이 사용하는 코드 (`TELEGRAM`) |
| `displayName` | 화면에 보여줄 이름 (`Telegram`) |
| `deleted` | 더 이상 사용하지 않는 유형인지 |

`code`에 `unique = true`가 있어 Telegram 코드가 중복 저장되는 것을 DB가 막는다.

### 7.2 `SubscriptionTargetType`

파일: `domain/SubscriptionTargetType.java`

“무엇에 대한 알림을 구독하는가?”의 대상 종류다.

```text
CULTIVATION  → 특정 재배
INQUIRY      → 특정 문의
USER         → 특정 사용자 계정
```

예를 들면 `targetType=CULTIVATION`, `targetId=12`는 “12번 재배에 관한 알림”이라는 뜻이다.

### 7.3 `NotificationEventType`

파일: `domain/NotificationEventType.java`

이벤트 종류를 정의한다.

```text
SENSOR_ERROR
HARVEST_COMPLETED
DAILY_FEEDBACK_COMPLETED
LOGIN_SUCCEEDED
```

이벤트마다 어떤 대상 종류가 자연스러운지 연결한다.

```text
SENSOR_ERROR → CULTIVATION
LOGIN_SUCCEEDED → USER
```

주의할 점: DB 컬럼 이름은 과거 Migration 호환성 때문에 `target_type`이지만, 실제 내용은 `subscription_target_type.id`를 참조하는 FK다. 의미상 `target_type_id`처럼 동작한다.

---

## 8. 구독 관련 Entity: “누가 무엇을 어디로 받을까?”

### 8.1 Endpoint

파일: `domain/NotificationEndpoint.java`

Endpoint는 **실제 알림을 받을 주소**다.

```text
Telegram: Chat ID
Discord: Webhook URL
```

예시:

```text
사용자 7
  ├─ Endpoint 1: Telegram 개인 알림방
  └─ Endpoint 2: Discord 가족 재배방
```

주요 필드:

| 필드 | 뜻 |
|---|---|
| `userId` | 이 Endpoint를 소유한 사용자 ID. 다른 서비스 DB FK는 아님 |
| `channelType` | Telegram인지 Discord인지 |
| `destination` | 실제 Chat ID 또는 Webhook URL |
| `displayName` | 사용자가 구분할 별칭 |
| `enabled` | 잠시 알림을 켜고 끄는 상태 |
| `deleted` | 목록에서 숨기는 소프트 삭제 상태 |

#### `enabled`와 `deleted`의 차이

```text
enabled=false
→ “지금은 알림을 잠시 끈다”
→ 목록에 보이고 다시 켤 수 있다.

deleted=true
→ “이 수신 경로를 삭제한다”
→ 기본 목록에서 보이지 않는다.
→ 발송 이력 보존 때문에 DB 행 자체는 남긴다.
```

#### Endpoint 메서드

```java
public void update(String destination, String displayName)
public void changeEnabled(boolean enabled)
public void softDelete()
```

`changeEnabled`는 삭제된 Endpoint를 다시 켜지 않도록 막는다.

`softDelete`는 단순히 삭제 표시만 하는 것이 아니라 `enabled=false`도 함께 처리해 즉시 발송 대상에서 제외한다.

### 8.2 `NotificationSubscriptionType`

파일: `domain/NotificationSubscriptionType.java`

SubscriptionType은 “어떤 이벤트를 어떤 대상 종류에 대해 구독할 수 있는가”라는 **구독 상품/규칙**이다.

예시:

```text
이벤트: SENSOR_ERROR
대상 유형: CULTIVATION
이름: 센서 오류 알림
```

사용자별 구독 데이터가 아니라 시스템 기준 데이터다.

### 8.3 `SubscriptionChannel`

파일: `domain/SubscriptionChannel.java`

특정 구독 유형이 어떤 채널에서 지원되는지 연결한다.

예를 들어 “센서 오류 알림”이 Telegram과 Discord 모두에서 가능하면 두 행이 생긴다.

```text
센서 오류 알림 × Telegram
센서 오류 알림 × Discord
```

`@UniqueConstraint`가 있어 같은 구독 유형과 같은 채널 조합이 중복 저장되는 것을 막는다.

### 8.4 `NotificationSubscription`

파일: `domain/NotificationSubscription.java`

Subscription은 실제 사용자가 만든 설정이다.

```text
“나는 12번 재배의 센서 오류 알림을
 내 Telegram Endpoint로 받고 싶다.”
```

이를 테이블 관계로 보면:

```text
NotificationSubscription
├─ subscriptionType: 센서 오류 알림
├─ endpoint: 내 Telegram 알림방
├─ targetId: 재배 12번
├─ enabled: true
└─ deleted: false
```

비삭제 구독의 중복은 DB의 Unique Index가 막는다.

```text
같은 subscriptionType + endpoint + targetId
그리고 deleted=false
→ 중복 생성 불가
```

일시정지(`enabled=false`)한 구독은 새 행을 만들지 않고 기존 행을 다시 활성화한다.

소프트 삭제한 과거 구독은 일반 조회와 비삭제 UNIQUE 대상에서 제외된다.

---

## 9. Notification과 Delivery: 원본 사건과 실제 발송을 분리한 이유

### 9.1 `Notification`

파일: `domain/Notification.java`

Notification은 Notification 서비스가 수신한 **사건 원본**이다.

```text
Rule이 “재배 12의 온도 센서 오류” 이벤트를 발행
→ Notification 한 건 생성
```

주요 필드:

| 필드 | 뜻 |
|---|---|
| `sourceEventId` | RabbitMQ 이벤트의 고유 UUID. 중복 처리 방지 기준 |
| `eventPayload` | 이벤트 상세 JSON |
| `createdAt` | Notification 원본 저장 시각 |

`notification.message`는 V7 Migration에서 제거했다.

사용자에게 실제로 보낸 최종 문구는 채널별 Delivery의
`renderedMessage`에만 보관한다.

#### `sourceEventId`가 unique인 이유

메시지 브로커는 네트워크 상황이나 재시도 때문에 같은 이벤트를 다시 전달할 수 있다.

```text
같은 eventId 도착
→ Notification을 두 번 만들면 안 됨
→ source_event_id UNIQUE가 DB 수준에서 중복을 차단
```

#### JSONB와 `Map<String, Object>`

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "event_payload", columnDefinition = "jsonb")
private Map<String, Object> eventPayload;
```

이벤트마다 상세 값의 모양이 다르다.

```json
{
  "sensorType": "TEMPERATURE",
  "currentValue": 28.5,
  "thresholdMax": 22.0
}
```

모든 이벤트의 상세 컬럼을 테이블에 미리 만들면 매우 복잡해진다. PostgreSQL `JSONB`에 원본 상세 데이터를 넣고, Java에서는 `Map<String, Object>`로 다룬다.

`@JdbcTypeCode(SqlTypes.JSON)`은 Hibernate에게 “이 Map은 JSON 타입으로 DB에 저장해라”라고 알려준다.

### 9.2 `NotificationDelivery`

파일: `domain/NotificationDelivery.java`

Delivery는 **한 채널로 실제 발송하려는 한 번의 시도/이력**이다.

사용자가 Telegram과 Discord 모두를 구독했다면 Notification은 1개지만 Delivery는 2개가 된다.

```text
Notification 1개: “12번 재배 온도 이상”
  ├─ Delivery 1개: Telegram 발송
  └─ Delivery 1개: Discord 발송
```

주요 연결:

| 연결 | 의미 |
|---|---|
| `notification` | 어떤 원본 사건인지 |
| `subscription` | 어떤 사용자의 어떤 구독 때문인지 |
| `template` | 어떤 채널용 템플릿을 썼는지 |

#### 템플릿이 Delivery에 있는 이유

Telegram과 Discord의 메시지 형식은 달라질 수 있다. 따라서 템플릿은 Notification 원본에 하나만 연결하는 것이 아니라, **채널별 발송 기록인 Delivery에 연결**한다.

```text
동일 사건
→ Telegram Template으로 렌더링한 Delivery
→ Discord Template으로 렌더링한 Delivery
```

이 구조는 V3 Migration에서 확정됐다.

#### `DeliveryStatus` enum

```java
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
```

| 상태 | 뜻 |
|---|---|
| `PENDING` | 발송 전 또는 재시도 대기 |
| `SENT` | 외부 채널 발송 성공 |
| `FAILED` | 최종 실패 |

`@Enumerated(EnumType.STRING)`은 enum의 숫자 순서가 아니라 이름(`PENDING`)을 DB의 VARCHAR에 저장하게 한다. 순서가 바뀌어도 데이터 의미가 깨지지 않아 더 안전하다.

#### Delivery의 도메인 메서드

```java
markSent(providerMessageId)
markFailed(error)
increaseAttemptCount()
```

- `markSent`: 상태를 SENT로 바꾸고 외부 Provider가 준 메시지 ID와 발송 시각을 저장
- `markFailed`: 실패 상태와 오류 메시지 저장
- `increaseAttemptCount`: 최대 3회까지만 재시도 횟수 증가

이 메서드들은 현재 `DeliveryStateService`와 `DeliveryDispatchService`에서 호출된다.

Telegram·Discord Provider 발송 결과에 따라 Delivery 상태가 바뀐다.

---

## 10. `NotificationTemplate`: 메시지 양식

파일: `domain/NotificationTemplate.java`

템플릿은 이벤트와 채널에 따라 다른 메시지 양식을 저장한다.

```text
이벤트: SENSOR_ERROR
채널: TELEGRAM
본문: [센서 오류] {{cultivationName}}의 {{deviceName}} ...
```

`version`이 있는 이유는 템플릿 문구를 나중에 바꾸더라도 어떤 버전으로 발송했는지 남길 수 있기 때문이다.

다음 조합은 중복될 수 없다.

```text
이벤트 유형 + 채널 유형 + 버전
```

현재 템플릿의 `{{cultivationName}}`, `{{currentValue}}` 같은 변수는 RabbitMQ 이벤트 payload에서 가져와 치환할 예정이다.

---

## 11. `messaging` 폴더와 `DomainEvent`

파일: `messaging/DomainEvent.java`

`DomainEvent`는 RabbitMQ에서 받을 모든 이벤트의 공통 겉봉투(envelope)다.

```java
public record DomainEvent(
    UUID eventId,
    String eventType,
    String producer,
    String targetType,
    Long targetId,
    OffsetDateTime occurredAt,
    JsonNode payload
) { ... }
```

### record란

`record`는 “값을 전달하는 데이터 묶음”을 짧고 안전하게 만드는 Java 문법이다.

일반 클래스라면 생성자·getter·equals·hashCode를 많이 작성해야 하지만 record는 이를 자동 제공한다. 보통 DTO나 이벤트에 잘 어울린다.

### 각 필드의 의미

| 필드 | 의미 | 예시 |
|---|---|---|
| `eventId` | 이벤트를 전 세계적으로 구분하는 UUID | `b3f...` |
| `eventType` | 사건 코드 | `SENSOR_ERROR` |
| `producer` | 이벤트를 만든 서비스 | `rule-service` |
| `targetType` | 대상 종류 | `CULTIVATION` |
| `targetId` | 대상의 실제 ID | `12` |
| `occurredAt` | 사건 발생 시각과 시간대 | `2026-07-30T10:30:00+09:00` |
| `payload` | 사건별 상세 JSON | 센서값·수확량 등 |

### `OffsetDateTime`

시간대 정보까지 포함하는 시간 타입이다.

```text
LocalDateTime: 2026-07-30 10:30
OffsetDateTime: 2026-07-30T10:30:00+09:00
```

서비스가 여러 서버나 여러 시간대에서 동작할 때는 OffsetDateTime처럼 시간대가 있는 값이 더 안전하다.

### `JsonNode`

Jackson의 JSON 트리 타입이다. 이벤트 종류별 payload 구조가 달라도 JSON 객체를 유연하게 받을 수 있다.

### `validate()` 메서드

Consumer가 구현되면 이벤트를 받은 직후 `event.validate()`를 호출한다.

검사 내용:

- `eventId`가 있는가
- 이벤트 코드·생산자·대상 타입이 비어 있지 않은가
- `targetId`가 양수인가
- 발생 시각이 있는가
- payload가 null이 아닌가

잘못된 이벤트를 초기에 막아야 DB 저장이나 외부 발송 단계까지 오류가 전파되지 않는다.

---

## 12. `repository` 폴더: DB 요청 창구

Repository는 Entity를 DB에 저장·조회하는 인터페이스다.

### `JpaRepository`

```java
public interface NotificationEndpointRepository
        extends JpaRepository<NotificationEndpoint, Long> { }
```

`JpaRepository<NotificationEndpoint, Long>`를 상속하면 Spring Data JPA가 아래 기본 기능을 자동 제공한다.

```text
save(entity)       저장 또는 수정
findById(id)       ID로 한 건 조회
findAll()          전체 조회
delete(entity)     물리 삭제 메서드
existsById(id)     존재 여부 확인
```

우리는 이 기본 기능을 직접 구현하지 않는다. Spring이 실행 중에 인터페이스의 구현체를 자동으로 만든다.

### `NotificationEndpointRepository`

```java
List<NotificationEndpoint> findAllByUserIdAndDeletedFalse(Long userId);
Optional<NotificationEndpoint> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
```

메서드 이름 자체가 쿼리 조건이다.

```text
findAllByUserIdAndDeletedFalse(7)
→ user_id=7 AND is_deleted=false 인 Endpoint 목록 조회
```

두 번째 메서드는 Endpoint ID만 아는 사용자가 다른 사람의 Endpoint를 조회·수정하는 것을 막기 위한 기본 소유권 확인에 쓰인다.

`Optional`은 “값이 있을 수도 없을 수도 있다”는 Java 타입이다. 조회 결과가 없을 때 `null`을 무심코 쓰다가 생기는 오류를 줄인다.

### `NotificationSubscriptionRepository`

```java
List<NotificationSubscription> findAllByEndpoint_UserIdAndDeletedFalse(Long userId);
Optional<NotificationSubscription> findByIdAndEndpoint_UserIdAndDeletedFalse(Long id, Long userId);
boolean existsBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndEnabledTrueAndDeletedFalse(...);
```

`Endpoint_UserId`처럼 밑줄을 쓰면 연관 관계를 타고 들어간 조건을 뜻한다.

```text
Subscription → Endpoint → userId
```

마지막 `existsBy...`는 활성 구독을 새로 만들기 전에 같은 조건의 구독이 이미 있는지 확인하는 용도다.

DB 부분 Unique Index도 같은 중복을 막으므로, Repository 검사와 DB 제약이 이중 안전장치가 된다.

```text
Service 검사: 사용자에게 친절한 “이미 존재합니다” 오류 제공
DB Unique Index: 동시 요청이 와도 실제 중복 저장 방지
```

---

## 13. `db/migration`: DB를 버전으로 관리하는 방법

### Flyway란

Flyway는 SQL 파일을 버전 순서대로 실행해 DB 구조를 맞추는 도구다.

```text
V1__create_notification_tables.sql
V2__seed_notification_reference_data.sql
V3__move_template_reference_to_delivery.sql
V4__add_endpoint_soft_delete.sql
V5__fix_active_subscription_unique_index.sql
```

Spring Boot가 시작할 때 Flyway는 `flyway_schema_history`라는 관리 테이블을 보고, 아직 실행하지 않은 Migration만 순서대로 실행한다.

### 왜 SQL을 한 파일만 고치지 않을까

이미 팀원 DB나 운영 DB에 V1이 실행된 뒤 V1 파일을 바꾸면, 사람마다 DB 구조가 달라질 수 있다.

그래서 원칙은 다음과 같다.

```text
이미 공유·실행된 Migration은 가급적 수정하지 않는다.
변경이 필요하면 V3, V4처럼 새 Migration을 추가한다.
```

---

## 14. V1: 최초 테이블 생성

파일: `V1__create_notification_tables.sql`

V1은 Notification 서비스가 소유하는 PostgreSQL 테이블을 만든다.

### 핵심 원칙: 다른 서비스 DB를 FK로 연결하지 않는다

`notification_endpoint.user_id`, `notification_subscription.target_id`는 숫자를 보관하지만 Auth나 Cultivation DB 테이블로 FK를 걸지 않는다.

이유는 MSA에서 각 서비스가 자신의 DB를 독립적으로 소유하기 때문이다.

```text
Notification DB → Auth DB를 직접 FK로 연결하지 않음
Notification DB → Cultivation DB를 직접 FK로 연결하지 않음
```

다른 서비스의 데이터가 실제로 존재하는지는 JWT 소유권 확인, 이벤트 계약, 필요한 내부 API를 통해 확인한다.

### V1 테이블을 그룹으로 보기

| 그룹 | 테이블 |
|---|---|
| 기준 코드 | `channel_type`, `subscription_target_type`, `notification_event_type` |
| 구독 정책 | `notification_subscription_type`, `subscription_channel` |
| 메시지 양식 | `notification_template` |
| 사용자 설정 | `notification_endpoint`, `notification_subscription` |
| 사건·발송 이력 | `notification`, `notification_delivery` |

### Constraint란

Constraint는 DB가 데이터 규칙을 강제로 지키게 하는 장치다.

| 종류 | 예 |
|---|---|
| PRIMARY KEY | 한 행을 고유하게 구분 |
| FOREIGN KEY | 존재하지 않는 참조 대상 연결 방지 |
| UNIQUE | 중복 코드·중복 조합 방지 |
| CHECK | 상태값·횟수 범위 제한 |
| NOT NULL | 필수값 누락 방지 |

예를 들어 Delivery의 상태에는 DB CHECK 제약이 있다.

```text
PENDING, SENT, FAILED 이외 값은 저장 불가
```

`attempt_count`도 0~3 범위만 허용한다.

---

## 15. V2: Seed 데이터

파일: `V2__seed_notification_reference_data.sql`

Seed 데이터는 앱이 처음 실행돼도 기본 코드와 템플릿이 존재하도록 넣는 기준 데이터다.

V2가 넣는 것:

- 채널: Telegram, Discord
- 대상 유형: 재배, 문의, 사용자
- 이벤트 10종
- 구독 유형
- 모든 구독 유형에서 지원하는 채널
- 이벤트×채널별 기본 템플릿

### `ON CONFLICT`

```sql
ON CONFLICT (code) DO UPDATE
```

동일 코드가 이미 있을 때 INSERT 오류로 끝내지 않고, 필요한 항목을 UPDATE한다. 이를 **idempotent(여러 번 실행해도 결과가 안정적인)** Seed라고 한다.

Seed는 개발 DB를 새로 만들거나 테스트할 때 매우 중요하다. 기준 코드가 없으면 사용자는 Endpoint나 Subscription을 만들 수 없다.

---

## 16. V3: 템플릿 FK 위치 변경

파일: `V3__move_template_reference_to_delivery.sql`

처음 V1에서는 Notification이 template를 참조했다. 그러나 한 사건이 Telegram과 Discord로 서로 다른 형식으로 발송될 수 있다.

```text
기존 생각: Notification 하나 → Template 하나
문제: 채널별 템플릿이 다를 수 있음
수정: Delivery마다 Template 하나
```

V3는 안전하게 다음 순서로 구조를 바꾼다.

```text
1. notification_delivery에 notification_template_id 추가
2. 기존 Notification의 template ID를 Delivery로 복사
3. Delivery의 template ID를 NOT NULL로 변경
4. Delivery → Template FK 생성
5. Notification의 기존 FK 제거
6. Notification의 template 컬럼 제거
```

기존 데이터가 있어도 가능한 한 잃지 않고 구조를 바꾸는 Migration 방식이다.

---

## 17. V4와 V5: 소프트 삭제·중복 구독 정책 보완

### V4: Endpoint 소프트 삭제 추가

Endpoint에는 처음 `enabled`만 있었지만, “잠시 끄기”와 “삭제”는 다른 행동이다.

```sql
ALTER TABLE notification_endpoint
ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
```

이후 Endpoint는 `enabled`와 `is_deleted`를 모두 가진다.

### V5: 활성 구독만 중복 금지

초기 Unique Index는 소프트 삭제나 비활성 구독까지 새 구독 생성을 막을 수 있었다.

V5는 아래 조건일 때만 중복을 막게 변경했다.

```text
is_deleted=false AND enabled=true
```

즉, 사용자가 예전에 끈 구독이나 삭제한 구독이 있어도 다시 새 구독을 만들 수 있다.

---

## 18. `application.yml`: 실행 환경의 설정 파일

파일: `src/main/resources/application.yml`

YAML은 들여쓰기로 구조를 표현하는 설정 파일 형식이다.

### `spring.application.name`

```yaml
spring:
  application:
    name: notification-service
```

Spring 애플리케이션의 이름이다. 로그, 모니터링, 서비스 탐색 설정에서 사용될 수 있다.

주의: CI/CD 임시 배포 이름 `notification-server`와 Spring 애플리케이션 이름 `notification-service`는 현재 서로 다르다. Config 중앙 배포 등록 전 최종 명명 규칙은 인프라 회의에서 확정해야 한다.

### DataSource: PostgreSQL 연결

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/notification_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

`${이름:기본값}`의 뜻:

```text
환경 변수 DB_URL이 있으면 그 값을 사용
없으면 jdbc:postgresql://localhost:5432/notification_db 사용
```

이 방식은 코드 수정 없이 로컬·테스트·운영 환경마다 DB 주소를 바꾸기 위해 사용한다.

운영 비밀번호는 `application.yml`에 직접 쓰지 않고 Kubernetes Secret 등의 환경 변수로 주입해야 한다.

### JPA 설정

```yaml
jpa:
  open-in-view: false
  hibernate:
    ddl-auto: validate
```

#### `open-in-view: false`

웹 요청이 끝날 때까지 DB 연결을 오래 잡고 있지 않게 한다. LAZY 연관 객체를 Controller에서 무심코 접근하는 설계를 줄이는 데 도움이 된다.

#### `ddl-auto: validate`

Hibernate가 테이블을 자동으로 만들거나 수정하지 않고, **Entity와 DB 구조가 맞는지 검사만** 한다.

```text
테이블 생성·변경: Flyway Migration
일치 검사: Hibernate validate
```

운영 DB 구조를 코드가 마음대로 바꾸지 않게 하므로 안전하다.

### Flyway 설정

```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: false
```

`classpath:db/migration`은 빌드된 애플리케이션 내부의 `src/main/resources/db/migration` 경로를 뜻한다.

`baseline-on-migrate: false`는 기존 DB를 자동으로 기준선 처리하지 않겠다는 뜻이다. 실수로 이미 존재하는 DB를 Flyway가 “관리 중”이라고 오해하지 않도록 명시적으로 다룬다.

### RabbitMQ 설정

```yaml
rabbitmq:
  host: ${RABBITMQ_HOST:localhost}
  port: ${RABBITMQ_PORT:5672}
```

Spring AMQP가 RabbitMQ에 연결할 때 사용할 기본값이다.

현재 `NotificationEventConsumer`가 `@RabbitListener`로 Queue를 구독한다.
메시지를 역직렬화하고 이벤트를 저장한 뒤 생성된 Delivery를 채널 Provider로 발송한다.

운영 host, vhost, exchange, queue, routing key는 팀 RabbitMQ 회의에서 확정한 값을 환경 변수 또는 별도 설정으로 넣어야 한다.

### 서버 포트

```yaml
server:
  port: ${SERVER_PORT:8080}
```

환경 변수 `SERVER_PORT`가 없으면 Spring Boot 웹 서버는 8080 포트에서 시작한다.

### Actuator Health

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

Actuator는 Spring Boot 앱의 상태를 확인하는 기능이다.

| 주소 | 용도 |
|---|---|
| `/actuator/health` | 전체 상태 확인 |
| `/actuator/health/liveness` | 프로세스가 살아 있는지 |
| `/actuator/health/readiness` | 트래픽을 받을 준비가 됐는지 |

Kubernetes는 readiness가 성공한 Pod에만 요청을 보내도록 구성할 수 있다.

### Logging

```yaml
logging:
  level:
    site.yesaido.notification_server: INFO
    org.flywaydb: INFO
```

우리 패키지와 Flyway의 INFO 이상 로그를 보여준다.

현재 Consumer와 Dispatch Service는 `eventId`, `eventType`, `deliveryId`,
`channel`, 성공·실패 결과를 로그로 남긴다.

배포 환경에서는 이 로그를 중앙 로그 시스템에서 검색할 수 있도록 연결한다.

---

## 19. 실제 이벤트 처리 흐름

현재 구현된 이벤트 처리 흐름은 다음과 같다.

```text
1. Rule Service가 SENSOR_ERROR 이벤트 발행
2. RabbitMQ가 Notification Consumer에 전달
3. JSON을 DomainEvent로 변환
4. DomainEvent.validate() 실행
5. source_event_id로 중복 확인
6. Notification 원본 저장
7. targetType·targetId에 맞는 활성 Subscription 조회
8. Endpoint의 channelType에 맞는 Template 선택
9. Delivery를 PENDING 상태로 저장
10. Telegram 또는 Discord Provider 호출
11. 성공이면 SENT, 실패면 재시도 후 FAILED 저장
```

### 예시

```json
{
  "eventId": "11111111-1111-1111-1111-111111111111",
  "eventType": "SENSOR_ERROR",
  "producer": "rule-service",
  "targetType": "CULTIVATION",
  "targetId": 12,
  "occurredAt": "2026-07-30T10:30:00+09:00",
  "payload": {
    "cultivationName": "느타리 1차 재배",
    "deviceName": "온도 센서 01",
    "errorMessage": "센서 응답 없음"
  }
}
```

이 이벤트가 오면:

```text
Notification: 1건 생성
Subscription: 재배 12번의 센서 오류 알림을 켠 사용자 조회
Delivery: Telegram·Discord 구독 수만큼 생성
Template: SENSOR_ERROR + 해당 채널 조합 선택
발송: 성공·실패 이력 저장
```

---

## 20. 현재 코드에서 꼭 기억할 점

1. `domain`은 DB Entity와 업무 규칙을 담는다.
2. `messaging/DomainEvent`는 RabbitMQ 이벤트의 공통 DTO다.
3. `repository`는 SQL을 직접 쓰지 않고 메서드 이름으로 기본 조회를 만들 수 있다.
4. `db/migration`은 DB 변경 이력이며 실행된 옛 파일을 함부로 고치지 않는다.
5. `application.yml`은 코드가 아니라 실행 환경을 바꾸는 설정이다.
6. `Notification`은 사건 원본, `NotificationDelivery`는 채널별 실제 발송이다.
7. `enabled`는 일시정지, `deleted`는 소프트 삭제다.
8. Entity는 setter 대신 의미 있는 메서드로 상태를 바꾼다.
9. 다른 서비스 DB에 FK를 걸지 않는 것이 MSA 경계 원칙이다.
10. 현재는 API·Consumer·Service·Provider·재시도·DLQ까지 구현되었고,
    남은 핵심은 팀 공통 RabbitMQ 계약과 실제 배포 환경 연동이다.

---

## 21. 스스로 확인해 볼 질문

1. Notification이 1개이고 Delivery가 2개가 될 수 있는 상황은 무엇인가?
2. `source_event_id` UNIQUE가 없으면 어떤 중복 문제가 생기는가?
3. `enabled=false`와 `is_deleted=true`는 왜 분리했는가?
4. Flyway V3가 Notification의 template FK를 Delivery로 옮긴 이유는 무엇인가?
5. `@ManyToOne(fetch = LAZY)`는 왜 쓰는가?
6. `ddl-auto: validate`와 Flyway의 역할은 어떻게 다른가?
7. `DomainEvent`는 왜 Entity가 아니라 record인가?
8. `targetId`가 다른 서비스 DB FK가 아닌 이유는 무엇인가?
9. `NotificationEndpointRepository`의 메서드 이름이 SQL 조건으로 바뀌는 원리는 무엇인가?
10. RabbitMQ Consumer를 구현하기 전에 Producer와 반드시 합의해야 할 내용은 무엇인가?

## 22. 관련 파일 바로가기

- [시작 클래스](../../src/main/java/site/yesaido/notification_server/NotificationServiceApplication.java)
- [domain 폴더](../../src/main/java/site/yesaido/notification_server/domain)
- [DomainEvent](../../src/main/java/site/yesaido/notification_server/messaging/DomainEvent.java)
- [repository 폴더](../../src/main/java/site/yesaido/notification_server/repository)
- [application.yml](../../src/main/resources/application.yml)
- [Migration 폴더](../../src/main/resources/db/migration)
- [Notification 도메인 문서](./notification.md)
- [Notification DB 문서](./notification-db.md)
- [Notification API 문서](./notification-api.md)

---

# 2부. 현재 구현된 코드를 실제 흐름으로 읽기

## 23. 2026-07-31 기준 구현 상태

이 절부터는 과거 계획이 아니라 현재 파일에 존재하는 코드를 설명한다.

현재 Notification Service는 다음 기능을 가지고 있다.

- Spring Boot 애플리케이션 실행
- PostgreSQL 연결
- Flyway V1~V7 실행
- JPA Entity 검증
- Endpoint 생성·조회·수정·일시정지·소프트 삭제
- Subscription 생성·조회·일시정지·소프트 삭제
- Subscription Type 조회
- 사용자 발송 이력 조회
- RabbitMQ 이벤트 수신
- 이벤트 JSON 역직렬화
- 이벤트 필수값 검증
- 중복 이벤트 방지
- 이벤트 유형과 대상 유형 검증
- 활성 구독 조회
- 채널별 최신 템플릿 조회
- 템플릿 변수 치환
- Delivery 생성
- Telegram 발송
- Discord 발송
- 최대 3회 재시도
- 성공·실패 상태 저장
- 최종 실패 메시지 DLQ 발행
- 공통 예외 응답
- 단위 테스트
- PostgreSQL 통합 테스트
- JaCoCo 커버리지 검사

아직 팀과 최종 합의가 필요한 부분도 있다.

- 실제 RabbitMQ Exchange 이름
- 실제 Queue 이름
- 실제 Routing Key 규칙
- RabbitMQ Virtual Host
- Producer별 최종 이벤트 JSON
- Kubernetes Deployment 이름
- Gateway 최종 API 경로
- Gateway의 `X-User-Id` 덮어쓰기 방식
- 운영 Telegram Bot Token 주입 방식
- 운영 Discord Webhook 등록 정책

코드가 존재한다는 것과 외부 시스템 연결이 끝났다는 것은 다르다.

현재 코드는 자체 테스트가 가능한 수준으로 구현되어 있다.

실제 팀 통합은 공통 계약이 확정된 뒤 진행한다.

---

## 24. 패키지 구조를 지도처럼 읽기

현재 기본 패키지는 다음과 같다.

```text
site.yesaido.notification_server
```

점은 폴더 경계를 나타낸다.

실제 폴더로 보면 다음과 같다.

```text
site/
└── yesaido/
    └── notification_server/
```

`site.yesaido`는 팀의 공통 패키지 영역이다.

`notification_server`는 이 서비스만의 영역이다.

Spring Boot 시작 클래스가 이 최상위 패키지에 있다.

그래서 하위 패키지를 자동으로 스캔할 수 있다.

### `config`

설정값을 Java 객체와 Spring Bean으로 바꾼다.

주요 파일은 다음 두 개다.

- `NotificationProperties`
- `NotificationRabbitConfig`

### `controller`

HTTP 요청이 가장 먼저 도착하는 애플리케이션 계층이다.

Controller는 긴 업무 로직을 직접 수행하지 않는다.

입력값을 받고 Service를 호출한다.

Service 결과를 HTTP 응답으로 바꾼다.

### `domain`

DB와 연결되는 Entity가 모여 있다.

상태 변경 규칙도 이곳에 둔다.

예를 들어 Endpoint 삭제는 단순한 필드 변경처럼 보인다.

하지만 코드에서는 `softDelete()`라는 의미 있는 메서드로 표현한다.

### `dto`

클라이언트와 주고받는 데이터 모양이다.

Entity를 그대로 API에 노출하지 않기 위해 별도로 둔다.

### `exception`

예상 가능한 오류를 이름 있는 예외로 표현한다.

공통 오류 응답도 이곳에서 만든다.

### `messaging`

RabbitMQ 메시지를 받거나 실패 메시지를 다시 발행한다.

HTTP가 아닌 비동기 입력 경로다.

### `provider`

Telegram과 Discord라는 외부 서비스에 요청을 보낸다.

외부 API 차이를 Service 바깥으로 분리한다.

### `repository`

DB 조회와 저장의 입구다.

Spring Data JPA가 구현체를 자동 생성한다.

### `service`

여러 Repository와 Domain 객체를 묶어 하나의 업무를 완성한다.

트랜잭션 경계도 이곳에서 관리한다.

### `template`

DB 템플릿과 이벤트 payload를 조합한다.

최종 사용자 메시지를 만드는 역할이다.

---

## 25. 입력 경로는 두 가지다

Notification Service에는 크게 두 종류의 입력이 들어온다.

첫 번째는 사용자가 보내는 HTTP 요청이다.

두 번째는 다른 서비스가 보내는 RabbitMQ 이벤트다.

### HTTP 입력

사용자는 다음과 같은 일을 한다.

- Telegram 수신 경로 등록
- Discord 수신 경로 등록
- 수신 경로 목록 조회
- 수신 경로 이름과 주소 수정
- 수신 경로 잠시 끄기
- 수신 경로 삭제
- 원하는 알림 구독
- 구독 잠시 끄기
- 구독 삭제
- 발송 이력 조회

흐름은 다음과 같다.

```text
Browser
→ Front
→ Gateway
→ Notification Controller
→ Service
→ Repository
→ PostgreSQL
```

### RabbitMQ 입력

다른 서비스에서 중요한 사건이 발생한다.

예를 들면 Rule Service가 센서 오류를 판단한다.

Rule Service는 Notification API를 직접 호출하지 않는다.

RabbitMQ에 이벤트를 발행한다.

흐름은 다음과 같다.

```text
Producer Service
→ RabbitMQ Exchange
→ Notification Queue
→ NotificationEventConsumer
→ NotificationEventService
→ TemplateRenderer
→ NotificationDelivery
→ Telegram 또는 Discord
```

두 입력 경로는 서로 다른 목적을 가진다.

HTTP는 사용자의 설정 관리다.

RabbitMQ는 실제 사건 처리와 발송이다.

---

## 26. Gateway와 `X-User-Id`

사용자는 로그인하면 JWT를 받는다.

JWT는 사용자의 신원을 증명하는 토큰이다.

하지만 Notification Service가 JWT를 다시 해석하지는 않는다.

팀에서 정한 책임은 다음과 같다.

```text
Gateway
1. JWT 서명 검증
2. 만료 여부 검증
3. JWT의 사용자 식별자 추출
4. 기존 X-User-Id 요청 헤더 제거
5. 검증한 사용자 ID로 X-User-Id 생성
6. 내부 서비스로 전달
```

Notification Service는 다음 헤더를 받는다.

```http
X-User-Id: 27
```

Controller에서는 다음처럼 사용한다.

```java
@RequestHeader("X-User-Id") @Positive Long userId
```

`@RequestHeader`는 HTTP 헤더에서 값을 읽는다.

`"X-User-Id"`는 헤더 이름이다.

`Long userId`는 숫자로 변환된 사용자 ID다.

`@Positive`는 1 이상의 숫자만 허용한다.

여기서 매우 중요한 보안 조건이 있다.

외부 사용자가 Notification Service에 직접 접근하면 안 된다.

왜냐하면 외부 사용자가 임의로 다음 헤더를 만들 수 있기 때문이다.

```http
X-User-Id: 1
```

따라서 운영 환경에서는 Gateway만 Notification Service에 접근할 수 있어야 한다.

Gateway는 사용자가 보낸 `X-User-Id`를 그대로 믿으면 안 된다.

기존 값을 제거하고 검증한 JWT 값으로 덮어써야 한다.

### `/api/v1`의 의미

현재 API는 다음 접두사를 사용한다.

```text
/api/v1
```

`api`는 API 경로라는 뜻이다.

`v1`은 첫 번째 공개 계약 버전이라는 뜻이다.

Controller와 Gateway 경로는 반드시 같아야 한다.

Notification Controller가 `/api/v1/notifications`를 받는다면,
Gateway도 `/api/v1/notifications/**`를 라우팅해야 한다.

Gateway만 `v1`을 빼면 요청이 매칭되지 않는다.

`v1`을 제거하려면 다음을 동시에 바꿔야 한다.

- Gateway route
- Controller `@RequestMapping`
- Front 요청 주소
- API 문서
- 테스트

---

## 27. Controller를 읽는 기본 방법

Controller 클래스 위에는 보통 세 가지 어노테이션이 있다.

```java
@Validated
@RestController
@RequestMapping("/api/v1/...")
```

### `@RestController`

이 클래스가 HTTP API를 제공한다고 Spring에 알려준다.

메서드 반환값은 기본적으로 JSON 응답이 된다.

### `@RequestMapping`

Controller 전체의 공통 URL을 정한다.

예를 들어 다음과 같다.

```java
@RequestMapping("/api/v1/notification-endpoints")
```

이 Controller의 모든 메서드는 이 경로에서 시작한다.

### `@Validated`

메서드 매개변수의 검증 어노테이션을 활성화한다.

`@Positive Long userId` 같은 검증이 동작한다.

### 생성자 주입

Controller는 Service를 필드로 가진다.

```java
private final NotificationEndpointService endpointService;
```

생성자에서 받는다.

```java
public NotificationEndpointController(
        NotificationEndpointService endpointService
) {
    this.endpointService = endpointService;
}
```

Spring이 Service 구현체를 찾아 전달한다.

이를 의존성 주입이라고 한다.

Controller가 직접 `new NotificationEndpointServiceImpl(...)`을 하지 않는다.

이 방식은 테스트하기 쉽다.

구현체 교체도 쉽다.

---

## 28. Endpoint Controller 한 줄씩 이해하기

Endpoint는 알림을 실제로 받을 주소다.

Telegram에서는 Chat ID다.

Discord에서는 Webhook URL이다.

### 생성 API

```java
@PostMapping
```

HTTP POST 요청을 받는다.

전체 경로는 다음과 같다.

```text
POST /api/v1/notification-endpoints
```

매개변수는 두 개다.

```java
@RequestHeader("X-User-Id") @Positive Long userId
```

Gateway가 전달한 사용자 ID다.

```java
@Valid @RequestBody EndpointCreateRequest request
```

JSON 요청 본문을 DTO로 바꾼다.

`@Valid`는 DTO 필드 검증을 수행한다.

Service 호출은 다음과 같다.

```java
EndpointResponse response = endpointService.create(userId, request);
```

생성 성공 후 `201 Created`를 반환한다.

```java
return ResponseEntity.created(location).body(response);
```

`Location` 헤더에는 생성된 리소스 주소가 들어간다.

### 목록 조회 API

```text
GET /api/v1/notification-endpoints
```

현재 로그인 사용자의 삭제되지 않은 Endpoint만 조회한다.

### 수정 API

```text
PATCH /api/v1/notification-endpoints/{endpointId}
```

`PATCH`는 리소스 일부를 변경한다는 의미다.

현재는 destination과 displayName을 변경한다.

### 일시정지 API

```text
PATCH /api/v1/notification-endpoints/{endpointId}/enabled
```

`enabled=false`면 잠시 사용하지 않는다.

삭제된 것은 아니다.

나중에 `enabled=true`로 다시 켤 수 있다.

### 삭제 API

```text
DELETE /api/v1/notification-endpoints/{endpointId}
```

응답은 `204 No Content`다.

DB 행을 실제로 지우지 않는다.

`is_deleted=true`로 바꾼다.

과거 발송 이력의 FK를 유지하기 위해서다.

---

## 29. Subscription Controller 이해하기

Subscription은 “어떤 알림을 어떤 Endpoint로 받을 것인가”라는 설정이다.

예시는 다음과 같다.

```text
사용자 27
재배 101의 센서 오류 알림
내 Discord Endpoint로 수신
```

### 생성

```text
POST /api/v1/notification-subscriptions
```

요청에는 보통 다음 값이 있다.

- 구독 종류 ID
- Endpoint ID
- 대상 ID

구독 종류는 “센서 오류 알림” 같은 기준 데이터다.

Endpoint는 실제 수신 주소다.

대상 ID는 재배 ID, 문의 ID 또는 사용자 ID다.

### 목록 조회

```text
GET /api/v1/notification-subscriptions
```

본인이 소유한 Endpoint에 연결된 구독만 반환한다.

### 일시정지

```text
PATCH /api/v1/notification-subscriptions/{subscriptionId}/enabled
```

`enabled=false`는 일시정지다.

같은 구독을 다시 생성하면 기존 행을 찾아 활성화한다.

### 삭제

```text
DELETE /api/v1/notification-subscriptions/{subscriptionId}
```

`is_deleted=true`로 바꾼다.

삭제된 구독은 일반 목록과 활성 구독 조회에서 제외된다.

---

## 30. Notification Controller 이해하기

클래스 이름은 `NotificationController`다.

하지만 현재 반환하는 핵심 데이터는 Delivery다.

경로는 다음과 같다.

```text
GET /api/v1/notifications
```

사용자는 본인의 실제 발송 이력을 확인한다.

Service 호출은 다음과 같다.

```java
queryService.findDeliveries(userId)
```

왜 Notification 원본이 아니라 Delivery를 조회할까?

Notification은 사건 원본이다.

한 Notification이 Telegram과 Discord로 각각 발송될 수 있다.

사용자가 궁금한 것은 실제로 자신에게 어떤 메시지가 전송됐는지다.

따라서 Delivery 목록이 화면에 더 적합하다.

---

## 31. DTO를 쓰는 이유

DTO는 Data Transfer Object의 약자다.

데이터 전달용 객체라는 뜻이다.

Entity와 DTO를 분리한 이유는 다음과 같다.

- DB 구조를 API에 그대로 노출하지 않기 위해
- 필요한 필드만 응답하기 위해
- 요청값 검증을 한곳에 모으기 위해
- Entity 연관관계의 무한 JSON 변환을 피하기 위해
- API 계약과 DB 계약을 독립적으로 변경하기 위해

### Request DTO

사용자가 서버로 보내는 JSON을 받는다.

예시는 `EndpointCreateRequest`다.

Request DTO에는 검증 어노테이션이 붙는다.

### Response DTO

서버가 사용자에게 보내는 JSON 모양이다.

예시는 `EndpointResponse`다.

Response DTO는 보통 `from(Entity)` 정적 메서드를 가진다.

Entity를 Response로 바꾸는 책임을 한곳에 모은다.

### `record`

DTO는 Java `record`로 작성되어 있다.

record는 불변 데이터 묶음을 간결하게 표현한다.

일반 클래스에서 필요한 생성자와 getter 성격의 메서드를 자동 제공한다.

다음 두 값은 만들어진 뒤 바뀌지 않는다.

```java
public record EndpointEnabledRequest(boolean enabled) {
}
```

접근할 때는 `getEnabled()`가 아니라 `enabled()`를 사용한다.

---

## 32. Bean Validation 기초

Bean Validation은 입력값을 선언적으로 검사한다.

직접 `if`를 반복해서 쓰는 양을 줄여준다.

### `@NotNull`

값이 반드시 있어야 한다.

`null`을 허용하지 않는다.

### `@NotBlank`

문자열이 null이면 안 된다.

빈 문자열도 안 된다.

공백만 있는 문자열도 안 된다.

### `@Size`

문자열이나 컬렉션 길이를 제한한다.

DB 컬럼 길이와 함께 맞추는 것이 좋다.

### `@Positive`

숫자가 1 이상이어야 한다.

ID 값 검증에 사용한다.

### `@Valid`

중첩된 DTO 또는 Request Body 검증을 실행한다.

검증에 실패하면 Controller 메서드 안으로 들어오지 않는다.

`GlobalExceptionHandler`가 공통 오류 응답으로 바꾼다.

---

## 33. Service 인터페이스와 구현체를 나눈 이유

Service는 두 종류의 파일로 나뉜다.

첫 번째는 인터페이스다.

```text
NotificationEndpointService
NotificationSubscriptionService
NotificationEventService
NotificationQueryService
```

두 번째는 구현체다.

```text
NotificationEndpointServiceImpl
NotificationSubscriptionServiceImpl
NotificationEventServiceImpl
NotificationQueryServiceImpl
```

인터페이스는 “무엇을 할 수 있는가”를 보여준다.

구현체는 “어떻게 하는가”를 보여준다.

Controller는 구현체가 아니라 인터페이스에 의존한다.

이 구조는 테스트 대역을 만들기 쉽다.

구현 방식을 바꿔도 Controller 변경을 줄일 수 있다.

무조건 모든 Service를 인터페이스로 나눠야 하는 것은 아니다.

하지만 팀 프로젝트에서 계층 경계를 명확히 보여주는 장점이 있다.

### `@Service`

Spring에게 업무 로직 객체라고 알려준다.

Component Scan 대상이 된다.

### `@Transactional(readOnly = true)`

클래스 기본값을 조회 전용 트랜잭션으로 만든다.

조회 메서드의 의도를 분명히 한다.

쓰기 메서드에는 다시 `@Transactional`을 붙인다.

### 트랜잭션

트랜잭션은 여러 DB 작업을 하나의 작업 단위로 묶는다.

중간에 실패하면 전체를 되돌릴 수 있다.

예를 들어 Notification과 Delivery를 만들다가 템플릿 조회가 실패하면,
불완전한 일부 데이터만 남지 않도록 롤백한다.

---

## 34. Endpoint Service를 순서대로 읽기

`NotificationEndpointServiceImpl`은 Endpoint 업무 규칙을 담당한다.

### 생성 과정

1단계는 Channel Type 조회다.

```java
channelTypeRepository.findByIdAndDeletedFalse(...)
```

삭제된 채널 기준 정보는 사용할 수 없다.

찾지 못하면 `NotificationNotFoundException`을 발생시킨다.

2단계는 destination 형식 검증이다.

```java
senderRegistry.get(channel.getCode())
        .validateDestination(request.destination());
```

Telegram이면 숫자 Chat ID인지 검사한다.

Discord면 허용된 HTTPS Webhook URL인지 검사한다.

3단계는 중복 검사다.

같은 사용자가 같은 채널과 같은 destination을 중복 등록하지 못하게 한다.

삭제된 Endpoint는 중복 검사에서 제외한다.

4단계는 Entity 생성이다.

```java
new NotificationEndpoint(
        userId,
        channel,
        request.destination(),
        request.displayName())
```

5단계는 Repository 저장이다.

6단계는 Response DTO 변환이다.

### 수정 과정

먼저 본인 소유의 Endpoint인지 조회한다.

Repository 메서드 이름에 `userId`가 포함되어 있다.

다른 사용자의 Endpoint ID를 넣어도 찾을 수 없다.

새 destination 형식을 다시 검증한다.

자기 자신을 제외한 중복 Endpoint가 있는지도 확인한다.

정상이라면 Entity의 `update()`를 호출한다.

### 삭제 과정

`deleteById()`를 호출하지 않는다.

Entity의 `softDelete()`를 호출한다.

내부적으로 삭제 상태와 활성 상태를 함께 변경한다.

과거 Delivery가 Endpoint를 참조할 수 있으므로 행을 보존한다.

---

## 35. Subscription Service를 순서대로 읽기

`NotificationSubscriptionServiceImpl`은 구독 설정을 담당한다.

### Endpoint 소유권 확인

요청의 Endpoint가 로그인 사용자 소유인지 확인한다.

삭제된 Endpoint도 사용할 수 없다.

### 구독 종류 확인

`subscriptionTypeId`로 기준 정보를 찾는다.

구독 종류는 이벤트 유형과 대상 유형을 연결한다.

예를 들어 센서 오류 구독은 다음 의미를 가진다.

```text
eventType = SENSOR_ERROR
targetType = CULTIVATION
```

### USER 대상 검증

대상 유형이 USER인 경우 `targetId`는 로그인 사용자 ID와 같아야 한다.

다른 사용자의 로그인 알림을 구독하지 못하게 한다.

현재 CULTIVATION과 INQUIRY 소유권은 해당 서비스 DB를 직접 조회하지 않는다.

MSA에서 다른 서비스 DB를 직접 참조하지 않기 때문이다.

향후 Gateway나 소유 서비스 API를 통한 권한 확인 계약이 필요할 수 있다.

### 채널 지원 확인

모든 알림이 모든 채널을 지원한다고 가정하지 않는다.

`subscription_channel` 테이블을 조회한다.

구독 종류와 Endpoint 채널 조합이 허용되는지 확인한다.

허용되지 않으면 `UnsupportedNotificationChannelException`을 발생시킨다.

### 기존 구독 재활성화

같은 비삭제 구독이 이미 있다면 새 행을 만들지 않는다.

기존 구독의 `enabled`를 true로 바꾼다.

이 정책은 일시정지 후 재구독에서 중복 행이 늘어나는 것을 막는다.

### 새 구독 생성

기존 비삭제 구독이 없을 때만 새 Entity를 저장한다.

DB UNIQUE 제약도 같은 규칙을 한 번 더 보호한다.

애플리케이션 검증은 친절한 오류를 제공한다.

DB 제약은 동시 요청에서도 데이터 정합성을 지킨다.

---

## 36. Repository 메서드 이름 읽는 방법

Spring Data JPA는 메서드 이름으로 쿼리를 만든다.

예를 들어 다음 메서드를 보자.

```java
findByIdAndUserIdAndDeletedFalse
```

단어를 나누면 다음과 같다.

```text
find By
id
And userId
And deleted False
```

뜻은 다음과 같다.

```sql
WHERE id = ?
  AND user_id = ?
  AND is_deleted = false
```

### 밑줄의 의미

다음 메서드를 보자.

```java
findByIdAndEndpoint_UserIdAndDeletedFalse
```

`Endpoint_UserId`는 연관된 Endpoint의 userId를 뜻한다.

JPA 연관관계를 따라 조건을 만든다.

### `existsBy`

행 전체를 가져오는 대신 존재 여부만 확인한다.

중복 검사에 적합하다.

### `findFirstBy...OrderByVersionDesc`

조건에 맞는 데이터를 버전 내림차순으로 정렬한다.

첫 번째 행만 가져온다.

즉 가장 최신 템플릿을 선택한다.

### 명시적 `@Query`

메서드 이름만으로 복잡한 쿼리를 표현하기 어려우면 JPQL을 작성한다.

활성 구독 조회는 여러 Entity 관계와 상태 조건을 함께 사용한다.

---

## 37. 이벤트 계약이란 무엇인가

계약은 Producer와 Consumer가 함께 지켜야 하는 데이터 규칙이다.

Producer는 이벤트를 만드는 서비스다.

Consumer는 이벤트를 받는 서비스다.

Notification Service는 Consumer다.

공통 이벤트 예시는 다음과 같다.

```json
{
  "eventId": "0c8e8aa0-5ef5-4a44-ae80-82827fc722d2",
  "eventType": "HARVEST_COMPLETED",
  "producer": "cultivation-server",
  "targetType": "CULTIVATION",
  "targetId": 101,
  "occurredAt": "2026-07-31T10:00:00+09:00",
  "payload": {
    "harvestId": 55,
    "quantity": 1200,
    "unit": "g"
  }
}
```

### `eventId`

사건 한 건의 고유 식별자다.

UUID를 사용한다.

Producer가 생성한다.

같은 메시지가 RabbitMQ에서 다시 전달되어도 같은 eventId를 유지해야 한다.

### `eventType`

무슨 일이 발생했는지 나타내는 코드다.

예시는 `HARVEST_COMPLETED`다.

### `producer`

어느 서비스가 이벤트를 만들었는지 나타낸다.

### `targetType`

사건이 어느 종류의 대상을 가리키는지 나타낸다.

현재 대표 값은 다음과 같다.

- `CULTIVATION`
- `INQUIRY`
- `USER`

### `targetId`

그 대상의 실제 ID다.

`targetType=CULTIVATION`이면 cultivation ID다.

`targetType=INQUIRY`이면 inquiry ID다.

`targetType=USER`이면 user ID다.

### `occurredAt`

사건이 실제로 발생한 시각이다.

`At`은 완료라는 뜻이 아니다.

영어 변수명에서 “그 시점”을 나타내는 관습이다.

### `payload`

사건의 상세 정보다.

payload 자체가 이벤트 전체는 아니다.

이벤트 안에 포함된 세부 데이터다.

---

## 38. `DomainEvent`와 `DomainEventParser`

`DomainEvent`는 공통 이벤트 JSON의 Java 표현이다.

Java `record`로 작성되어 있다.

`eventId`는 UUID다.

`occurredAt`은 `OffsetDateTime`이다.

`payload`는 `JsonNode`다.

payload 구조가 이벤트마다 달라질 수 있기 때문이다.

### Parser의 역할

RabbitMQ는 문자열 메시지를 전달한다.

Parser는 문자열을 `DomainEvent`로 바꾼다.

Jackson의 `ObjectMapper`를 사용한다.

파싱 후 `validate()`를 호출한다.

### 파싱 오류

JSON 문법이 잘못될 수 있다.

UUID 형식이 틀릴 수 있다.

시간 형식이 틀릴 수 있다.

필수 필드가 빠질 수 있다.

이 오류를 `InvalidDomainEventException`으로 감싼다.

Consumer는 이를 처리 실패로 보고 Queue 재처리를 막는다.

---

## 39. RabbitMQ 기본 개념

RabbitMQ는 메시지 브로커다.

서비스 사이에서 메시지를 전달한다.

Producer와 Consumer를 시간적으로 분리한다.

Producer는 Notification Service가 실행 중인지 매번 확인하지 않아도 된다.

메시지를 RabbitMQ에 맡길 수 있다.

### Exchange

Producer가 메시지를 처음 보내는 곳이다.

우체국 분류 창구와 비슷하다.

### Queue

Consumer가 처리할 메시지가 쌓이는 곳이다.

우편함과 비슷하다.

### Binding

Exchange와 Queue를 연결하는 규칙이다.

### Routing Key

메시지를 어느 Queue로 보낼지 판단하는 문자열이다.

Topic Exchange에서는 패턴을 사용할 수 있다.

### `#`

0개 이상의 여러 단어와 매칭한다.

예를 들어 `notification.event.#`는 다음과 매칭할 수 있다.

```text
notification.event.sensor.error
notification.event.harvest.completed
```

### Durable

RabbitMQ가 재시작되어도 Exchange나 Queue 정의를 유지한다.

코드에서 주요 Queue와 Exchange를 durable로 만든다.

---

## 40. `NotificationRabbitConfig` 읽기

이 클래스에는 `@Configuration`이 붙어 있다.

Spring 설정 클래스라는 뜻이다.

`@EnableConfigurationProperties`는 `NotificationProperties`를 활성화한다.

### 이벤트 Exchange Bean

```java
new TopicExchange(name, true, false)
```

첫 번째 인자는 이름이다.

두 번째 true는 durable이다.

세 번째 false는 자동 삭제하지 않는다는 뜻이다.

### 이벤트 Queue Bean

Queue에는 DLX 관련 argument가 들어간다.

```text
x-dead-letter-exchange
x-dead-letter-routing-key
```

Consumer가 메시지를 거절하면 DLX로 이동할 수 있다.

### DLQ

Dead Letter Queue의 약자다.

정상 처리하지 못한 메시지를 따로 보관한다.

운영자는 DLQ를 보고 원인을 분석한다.

### Binding Bean

Exchange, Queue, Routing Key를 연결한다.

설정 이름은 `application.yml` 또는 환경 변수에서 읽는다.

---

## 41. `NotificationEventConsumer`의 전체 흐름

Consumer는 RabbitMQ 메시지 처리의 입구다.

`@Component`로 Spring Bean이 된다.

`@RabbitListener`가 Queue를 구독한다.

### 1단계: 문자열 수신

```java
public void consume(String message)
```

RabbitMQ Body가 문자열로 들어온다.

### 2단계: 파싱

```java
eventParser.parse(message)
```

JSON을 `DomainEvent`로 바꾼다.

### 3단계: 업무 처리

```java
eventService.process(event)
```

Notification과 Delivery를 생성한다.

### 4단계: 중복 분기

이미 처리한 eventId면 발송하지 않는다.

중복은 시스템 오류가 아니다.

RabbitMQ 재전송에서 발생할 수 있는 정상 분기다.

### 5단계: Delivery 발송

생성된 Delivery ID를 하나씩 Dispatch Service에 전달한다.

### 6단계: 실패 처리

RuntimeException이 발생하면 오류 로그를 남긴다.

`AmqpRejectAndDontRequeueException`을 던진다.

같은 실패 메시지를 무한히 Queue에 다시 넣지 않기 위해서다.

Queue의 DLX 설정에 따라 DLQ로 이동한다.

### 로그에 payload 원문을 남기지 않는 이유

payload에는 사용자 정보나 민감한 값이 들어갈 수 있다.

그래서 eventId와 이벤트 유형 같은 추적 정보만 남기는 편이 안전하다.

---

## 42. `NotificationEventServiceImpl` 핵심 알고리즘

이 Service는 이벤트 한 건을 DB의 Notification과 Delivery로 바꾼다.

### 중복 이벤트 검사

```java
existsBySourceEventId(event.eventId())
```

이미 존재하면 `duplicateEvent()` 결과를 반환한다.

DB에도 UNIQUE 제약이 있다.

애플리케이션 검사와 DB 제약을 함께 사용한다.

### 이벤트 유형 조회

`eventType` 문자열을 기준 테이블에서 찾는다.

등록되지 않은 코드는 계약 위반이다.

Seed에 없는 임의 이벤트는 처리하지 않는다.

### 대상 유형 검증

이벤트 유형 기준 정보에는 기대하는 Target Type이 연결되어 있다.

예를 들어 `HARVEST_COMPLETED`는 `CULTIVATION`이어야 한다.

실제 메시지가 `USER`로 오면 계약 오류다.

### 활성 구독 조회

다음 조건을 만족하는 구독만 찾는다.

- 이벤트 유형 일치
- 대상 유형 일치
- targetId 일치
- 구독 enabled=true
- 구독 is_deleted=false
- Endpoint enabled=true
- Endpoint is_deleted=false

### Notification 저장

Notification은 사건 원본 기록이다.

현재 보관 필드는 다음과 같다.

- `id`
- `source_event_id`
- `event_payload`
- `created_at`

과거의 `notification.message`는 V7에서 제거했다.

### 왜 `notification.message`를 제거했는가

채널별 최종 문구가 다를 수 있기 때문이다.

Telegram과 Discord에 같은 사건을 다른 형식으로 보낼 수 있다.

공통 message는 실제 발송 내용을 정확히 나타내지 못한다.

그래서 최종 문구는 Delivery에만 저장한다.

### Delivery 반복 생성

활성 구독마다 다음을 수행한다.

1. 채널별 최신 템플릿 조회
2. 이벤트 payload를 템플릿에 치환
3. NotificationDelivery 생성
4. Delivery ID 수집

한 사건에 구독이 3개라면 Delivery도 3개 생길 수 있다.

---

## 43. Notification과 Delivery를 다시 구분하기

이 구분은 알림 서비스 이해의 핵심이다.

### Notification

시스템에서 사건이 한 번 발생했다는 원본 기록이다.

예시는 “재배 101의 센서가 오프라인이 되었다”다.

### Delivery

그 Notification을 특정 사용자의 특정 채널로 보낸 한 건이다.

예시는 “사용자 27의 Discord Webhook으로 발송했다”다.

### 관계

```text
Notification 1
├── Delivery 1: 사용자 A Telegram
├── Delivery 2: 사용자 A Discord
└── Delivery 3: 사용자 B Telegram
```

### 최종 메시지 위치

실제 발송한 문구는 다음 필드에 있다.

```text
notification_delivery.rendered_message
```

`notification.message`는 존재하지 않는다.

ERD에서도 Notification의 message 행을 제거해야 한다.

Delivery의 rendered_message는 제거하면 안 된다.

이 값은 “그때 실제로 무엇을 보냈는가”를 증명하는 이력이다.

---

## 44. `TemplateRenderer` 이해하기

템플릿은 다음처럼 생길 수 있다.

```text
[센서 오류] {{deviceName}}에서 오류가 발생했습니다.
현재 값: {{currentValue}}{{unit}}
```

이벤트 payload는 다음과 같을 수 있다.

```json
{
  "deviceName": "온도센서 1",
  "currentValue": 29.5,
  "unit": "℃"
}
```

렌더링 결과는 다음과 같다.

```text
[센서 오류] 온도센서 1에서 오류가 발생했습니다.
현재 값: 29.5℃
```

### 정규식

Renderer는 `{{변수명}}` 형태를 찾는다.

공백이 있어도 처리한다.

```text
{{deviceName}}
{{ deviceName }}
```

### `flatten`

중첩 JSON을 점 표기법으로 평평하게 만든다.

예를 들어 다음 payload를 보자.

```json
{
  "sensor": {
    "name": "온도센서 1"
  }
}
```

다음 변수로 바뀐다.

```text
sensor.name = 온도센서 1
```

템플릿에서는 다음처럼 쓴다.

```text
{{sensor.name}}
```

### 공통 변수

payload 밖의 이벤트 정보도 변수로 제공한다.

- `eventId`
- `eventType`
- `producer`
- `targetType`
- `targetId`
- `occurredAt`

### 누락 변수

템플릿에는 변수가 있는데 payload에 값이 없을 수 있다.

빈 문자열로 조용히 보내지 않는다.

`TemplateRenderingException`을 발생시킨다.

잘못된 알림을 보내는 것보다 실패를 기록하는 편이 안전하다.

---

## 45. Provider 패턴 이해하기

Telegram과 Discord는 요청 형식이 다르다.

Service 안에 `if TELEGRAM`, `if DISCORD`를 계속 쓰면 코드가 복잡해진다.

그래서 공통 인터페이스를 둔다.

```java
public interface NotificationSender {
    String channelCode();
    void validateDestination(String destination);
    ProviderSendResult send(String destination, String message);
}
```

### `channelCode`

Provider가 담당하는 채널 코드를 알려준다.

Telegram은 `TELEGRAM`이다.

Discord는 `DISCORD`다.

### `validateDestination`

수신 주소가 안전하고 올바른 형식인지 확인한다.

Endpoint 생성·수정 시에도 사용한다.

발송 직전에도 다시 사용한다.

### `send`

외부 API를 호출한다.

성공하면 외부 서비스의 메시지 ID를 반환할 수 있다.

### Sender Registry

Spring이 모든 `NotificationSender` 구현체 목록을 주입한다.

Registry는 채널 코드를 Key로 보관한다.

호출자는 채널 코드만 알고 Sender를 선택할 수 있다.

새 채널을 추가할 때 기존 Dispatch 로직 변경을 줄일 수 있다.

---

## 46. Telegram Sender

Telegram은 Bot API를 사용한다.

수신 주소는 Chat ID다.

### Chat ID 검증

Chat ID는 숫자 문자열로 제한한다.

그룹 Chat ID는 음수일 수 있어 앞의 `-`를 허용한다.

### Bot Token

Bot Token은 비밀값이다.

코드나 Git에 넣으면 안 된다.

환경 변수 `TELEGRAM_BOT_TOKEN`으로 주입한다.

Token이 비어 있으면 발송을 시도하지 않고 명확한 예외를 발생시킨다.

### API 요청

HTTP POST를 사용한다.

요청 Body에는 `chat_id`와 `text`가 들어간다.

### 응답 처리

Telegram 응답의 `result.message_id`를 읽는다.

이 값을 `provider_message_id`에 저장할 수 있다.

### 오류 감싸기

외부 API 오류를 `NotificationProviderException`으로 변환한다.

상위 Dispatch Service는 채널 세부 구현을 몰라도 재시도할 수 있다.

---

## 47. Discord Sender와 보안

Discord는 Webhook URL로 메시지를 보낸다.

Webhook URL 전체가 수신 주소다.

### 왜 URL 검증이 중요한가

사용자가 임의 URL을 등록할 수 있으면 서버가 내부 주소를 호출할 수 있다.

이를 SSRF 위험이라고 한다.

### 현재 검증 조건

- scheme이 HTTPS
- host가 허용 목록에 존재
- userInfo가 없음
- path가 `/api/webhooks/`로 시작

허용 Host 기본값은 다음과 같다.

- `discord.com`
- `discordapp.com`

운영 정책에 따라 환경 변수로 조정할 수 있다.

### `wait=true`

Discord Webhook 호출에 `wait=true`를 붙인다.

그래야 생성된 메시지 응답을 받을 수 있다.

응답의 `id`를 Provider Message ID로 저장한다.

### Webhook 로그 금지

Discord Webhook URL에는 인증 성격의 토큰이 포함된다.

전체 URL을 로그에 남기면 안 된다.

DB 접근과 운영 로그 권한도 제한해야 한다.

---

## 48. 재시도 구조

외부 API는 일시적으로 실패할 수 있다.

네트워크 지연이 생길 수 있다.

Telegram 또는 Discord가 잠시 오류를 반환할 수 있다.

한 번 실패했다고 바로 최종 실패로 처리하면 사용자 경험이 나빠진다.

그래서 최대 3회 시도한다.

### 왜 무한 재시도를 하지 않는가

영구적으로 잘못된 Chat ID일 수 있다.

삭제된 Webhook일 수 있다.

외부 서비스 장애가 길어질 수 있다.

무한 재시도는 자원과 로그를 계속 소비한다.

### 최대 횟수 방어

설정값이 0이어도 최소 1회는 시도한다.

설정값이 10이어도 현재 정책상 최대 3회로 제한한다.

### Backoff

재시도 사이에 기다리는 시간이다.

기본값은 1초다.

즉시 연속 호출로 외부 서비스를 압박하지 않게 한다.

---

## 49. `DeliveryDispatchService`

이 클래스는 한 Delivery를 실제로 발송한다.

### 시작

`dispatch(deliveryId)`를 호출한다.

### 시도 횟수 증가

`stateService.startAttempt(deliveryId)`를 호출한다.

DB의 attemptCount가 증가한다.

필요한 발송 정보가 `DeliveryCommand`로 반환된다.

### Sender 선택

채널 코드로 Registry에서 Sender를 찾는다.

### 외부 발송

destination과 renderedMessage를 Sender에 전달한다.

### 성공

Delivery 상태를 `SENT`로 바꾼다.

Provider Message ID와 sentAt을 기록한다.

### 실패

RetryTemplate이 정해진 횟수만큼 다시 실행한다.

최종 실패하면 상태를 `FAILED`로 바꾼다.

실패 원인을 저장한다.

실패 정보를 Dead Letter Publisher로 보낸다.

### 로그

성공 로그에는 deliveryId와 channel을 남긴다.

최종 실패 로그에는 deliveryId와 원인 예외를 남긴다.

destination이나 Token은 남기지 않는다.

---

## 50. `DeliveryStateService`와 `REQUIRES_NEW`

발송 시도 상태는 별도 트랜잭션으로 저장한다.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
```

`REQUIRES_NEW`는 항상 새로운 트랜잭션을 시작한다.

### 왜 별도 트랜잭션인가

외부 API 호출이 실패해도 시도 횟수는 DB에 남아야 한다.

큰 트랜잭션 하나에 모두 묶으면 예외로 인해 시도 기록까지 롤백될 수 있다.

그래서 다음 상태 변경을 각각 독립적으로 저장한다.

- 시도 횟수 증가
- 성공 상태 저장
- 실패 상태 저장

### `DeliveryCommand`

Entity를 외부 발송 계층에 그대로 넘기지 않는다.

필요한 값만 담은 Command를 반환한다.

- deliveryId
- channelCode
- destination
- message

이렇게 하면 Transaction 밖에서 LAZY Entity를 잘못 접근할 가능성을 줄인다.

### 오류 문자열 정리

오류 메시지가 null이거나 공백이면 기본 문구를 사용한다.

너무 긴 오류는 1,000자로 자른다.

DB에 무제한 내부 오류 문자열이 쌓이는 것을 막는다.

---

## 51. Delivery 상태 머신

Delivery는 상태를 가진다.

대표 상태는 다음과 같다.

```text
PENDING
SENT
FAILED
```

### PENDING

아직 발송 완료되지 않은 상태다.

Delivery 생성 시 기본 상태다.

### SENT

외부 채널 발송에 성공했다.

sentAt이 기록된다.

### FAILED

최대 재시도 후에도 실패했다.

error가 기록된다.

### BOOLEAN을 쓰지 않는 이유

성공 true, 실패 false만으로는 “아직 대기 중”을 표현할 수 없다.

그래서 VARCHAR 또는 Enum을 사용한다.

상태가 늘어날 가능성도 있다.

예를 들어 향후 `SENDING`을 추가할 수 있다.

### 도메인 메서드

상태 변경은 Setter로 하지 않는다.

의미 있는 메서드를 사용한다.

```text
increaseAttemptCount()
markSent()
markFailed()
```

잘못된 상태 전이는 `InvalidDeliveryStateException`으로 막는다.

---

## 52. 두 종류의 Dead Letter

알림 서비스에는 실패 메시지가 두 맥락에서 나타난다.

### 입력 이벤트 DLQ

RabbitMQ에서 받은 원본 이벤트를 처리할 수 없을 때 사용한다.

예시는 잘못된 JSON이다.

예시는 등록되지 않은 eventType이다.

Consumer가 메시지를 거절한다.

Queue 설정에 의해 DLQ로 이동한다.

### 발송 최종 실패 메시지

이벤트 파싱과 DB 저장은 성공했지만 외부 발송이 실패할 수 있다.

최대 재시도 후 Delivery는 FAILED가 된다.

`DeadLetterPublisher`가 deliveryId와 실패 원인을 발행한다.

두 실패를 구분해야 운영 분석이 쉽다.

원본 이벤트 실패는 계약 또는 처리 문제다.

Delivery 실패는 외부 채널 또는 destination 문제일 가능성이 크다.

---

## 53. 공통 예외 처리

Controller마다 try-catch를 반복하지 않는다.

`GlobalExceptionHandler`가 한곳에서 처리한다.

`@RestControllerAdvice`는 모든 Controller에 적용되는 예외 처리기다.

### 400 Bad Request

요청값 형식이 잘못되었다.

필수값이 없을 수 있다.

양수가 아닌 ID일 수 있다.

지원하지 않는 채널 조합일 수 있다.

### 404 Not Found

리소스가 없을 수 있다.

다른 사용자 소유여서 조회되지 않을 수도 있다.

소유권 정보를 노출하지 않기 위해 같은 404로 처리할 수 있다.

### 409 Conflict

이미 같은 Endpoint가 있다.

중복 리소스 생성 시도다.

### 502 Bad Gateway

Telegram 또는 Discord 외부 호출이 실패했다.

### 500 Internal Server Error

예상하지 못한 서버 내부 오류다.

사용자 응답에 stack trace를 보여주지 않는다.

서버 로그에는 원인 예외를 남긴다.

---

## 54. `application.yml`을 환경별로 읽기

YAML은 들여쓰기로 구조를 표현한다.

탭 대신 공백을 사용한다.

### 애플리케이션 이름

```yaml
spring:
  application:
    name: notification-server
```

서비스 검색과 배포 이름 기준으로 사용한다.

Gateway의 URI도 다음 이름을 사용한다.

```text
lb://notification-server
```

### DB 설정

환경 변수가 있으면 환경 변수 값을 사용한다.

없으면 로컬 기본값을 사용한다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

운영 비밀번호를 YAML 기본값에 작성하면 안 된다.

### RabbitMQ 설정

```text
RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
RABBITMQ_VHOST
```

팀 통합 시 인프라 담당자가 제공한 값으로 주입한다.

### Notification Rabbit 설정

Exchange, Queue, Routing Key를 별도 namespace로 관리한다.

코드에 문자열을 여러 번 하드코딩하지 않는다.

### Provider 설정

Telegram Base URL과 Bot Token을 관리한다.

Discord 허용 Host 목록을 관리한다.

### Retry 설정

최대 시도 횟수와 Backoff를 관리한다.

현재 코드가 최대 3회로 한 번 더 제한한다.

### Eureka와 Kubernetes

환경 변수로 서비스 검색 기능을 켜거나 끌 수 있다.

로컬 테스트에서는 보통 false다.

배포 방식이 확정되면 인프라 설정에 맞춘다.

---

## 55. Flyway V1부터 V7까지

Migration 파일은 시간 순서대로 DB 구조가 어떻게 변했는지 보여준다.

### V1

최초 Notification 테이블들을 생성했다.

FK, UNIQUE, INDEX도 함께 만들었다.

### V2

기준 Seed 데이터를 넣었다.

채널, 대상 유형, 이벤트 유형, 구독 종류, 템플릿이 포함된다.

### V3

Template FK를 Notification에서 Delivery로 옮겼다.

채널별 템플릿을 정확히 기록하기 위해서다.

### V4

Endpoint에 `is_deleted`를 추가했다.

`enabled`와 삭제 상태를 분리했다.

### V5

활성 구독 중복 방지 Index를 보완했다.

### V6

일시정지 구독을 다시 사용할 수 있도록 비삭제 구독 UNIQUE 정책으로 정리했다.

### V7

`notification.message`를 제거했다.

최종 발송 문구는 Delivery의 `rendered_message`에만 보관한다.

### 기존 Migration을 수정하지 않는 이유

누군가 이미 V1을 실행했을 수 있다.

V1 내용을 바꾸면 기존 DB와 새 DB가 서로 달라진다.

Flyway checksum 오류도 발생할 수 있다.

따라서 변경은 항상 다음 버전으로 추가한다.

---

## 56. Seed와 구현의 차이

Seed는 DB에 미리 넣는 기준 데이터다.

구현은 그 데이터를 사용해 실제 행동을 수행하는 Java 코드다.

예를 들어 `SENSOR_ERROR` Seed가 있다고 하자.

이것만으로 알림이 자동 발송되지는 않는다.

다음 구현이 함께 있어야 한다.

- Consumer가 이벤트 수신
- Event Service가 유형 조회
- Subscription 조회
- Template 조회
- Renderer가 메시지 생성
- Provider가 외부 발송
- Delivery 결과 저장

반대로 Java 코드만 있고 Seed가 없으면 어떻게 될까?

Event Service가 `SENSOR_ERROR` 기준 정보를 찾지 못한다.

등록되지 않은 이벤트 유형 오류가 발생한다.

Seed와 구현은 서로 다른 역할이며 둘 다 필요하다.

---

## 57. 테스트 구조

테스트는 “코드가 있어 보인다”를 확인하는 것이 아니다.

원하는 조건에서 실제 결과가 맞는지 검증한다.

### Domain Test

Entity 상태 변경 규칙을 확인한다.

Endpoint 일시정지와 삭제를 확인한다.

Subscription 재활성화를 확인한다.

Delivery 상태 전이를 확인한다.

### Parser Test

정상 JSON을 DomainEvent로 바꾸는지 확인한다.

필수 필드 누락을 거절하는지 확인한다.

잘못된 targetId를 거절하는지 확인한다.

### Repository Test

실제 PostgreSQL 쿼리를 확인한다.

삭제되지 않은 본인 Endpoint만 조회되는지 확인한다.

일시정지 구독 재사용 조건을 확인한다.

### Service Test

Repository를 Mock으로 대체한다.

업무 흐름과 예외 분기를 빠르게 검증한다.

### Sender Test

Telegram Chat ID 검증을 확인한다.

Discord Webhook URL 검증을 확인한다.

### Dispatch Test

성공 시 SENT가 되는지 확인한다.

실패 후 재시도 횟수를 확인한다.

최종 실패 시 DLQ 발행을 확인한다.

### Consumer Test

중복 이벤트를 다시 발송하지 않는지 확인한다.

생성된 Delivery를 Dispatch하는지 확인한다.

---

## 58. 테스트 명령 해석하기

일반 검증 명령은 다음과 같다.

```bash
mvn clean verify
```

### `mvn`

Maven을 실행한다.

### `clean`

이전 target 결과물을 지운다.

### `verify`

컴파일, 테스트, 패키징, 검증 단계까지 수행한다.

현재 JaCoCo 검사도 포함된다.

### PostgreSQL 통합 테스트

통합 테스트 옵션을 true로 설정한다.

실제 PostgreSQL URL과 계정을 전달한다.

이 테스트는 Flyway와 JPA validate를 함께 확인한다.

### 테스트 결과 읽기

다음 값이 중요하다.

```text
Failures: 0
Errors: 0
```

Skipped가 있다면 이유를 확인해야 한다.

통합 테스트 옵션이 꺼져 의도적으로 건너뛴 것인지 확인한다.

---

## 59. JaCoCo 커버리지

JaCoCo는 테스트가 코드의 어느 부분을 실행했는지 측정한다.

커버리지가 높다고 버그가 없다는 뜻은 아니다.

하지만 전혀 테스트되지 않은 영역을 찾는 데 도움이 된다.

### Line Coverage

실행된 코드 줄 비율이다.

### Branch Coverage

if문의 true와 false 같은 분기가 각각 실행됐는지 본다.

### 커버리지를 올리는 올바른 방법

숫자만 올리기 위한 의미 없는 테스트를 만들지 않는다.

실제 위험이 큰 분기를 우선 테스트한다.

- 권한 오류
- 중복 데이터
- 삭제 데이터
- 외부 발송 실패
- 재시도
- 상태 전이
- 잘못된 이벤트 계약

---

## 60. 초보자가 코드를 읽는 순서

한 번에 모든 파일을 읽지 않는다.

다음 순서를 추천한다.

1. `application.yml`
2. `NotificationServiceApplication`
3. Controller 하나
4. 해당 Service 인터페이스
5. Service 구현체
6. 사용되는 Repository
7. 관련 Entity
8. Request·Response DTO
9. 관련 테스트
10. Migration

예를 들어 Endpoint 생성을 공부한다면 다음 순서다.

```text
NotificationEndpointController.create
→ NotificationEndpointService.create
→ NotificationEndpointServiceImpl.create
→ ChannelTypeRepository
→ NotificationEndpointRepository
→ NotificationEndpoint Entity
→ EndpointCreateRequest
→ EndpointResponse
→ NotificationEndpointServiceImplTest
```

메서드 안에서 모르는 클래스를 만날 때 한 단계만 따라간다.

너무 깊게 계속 이동하면 전체 흐름을 잃기 쉽다.

먼저 입력과 출력부터 찾는다.

그다음 중간 검증을 하나씩 읽는다.

---

## 61. 디버깅할 때 확인할 순서

### 애플리케이션이 시작하지 않을 때

1. 첫 번째 `Caused by`를 찾는다.
2. DB 연결 주소를 확인한다.
3. PostgreSQL이 실행 중인지 확인한다.
4. Flyway 오류인지 확인한다.
5. JPA validate 오류인지 확인한다.
6. RabbitMQ 연결 오류인지 확인한다.
7. 환경 변수가 빠졌는지 확인한다.

### API가 404일 때

1. Controller `@RequestMapping` 확인
2. HTTP Method 확인
3. Gateway path 확인
4. `/api/v1` 포함 여부 확인
5. 서비스 이름 확인
6. Gateway route의 `lb://notification-server` 확인

### API가 400일 때

1. Request JSON 필드 확인
2. `X-User-Id` 헤더 확인
3. DTO 검증 조건 확인
4. ID가 양수인지 확인
5. ErrorResponse 코드 확인

### DB 데이터가 조회되지 않을 때

1. userId 조건 확인
2. enabled 확인
3. is_deleted 확인
4. FK ID 확인
5. Repository 메서드 조건 확인
6. 트랜잭션 롤백 여부 확인

### RabbitMQ 이벤트가 처리되지 않을 때

1. Exchange 이름 확인
2. Queue 이름 확인
3. Binding 확인
4. Routing Key 확인
5. Vhost 확인
6. Consumer 로그 확인
7. eventId 형식 확인
8. eventType Seed 확인
9. targetType 일치 확인
10. DLQ 확인

### 외부 발송이 실패할 때

1. Endpoint destination 형식 확인
2. Telegram Bot Token 확인
3. Discord 허용 Host 확인
4. 외부 API 응답 확인
5. attemptCount 확인
6. Delivery status 확인
7. error 확인
8. 최종 실패 Queue 확인

---

## 62. 로그를 남길 때의 원칙

로그는 운영 중 사건을 추적하는 기록이다.

무조건 많이 남기는 것이 좋은 것은 아니다.

필요한 식별자와 상태를 남긴다.

### 이벤트 로그

- eventId
- eventType
- targetType
- targetId

### 발송 로그

- notificationId
- deliveryId
- channel
- attemptCount
- 성공 여부
- 재시도 여부

### 남기지 말아야 할 값

- JWT 원문
- 비밀번호
- Telegram Bot Token
- Discord Webhook 전체 URL
- Telegram Chat ID 전체 값
- 민감 payload 원문

### 로그 레벨

`DEBUG`는 상세 개발 정보다.

`INFO`는 정상적인 주요 상태 변화다.

`WARN`은 복구 가능하지만 주의가 필요한 상황이다.

`ERROR`는 최종 실패나 운영 대응이 필요한 상황이다.

중복 이벤트는 보통 정상 분기다.

최종 발송 실패는 ERROR에 가깝다.

---

## 63. 자주 혼동하는 용어 정리

### 이벤트

이미 발생한 일에 대한 기록이다.

명령이 아니다.

`HARVEST_COMPLETED`는 “수확을 완료하라”가 아니다.

“수확이 완료되었다”는 뜻이다.

### Payload

이벤트 안의 상세 데이터다.

이벤트 전체와 같은 뜻이 아니다.

### Producer

이벤트를 발행하는 서비스다.

### Consumer

이벤트를 소비하는 서비스다.

### Endpoint

실제 알림을 받을 주소다.

API Endpoint와 이름이 같아 혼동할 수 있다.

이 문서에서 Notification Endpoint는 Telegram Chat ID나 Discord Webhook을 뜻한다.

### Subscription

어떤 알림을 받을지 정한 사용자 설정이다.

### Template

최종 메시지를 만들기 위한 문장 양식이다.

### Renderer

템플릿의 변수를 실제 값으로 바꾸는 코드다.

### Sender

완성된 메시지를 외부 채널로 보내는 코드다.

### Notification

사건 한 건의 원본 저장 기록이다.

### Delivery

특정 구독과 채널로 발송한 한 건의 기록이다.

### Migration

DB 구조 변경을 버전 순서대로 실행하는 SQL이다.

### Seed

애플리케이션이 시작할 때 필요한 기준 데이터다.

### Repository

DB 조회와 저장을 담당하는 Java 인터페이스다.

### DTO

계층 또는 시스템 사이에서 전달하는 데이터 모양이다.

### Entity

DB 테이블 행과 연결되는 Java 객체다.

### DLQ

정상 처리하지 못한 메시지를 보관하는 Queue다.

### Idempotency

같은 요청을 여러 번 처리해도 결과가 한 번 처리한 것과 같도록 만드는 성질이다.

`source_event_id` UNIQUE가 중복 발송을 막는 핵심이다.

---

## 64. 코드 리뷰 때 설명할 수 있어야 하는 질문

### 왜 Notification과 Delivery를 분리했나요?

한 사건이 여러 사용자와 여러 채널로 발송될 수 있기 때문이다.

### 왜 Template FK가 Delivery에 있나요?

Telegram과 Discord가 서로 다른 템플릿을 사용할 수 있기 때문이다.

### 왜 `notification.message`를 제거했나요?

공통 메시지의 의미가 애매하고 실제 발송 문구는 채널별 Delivery에 있기 때문이다.

### 왜 `rendered_message`를 저장하나요?

템플릿이 나중에 바뀌어도 당시 실제 발송 문구를 보존하기 위해서다.

### 왜 Endpoint에 enabled와 is_deleted가 둘 다 있나요?

일시정지와 삭제의 의미가 다르기 때문이다.

### 왜 소프트 삭제를 사용하나요?

과거 Delivery와의 관계 및 감사 이력을 보존하기 위해서다.

### 왜 JWT를 Notification에서 검증하지 않나요?

Gateway가 인증 책임을 맡기로 했기 때문이다.

### `X-User-Id`를 믿어도 되나요?

외부 직접 접근을 차단하고 Gateway가 검증한 값으로 덮어쓸 때만 믿을 수 있다.

### 왜 다른 서비스 ID를 FK로 연결하지 않나요?

MSA에서는 서비스별 DB 소유권을 지키기 위해서다.

### 왜 최대 3회 재시도하나요?

일시 오류는 복구하되 무한 재시도로 자원을 낭비하지 않기 위해서다.

### 왜 제어 성공 알림은 보내지 않나요?

자동 제어의 일반적인 성공이 너무 자주 발생하면 중요한 경고가 묻힐 수 있기 때문이다.

제어 실패는 사용자의 대응이 필요하므로 알림 대상이다.

---

## 65. 실제 한 건의 이벤트를 끝까지 추적하기

Rule Service가 센서 오류를 판단했다고 가정한다.

### 1단계

Rule Service가 `SENSOR_ERROR` 이벤트를 만든다.

### 2단계

RabbitMQ Exchange에 이벤트를 발행한다.

### 3단계

Routing Key와 Binding이 맞는 Notification Queue로 들어간다.

### 4단계

`NotificationEventConsumer.consume()`이 문자열을 받는다.

### 5단계

`DomainEventParser`가 JSON을 `DomainEvent`로 변환한다.

### 6단계

`DomainEvent.validate()`가 필수값을 확인한다.

### 7단계

`NotificationEventServiceImpl`이 eventId 중복 여부를 확인한다.

### 8단계

`notification_event_type`에서 `SENSOR_ERROR`를 찾는다.

### 9단계

이벤트 targetType이 기준 정보와 일치하는지 확인한다.

### 10단계

해당 cultivation ID의 활성 구독을 조회한다.

### 11단계

Notification 원본을 저장한다.

### 12단계

각 구독의 채널에 맞는 최신 Template을 찾는다.

### 13단계

`TemplateRenderer`가 payload 값을 치환한다.

### 14단계

`NotificationDelivery`를 PENDING 상태로 저장한다.

### 15단계

Consumer가 Delivery ID를 Dispatch Service로 넘긴다.

### 16단계

DeliveryStateService가 attemptCount를 증가시킨다.

### 17단계

Sender Registry가 Telegram 또는 Discord Sender를 선택한다.

### 18단계

외부 API를 호출한다.

### 19단계

성공하면 Delivery를 SENT로 바꾼다.

### 20단계

실패하면 정해진 횟수만큼 재시도한다.

### 21단계

최종 실패하면 FAILED와 error를 저장한다.

### 22단계

실패 메시지를 DLQ 쪽으로 발행한다.

### 23단계

사용자는 조회 API에서 본인의 Delivery 이력을 확인한다.

---

## 66. 앞으로 실제 통합에서 확인할 체크리스트

### Gateway

- Notification Route 존재
- `/api/v1` 경로 일치
- `lb://notification-server` 일치
- JWT 검증 완료
- 외부 `X-User-Id` 제거
- 검증한 사용자 ID 헤더 추가
- Notification Service 직접 외부 접근 차단

### RabbitMQ

- Exchange 이름
- Queue 이름
- Routing Key
- Vhost
- 계정 권한
- Durable 정책
- ACK/NACK 정책
- DLX 이름
- DLQ 이름
- 재처리 정책

### Producer

- eventId 생성 책임
- eventType 코드
- producer 문자열
- targetType
- targetId 의미
- occurredAt 시간대
- payload 필드
- 필수값
- 숫자 단위
- 실패 시 재발행 정책

### PostgreSQL

- DB URL
- Schema
- 계정 권한
- Flyway 실행 권한
- V1~V7 정상 적용
- JPA validate 성공
- 운영 백업 정책

### Telegram

- Bot 생성
- Bot Token Secret 등록
- Chat ID 등록 방법
- 테스트 메시지 발송
- 실패 응답 확인

### Discord

- Webhook 생성 방법
- 허용 Host
- Webhook URL 보호
- 테스트 메시지 발송
- 삭제된 Webhook 실패 확인

### 운영

- 중앙 로그 수집
- Health Check
- Readiness Probe
- Liveness Probe
- 실패 알림
- DLQ 모니터링
- Secret 관리

---

## 67. 마지막 복습

Notification Service는 판단 서비스가 아니다.

이미 발생한 사건을 사용자에게 전달하는 서비스다.

사용자 설정은 HTTP API로 관리한다.

실제 사건은 RabbitMQ 이벤트로 받는다.

Gateway는 JWT를 검증한다.

Notification은 `X-User-Id`로 본인 리소스만 처리한다.

Endpoint는 수신 주소다.

Subscription은 수신 설정이다.

Notification은 사건 원본이다.

Delivery는 실제 발송 한 건이다.

Template은 메시지 양식이다.

Renderer는 양식에 값을 채운다.

Sender는 외부 채널에 보낸다.

재시도는 최대 3회다.

최종 실패는 FAILED로 저장한다.

처리하지 못한 메시지는 DLQ로 보낸다.

`source_event_id`는 중복 발송을 막는다.

`notification.message`는 제거되었다.

`notification_delivery.rendered_message`는 유지한다.

Migration은 기존 파일을 고치지 않고 새 버전으로 추가한다.

현재 최신 Migration은 V7이다.

현재 API 경로는 `/api/v1`이다.

현재 서비스명은 `notification-server`다.

현재 기본 패키지는 `site.yesaido.notification_server`다.

코드를 읽을 때는 Controller에서 Service, Repository, Entity 순서로 따라간다.

RabbitMQ 흐름은 Consumer에서 Event Service, Renderer, Dispatch 순서로 따라간다.

이 전체 연결을 이해하면 Notification Service의 핵심 구조를 이해한 것이다.

---

## 68. 2026-07-31 코드 안정성 보완

오늘은 외부 이벤트 계약이나 ERD를 바꾸지 않고, 현재 코드의 운영 안정성만 보완했다.

### 68.1 중복 이벤트를 정상 처리하는 이유

RabbitMQ는 같은 메시지를 다시 전달할 수 있고, Consumer가 여러 개 실행되면 같은 이벤트가 동시에 들어올 수도 있다.

`notification.source_event_id`에는 UNIQUE 제약이 있으므로 DB에는 같은 이벤트가 두 번 저장되지 않는다. 다만 동시 저장 시도에서 발생한 UNIQUE 충돌을 일반 오류로 취급하면, 이미 정상 처리된 중복 이벤트가 불필요하게 DLQ로 이동할 수 있다.

현재 처리 순서는 다음과 같다.

```text
source_event_id 사전 확인
→ 이미 있으면 정상 중복 종료
→ 저장 중 UNIQUE 충돌 발생
→ 다시 저장 여부 확인
→ 이미 저장되어 있으면 정상 중복 종료
→ 저장되지 않았다면 실제 영속화 오류로 DLQ 처리
```

이 변경은 테이블을 새로 만들거나 제약조건을 바꾸는 작업이 아니다. 기존 UNIQUE 제약을 동시성 상황에서도 올바르게 해석하는 Consumer 처리 개선이다.

### 68.2 재시도 횟수를 한 곳에서 관리하는 이유

발송 실패 재시도 횟수는 프로젝트 정책상 최대 3회다. 이전에는 도메인 객체와 환경 설정에 각각 재시도 횟수가 존재할 수 있어 두 값이 달라질 위험이 있었다.

현재는 `NotificationDelivery.MAX_ATTEMPT_COUNT`를 단일 정책 기준으로 사용한다.

```text
1회차 시도
→ 실패하면 2회차
→ 실패하면 3회차
→ 다시 실패하면 FAILED 저장 + DLQ 발행
```

환경 설정에서는 재시도 사이의 대기 시간만 조절한다. 운영자가 임의로 5회, 10회 재시도하도록 바꾸어 도메인 상태 규칙과 불일치하는 상황을 막기 위한 결정이다.

### 68.3 Consumer 로그를 실패 유형별로 나누는 이유

모든 오류를 `processing failed` 하나로 기록하면 운영 중 원인을 찾기 어렵다. 그래서 다음 유형으로 로그를 구분한다.

| 유형 | 의미 | 처리 |
|---|---|---|
| 계약 오류 | JSON 형식, eventType, targetType 등이 계약과 다름 | 재큐잉하지 않고 DLQ |
| 기준 설정 오류 | 이벤트 유형이나 템플릿이 등록되지 않음 | 재큐잉하지 않고 DLQ, 운영 로그 확인 |
| 영속화 오류 | DB 저장 또는 제약조건 문제 | 중복 여부 확인 후 중복 또는 DLQ |
| 시스템 오류 | 예상하지 못한 내부 예외 | 오류 로그와 원인 예외 기록 후 DLQ |

로그에는 장애를 추적할 수 있도록 `eventId`, `eventType`, `targetType`, `targetId`를 남긴다. 단, Bot Token, Discord Webhook 전체 주소, Chat ID, 민감한 payload 원문은 남기지 않는다.

### 68.4 테스트 결과

2026-07-31에는 Docker PostgreSQL 통합 환경을 활성화해 다음을 확인했다.

- PostgreSQL 16.14 연결
- Flyway V1~V7 검증
- Endpoint Repository 통합 테스트
- Subscription Repository 통합 테스트
- UNIQUE 제약조건 테스트
- 전체 테스트 41개 통과
- 실패 0개, 오류 0개, 건너뛴 테스트 0개

이 테스트는 단순히 Java 객체만 검사한 것이 아니라 실제 PostgreSQL에 연결해 Migration과 Repository 쿼리를 함께 검증한 것이다.

## 69. 2026-08-03 로컬 통합 실행 확인

### 69.1 서비스별 로컬 포트

프로젝트를 하나의 애플리케이션으로 실행하는 것이 아니라 여러 Spring Boot 서비스로 나누어 실행한다. 각 서비스는 서로 다른 포트를 사용한다.

| 포트 | 서비스 | 역할 |
|---:|---|---|
| 9002 | Front Server | 사용자가 접속하는 웹 화면 |
| 8000 | Gateway | 프론트 요청을 백엔드 서비스로 전달 |
| 9003 | User Server | 회원가입·로그인·사용자 기능 |
| 9001 | Cultivation Server | 재배·센서·사진 기능 |
| 8080 | Notification Service | 알림 이벤트 수신·알림 저장·발송 |
| 8761 | Eureka | 서비스 주소 등록 및 검색 |

Front Server의 개발 설정은 `server.port: 9002`, Gateway는 `server.port: 8000`으로 되어 있다. 따라서 로컬 홈페이지는 다음 주소로 접속한다.

```text
http://localhost:9002
```

`localhost:9000`은 현재 어떤 서비스에도 지정되지 않은 포트이므로 접속하면 `ERR_CONNECTION_REFUSED`가 발생한다.

### 69.2 회원가입 확인 흐름

2026-08-03 로컬에서 홈페이지 접속과 회원가입을 확인했다.

```text
브라우저
  → Front Server :9002
  → Gateway :8000
  → User Server :9003
```

이 흐름에는 Notification Service가 포함되지 않는다. 따라서 회원가입만 확인할 때는 Notification을 실행하지 않아도 된다.

### 69.3 Notification을 실행해야 하는 시점

Notification은 다음 통합 테스트를 진행할 때 실행한다.

- RabbitMQ 이벤트를 실제로 수신할 때
- 수신 이벤트를 `notification`과 `notification_delivery`에 저장할 때
- Telegram·Discord 발송을 테스트할 때
- 알림 목록 API를 Front와 연결할 때
- 전체 서비스 간 통합 테스트를 수행할 때

Notification을 실행하면 기본적으로 PostgreSQL과 RabbitMQ도 필요하다.

```text
PostgreSQL: localhost:5432/notification_db
RabbitMQ:   localhost:5672
Notification: localhost:8080
```

DB 또는 RabbitMQ가 준비되지 않은 상태에서 Notification만 실행하면 애플리케이션 시작 실패나 연결 오류가 발생할 수 있다. 따라서 실제 RabbitMQ Exchange·Queue·Routing Key와 최종 Producer payload를 회의에서 확정한 뒤 실행·연동 테스트를 진행한다.

### 69.4 로컬 실행 판단 기준

| 확인하려는 기능 | 필요한 서비스 |
|---|---|
| 홈페이지 화면 | Front Server |
| 회원가입·로그인 | Front + Gateway + User + Eureka |
| 재배·센서 화면 | Front + Gateway + Cultivation + Eureka |
| 알림 통합 | Front + Gateway + Notification + RabbitMQ + PostgreSQL |
| 전체 시나리오 | 위 서비스 전체 |

IntelliJ 실행 목록에 서비스가 등록되어 있어도 실제 실행 여부는 각 실행 설정의 정지 버튼, 콘솔의 `Started ...` 로그, 해당 포트의 LISTEN 상태를 함께 확인한다.
