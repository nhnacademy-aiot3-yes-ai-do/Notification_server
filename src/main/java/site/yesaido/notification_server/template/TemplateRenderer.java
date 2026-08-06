package site.yesaido.notification_server.template;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.exception.TemplateRenderingException;
import site.yesaido.notification_server.messaging.DomainEvent;

@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    public String render(String template, DomainEvent event) {
        Map<String, String> variables = new HashMap<>();
        flatten("", event.payload(), variables);
        variables.put("eventId", event.eventId().toString());
        variables.put("eventType", event.eventType());
        variables.put("producer", event.producer());
        variables.put("targetType", event.targetType());
        variables.put("targetId", event.targetId().toString());
        variables.put("occurredAt", event.occurredAt().toString());

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                throw new TemplateRenderingException(
                        "템플릿 변수 값을 찾을 수 없습니다: " + name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void flatten(String prefix, JsonNode node, Map<String, String> variables) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            variables.put(prefix, node.asText());
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flatten(key, field.getValue(), variables);
            }
        }
    }
}
