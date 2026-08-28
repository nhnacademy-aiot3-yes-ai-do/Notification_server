# Telegram webhook 운영 절차

Telegram 계정 연동은 Bot API가 아래 공개 URL로 Update를 전달해야 완료된다.

```text
https://api.yes-nhn.site/webhooks/telegram
```

Notification Server의 Controller는 callback URL을 Telegram에 자동 등록하지 않는다. Bot을 새로 만들거나
Bot Token·webhook secret·공개 도메인을 변경하면 운영자가 등록 스크립트를 실행해야 한다.

## 사전 조건

- `curl`, `jq`가 설치되어 있어야 한다.
- `TELEGRAM_BOT_TOKEN`은 운영 Bot의 Token이어야 한다.
- `TELEGRAM_WEBHOOK_SECRET`은 실행 중인 Notification Server의 같은 이름의 환경변수와 같아야 한다.
- secret은 영문 대·소문자, 숫자, `_`, `-`만 사용하며 길이는 1~256자여야 한다.
- Token, secret, Telegram Chat ID, `/start` payload를 명령 출력이나 문서에 기록하지 않는다.
- 운영 환경에서는 `TELEGRAM_API_BASE_URL`과 `TELEGRAM_ALLOW_LOCAL_TEST_API`를 설정하지 않는다. 스크립트는
  기본값인 Telegram 공식 HTTPS API만 사용한다.

## 등록 및 검증

Kubernetes Secret을 사용할 수 있는 운영 단말에서는 값을 화면에 출력하지 않고 환경변수로 읽는다.

```bash
export TELEGRAM_BOT_TOKEN="$(
  kubectl get secret notification-secret \
    -o jsonpath='{.data.TELEGRAM_BOT_TOKEN}' | base64 --decode
)"
export TELEGRAM_WEBHOOK_SECRET="$(
  kubectl get secret notification-secret \
    -o jsonpath='{.data.TELEGRAM_WEBHOOK_SECRET}' | base64 --decode
)"

./scripts/telegram/configure-webhook.sh

unset TELEGRAM_BOT_TOKEN TELEGRAM_WEBHOOK_SECRET
```

스크립트는 다음 작업을 순서대로 수행한다.

1. `setWebhook`에 callback URL, `secret_token`, `allowed_updates=["message"]`를 전달한다.
2. `getWebhookInfo`를 조회한다.
3. 등록된 URL이 운영 callback URL과 정확히 같은지 검증한다.
4. URL과 `pending_update_count`를 출력한다.
5. Telegram이 최근 전달 오류를 보고하면 `last_error_message`를 출력한다.

성공 출력에는 Bot Token과 webhook secret이 포함되지 않는다. 요청 실패나 Telegram의 실패 응답도 원문을
출력하지 않으며, credential을 `curl`의 명령행 인자로 전달하지 않는다.

## 계정 연동 확인

1. Front에서 Telegram 연동 세션을 새로 생성한다.
2. Telegram private chat에서 새 deep link의 `/start <payload>`를 실행한다.
3. Gateway 로그에서 `gateway_request method=POST path=/webhooks/telegram`을 확인한다.
4. 세션 조회 결과가 `PENDING`에서 `LINKED`로 바뀌는지 확인한다.
5. Telegram 연동 완료 메시지가 도착하는지 확인한다.

확인 과정에서도 Chat ID와 `/start` payload 원문은 로그에 남기지 않는다.
