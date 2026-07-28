# Notification Service 초보자 학습 가이드

> 대상: AI 파트에서 Notification 파트로 이동한 프로젝트 구성원  
> 기준일: 2026-07-24  
> 목표: 이 문서와 IntelliJ 코드를 함께 보면서 “우리 프로젝트가 무엇을 만들고 있고, 오늘 무엇을 만들었으며, 다음에는 왜 무엇을 만드는지”를 이해한다.

---

## 0. 먼저: 지금 우리가 만드는 서비스는 무엇인가?

우리는 **버섯 재배를 도와주는 IoT + AI 서비스**를 만들고 있다.

사용자는 버섯을 재배하면서 온도, 습도, 이산화탄소, 조도 등의 센서 데이터를 본다. 센서값이 이상하거나 센서가 끊기거나, 수확이 끝나거나, AI 일일 피드백이 완성되면 사용자에게 알려줄 필요가 있다.

그 “알려주는 역할”만 전문적으로 맡는 것이 **Notification Service(알림 서비스)**다.

간단히 말하면 다음과 같다.

```text
다른 서비스가 사건을 발견한다
        ↓
"이런 일이 생겼어요"라는 이벤트를 RabbitMQ로 보낸다
        ↓
Notification Service가 이벤트를 받는다
        ↓
누가 어떤 채널로 이 알림을 받기로 했는지 확인한다
        ↓
Telegram 또는 Discord로 보낸다
        ↓
성공/실패와 재시도 이력을 DB에 저장한다
```

예를 들어 온도 28℃가 측정됐다고 하자.

- **Sensor/Data Generator**: 온도 28℃라는 데이터를 만든다.
- **Rule Service**: "이 재배의 적정 온도보다 높다"고 판단한다.
- **Rule Service**: `ENVIRONMENT_THRESHOLD_BREACHED` 이벤트를 RabbitMQ에 보낸다.
- **Notification Service**: 재배 12번의 환경 이상 알림을 구독한 사용자를 찾는다.
- **Notification Service**: Telegram/Discord 메시지를 만들고 발송한다.

중요한 분리 원칙은 아래 한 줄이다.

> Notification Service는 **판단하지 않고 전달한다.**

즉, "온도가 높은가?", "센서가 고장인가?", "물을 켜야 하는가?"는 Rule Service의 일이다. Notification은 Rule이 이미 판단해서 보낸 결과를 사용자에게 안전하게 전달한다.

---

## 1. 프로젝트 전체 구조부터 이해하기

### 1.1 서비스들이 하는 일

| 서비스 | 쉬운 설명 | 알림과의 관계 |
|---|---|---|
| Auth Service | 회원가입, 로그인, OAuth 인증 | 로그인 성공 이벤트 발행 |
| Cultivation Service | 재배 생성·종료, 수확, 사진, 재배 멤버 관리 | 수확 완료·재배 종료 이벤트 발행 |
| Sensor Service / Data Generator | 센서 데이터를 실제 또는 가상으로 수집·생성 | Rule 판단의 입력 데이터 제공 |
| Rule Service | 센서값과 임계값을 비교하고 이상·복구·제어 실패 판단 | 환경 이상·센서 오류·제어 실패 이벤트 발행 |
| AI Service | AI 일일 피드백, 챗봇, 성장 관련 AI 기능 | 일일 피드백 생성 완료 이벤트 발행 |
| Notification Service | 이벤트를 사용자 메시지로 바꿔 외부 채널에 전달 | 이번에 우리가 구현하는 서비스 |
| Gateway | 프론트 요청을 적절한 서비스로 전달 | 알림 API의 진입점 |
| RabbitMQ | 서비스끼리 이벤트를 전달하는 우편함/택배 시스템 | Notification이 이벤트를 받는 통로 |
| PostgreSQL | 각 서비스가 자기 데이터를 저장하는 DB | Notification의 구독·발송 이력 저장소 |

### 1.2 왜 서비스를 나눴나?

한 프로그램 안에 모든 기능을 넣으면 처음에는 쉬워 보이지만, 나중에는 한 부분의 수정이 다른 부분을 망가뜨리기 쉽다.

예를 들어 Telegram 발송이 실패했다고 Rule Service까지 멈추면 안 된다. 온도 이상 판단은 계속 되어야 한다. 그래서 다음처럼 책임을 나눴다.

```text
Rule Service: "이상이다"를 판단
Notification Service: "누구에게, 어디로, 몇 번 재시도해서 보낼지" 담당
```

이 구조를 **서비스 경계**라고 한다. 각 서비스는 자기 책임에 집중하고, 다른 서비스와는 이벤트나 API 계약으로만 연결한다.

### 1.3 각 서비스가 DB를 직접 공유하지 않는 이유

Notification Service가 Cultivation DB의 테이블을 직접 조회하면 처음엔 편할 수 있다. 하지만 그러면 두 서비스가 강하게 묶인다.

문제 예시:

- Cultivation 테이블 이름이나 컬럼이 바뀌면 Notification도 같이 깨진다.
- DB 권한을 여러 서비스에 열어야 한다.
- 어떤 서비스가 어떤 데이터를 바꿨는지 책임이 흐려진다.

그래서 Notification은 다른 서비스의 DB를 직접 보지 않는다. 필요한 사실은 다음 두 방식으로 받는다.

- RabbitMQ 이벤트: "수확이 완료됐습니다"
- 필요한 경우 다른 서비스의 API: "이 사용자가 이 재배에 권한이 있나요?"

---

## 2. Notification Service의 전체 처리 흐름

가장 대표적인 "환경 이상 알림"을 예로 들면 다음과 같다.

```text
[센서 데이터]
온도 28.5℃
       ↓
[Rule Service]
임계값 18~22℃보다 높음 판단
       ↓ RabbitMQ 이벤트
ENVIRONMENT_THRESHOLD_BREACHED
targetType=CULTIVATION, targetId=12
       ↓
[Notification Consumer]
이벤트 형식 검증 + eventId 중복 확인
       ↓
[구독 조회]
재배 12번의 환경 이상을 구독한 활성 Endpoint 조회
       ↓
[메시지 생성]
"느타리버섯 1차 재배의 온도 값이 정상 범위를 벗어났습니다"
       ↓
[Delivery 생성]
Telegram/Discord별 발송 대기 기록 생성
       ↓
[외부 발송]
Telegram Bot API 또는 Discord Webhook 호출
       ↓
[결과 저장]
SENT 또는 FAILED, 시도 횟수, 오류 메시지 저장
```

여기서 오늘 한 일은 **가운데와 아래 단계가 나중에 안전하게 동작할 수 있도록 DB의 빈 그릇과 기준 데이터를 마련한 것**이다.

아직 하지 않은 일은 다음이다.

- RabbitMQ에서 실제 이벤트 받기
- 구독을 조회해서 알림 원본과 Delivery 만들기
- Telegram·Discord로 실제 발송하기
- 실패 재시도하기

---

## 3. 오늘 한 작업을 한 번에 정리하기

오늘 만든 것은 눈에 보이는 화면 기능이 아니라, Notification Service의 **기초 공사**다.

### 3.1 오늘 완료한 항목

1. Spring Boot + Maven 프로젝트 구조 생성
2. PostgreSQL 연결 설정
3. Flyway DB Migration 설정
4. Notification 전용 테이블 10개 생성
5. 테이블과 연결되는 JPA Entity 작성
6. Telegram·Discord, 이벤트 코드, 템플릿 같은 기준 데이터 Seed 작성
7. Docker PostgreSQL 빈 DB에서 Migration과 Seed가 실제로 동작하는지 검증
8. Seed를 다시 실행해도 데이터가 중복되지 않는지 검증

### 3.2 오늘 아직 하지 않은 항목

이것은 실패한 것이 아니라 다음 단계의 작업이다.

- Repository 작성
- Endpoint/Subscription API
- RabbitMQ Consumer
- Event DTO
- 구독 조회
- 메시지 템플릿 치환 기능
- Telegram/Discord Sender
- 재시도 기능
- Controller/API 보안
- 통합 테스트

---

## 4. IntelliJ에서 프로젝트를 읽는 순서

처음부터 모든 파일을 열면 헷갈리므로 아래 순서대로 보면 좋다.

```text
1. pom.xml
2. application.yml
3. V1__create_notification_tables.sql
4. V2__seed_notification_reference_data.sql
5. domain 패키지의 Entity 파일
6. Notification_역할분담_및_개발실행계획.md
7. 나중에 추가될 Repository → Service → Consumer → Sender 순서
```

현재 파일 위치는 다음과 같다.

