package site.yesaido.notification_server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification_template",
       uniqueConstraints = @UniqueConstraint(columnNames = {
           "notification_event_type_id", "channel_type_id", "version"
       }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_event_type_id", nullable = false)
    private NotificationEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_type_id", nullable = false)
    private ChannelType channelType;

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(nullable = false)
    private int version;

    public NotificationTemplate(NotificationEventType eventType, ChannelType channelType,
                                String bodyTemplate, int version) {
        this.eventType = eventType;
        this.channelType = channelType;
        this.bodyTemplate = bodyTemplate;
        this.version = version;
    }

    public void changeBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }
}
