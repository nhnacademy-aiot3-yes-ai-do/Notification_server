package site.yesaido.notification_server.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class TelegramWebhookOperationScriptTest {

    private static final Path SCRIPT = Path.of("scripts/telegram/configure-webhook.sh");
    private static final String BOT_TOKEN = "123456:test-bot-token";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String WEBHOOK_URL = "https://api.example.test/webhooks/telegram";

    @Test
    void registersAndVerifiesWebhookWithoutPrintingCredentials() throws Exception {
        assertThat(SCRIPT).exists().isRegularFile();

        Map<String, String> setWebhookForm = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot" + BOT_TOKEN + "/setWebhook", exchange -> {
            setWebhookForm.putAll(readForm(exchange));
            respond(exchange, 200, "{\"ok\":true,\"result\":true}");
        });
        server.createContext("/bot" + BOT_TOKEN + "/getWebhookInfo", exchange -> respond(exchange, 200,
                "{\"ok\":true,\"result\":{\"url\":\"" + WEBHOOK_URL
                        + "\",\"pending_update_count\":0,\"max_connections\":40}}"));
        server.start();

        try {
            ProcessResult result = runScript(server.getAddress().getPort());

            assertThat(result.exitCode())
                    .as("script output: %s", result.output())
                    .isZero();
            assertThat(setWebhookForm)
                    .containsEntry("url", WEBHOOK_URL)
                    .containsEntry("secret_token", WEBHOOK_SECRET)
                    .containsEntry("allowed_updates", "[\"message\"]");
            assertThat(result.output())
                    .contains("url=" + WEBHOOK_URL)
                    .contains("pending_update_count=0")
                    .doesNotContain(BOT_TOKEN)
                    .doesNotContain(WEBHOOK_SECRET);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsWhenTelegramReportsDifferentWebhookUrl() throws Exception {
        assertThat(SCRIPT).exists().isRegularFile();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot" + BOT_TOKEN + "/setWebhook",
                exchange -> respond(exchange, 200, "{\"ok\":true,\"result\":true}"));
        server.createContext("/bot" + BOT_TOKEN + "/getWebhookInfo", exchange -> respond(exchange, 200,
                "{\"ok\":true,\"result\":{\"url\":\"https://wrong.example.test/webhook\"}}"));
        server.start();

        try {
            ProcessResult result = runScript(server.getAddress().getPort());

            assertThat(result.exitCode()).isNotZero();
            assertThat(result.output())
                    .contains("Telegram webhook URL verification failed")
                    .doesNotContain(BOT_TOKEN)
                    .doesNotContain(WEBHOOK_SECRET);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotPrintFailedTelegramResponseThatCouldContainCredentials() throws Exception {
        assertThat(SCRIPT).exists().isRegularFile();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot" + BOT_TOKEN + "/setWebhook", exchange -> respond(exchange, 200,
                "{\"ok\":false,\"description\":\"rejected " + BOT_TOKEN + " " + WEBHOOK_SECRET + "\"}"));
        server.start();

        try {
            ProcessResult result = runScript(server.getAddress().getPort());

            assertThat(result.exitCode()).isNotZero();
            assertThat(result.output())
                    .contains("Telegram setWebhook response was not successful")
                    .doesNotContain(BOT_TOKEN)
                    .doesNotContain(WEBHOOK_SECRET);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsCredentialsOutOfCurlCommandLine() throws Exception {
        Path fakeBin = Files.createTempDirectory("telegram-webhook-curl");
        Path capturedArguments = fakeBin.resolve("curl-arguments.txt");
        Path fakeCurl = fakeBin.resolve("curl");
        Files.writeString(fakeCurl, """
                #!/usr/bin/env bash
                printf '%s\n' "$@" >> "$CURL_ARGUMENTS_FILE"
                if [[ "$*" == *"/bot"* ]]; then
                  request_material="$*"
                else
                  request_material="$*$(cat)"
                fi
                if [[ "$request_material" == *"getWebhookInfo"* ]]; then
                  printf '%s' '{"ok":true,"result":{"url":"https://api.example.test/webhooks/telegram","pending_update_count":0}}'
                else
                  printf '%s' '{"ok":true,"result":true}'
                fi
                """);
        assertThat(fakeCurl.toFile().setExecutable(true)).isTrue();

        String systemPath = System.getenv("PATH");
        ProcessResult result = runScript(Map.of(
                "PATH", fakeBin + System.getProperty("path.separator") + systemPath,
                "CURL_ARGUMENTS_FILE", capturedArguments.toString()));

        assertThat(result.exitCode())
                .as("script output: %s", result.output())
                .isZero();
        assertThat(Files.readString(capturedArguments))
                .startsWith("--disable\n--config\n-\n")
                .doesNotContain(BOT_TOKEN)
                .doesNotContain(WEBHOOK_SECRET)
                .doesNotContain("--fail-with-body");
    }

    @Test
    void rejectsNonOfficialApiBaseWithoutLocalTestOptIn() throws Exception {
        ProcessResult result = runScript(Map.of(
                "TELEGRAM_API_BASE_URL", "http://127.0.0.1:1"));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
                .contains("TELEGRAM_API_BASE_URL override is allowed only for local tests")
                .doesNotContain(BOT_TOKEN)
                .doesNotContain(WEBHOOK_SECRET);
    }

    private ProcessResult runScript(int port) throws IOException, InterruptedException {
        return runScript(Map.of(
                "TELEGRAM_API_BASE_URL", "http://127.0.0.1:" + port,
                "TELEGRAM_ALLOW_LOCAL_TEST_API", "true"));
    }

    private ProcessResult runScript(Map<String, String> environment) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", SCRIPT.toString());
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("TELEGRAM_BOT_TOKEN", BOT_TOKEN);
        processBuilder.environment().put("TELEGRAM_WEBHOOK_SECRET", WEBHOOK_SECRET);
        processBuilder.environment().put("TELEGRAM_WEBHOOK_URL", WEBHOOK_URL);
        processBuilder.environment().putAll(environment);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new ConcurrentHashMap<>();
        Arrays.stream(body.split("&"))
                .map(field -> field.split("=", 2))
                .forEach(field -> form.put(
                        URLDecoder.decode(field[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(field[1], StandardCharsets.UTF_8)));
        return form;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
