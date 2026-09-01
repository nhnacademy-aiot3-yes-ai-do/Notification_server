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
@Table(name = "channel_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelType extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public ChannelType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public void changeDetails(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void restore() {
        this.deleted = false;
    }
}
