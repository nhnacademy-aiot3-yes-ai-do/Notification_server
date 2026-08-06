package site.yesaido.notification_server.domain;

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
@Table(name = "notification_endpoint")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEndpoint extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_type_id", nullable = false)
    private ChannelType channelType;

    @Column(nullable = false, length = 500)
    private String destination;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public NotificationEndpoint(Long userId, ChannelType channelType, String destination,
                                String displayName) {
        this.userId = userId;
        this.channelType = channelType;
        this.destination = destination;
        this.displayName = displayName;
        this.enabled = true;
        this.deleted = false;
    }

    public void update(String destination, String displayName) {
        this.destination = destination;
        this.displayName = displayName;
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