```text
Notification_service/
├── pom.xml
├── Notification_역할분담_및_개발실행계획.md
├── Notification_초보자_학습가이드.md   ← 지금 읽고 있는 문서
├── GROK_PROJECT_CONTEXT.md
├── docs/
│   ├── 서영님_Endpoint_Subscription_구현안내.md
│   └── reference/
├── src/main/java/com/ecosphere/notification/
│   ├── NotificationServiceApplication.java
│   └── domain/
│       ├── AuditEntity.java
│       ├── ChannelType.java
│       ├── NotificationEndpoint.java
│       ├── NotificationSubscription.java
│       ├── Notification.java
│       └── ...
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_notification_tables.sql
        └── V2__seed_notification_reference_data.sql
```

---

## 5. Maven과 `pom.xml`: 프로젝트의 재료 목록

파일: `pom.xml`

### 5.1 Maven이란?

Java 프로젝트에는 Spring Boot, PostgreSQL 드라이버, RabbitMQ 라이브러리처럼 외부 재료가 필요하다. Maven은 이 재료를 다운로드하고, 프로젝트를 빌드·테스트하는 도구다.

`pom.xml`은 "우리 프로젝트에 어떤 재료가 필요한지" 적는 파일이다.

### 5.2 현재 주요 의존성

| 의존성 | 하는 일 | 왜 필요한가 |
|---|---|---|
| `spring-boot-starter-web` | REST API 서버 기능 | Endpoint·Subscription API를 만들 때 필요 |
| `spring-boot-starter-data-jpa` | Java 객체와 DB 테이블 연결 | Entity와 Repository를 사용하기 위해 필요 |
| `spring-boot-starter-amqp` | RabbitMQ 연결·Consumer 기능 | 다른 서비스 이벤트를 받기 위해 필요 |
| `spring-boot-starter-validation` | 요청값 검증 | URL, 이름, ID 등의 입력 검증에 필요 |
| `spring-boot-starter-actuator` | health 같은 운영 상태 확인 | 서비스가 살아 있는지 확인할 때 필요 |
| `flyway-core` | DB Migration 관리 | DB 테이블 생성 이력을 관리 |
| `postgresql` | PostgreSQL JDBC 드라이버 | Java가 PostgreSQL에 접속하기 위해 필요 |
| `lombok` | Getter, 생성자 등 반복 코드 감소 | Entity 코드가 짧아짐 |
| `spring-boot-starter-test` | 테스트 도구 | 단위/통합 테스트 작성 |
| `spring-rabbit-test` | RabbitMQ 테스트 지원 | Consumer 테스트 |
| `testcontainers` | 테스트용 Docker DB 실행 | 빈 DB 통합 테스트 |

### 5.3 Java 21

`<java.version>21</java.version>`은 이 프로젝트가 Java 21을 기준으로 빌드된다는 뜻이다. 팀의 Java 버전이 다르면 빌드가 실패할 수 있으므로 통일해야 한다.

---

## 6. `application.yml`: 실행 환경의 설정 지도

파일: `src/main/resources/application.yml`

이 파일은 코드가 "어떤 DB에 붙을지", "몇 번 포트로 서버를 열지", "RabbitMQ는 어디에 있는지"를 알려준다.

### 6.1 Database 설정

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/notification_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

읽는 방법:

- `DB_URL` 환경변수가 있으면 그 값을 사용한다.
- 없으면 `jdbc:postgresql://localhost:5432/notification_db`를 사용한다.
- `${변수:기본값}` 문법은 "환경변수 우선, 없으면 기본값"이다.

이 방식이 필요한 이유는 내 컴퓨터, 팀 개발 서버, 운영 서버의 DB 주소가 다르기 때문이다. 비밀번호를 코드에 고정하지 않고 실행 환경에서 바꿀 수 있다.

오늘 Docker 검증용 PostgreSQL은 호스트 포트 `55432`를 사용했으므로, 검증할 때는 다음처럼 기본값을 덮어썼다.

```text
DB_URL=jdbc:postgresql://localhost:55432/notification_db
```

### 6.2 JPA의 `ddl-auto: validate`

```yaml
jpa:
  hibernate:
    ddl-auto: validate
```

이 설정은 매우 중요하다.

- `create`: 앱이 실행될 때 테이블을 새로 만들 수 있음
- `update`: Hibernate가 테이블을 추측해서 수정할 수 있음
- `validate`: 테이블을 바꾸지 않고, Entity와 실제 DB 구조가 맞는지만 검사

우리는 DB 구조를 Flyway가 책임지도록 했으므로 `validate`를 사용한다. 즉:

> Flyway가 테이블을 만들고, Hibernate/JPA는 Entity가 그 테이블과 맞는지만 확인한다.

### 6.3 Flyway 설정

```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
```

실행 시 `src/main/resources/db/migration`의 SQL 파일을 버전 순서대로 실행한다.

### 6.4 RabbitMQ 설정

```yaml
rabbitmq:
  host: ${RABBITMQ_HOST:localhost}
  port: ${RABBITMQ_PORT:5672}
```

현재는 기본 연결 정보만 준비돼 있다. 아직 실제 exchange, queue, routing key, 운영 주소는 팀과 최종 확정하지 않았다. Consumer 구현 전 이 값들을 받아서 설정을 추가한다.

### 6.5 서버 포트

```yaml
server:
  port: ${SERVER_PORT:8080}
```

Notification Service가 로컬에서 기본적으로 `8080` 포트를 사용한다는 뜻이다. 나중에 여러 서비스가 동시에 실행되면 포트 충돌을 피하기 위해 팀 인프라 규칙에 맞게 바뀔 수 있다.

---

## 7. Flyway: DB 구조를 시간 순서대로 관리하는 방법

### 7.1 Migration이 왜 필요한가?

팀원이 각자 DB를 수동으로 만들면 다음 문제가 생긴다.

- 누구는 테이블이 있고 누구는 없다.
- 컬럼 이름이 미세하게 다르다.
- 새 팀원이 들어오면 DB를 어떻게 맞출지 어렵다.
- 배포할 때 운영 DB와 개발 DB가 달라진다.

Flyway는 SQL 파일에 번호를 붙여서 이 문제를 해결한다.

```text
V1__create_notification_tables.sql
V2__seed_notification_reference_data.sql
```

- `V1`: 테이블 생성
- `V2`: 기준 데이터 삽입

앱이 실행되면 Flyway가 "이 DB에 V1과 V2를 이미 실행했나?"를 확인한다. 실행하지 않았다면 차례대로 실행하고, 실행한 기록은 `flyway_schema_history` 테이블에 남긴다.

### 7.2 Migration 파일은 왜 고치면 위험한가?

이미 다른 팀원의 DB에서 V1이 실행된 뒤 V1을 수정하면 Flyway가 체크섬 불일치를 감지할 수 있다. 그래서 일반 원칙은:

```text
이미 공유된 V1을 수정하기보다
V3__add_xxx.sql 같은 새 Migration을 추가한다.
```

현재는 아직 초기 개발 단계이고 V1/V2가 팀에 널리 배포되기 전일 수 있지만, 커밋·공유 이후에는 이 원칙을 지키는 편이 안전하다.

---

## 8. V1 SQL: 테이블을 왜 이렇게 나눴는가?

파일: `src/main/resources/db/migration/V1__create_notification_tables.sql`

처음 보면 테이블이 많아서 복잡해 보이지만, 각각의 질문에 답하도록 나눈 것이다.

```text
무슨 채널로 보낼 수 있나?             → channel_type
무슨 대상에 관한 알림인가?             → subscription_target_type
무슨 사건이 발생했나?                  → notification_event_type
사용자가 무엇을 구독할 수 있나?         → notification_subscription_type
그 구독을 어느 채널로 지원하나?          → subscription_channel
무슨 문구로 보여줄까?                   → notification_template
사용자가 실제로 받을 주소는 무엇인가?    → notification_endpoint
사용자가 무엇을 받기로 했나?            → notification_subscription
시스템에 어떤 알림 사건이 들어왔나?      → notification
실제 발송은 몇 번 시도했고 결과는?       → notification_delivery
```

### 8.1 기준 데이터 테이블과 업무 데이터 테이블

테이블은 크게 두 종류로 나눌 수 있다.

#### 기준 데이터(자주 안 바뀌는 사전)

- `channel_type`
- `subscription_target_type`
- `notification_event_type`
- `notification_subscription_type`
- `subscription_channel`
- `notification_template`

예: Telegram이라는 채널, SENSOR_ERROR라는 이벤트 코드는 매일 새로 생기는 데이터가 아니다.

#### 업무 데이터(사용·이벤트가 발생하면서 쌓이는 기록)

- `notification_endpoint`
- `notification_subscription`
- `notification`
- `notification_delivery`

예: 어떤 사용자가 Discord Webhook을 등록하거나, 실제 센서 오류가 발생한 것은 매일 새로 생길 수 있다.

이 구분을 이해하면 Seed가 왜 기준 데이터만 넣고 실제 사용자의 Endpoint나 구독을 넣지 않는지도 이해하기 쉬워진다.

---

## 9. 테이블 하나씩 쉽게 보기

