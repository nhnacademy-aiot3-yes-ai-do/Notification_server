package site.yesaido.notification_server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.yesaido.notification_server.exception.delivery.DeliveryAttemptLimitExceededException;
import site.yesaido.notification_server.exception.delivery.DeliveryClaimAttemptLimitExceededException;
import site.yesaido.notification_server.exception.delivery.DeliveryNotPendingException;
import site.yesaido.notification_server.exception.delivery.DeliveryNotSendingException;

@Getter
@Entity
@Table(name = "notification_delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends AuditEntity {

    /**
     * 알림 서비스의 확정 발송 정책이다. Provider 장애가 지속될 때 무한 재시도를
     * 막고, 최종 실패 이력과 DLQ를 남기기 위해 최대 3회만 시도한다.
     */
    public static final short MAX_ATTEMPT_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_subscription_id", nullable = false)
    private NotificationSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_template_id", nullable = false)
    private NotificationTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "rendered_message", nullable = false, columnDefinition = "TEXT")
    private String renderedMessage;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "sent_at")
    private java.time.LocalDateTime sentAt;

    public NotificationDelivery(Notification notification, NotificationSubscription subscription,
                                NotificationTemplate template,
                                String renderedMessage) {
        this.notification = notification;
        this.subscription = subscription;
        this.template = template;
        this.status = DeliveryStatus.PENDING;
        this.renderedMessage = renderedMessage;
        this.attemptCount = 0;
    }

    /** 외부 발송에 성공했을 때만 호출한다. */
    public void markSent(String providerMessageId) {
        requireSending();
        this.status = DeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = LocalDateTime.now();
    }

    /** 재시도를 모두 소진한 최종 실패일 때 호출한다. */
    public void markFailed(String error) {
        requireSending();
        this.status = DeliveryStatus.FAILED;
        this.error = error;
    }

    /** 재시도 횟수를 도메인 메서드로만 증가시킨다. */
    public void increaseAttemptCount() {
        requireSending();
        if (attemptCount >= MAX_ATTEMPT_COUNT) {
            throw new DeliveryAttemptLimitExceededException("최대 발송 시도 횟수를 초과할 수 없습니다.");
        }
        this.attemptCount++;
    }

    public boolean canRetry() {
        return (status == DeliveryStatus.PENDING || status == DeliveryStatus.SENDING)
                && attemptCount < MAX_ATTEMPT_COUNT;
    }

    /**
     * 외부 Provider 호출 전에 발송 권한을 선점한다. 실제 동시성 제어는 Repository의
     * 조건부 UPDATE로 보장하고, 이 메서드는 도메인 상태 전이 규칙을 표현한다.
     */
    public void claimForDispatch() {
        requirePending();
        if (attemptCount >= MAX_ATTEMPT_COUNT) {
            throw new DeliveryClaimAttemptLimitExceededException(
                    "최대 발송 시도 횟수를 초과한 발송은 선점할 수 없습니다.");
        }
        this.status = DeliveryStatus.SENDING;
    }

    /** 처리 프로세스가 비정상 종료되어 오래 멈춘 선점을 회복할 때만 호출한다. */
    public void releaseStaleClaim() {
        requireSending();
        this.status = DeliveryStatus.PENDING;
    }

    private void requirePending() {
        if (status != DeliveryStatus.PENDING) {
            throw new DeliveryNotPendingException(
                    "대기 상태의 발송만 변경할 수 있습니다. 현재 상태: " + status);
        }
    }

    private void requireSending() {
        if (status != DeliveryStatus.SENDING) {
            throw new DeliveryNotSendingException(
                    "발송 중 상태의 발송만 변경할 수 있습니다. 현재 상태: " + status);
        }
    }
}
