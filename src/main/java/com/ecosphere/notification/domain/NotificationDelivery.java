package com.ecosphere.notification.domain;

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

@Getter
@Entity
@Table(name = "notification_delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends AuditEntity {

    private static final short MAX_ATTEMPT_COUNT = 3;

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
        requirePending();
        this.status = DeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = LocalDateTime.now();
    }

    /** 재시도를 모두 소진한 최종 실패일 때 호출한다. */
    public void markFailed(String error) {
        requirePending();
        this.status = DeliveryStatus.FAILED;
        this.error = error;
    }

    /** 재시도 횟수를 도메인 메서드로만 증가시킨다. */
    public void increaseAttemptCount() {
        requirePending();
        if (attemptCount >= MAX_ATTEMPT_COUNT) {
            throw new InvalidDeliveryStateException("최대 발송 시도 횟수를 초과할 수 없습니다.");
        }
        this.attemptCount++;
    }

    public boolean canRetry() {
        return status == DeliveryStatus.PENDING && attemptCount < MAX_ATTEMPT_COUNT;
    }

    private void requirePending() {
        if (status != DeliveryStatus.PENDING) {
            throw new InvalidDeliveryStateException(
                    "대기 상태의 발송만 변경할 수 있습니다. 현재 상태: " + status);
        }
    }
}