### 9.1 `channel_type`: 어디로 보낼 수 있나?

예시 데이터:

| id | code | display_name |
|---:|---|---|
| 1 | TELEGRAM | Telegram |
| 2 | DISCORD | Discord |

`code`는 프로그램이 사용하는 안정적인 값이고, `display_name`은 화면에 보여줄 한글/영문 이름이다.

왜 문자열을 Endpoint 테이블에 바로 넣지 않고 분리했을까?

- 오타를 막는다. (`DISCORD`, `Discord`, `discord` 혼재 방지)
- 새 채널을 추가할 때 기준 데이터 하나만 추가하면 된다.
- 화면에서 지원 채널 목록을 쉽게 조회한다.

### 9.2 `subscription_target_type`: 무엇에 대한 알림인가?

현재 Seed 값:

| target_type | 뜻 |
|---|---|
| `CULTIVATION` | 특정 재배에 대한 알림 |
| `INQUIRY` | 특정 문의에 대한 알림 |
| `USER` | 특정 사용자 계정에 대한 알림 |

예시:

- 재배 12번의 센서 오류 → `CULTIVATION`, `target_id=12`
- 사용자 101번의 로그인 성공 → `USER`, `target_id=101`

### 9.3 `notification_event_type`: 실제로 어떤 일이 일어났나?

예:

| code | 의미 |
|---|---|
| `SENSOR_ERROR` | 센서 오류 |
| `HARVEST_COMPLETED` | 수확 완료 |
| `DAILY_FEEDBACK_COMPLETED` | AI 일일 피드백 완료 |

이 테이블은 RabbitMQ의 `eventType` 값과 연결된다.

### 9.4 `notification_subscription_type`: 사용자가 화면에서 켜고 끄는 항목

개발자 코드인 `SENSOR_ERROR`를 사용자 화면에 그대로 보여주기보다, "센서 오류 알림"이라는 구독 항목으로 보여주기 위한 테이블이다.

```text
event type: SENSOR_ERROR
subscription name: 센서 오류 알림
target type: CULTIVATION
```

즉, 이벤트는 시스템 내부 언어이고, 구독 유형은 사용자 설정 화면 언어에 가깝다.

### 9.5 `subscription_channel`: 이 알림을 어느 채널에서 지원하나?

현재 Seed는 모든 구독 유형과 Telegram·Discord를 연결한다. 그래서 현재 기준으로는 모든 알림을 두 채널에서 받을 수 있다.

나중에 "로그인 알림은 Telegram만 지원" 같은 정책이 나오면 이 매핑만 바꾸면 된다.

### 9.6 `notification_template`: 어떤 문구로 보낼까?

예시 템플릿:

```text
[환경 이상] {{cultivationName}}의 {{sensorType}} 값이 정상 범위를 벗어났습니다.
현재값: {{currentValue}}{{unit}} / 정상 범위: {{thresholdMin}}~{{thresholdMax}}{{unit}}
```

`{{cultivationName}}` 같은 부분은 이벤트 payload의 실제 값으로 바뀐다.

```text
{{cultivationName}} → 느타리버섯 1차 재배
{{sensorType}}      → 온도
{{currentValue}}    → 28.5
{{unit}}            → ℃
```

최종 문구:

```text
[환경 이상] 느타리버섯 1차 재배의 온도 값이 정상 범위를 벗어났습니다.
현재값: 28.5℃ / 정상 범위: 18~22℃
```

### 9.7 `notification_endpoint`: 사용자의 실제 수신 주소

예시:

| user_id | channel | destination | display_name |
|---:|---|---|---|
| 101 | TELEGRAM | `123456789` | 내 텔레그램 |
| 101 | DISCORD | `https://discord.com/api/webhooks/...` | 재배 알림 채널 |

- Telegram의 destination은 Chat ID가 될 수 있다.
- Discord의 destination은 Webhook URL이 될 수 있다.
- `display_name`은 사용자가 "거실 디스코드", "개인 텔레그램"처럼 알아보기 쉽게 붙이는 이름이다.
- `enabled=false`면 Endpoint는 존재하지만 현재 발송하지 않는다.

### 9.8 `notification_subscription`: 누가 무엇을 받기로 했나?

가장 중요한 사용자 설정 테이블 중 하나다.

예:

```text
사용자 101의 Discord Endpoint
  + 재배 12번
  + 센서 오류 알림
  + enabled=true
```

이 한 줄이 뜻하는 것은:

> 재배 12번에서 센서 오류가 발생하면 사용자 101의 Discord로 보내라.

사용자 ID는 Subscription에 직접 저장하지 않는다. Endpoint가 `user_id`를 가지고 있으므로, Subscription → Endpoint를 따라가면 수신 사용자를 알 수 있다.

### 9.9 `notification`: 들어온 이벤트로 만든 알림 원본

RabbitMQ 이벤트 하나를 받았을 때 "이 사건을 이미 처리했는가?"와 "무슨 내용이었는가?"를 보관한다.

주요 컬럼:

| 컬럼 | 뜻 |
|---|---|
| `source_event_id` | Producer가 보낸 `eventId`. 중복 방지 기준 |
| `event_payload` | 원본 상세 데이터(JSONB) |
| `message` | 최종 사람이 읽을 문구 |
| `notification_template_id` | 사용한 템플릿 |

### 9.10 `notification_delivery`: 실제 발송 시도 이력

Notification 하나가 있어도 수신자나 채널이 여러 개면 실제 발송은 여러 번 일어날 수 있다.

예:

```text
환경 이상 이벤트 1개
  ├─ 사용자 A Telegram 발송
  ├─ 사용자 A Discord 발송
  └─ 사용자 B Discord 발송
```

각 발송 건은 `notification_delivery` 한 줄이다.

| 상태 | 의미 |
|---|---|
| `PENDING` | 아직 보내지 않았거나 재시도 대기 |
| `SENT` | 발송 성공 |
| `FAILED` | 최대 재시도 뒤 최종 실패 |

`attempt_count`는 몇 번 시도했는지, `error`는 왜 실패했는지, `provider_message_id`는 Telegram/Discord가 돌려준 메시지 ID 등을 저장할 자리다.

---

## 10. 관계를 그림으로 이해하기

### 10.1 설정 데이터 관계

```text
channel_type ───────────────┐
                             ├─ notification_endpoint ─┐
users (다른 서비스) ─────────┘                           │
                                                         ├─ notification_subscription
notification_event_type ─┐                               │
                          ├─ notification_subscription_type ─┘
subscription_target_type ─┘

notification_subscription_type ─┬─ subscription_channel ─ channel_type
                                └─ notification_template ─ channel_type
```

### 10.2 실제 이벤트가 오면 생기는 관계

```text
RabbitMQ event(eventId)
       ↓
notification (한 번만 생성)
       ↓
notification_delivery (활성 구독 수만큼 생성)
       ↓
notification_subscription
       ↓
notification_endpoint
       ↓
Telegram 또는 Discord
```

---

## 11. 제약조건(UNIQUE, FK, INDEX)은 왜 필요한가?

### 11.1 Primary Key

모든 테이블의 `id`는 각 행을 구분하는 번호다.

```sql
id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
```

`IDENTITY`는 DB가 1, 2, 3처럼 번호를 자동으로 만든다는 뜻이다.

### 11.2 Foreign Key(FK)

Foreign Key는 "없는 데이터를 가리키면 안 된다"는 안전장치다.

예:

```text
notification_endpoint.channel_type_id
    → channel_type.id
```

즉, 존재하지 않는 채널 ID 999를 가진 Endpoint는 만들 수 없다.

### 11.3 UNIQUE

UNIQUE는 중복되면 안 되는 값을 막는다.

예:

```text
channel_type.code = TELEGRAM
```

TELEGRAM 채널을 두 번 만들면 안 되므로 `UNIQUE`다.

특히 중요한 것은:

```text
notification.source_event_id UNIQUE
```

RabbitMQ는 네트워크 문제 등으로 같은 메시지를 다시 전달할 수 있다. 이 제약이 있으면 같은 `eventId`로 Notification이 두 번 생기는 것을 DB 수준에서 막을 수 있다.

### 11.4 Partial Unique Index: 활성 구독만 중복 방지

```sql
CREATE UNIQUE INDEX uq_active_notification_subscription
    ON notification_subscription (...)
    WHERE is_deleted = FALSE;
```

뜻:

- 현재 살아 있는 구독은 동일한 조합으로 두 번 만들 수 없다.
- 과거에 삭제 처리한 구독은 이력으로 남길 수 있다.

예를 들어 사용자가 재배 12번의 센서 오류 Discord 구독을 삭제한 뒤 다시 만들 수 있어야 한다. 과거 삭제 이력까지 모두 중복으로 막으면 다시 구독할 수 없으므로, `is_deleted=false`인 현재 구독만 중복 방지한다.

### 11.5 INDEX

