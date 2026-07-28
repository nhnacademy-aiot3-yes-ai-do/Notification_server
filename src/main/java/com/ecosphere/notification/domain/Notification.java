package com.ecosphere.notification.domain;

import java.util.Map;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> eventPayload;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    public Notification(UUID sourceEventId,
                        Map<String, Object> eventPayload, String message) {
        this.sourceEventId = sourceEventId;
        this.eventPayload = eventPayload;
        this.message = message;
    }
}
