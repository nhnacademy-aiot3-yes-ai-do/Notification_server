package com.ecosphere.notification.domain;

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
@Table(name = "notification_subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSubscription extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_subscription_type_id", nullable = false)
    private NotificationSubscriptionType subscriptionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_endpoint_id", nullable = false)
    private NotificationEndpoint endpoint;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public NotificationSubscription(NotificationSubscriptionType subscriptionType,
                                    NotificationEndpoint endpoint, Long targetId) {
        this.subscriptionType = subscriptionType;
        this.endpoint = endpoint;
        this.targetId = targetId;
        this.enabled = true;
        this.deleted = false;
    }

    public void changeEnabled(boolean enabled) {
        if (!deleted) {
            this.enabled = enabled;
        }
    }

    public void softDelete() {
        this.enabled = false;
        this.deleted = true;
    }
}
