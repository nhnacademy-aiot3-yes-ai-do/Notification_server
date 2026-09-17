package site.yesaido.notification_server.entity;

import jakarta.persistence.*;
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
