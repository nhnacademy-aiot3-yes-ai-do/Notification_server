package site.yesaido.notification_server.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_event_type_id", nullable = false)
    private NotificationEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> eventPayload;

    public Notification(UUID sourceEventId,
                        NotificationEventType eventType,
                        Map<String, Object> eventPayload) {
        this(sourceEventId, eventType, null, null, eventPayload);
    }

    public Notification(UUID sourceEventId,
                        NotificationEventType eventType,
                        Long targetId,
                        OffsetDateTime occurredAt,
                        Map<String, Object> eventPayload) {
        this.sourceEventId = sourceEventId;
        this.eventType = eventType;
        this.targetId = targetId;
        this.occurredAt = occurredAt;
        this.eventPayload = eventPayload;
    }
}