Index는 책의 색인처럼 조회를 빠르게 만든다.

예:

```sql
CREATE INDEX idx_notification_delivery_status
    ON notification_delivery (status, updated_at);
```

나중에 `PENDING` 상태의 발송 대기를 빠르게 찾거나 실패 재시도 대상을 찾을 때 도움이 된다.

---

## 12. Seed 데이터: 왜 필요하고 왜 중복되면 안 되나?

파일: `src/main/resources/db/migration/V2__seed_notification_reference_data.sql`

### 12.1 Seed란?

서비스가 처음 실행될 때 꼭 필요한 기준 데이터를 미리 넣는 작업이다.

Notification은 채널, 이벤트 코드, 템플릿이 하나도 없으면 아무 기능도 만들 수 없다. 그래서 아래 데이터를 넣는다.

- Telegram, Discord 채널
- CULTIVATION, INQUIRY, USER 대상 유형
- 이벤트 코드 10개
- 화면에 보일 구독 유형 10개
- 구독 유형별 지원 채널
- 이벤트·채널별 템플릿

### 12.2 `ON CONFLICT`란?

예:

```sql
INSERT INTO channel_type (code, display_name)
VALUES ('TELEGRAM', 'Telegram')
ON CONFLICT (code) DO UPDATE ...
```

TELEGRAM이 없으면 새로 넣고, 이미 있으면 업데이트한다는 뜻이다.

이것을 **멱등성(idempotency)**이라고 부른다.

쉽게 말하면:

> Seed를 한 번 실행하든 열 번 실행하든 결과가 같아야 한다.

오늘 실제로 Seed를 다시 실행해도 이벤트 10개, 템플릿 20개가 중복 증가하지 않는 것을 검증했다.

### 12.3 왜 템플릿은 20개인가?

현재 이벤트 10개 × 채널 2개(Telegram, Discord) = 20개다.

현재는 두 채널의 문구가 거의 같아도, 나중에 Discord는 Markdown, Telegram은 일반 텍스트처럼 채널별 문법이 달라질 수 있으므로 각각의 템플릿을 둔다.

---

## 13. JPA Entity: SQL 테이블을 Java 클래스로 보는 방법

파일 위치: `src/main/java/com/ecosphere/notification/domain/`

JPA Entity는 DB 테이블을 Java 클래스처럼 다루게 해준다.

예를 들어 SQL에서:

```sql
SELECT * FROM channel_type WHERE code = 'TELEGRAM';
```

Java에서는 나중에 Repository를 통해 `ChannelType` 객체로 다룰 수 있다.

### 13.1 Entity에서 자주 보는 어노테이션

| 어노테이션 | 의미 |
|---|---|
| `@Entity` | 이 클래스는 DB 테이블과 연결됨 |
| `@Table(name = "...")` | 연결할 테이블 이름 |
| `@Id` | Primary Key |
| `@GeneratedValue` | ID 자동 생성 방식 |
| `@Column` | 컬럼 설정 |
| `@ManyToOne` | 여러 행이 하나의 기준 데이터를 참조하는 관계 |
| `@JoinColumn` | FK 컬럼 이름 |
| `FetchType.LAZY` | 실제 필요할 때 연관 데이터를 가져오기 |
| `@MappedSuperclass` | 공통 컬럼을 상속용 클래스로 분리 |

### 13.2 `AuditEntity.java`

파일: `domain/AuditEntity.java`

```java
@MappedSuperclass
public abstract class AuditEntity {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

여러 테이블에 반복되는 `created_at`, `updated_at` 컬럼을 한 곳에 모은 추상 부모 클래스다.

`ChannelType`, `NotificationEndpoint`, `NotificationSubscription` 같은 Entity가 이 클래스를 상속한다.

장점:

- 동일한 컬럼 정의를 매번 복사하지 않아도 된다.
- 생성·수정 시간 규칙을 한 곳에서 관리하기 쉬워진다.

### 13.3 `ChannelType.java`

파일: `domain/ChannelType.java`

이 클래스는 `channel_type` 테이블을 표현한다.

```java
@Entity
@Table(name = "channel_type")
public class ChannelType extends AuditEntity {
    private Long id;
    private String code;
    private String displayName;
    private boolean deleted;
}
```

`@Column(name = "is_deleted") private boolean deleted;`처럼 Java 이름과 DB 컬럼 이름은 다를 수 있다.

### 13.4 `SubscriptionTargetType.java`

파일: `domain/SubscriptionTargetType.java`

`CULTIVATION`, `INQUIRY`, `USER`라는 대상 분류를 표현한다. 문자열을 여기저기 코드에 하드코딩하지 않고 기준 테이블로 관리하기 위해 만들었다.

### 13.5 `NotificationEventType.java`

파일: `domain/NotificationEventType.java`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "target_type")
private SubscriptionTargetType targetType;
```

이 뜻은 하나의 이벤트 유형이 하나의 대상 유형을 가진다는 것이다.

예:

```text
SENSOR_ERROR → CULTIVATION
LOGIN_SUCCEEDED → USER
```

### 13.6 `NotificationSubscriptionType.java`

파일: `domain/NotificationSubscriptionType.java`

이벤트 코드와 사용자가 고르는 구독 항목을 연결한다.

```text
SENSOR_ERROR (개발자 이벤트 코드)
       ↓
센서 오류 알림 (사용자 화면에 보여줄 구독 이름)
```

### 13.7 `NotificationEndpoint.java`

파일: `domain/NotificationEndpoint.java`

```java
private Long userId;
private ChannelType channelType;
private String destination;
private String displayName;
private boolean enabled;
```

`userId`는 Auth DB에 FK를 걸지 않고 숫자만 저장하는 **소프트 참조**다. 서비스가 DB를 공유하지 않기 때문이다.

`destination`은 실제 수신 위치다. Telegram에서는 Chat ID, Discord에서는 Webhook URL이 들어갈 수 있다.

### 13.8 `NotificationSubscription.java`

파일: `domain/NotificationSubscription.java`

```java
private NotificationSubscriptionType subscriptionType;
private NotificationEndpoint endpoint;
private Long targetId;
private boolean enabled;
private boolean deleted;
```

이 Entity는 "누가 무엇을 받을지"를 표현한다.

### 13.9 `NotificationTemplate.java`

파일: `domain/NotificationTemplate.java`

이벤트 유형 + 채널 유형 + 버전별 메시지 템플릿이다.

```java
@Table(... uniqueConstraints = {
  "notification_event_type_id", "channel_type_id", "version"
})
```

동일 이벤트·동일 채널·동일 버전의 템플릿이 두 번 생기지 않게 막는다.

### 13.10 `Notification.java`

파일: `domain/Notification.java`

```java
private UUID sourceEventId;
private Map<String, Object> eventPayload;
private String message;
```

여기서 핵심은 `sourceEventId`다. RabbitMQ 이벤트의 `eventId`와 연결해서 중복 처리를 막는다.

`eventPayload`가 `Map<String, Object>`인 이유는 이벤트마다 필요한 상세 데이터 모양이 다르기 때문이다.

- 환경 이상: 센서값, 임계값, 단위
- 수확: 수확량, 수확일
- 로그인: provider

이런 서로 다른 데이터 모양을 한 테이블에 보관하기 위해 PostgreSQL의 `JSONB`를 사용한다.

### 13.11 `@JdbcTypeCode(SqlTypes.JSON)`은 무엇인가?

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private Map<String, Object> eventPayload;
```

JPA/Hibernate에게 "이 Java Map은 PostgreSQL JSONB 컬럼으로 저장해줘"라고 알려주는 설정이다.

### 13.12 `NotificationDelivery.java`

파일: `domain/NotificationDelivery.java`

`Notification`이 사건 자체라면 `NotificationDelivery`는 실제 발송 작업이다.

```java
private String status;       // PENDING, SENT, FAILED
private short attemptCount;  // 시도 횟수
private String error;        // 최종/최근 오류
private LocalDateTime sentAt;
```

나중에 Sender가 발송에 성공하면 `status=SENT`, `sentAt=현재시각`으로 바꾸고, 실패하면 `attemptCount`를 증가시킨다.

---

## 14. Lombok은 왜 쓰나?

Entity에서 `getId()`, `getCode()`, 생성자 같은 반복 코드는 길고 실수하기 쉽다. Lombok은 어노테이션으로 이를 만들어 준다.

예:

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
```

- `@Getter`: 모든 필드의 getter 생성
- `@NoArgsConstructor(access = PROTECTED)`: JPA가 사용할 기본 생성자 생성. 외부에서 아무 값 없이 Entity를 막 만드는 것을 어느 정도 방지

`PROTECTED` 생성자가 필요한 이유는 JPA가 Entity를 DB에서 읽어올 때 기본 생성자를 사용하기 때문이다.

---

## 15. Docker PostgreSQL 검증은 무엇을 확인한 것인가?

