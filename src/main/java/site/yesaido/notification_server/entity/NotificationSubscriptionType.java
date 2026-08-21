package site.yesaido.notification_server.entity;

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

@Getter
@Entity
@Table(name = "notification_subscription_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSubscriptionType extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_event_type_id", nullable = false)
    private NotificationEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_target_type_id", nullable = false)
    private SubscriptionTargetType targetType;

    @Column(name = "notification_subscription_name", nullable = false, length = 20)
    private String name;

    @Column(length = 500)
    private String description;

    public NotificationSubscriptionType(NotificationEventType eventType,
                                        SubscriptionTargetType targetType,
                                        String name, String description) {
        this.eventType = eventType;
        this.targetType = targetType;
        this.name = name;
        this.description = description;
    }
}
