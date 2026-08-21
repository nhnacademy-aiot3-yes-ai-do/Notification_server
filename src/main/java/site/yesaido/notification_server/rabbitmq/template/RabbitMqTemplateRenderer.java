package site.yesaido.notification_server.rabbitmq.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.exception.template.NotificationTemplateVariableMissingException;

@Component
@RequiredArgsConstructor
public class RabbitMqTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    private final ObjectMapper objectMapper;

    public String render(String template, Object payload) {
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String variable = matcher.group(1);
            JsonNode value = payloadNode.at(toJsonPointer(variable));
            if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
                throw new NotificationTemplateVariableMissingException(
                        "템플릿 변수 값을 찾을 수 없습니다: " + variable);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value.asText()));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String toJsonPointer(String variable) {
        StringBuilder pointer = new StringBuilder();
        for (String segment : variable.split("\\.")) {
            pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }
}