우리는 로컬 Docker에서 빈 PostgreSQL DB를 만들고 실제 애플리케이션을 실행했다.

검증한 흐름:

```text
빈 PostgreSQL DB
      ↓
Spring Boot 실행
      ↓
Flyway가 V1 실행: 테이블 생성
      ↓
Flyway가 V2 실행: 기준 데이터 삽입
      ↓
JPA가 Entity와 테이블 구조가 맞는지 validate
      ↓
애플리케이션 정상 기동
```

확인된 결과:

- Migration V1, V2 성공
- 테이블 생성 성공
- 채널 2개 생성
- 대상 유형 3개 생성
- 이벤트 유형 10개 생성
- 구독 유형 10개 생성
- 템플릿 20개 생성
- Seed 재실행 시 중복 생성 없음

이 검증의 의미는 다음과 같다.

> 내 컴퓨터에 우연히 남아 있던 테이블 덕분에 되는 것이 아니라, 새 DB에서도 프로젝트가 스스로 필요한 DB 구조를 만들 수 있다.

---

## 16. IntelliJ Database 창에서 보는 것

현재 IntelliJ에 `notification_db@localhost` 데이터 소스를 연결했다.

로컬 Docker 검증 컨테이너 연결값은 다음이었다.

```text
Host: localhost
Port: 55432
Database: notification_db
User: postgres
```

IntelliJ 왼쪽 Database 창에서:

```text
notification_db
  └─ public
      └─ tables
          ├─ channel_type
          ├─ notification_event_type
          ├─ notification_subscription
          └─ ...
```

볼 수 있다.

공부 방법:

1. `channel_type` 테이블을 열어 Telegram, Discord Seed 데이터를 본다.
2. `notification_event_type`에서 이벤트 코드 10개를 본다.
3. `notification_template`에서 `{{cultivationName}}` 같은 템플릿 문법을 본다.
4. IntelliJ에서 해당 SQL/Entity를 열어 컬럼이 어떻게 연결되는지 비교한다.

---

## 17. 지금 문서·코드에서 발견되는 “다음 단계 전 확인 사항”

이 부분은 "현재 코드가 틀렸다"는 단정이 아니다. 팀 정책과 실제 구현을 완전히 맞추기 위해 Consumer·API 전에 확인해야 하는 체크리스트다. 초보자에게는 이런 차이를 발견하는 것도 중요한 공부다.

### 17.1 Endpoint 삭제 정책과 실제 테이블 컬럼

역할분담 문서와 구현 안내에서는 Endpoint도 `is_deleted=true`로 소프트 삭제하자는 방향을 제안했다.

그런데 현재 `notification_endpoint` 테이블에는 `is_deleted` 컬럼이 없다. 현재 컬럼은 `enabled`까지만 있다.

```text
현재 SQL: Endpoint = enabled만 있음
제안 정책: Endpoint = enabled + is_deleted 사용
```

선택지는 두 가지다.

1. Endpoint는 `enabled=false`만으로 비활성화하고 실제 삭제 API는 제공하지 않는다.
2. Endpoint에도 `is_deleted`를 추가하는 V3 Migration을 만든다.

서영님이 Endpoint API를 만들기 전에 팀에서 한 번 확인하면 된다. 이미 V1이 공유·실행됐다면 V1 수정이 아니라 `V3__add_endpoint_deleted.sql`처럼 새 파일을 추가해야 한다.

### 17.2 문의 답변 이벤트의 대상 유형

최신 대화 정책에서는 문의 답변 알림이 사용자에게 가므로 `targetType=USER`, `targetId=문의 작성자 user_id`라는 방향을 이야기했다.

하지만 현재 V2 Seed에는 다음처럼 들어 있다.

```text
INQUIRY_ANSWERED → INQUIRY
```

이 차이는 실제 UI와 구독 방식에 영향을 준다.

| 선택 | targetId 의미 | 사용자 경험 |
|---|---|---|
| `INQUIRY` | inquiry_id | 특정 문의별로 구독하는 구조 |
| `USER` | user_id | 내 문의 답변 알림을 계정 단위로 받는 구조 |

보통 사용자는 문의를 작성할 때마다 구독을 새로 만들지 않으므로, "내 문의에 답변이 달리면 알려줘"라는 의미의 `USER` 방식이 편할 가능성이 크다. 다만 현재 ERD/Seed와 함께 최종 결정해야 한다.

### 17.3 Notification 하나와 채널별 Template의 관계

현재 `notification` 테이블은 `notification_template_id`를 하나만 가진다. 반면 한 이벤트는 Telegram과 Discord 모두로 발송될 수 있고, 채널별 템플릿은 서로 다를 수 있다.

```text
이벤트 1개
  ├─ Telegram Template
  └─ Discord Template
```

현재 Seed의 문구는 두 채널이 거의 같아서 당장 문제는 작다. 하지만 나중에 채널 문법이 달라지면 다음 중 하나를 결정해야 한다.

1. `notification`은 사건 원본만 보관하고, 실제 채널별 template/rendered message는 `notification_delivery`에만 보관한다.
2. 채널별로 Notification을 따로 만든다.
3. Notification에 Template FK를 두지 않고 event type만 연결한다.

현재 `notification_delivery.rendered_message`가 이미 있으므로, 1번이 자연스러울 수 있다. Consumer 구현 전에 설계 의도를 팀과 확인하면 좋다.

### 17.4 `created_at`, `updated_at` 자동 갱신

현재 SQL은 `DEFAULT CURRENT_TIMESTAMP`로 insert 시 생성 시간을 넣는다. 하지만 `updated_at`을 Java Entity 수정 시 자동으로 바꾸는 코드나 DB trigger는 아직 없다.

나중에 다음 중 하나를 적용할 수 있다.

- JPA의 `@PrePersist`, `@PreUpdate`
- Spring Data JPA Auditing
- DB trigger
- update SQL에서 직접 `updated_at=CURRENT_TIMESTAMP`

당장 Consumer 뼈대 구현을 막지는 않지만, Endpoint·Subscription 수정 API에서 필요해질 수 있다.

### 17.5 Template 변수와 Producer payload의 일치

예:

```text
템플릿: {{cultivationName}}, {{sensorType}}, {{currentValue}}
```

그렇다면 Rule Service가 실제 payload에 정확히 같은 이름을 보내야 한다.

```json
{
  "cultivationName": "느타리버섯 1차 재배",
  "sensorType": "TEMPERATURE",
  "currentValue": 28.5
}
```

`cultivation_name`처럼 다른 이름으로 보내면 템플릿 치환이 실패한다. 그래서 Consumer 구현 전에 Producer별 실제 JSON 샘플을 받는 것이다.

---

## 18. RabbitMQ를 초보자 관점에서 이해하기

### 18.1 왜 REST API 호출이 아니라 RabbitMQ를 쓰나?

Rule Service가 환경 이상을 발견할 때마다 Notification에 HTTP 요청을 직접 보낸다고 생각해보자.

```text
Rule → Notification HTTP 호출 → Telegram 호출
```

Notification이 잠깐 꺼져 있으면 Rule의 요청이 실패한다. Rule은 재시도해야 하고, 알림 실패 때문에 센서 판단이 느려질 수 있다.

RabbitMQ를 사용하면:

```text
Rule → RabbitMQ에 메시지 보관 → Notification이 받을 때 처리
```

장점:

- Rule과 Notification이 동시에 살아 있지 않아도 된다.
- Notification이 잠깐 느려도 Rule의 판단이 크게 막히지 않는다.
- 여러 Consumer가 같은 이벤트를 필요에 따라 받을 수 있다.
- 재전달과 실패 처리를 설계하기 쉽다.

### 18.2 RabbitMQ 용어

| 용어 | 쉬운 비유 | 역할 |
|---|---|---|
| Producer | 편지 보내는 사람 | 이벤트를 발행하는 서비스 |
| Exchange | 우체국 분류 창구 | routing key에 따라 메시지 분배 |
| Queue | 우편함 | Consumer가 꺼내 갈 메시지 보관소 |
| Consumer | 편지 받는 사람 | 메시지를 실제 처리하는 서비스 |
| Routing Key | 주소 분류 태그 | 어떤 Queue로 보낼지 결정 |
| ACK | 수령 확인 | "처리 성공했으니 메시지 지워도 됨" |
| NACK | 처리 실패 알림 | "처리 못 했음"을 브로커에 알림 |
| DLQ | 실패 편지함 | 계속 실패한 메시지를 따로 보관 |

### 18.3 Notification의 RabbitMQ 역할

```text
Producer: Rule, Cultivation, AI, Auth, Inquiry
Consumer: Notification Service
```

Notification은 Consumer다. 이벤트 하나를 받아서 DB를 저장하고 외부 발송까지 완료한 뒤 ACK하는 시점은 나중에 재시도 정책과 함께 신중하게 정해야 한다.

---

