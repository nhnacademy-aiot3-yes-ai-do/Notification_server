# Notification Server

알림 이벤트를 수신하고 구독·발송 설정에 따라 처리하는 Notification 서비스입니다. RabbitMQ 이벤트를 유형별로 분류하고 알림 및 발송 상태를 관리하며, 이메일·Telegram 등 채널 연동과 Telegram 계정 연결 기능을 제공합니다.

## 주요 기능

- RabbitMQ를 통한 외부 이벤트 수신과 이벤트 유형별 처리
- 알림 이벤트 유형, 구독 유형, 채널별 템플릿·수신 주소 관리
- 사용자 구독 설정과 알림 이력 조회
- 발송 작업 및 재시도·대기 상태 복구 관리
- Telegram 계정 연결, webhook 수신, `/start` 연결 흐름
- 내부 서비스 연동 API와 관리자 API
- PostgreSQL 데이터 저장, Flyway 스키마 관리, Redis 기반 상태 관리

## 기술 스택

- Java 21, Spring Boot, Maven
- Spring Web, Spring Data JPA, PostgreSQL, Flyway
- RabbitMQ, Redis
- Spring Cloud OpenFeign을 이용한 내부 서비스 연동
- Springdoc OpenAPI / Swagger UI

## 실행 환경

로컬 실행 전 다음 의존 서비스를 준비해야 합니다.

- PostgreSQL
- RabbitMQ
- Redis
- 연동 기능을 사용할 경우 Telegram Bot 설정 및 필요한 내부 서비스 주소

저장소에 `.env` 파일을 커밋하지 마세요. 개인 실행 환경에 맞는 값을 별도로 준비하고, 설정 변수와 기본값은 [`src/main/resources/application.yml`](src/main/resources/application.yml)을 확인하세요. 주요 설정 변수는 다음과 같습니다. 비밀 값은 저장소에 올리지 마세요.

| 구분 | 변수 예시 | 설명 |
| --- | --- | --- |
| 데이터베이스 | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 정보 |
| 메시지 브로커 | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ 접속 정보 |
| 캐시 | `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `REDIS_PASSWORD` | Redis 접속 정보 |
| Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET` | Bot과 webhook 설정 |
| 연동 주소 | `PUBLIC_APP_ORIGIN`, `USER_SERVER_URL`, `NOTIFICATION_DASHBOARD_URL` | 웹 화면 및 내부 서비스 주소 |

일부 설정에는 로컬 개발용 기본값이 있습니다. 팀·배포 환경의 값은 담당 환경 설정을 따르고, 실제 토큰과 비밀번호를 README나 Git에 기록하지 마세요.

## 로컬 실행

Java 21이 설치되어 있는지 확인한 뒤 PostgreSQL, RabbitMQ, Redis를 실행하고 환경 변수를 설정합니다. 이후 저장소 루트에서 실행합니다.

```bash
./mvnw spring-boot:run
```

기본 포트는 Spring Boot 기본값인 `8080`입니다. 애플리케이션 설정에서 포트를 변경했다면 해당 값을 사용하세요. DB 스키마는 Flyway 마이그레이션으로 관리됩니다.

## API 문서

애플리케이션 실행 후 Swagger UI를 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI 문서: `http://localhost:8080/api-docs`

API 문서 노출은 `SWAGGER_ENABLED` 설정의 영향을 받습니다.

## 문서

- [Notification 기능과 구성](docs/reference/notification.md)
- [DB 구조](docs/reference/notification-db.md)
- [Notification 이벤트 계약](docs/rabbitmq/notification-contract.md)
- [AI 이벤트 계약](docs/rabbitmq/ai-event-contract.md)
- [Telegram webhook 연동](docs/reference/telegram-webhook-integration.md)
- [트러블슈팅 회고](docs/troubleshooting.md)
