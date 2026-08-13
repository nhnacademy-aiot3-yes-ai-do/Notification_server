package site.yesaido.notification_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "subscription_channel",
       uniqueConstraints = @UniqueConstraint(columnNames = {
           "notification_subscription_type_id", "channel_type_id"
       }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_subscription_type_id", nullable = false)
    private NotificationSubscriptionType subscriptionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_type_id", nullable = false)
    private ChannelType channelType;

    public SubscriptionChannel(NotificationSubscriptionType subscriptionType, ChannelType channelType) {
        this.subscriptionType = subscriptionType;
        this.channelType = channelType;
    }
}