## 19. 다음 구현: Consumer는 무엇을 만들게 되는가?

Consumer 구현은 "RabbitMQ 메시지를 받아 Notification DB 데이터로 바꾸는 기능"이다.

### 19.1 먼저 만들 파일의 예상 구조

```text
src/main/java/com/ecosphere/notification/
├── event/
│   ├── DomainEvent.java
│   └── EventType.java (필요 시)
├── consumer/
│   └── NotificationEventConsumer.java
├── repository/
│   ├── NotificationRepository.java
│   ├── NotificationSubscriptionRepository.java
│   └── ...
├── service/
│   ├── NotificationCreationService.java
│   ├── TemplateRenderer.java
│   └── DeliveryService.java
└── sender/
    ├── NotificationSender.java
    ├── TelegramNotificationSender.java
    └── DiscordNotificationSender.java
```

아직 이 파일들은 만들지 않았다. 다음 구현 단계에서 추가한다.

### 19.2 공통 Event DTO 예시

```java
public record DomainEvent(
    UUID eventId,
    String eventType,
    String producer,
    String targetType,
    Long targetId,
    OffsetDateTime occurredAt,
    Map<String, Object> payload
) {}
```

이 코드는 RabbitMQ JSON을 Java에서 읽기 쉬운 데이터 묶음으로 바꾼다.

### 19.3 Consumer가 해야 하는 순서

```text
1. RabbitMQ JSON 수신
2. JSON → DomainEvent 변환
3. 필수 필드 검사
4. eventType이 기준 테이블에 존재하는지 확인
5. source_event_id(eventId)가 이미 처리됐는지 확인
6. targetType/targetId에 맞는 활성 Subscription 조회
7. 구독이 없으면 종료
8. Template과 payload로 메시지 생성
9. Notification 저장
10. Delivery를 구독 수만큼 저장
11. 발송 처리 또는 발송 Queue로 전달
```

### 19.4 중복 이벤트는 왜 꼭 처리해야 하나?

메시지 브로커에서는 "최소 한 번 전달(at-least-once)"이 흔하다. 즉, 같은 이벤트가 두 번 도착할 수 있다.

예:

```text
Consumer가 DB 저장 성공
하지만 ACK 직전에 네트워크 끊김
RabbitMQ는 성공 여부를 몰라 같은 메시지를 다시 보냄
```

이때 `source_event_id UNIQUE`가 없다면 사용자에게 같은 알림이 두 번 갈 수 있다. 현재 DB의 UNIQUE 제약은 이런 상황의 마지막 안전장치다.

---

## 20. 서영님이 구현할 Endpoint·Subscription API와 나의 Consumer 관계

현재 역할 분담은 병렬 작업이 가능하도록 나뉘어 있다.

| 담당 | 주 역할 | 결과물 |
|---|---|---|
| 서영님 | Endpoint, Subscription API, 권한 검증 | 사용자가 알림을 받을 수 있는 설정 데이터 |
| 나(호준) | Event 계약, Consumer, 알림 생성, 발송, 재시도 | 실제 이벤트를 알림으로 전달 |

두 작업이 만나는 지점은 `notification_subscription`이다.

```text
서영님이 만든 구독
      ↓
notification_subscription 테이블
      ↑
내 Consumer가 활성 구독을 조회
      ↓
notification_delivery 생성
```

그래서 서영님이 API를 만들면 나에게 필요한 것은 완성된 화면이 아니라 다음 정보다.

- Endpoint와 Subscription의 실제 DTO 필드명
- 활성 구독의 판단 규칙
- 삭제·비활성화 처리 방식
- 인증한 사용자 ID를 가져오는 방법
- 재배 권한 검증 방식

---

## 21. 앞으로 각 담당자에게 받아야 하는 정보

아직 당장 받을 필요는 없지만 Consumer 실제 연동 전에는 받아야 한다.

| 담당 | 받을 정보 |
|---|---|
| Rule | 환경 이상/복구/센서 오류/제어 실패의 실제 payload JSON, 발행 조건 |
| Cultivation | 수확 완료/재배 종료 payload JSON, 수확량 단위, 발행 시점 |
| AI | 일일 피드백 완료 payload JSON, feedbackId/요약/저장 완료 시점 |
| Auth | 로그인 성공 payload JSON, 실제 JWT `userId` claim 이름 |
| Inquiry | 문의 답변 완료 payload JSON, targetType과 targetId 최종 기준 |
| 인프라/RabbitMQ 담당 | exchange, queue, routing key, DLQ, ACK/NACK 규칙, 개발/운영 접속 정보 |
| 서영님 | Subscription 조회 규칙과 API/DTO 형식 |

공통으로 필요한 RabbitMQ 이벤트 형식은 다음을 기준으로 한다.

```json
{
  "eventId": "UUID",
  "eventType": "SENSOR_ERROR",
  "producer": "rule-service",
  "targetType": "CULTIVATION",
  "targetId": 12,
  "occurredAt": "2026-07-24T10:30:00+09:00",
  "payload": {}
}
```

---

## 22. 현재 내가 할 일과 일정의 의미

### 오늘 완료한 일

```text
Notification DB 기초 공사 완료
Migration + Entity + Seed + 빈 DB 검증
```

### 다음 작업일에 시작할 일

```text
공통 Event DTO
RabbitMQ Consumer 뼈대
이벤트 검증
중복 이벤트 방지
활성 구독 조회
Notification·Delivery 생성
```

### 그 다음

```text
Template Renderer
Telegram Sender
Discord Sender
발송 상태 저장
재시도 정책
통합 테스트
```

일정표의 날짜는 "그 기간에 집중할 목표"다. 오늘 DB 작업을 먼저 끝냈다고 해서 일정이 틀린 것이 아니다. 오히려 Consumer 작업 전에 필요한 기초를 앞당겨 끝낸 것이다.

---

## 23. 초보자용 용어 사전

| 용어 | 쉬운 설명 |
|---|---|
| Entity | DB 테이블 한 줄을 Java 객체로 다루기 위한 클래스 |
| Repository | Entity를 DB에서 조회·저장하는 인터페이스 |
| Migration | DB 구조 변경을 버전별 SQL 파일로 관리하는 것 |
| Flyway | Migration을 실행·기록하는 도구 |
| Seed | 서비스 시작에 필요한 기준 데이터를 미리 넣는 작업 |
| Endpoint | 실제 알림 수신 주소. Telegram Chat ID, Discord Webhook URL 등 |
| Subscription | 어떤 알림을 받을지 선택한 설정 |
| Event | 다른 서비스에서 발생한 사건을 알리는 데이터 |
| Payload | 이벤트에 포함된 상세 데이터 |
| Consumer | RabbitMQ 메시지를 받는 프로그램 |
| Producer | RabbitMQ 메시지를 보내는 프로그램 |
| Template | 변수를 넣어 메시지를 만들기 위한 문구 틀 |
| Delivery | 실제 한 번의 발송 작업/이력 |
| Idempotency | 같은 요청을 여러 번 처리해도 결과가 하나처럼 유지되는 성질 |
| Soft Delete | DB에서 지우지 않고 삭제 여부만 표시하는 방식 |
| FK | 다른 테이블의 존재하는 행만 가리키게 하는 연결 규칙 |
| UNIQUE | 같은 값이 중복되지 않도록 하는 규칙 |
| JSONB | PostgreSQL에서 JSON 형태 데이터를 저장하는 컬럼 타입 |
| DTO | 서비스·API·메시지 사이에서 데이터를 옮기기 위한 객체 |

### 23.1 Java·Spring 기본 용어

| 용어 | 쉬운 설명 |
|---|---|
| Java | 현재 백엔드 코드를 작성하는 프로그래밍 언어 |
| JVM | Java 코드를 실제로 실행해 주는 가상 실행 환경 |
| Spring Boot | Java 서버를 빠르게 만들 수 있게 도와주는 프레임워크 |
| Framework | 자주 필요한 구조를 미리 제공하는 개발 도구 묶음 |
| Package | Java 파일을 역할별로 묶는 폴더 같은 개념 |
| Class | 객체를 만들기 위한 설계도. 예: `Notification` 클래스 |
| Object | Class 설계도로 실제 만든 값. 예: 특정 알림 한 건 |
| Method | Class 안에 들어 있는 동작. 예: `send()` |
| Constructor | 객체를 처음 만들 때 필요한 값을 받는 특별한 Method |
| Interface | "이 기능을 제공한다"는 약속. 구현 방법은 다른 Class가 작성 가능 |
| Annotation | `@Entity`처럼 코드에 의미나 설정을 붙이는 표식 |
| Bean | Spring이 생성하고 관리하는 객체 |
| Dependency Injection(DI) | 필요한 객체를 직접 만들지 않고 Spring이 넣어주는 방식 |
| Component Scan | Spring이 `@Component`, `@Service` 등을 찾아 Bean으로 등록하는 과정 |
| Controller | HTTP 요청을 처음 받는 계층 |
| Service | 실제 업무 규칙을 처리하는 계층 |
| Repository | DB 조회·저장을 담당하는 계층 |
| Configuration | Spring이나 외부 연결 설정을 모은 코드 |
| Exception | 프로그램 실행 중 발생한 오류 상황 |
| Logging | 실행 과정과 오류를 기록으로 남기는 것 |

