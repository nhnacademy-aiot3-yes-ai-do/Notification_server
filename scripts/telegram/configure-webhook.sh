#!/usr/bin/env bash
set -euo pipefail

readonly DEFAULT_TELEGRAM_API_BASE_URL="https://api.telegram.org"
readonly DEFAULT_TELEGRAM_WEBHOOK_URL="https://api.yes-nhn.site/webhooks/telegram"
readonly ALLOWED_UPDATES='["message"]'

telegram_bot_token="${TELEGRAM_BOT_TOKEN:-}"
telegram_webhook_secret="${TELEGRAM_WEBHOOK_SECRET:-}"
telegram_api_base_url="${TELEGRAM_API_BASE_URL:-$DEFAULT_TELEGRAM_API_BASE_URL}"
telegram_webhook_url="${TELEGRAM_WEBHOOK_URL:-$DEFAULT_TELEGRAM_WEBHOOK_URL}"
telegram_allow_local_test_api="${TELEGRAM_ALLOW_LOCAL_TEST_API:-false}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command is not installed: $1" >&2
    exit 1
  fi
}

require_value() {
  local variable_name="$1"
  local variable_value="$2"

  if [ -z "$variable_value" ]; then
    echo "Required environment variable is missing: $variable_name" >&2
    exit 1
  fi
}

curl_config_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '%s' "$value"
}

telegram_request() {
  local method="$1"
  local endpoint="$2"
  shift 2
  local request_url="${telegram_api_base_url}/bot${telegram_bot_token}/${endpoint}"
  local form_field

  {
    printf '%s\n' \
      'fail' \
      'silent' \
      'show-error' \
      'connect-timeout = 5' \
      'max-time = 15' \
      "request = \"${method}\"" \
      "url = \"$(curl_config_escape "$request_url")\""

    for form_field in "$@"; do
      printf 'data-urlencode = "%s"\n' "$(curl_config_escape "$form_field")"
    done
  } | curl --disable --config - 2>/dev/null
}

require_command curl
require_command jq
require_value TELEGRAM_BOT_TOKEN "$telegram_bot_token"
require_value TELEGRAM_WEBHOOK_SECRET "$telegram_webhook_secret"

if [[ ! "$telegram_bot_token" =~ ^[0-9]+:[A-Za-z0-9_-]+$ ]]; then
  echo "TELEGRAM_BOT_TOKEN has an invalid format" >&2
  exit 1
fi

if [[ ! "$telegram_webhook_secret" =~ ^[A-Za-z0-9_-]+$ ]] \
  || [ "${#telegram_webhook_secret}" -gt 256 ]; then
  echo "TELEGRAM_WEBHOOK_SECRET must use 1-256 characters from A-Z, a-z, 0-9, _ and -" >&2
  exit 1
fi

if [[ "$telegram_webhook_url" != https://* ]] \
  || [[ "$telegram_webhook_url" == *$'\n'* ]] \
  || [[ "$telegram_webhook_url" == *$'\r'* ]] \
  || [[ "$telegram_webhook_url" == *'"'* ]] \
  || [[ "$telegram_webhook_url" == *'\\'* ]]; then
  echo "TELEGRAM_WEBHOOK_URL must be a valid HTTPS URL" >&2
  exit 1
fi

telegram_api_base_url="${telegram_api_base_url%/}"
if [ "$telegram_api_base_url" != "$DEFAULT_TELEGRAM_API_BASE_URL" ]; then
  if [ "$telegram_allow_local_test_api" != "true" ] \
    || [[ ! "$telegram_api_base_url" =~ ^http://(127\.0\.0\.1|localhost):[0-9]+$ ]]; then
    echo "TELEGRAM_API_BASE_URL override is allowed only for local tests" >&2
    exit 1
  fi
fi

if ! set_webhook_response="$(telegram_request POST setWebhook \
  "url=${telegram_webhook_url}" \
  "secret_token=${telegram_webhook_secret}" \
  "allowed_updates=${ALLOWED_UPDATES}")"; then
  echo "Telegram setWebhook request failed" >&2
  exit 1
fi

if [ "$(jq -r '.ok == true and .result == true' <<< "$set_webhook_response")" != "true" ]; then
  echo "Telegram setWebhook response was not successful" >&2
  exit 1
fi

if ! webhook_info_response="$(telegram_request GET getWebhookInfo)"; then
  echo "Telegram getWebhookInfo request failed" >&2
  exit 1
fi

if [ "$(jq -r '.ok == true and (.result | type == "object")' <<< "$webhook_info_response")" != "true" ]; then
  echo "Telegram getWebhookInfo response was not successful" >&2
  exit 1
fi

registered_url="$(jq -r '.result.url // ""' <<< "$webhook_info_response")"
if [ "$registered_url" != "$telegram_webhook_url" ]; then
  echo "Telegram webhook URL verification failed" >&2
  exit 1
fi

pending_update_count="$(jq -r '.result.pending_update_count // 0' <<< "$webhook_info_response")"
last_error_message="$(jq -r '.result.last_error_message // ""' <<< "$webhook_info_response")"

echo "Telegram webhook registration verified"
echo "url=$registered_url"
echo "pending_update_count=$pending_update_count"
if [ -n "$last_error_message" ]; then
  echo "last_error_message=$last_error_message"
fi
