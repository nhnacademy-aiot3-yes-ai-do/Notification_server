package site.yesaido.notification_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "subscription_target_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionTargetType extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, unique = true, length = 30)
    private String targetType;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    public SubscriptionTargetType(String targetType, String displayName) {
        this.targetType = targetType;
        this.displayName = displayName;
    }
}