### 23.2 웹·API·보안 용어

| 용어 | 쉬운 설명 |
|---|---|
| HTTP | 브라우저·프론트·서버가 요청과 응답을 주고받는 규칙 |
| REST API | URL과 HTTP Method로 기능을 제공하는 서버 인터페이스 방식 |
| Endpoint(API Endpoint) | API가 제공되는 구체적인 URL. 예: `/api/v1/notifications/endpoints` |
| Request | 클라이언트가 서버에 보내는 요청 |
| Response | 서버가 클라이언트에 돌려주는 결과 |
| JSON | 키와 값으로 데이터를 표현하는 형식 |
| Request Body | POST/PATCH 요청에 함께 보내는 JSON 데이터 본문 |
| Path Variable | URL에 포함된 값. 예: `/endpoints/{endpointId}`의 `endpointId` |
| Query Parameter | URL 뒤에 붙여 조건을 전달하는 값. 예: `?page=0` |
| HTTP Method | 요청의 목적. GET 조회, POST 생성, PATCH 일부 수정, DELETE 삭제 |
| HTTP Status | 응답 결과 번호. 200 성공, 201 생성 성공, 400 요청 오류, 401 미인증, 403 권한 없음, 404 없음, 500 서버 오류 |
| Authentication(인증) | "너는 누구인가?"를 확인하는 일 |
| Authorization(인가) | "너에게 이 작업을 할 권한이 있는가?"를 확인하는 일 |
| JWT | 로그인한 사용자의 정보와 서명을 담아 전달하는 토큰 형식 |
| Claim | JWT 안에 들어 있는 정보 항목. 예: `userId`, `sub`, `role` |
| OAuth | Google 같은 외부 계정으로 로그인하는 표준 방식 |
| Secret | 토큰·비밀번호처럼 외부에 노출하면 안 되는 값 |
| Environment Variable | 코드에 직접 쓰지 않고 실행 환경에서 주입하는 설정값 |
| Webhook | 특정 일이 생겼을 때 지정 URL로 HTTP 요청을 보내는 방식. Discord 발송에 사용 가능 |

### 23.3 DB·SQL 추가 용어

| 용어 | 쉬운 설명 |
|---|---|
| Database(DB) | 데이터를 구조적으로 보관하는 시스템 |
| DBMS | Database를 관리하는 프로그램. PostgreSQL이 여기에 해당 |
| PostgreSQL | 우리 Notification Service가 사용하는 관계형 DBMS |
| Table | 같은 종류의 데이터를 행과 열로 저장하는 공간 |
| Row(행) | 테이블의 데이터 한 건 |
| Column(열) | 데이터의 한 항목. 예: `created_at` |
| Schema | DB 안의 테이블·컬럼·제약조건 구조 전체 |
| SQL | DB를 조회·생성·수정하는 언어 |
| DDL | 테이블 구조를 만드는 SQL. `CREATE TABLE`, `ALTER TABLE` 등 |
| DML | 데이터를 넣고 바꾸는 SQL. `INSERT`, `UPDATE`, `DELETE` 등 |
| Transaction | 여러 DB 작업을 하나처럼 성공 또는 실패 처리하는 단위 |
| Commit | Transaction의 변경 내용을 확정 저장하는 것 |
| Rollback | Transaction 중 문제가 생겼을 때 변경을 되돌리는 것 |
| Nullable | 해당 컬럼이 비어 있어도 되는지 여부 |
| NOT NULL | 해당 컬럼은 반드시 값이 있어야 한다는 제약 |
| Default Value | 값을 안 넣었을 때 DB가 자동으로 넣는 기본값 |
| Timestamp | 날짜와 시간을 저장하는 타입 |
| UTC | 세계 공통 시간 기준. 서비스 간 시간을 맞출 때 자주 사용 |
| Timezone | 시간대. 한국은 보통 `+09:00` |
| JSONB | PostgreSQL에서 JSON을 검색·저장하기 좋게 만든 타입 |
| Soft Reference | 다른 서비스 DB의 실제 FK 대신 ID 값만 보관하는 연결 방식 |
| Soft Delete | 행을 실제로 지우지 않고 삭제 여부만 표시하는 방식 |
| Hard Delete | `DELETE`로 DB에서 행을 실제로 지우는 방식 |
| Constraint | DB가 데이터 규칙을 강제하는 장치. PK, FK, UNIQUE, CHECK 등이 있음 |
| Check Constraint | 값의 범위를 제한하는 제약. 예: status는 PENDING/SENT/FAILED만 허용 |
| Composite Unique | 여러 컬럼의 조합이 중복되지 않게 하는 UNIQUE 규칙 |
| Partial Index | 특정 조건을 만족하는 행만 대상으로 만드는 Index |

### 23.4 JPA·Hibernate 용어

| 용어 | 쉬운 설명 |
|---|---|
| JPA | Java 객체와 관계형 DB를 연결하는 표준 규칙 |
| Hibernate | JPA 규칙을 실제로 구현한 대표 라이브러리 |
| ORM | 객체(Object)와 관계형 DB(Relational DB)를 연결하는 방식 |
| Entity | 테이블과 연결되는 Java Class |
| Persistence | 객체를 DB에 오래 보관하는 것 |
| Persist | 새 Entity를 DB에 저장하는 것 |
| Mapping | Java 필드와 DB 컬럼을 연결하는 설정 |
| Association | Entity 사이의 관계. 예: Delivery가 Notification을 참조 |
| `@ManyToOne` | 여러 행이 하나의 기준 행을 참조하는 관계 |
| Lazy Loading | 연관 데이터를 처음부터 가져오지 않고 필요할 때 가져오는 방식 |
| Eager Loading | Entity를 읽을 때 연관 데이터도 바로 가져오는 방식 |
| N+1 문제 | 목록 조회 중 연관 데이터를 개별로 또 조회해서 쿼리가 너무 많이 나가는 문제 |
| `ddl-auto=validate` | Entity와 테이블이 맞는지 검사만 하고 DB 구조는 자동 수정하지 않는 설정 |
| Dirty Checking | JPA가 관리 중인 Entity의 값 변경을 감지해서 UPDATE하는 기능 |
| Auditing | 생성·수정 시각이나 생성자 정보를 자동 기록하는 기능 |

### 23.5 RabbitMQ·비동기 처리 추가 용어

| 용어 | 쉬운 설명 |
|---|---|
| Message Broker | 서비스 사이의 메시지를 보관·전달해주는 중간 시스템 |
| RabbitMQ | 우리 프로젝트에서 사용할 Message Broker |
| Asynchronous(비동기) | 요청을 보낸 뒤 상대 작업이 끝날 때까지 계속 기다리지 않는 방식 |
| Synchronous(동기) | 요청을 보낸 뒤 결과가 올 때까지 기다리는 방식 |
| Event-driven | 특정 사건(Event)이 발생했을 때 다른 작업이 이어지는 구조 |
| Domain Event | 업무상 의미 있는 사건. 예: 수확 완료, 센서 오류 |
| Event Contract | Producer와 Consumer가 합의한 이벤트 JSON 형식·필드·의미 |
| Event Envelope | 모든 이벤트에 공통으로 넣는 바깥 형식. eventId, eventType 등이 들어감 |
| Event Payload | 사건별 상세 정보. 센서값, 수확량, 오류 메시지 등 |
| Exchange | 메시지를 routing key 규칙으로 Queue에 분배하는 RabbitMQ 구성요소 |
| Topic Exchange | routing key 패턴으로 메시지를 분배하는 Exchange 종류 |
| Queue | Consumer가 처리할 때까지 메시지를 보관하는 공간 |
| Routing Key | 메시지를 어느 Queue로 보낼지 구분하는 문자열 |
| Binding | Exchange와 Queue를 routing key 규칙으로 연결한 설정 |
| Consumer | Queue에서 메시지를 받아 처리하는 프로그램 |
| Producer | Exchange에 메시지를 발행하는 프로그램 |
| Acknowledgement(ACK) | Consumer가 "정상 처리했다"고 RabbitMQ에 알리는 것 |
| NACK | Consumer가 "처리 실패했다"고 알리는 것 |
| Requeue | 실패한 메시지를 다시 Queue에 넣는 것 |
| Retry | 실패한 작업을 일정 횟수 다시 시도하는 것 |
| DLQ(Dead Letter Queue) | 계속 실패해서 일반 Queue에서 분리한 메시지 보관소 |
| At-least-once | 메시지가 최소 한 번은 오지만, 중복 전달될 수 있는 방식 |
| Exactly-once | 정확히 한 번만 처리되는 것을 목표로 하는 방식. 실제로는 중복 방지로 구현하는 경우가 많음 |
| Idempotent Consumer | 같은 이벤트를 여러 번 받아도 결과가 한 번 처리한 것처럼 유지되는 Consumer |

