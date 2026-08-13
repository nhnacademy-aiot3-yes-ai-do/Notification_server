package site.yesaido.notification_server.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleEngineEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void sensorDataDto_acceptsStringSensorTypeAndUnit() throws Exception {
        String json = """
                {
                  "place": "농장 A",
                  "location": "재배동 1",
                  "deviceModel": "sensor-v1",
                  "deviceName": "온도 센서",
                  "deviceEui": "device-eui-1",
                  "sensorType": "TEMPERATURE",
                  "unit": "°C",
                  "value": 23.5,
                  "time": "2026-08-11T12:30:00+09:00",
                  "cultivationId": 101
                }
                """;

        RuleEngineEvent.SensorDataDto sensorData = objectMapper.readValue(
                json,
                RuleEngineEvent.SensorDataDto.class
        );

        assertEquals("TEMPERATURE", sensorData.sensorType());
        assertEquals("°C", sensorData.unit());
        assertEquals(23.5, sensorData.value());
        assertEquals(101L, sensorData.cultivationId());
    }
}
