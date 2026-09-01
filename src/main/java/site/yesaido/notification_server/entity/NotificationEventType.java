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
@Table(name = "notification_event_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEventType extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_type", nullable = false)
    private SubscriptionTargetType targetType;

    public NotificationEventType(String code, String displayName, String description,
                                 SubscriptionTargetType targetType) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.targetType = targetType;
    }

    public void changeDetails(String code, String displayName, String description,
                              SubscriptionTargetType targetType) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.targetType = targetType;
    }
}