### 23.6 배포·개발 환경·협업 용어

| 용어 | 쉬운 설명 |
|---|---|
| Local | 내 컴퓨터에서 실행하는 개발 환경 |
| Development(Dev) | 팀이 함께 기능을 테스트하는 개발 서버 환경 |
| Production(Prod) | 실제 사용자에게 제공하는 운영 환경 |
| Docker | 프로그램과 실행 환경을 컨테이너로 묶어 어디서나 비슷하게 실행하는 도구 |
| Container | Docker로 실행한 독립된 작은 실행 공간 |
| Image | Container를 만들기 위한 실행 템플릿 |
| Port | 한 컴퓨터 안에서 어떤 프로그램에 연결할지 구분하는 번호 |
| `localhost` | 현재 내 컴퓨터 자신을 가리키는 주소 |
| Health Check | 서비스가 정상 동작 중인지 확인하는 요청 |
| Actuator | Spring Boot의 Health Check 같은 운영 기능 도구 |
| Build | 소스 코드를 실행 가능한 결과물로 만드는 과정 |
| Compile | Java 코드를 JVM이 이해할 수 있는 형태로 변환하는 과정 |
| Test | 코드가 기대대로 동작하는지 자동 확인하는 코드 |
| Unit Test | 작은 Method/Service 단위를 검사하는 테스트 |
| Integration Test | DB, RabbitMQ, API 등 여러 구성요소를 함께 연결해 검사하는 테스트 |
| Testcontainers | 테스트 중 Docker Container를 띄워 실제 DB 등을 검증하는 라이브러리 |
| CI | Git에 변경이 올라오면 빌드·테스트를 자동 실행하는 환경 |
| CD | 테스트를 통과한 코드를 자동 또는 반자동으로 배포하는 과정 |
| Configuration Profile | local/dev/prod처럼 환경별 설정을 분리하는 방법 |

### 23.7 Git 협업 용어

| 용어 | 쉬운 설명 |
|---|---|
| Git | 코드 변경 이력을 관리하는 도구 |
| Repository(Repo) | Git으로 관리되는 프로젝트 폴더와 원격 저장소 |
| GitHub | 원격 Repository를 공유하고 협업하는 서비스 |
| Working Tree | 현재 내 컴퓨터에서 수정 중인 파일 상태 |
| Stage | Commit에 넣을 파일을 골라 둔 임시 영역 |
| Commit | 특정 시점의 변경 내용을 Git 이력에 저장하는 것 |
| Branch | 기존 코드와 분리된 작업 줄기 |
| Main Branch | 기본이 되는 안정 브랜치 |
| Feature Branch | 특정 기능 개발을 위한 브랜치 |
| Push | 내 Commit을 GitHub 같은 원격 저장소에 올리는 것 |
| Pull | 원격 변경 내용을 내 컴퓨터로 가져오는 것 |
| Merge | 두 Branch의 변경을 합치는 것 |
| Conflict | 같은 부분을 서로 다르게 수정해 자동으로 합칠 수 없는 상태 |
| Pull Request(PR) | 내 Branch 변경을 다른 Branch에 합쳐 달라고 요청하는 협업 단위 |
| Review | PR의 코드와 설계를 다른 팀원이 확인하는 과정 |
| `.gitignore` | Git이 추적하지 않을 파일 목록. `.idea`, 비밀번호 파일 등을 넣음 |

### 23.8 Notification 도메인 용어 다시 정리

| 용어 | 우리 프로젝트에서의 정확한 의미 |
|---|---|
| Channel | 알림을 보내는 매체. 현재 Telegram, Discord |
| Endpoint | 특정 사용자의 실제 수신 주소. Chat ID 또는 Webhook URL |
| Event Type | 시스템에서 발생한 사건의 코드. `SENSOR_ERROR` 등 |
| Subscription Type | 사용자가 UI에서 선택하는 알림 항목. "센서 오류 알림" 등 |
| Subscription | 특정 대상의 특정 알림을 특정 Endpoint로 받겠다는 실제 설정 |
| Target Type | 알림이 연결된 대상의 종류. CULTIVATION, INQUIRY, USER |
| Target ID | 대상의 실제 번호. cultivation_id, inquiry_id, user_id 등 |
| Notification | 수신한 도메인 이벤트로 생성한 알림 원본 |
| Delivery | Notification을 특정 구독/Endpoint로 실제 보내려는 한 건의 작업 |
| Rendered Message | 템플릿 변수까지 실제 값으로 치환된 최종 발송 문구 |
| Provider | Telegram/Discord처럼 실제 발송을 제공하는 외부 시스템 |
| Provider Message ID | 외부 Provider가 발송 성공 후 반환하는 메시지 식별값 |

---

## 24. IntelliJ로 공부할 때 추천 실습 순서

### 실습 1: Seed 데이터 확인

1. IntelliJ Database 창에서 `channel_type`을 연다.
2. Telegram, Discord 두 줄을 확인한다.
3. `V2__seed_notification_reference_data.sql`의 첫 INSERT와 비교한다.

### 실습 2: 이벤트 유형 추적

1. `notification_event_type` 테이블에서 `SENSOR_ERROR`를 찾는다.
2. V2 Seed에서 같은 코드가 어디에 들어가는지 찾는다.
3. `NotificationEventType.java`를 연다.
4. `target_type` FK가 `SubscriptionTargetType`으로 연결되는 것을 본다.

### 실습 3: 구독이 무엇인지 손으로 따라가기

아래 문장을 각 테이블로 나눠 생각한다.

> "사용자 101이 재배 12번의 센서 오류를 내 Discord로 받고 싶다."

```text
사용자 101              → notification_endpoint.user_id
내 Discord               → notification_endpoint.destination
센서 오류 알림            → notification_subscription_type
재배 12번                → notification_subscription.target_id
Endpoint와 구독 연결      → notification_subscription.notification_endpoint_id
```

### 실습 4: 중복 방지 이해

1. V1의 `uq_notification_source_event`를 찾는다.
2. `Notification.java`의 `sourceEventId`를 찾는다.
3. RabbitMQ 메시지가 두 번 온 상황을 상상한다.
4. 왜 DB UNIQUE가 필요한지 설명해본다.

### 실습 5: 템플릿과 Payload 연결

1. V2의 `SENSOR_ERROR` 템플릿을 찾는다.
2. `{{cultivationName}}`, `{{deviceName}}`, `{{errorMessage}}`를 적는다.
3. Rule 담당자가 보내야 할 payload JSON을 직접 작성해본다.
4. 다음 Consumer 단계에서 이 값을 어떻게 치환할지 상상한다.

---

## 25. 이 문서를 읽은 뒤 스스로 답할 수 있어야 하는 질문

다음 질문에 짧게라도 답할 수 있으면 오늘 작업의 큰 맥락을 이해한 것이다.

1. Notification Service가 Rule Service 대신 센서 이상을 판단하지 않는 이유는?
2. Endpoint와 Subscription의 차이는?
3. Notification과 Notification Delivery의 차이는?
4. `source_event_id`가 UNIQUE인 이유는?
5. Seed를 여러 번 실행해도 중복되면 안 되는 이유는?
6. Template에 `{{currentValue}}`가 있으면 Producer payload에는 무엇이 필요할까?
7. 왜 Notification이 Cultivation DB를 직접 조회하면 안 될까?
8. Consumer 구현 전에 다른 담당자에게 payload와 RabbitMQ 정보를 받아야 하는 이유는?
9. `enabled=false`와 `is_deleted=true`는 어떤 차이가 있을까?
10. Delivery가 `PENDING`, `SENT`, `FAILED`로 나뉘는 이유는?

---

## 26. 마지막 요약

오늘 한 일은 "알림을 실제로 보내는 기능"이 아니라, 그 기능이 안정적으로 올라갈 수 있는 **데이터 기반과 프로젝트 골격**을 만든 일이다.

```text
오늘
  DB 구조 + Entity + 기준 데이터 + 검증

다음
  RabbitMQ 이벤트를 받는 Consumer
  → 활성 구독 조회
  → Notification/Delivery 생성

그 다음
  템플릿 메시지 생성
  → Telegram/Discord 발송
  → 실패 재시도와 통합 테스트
```

이 구조를 한 문장으로 말하면 다음과 같다.

> 다른 서비스가 발생시킨 재배·AI·인증 이벤트를 RabbitMQ로 받아, 사용자의 구독 설정과 수신 경로에 맞춰 Telegram·Discord로 전달하고 그 결과를 안전하게 기록하는 서비스가 Notification Service다.
