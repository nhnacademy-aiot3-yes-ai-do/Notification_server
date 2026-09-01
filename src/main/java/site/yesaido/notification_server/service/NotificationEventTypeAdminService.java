package site.yesaido.notification_server.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.common.exception.client.ConflictException;
import site.yesaido.common.exception.client.ForbiddenException;
import site.yesaido.common.exception.client.NotFoundException;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeRequest;
import site.yesaido.notification_server.dto.admin.NotificationEventTypeResponse;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.SubscriptionTargetType;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionTypeRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;
import site.yesaido.notification_server.repository.SubscriptionTargetTypeRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationEventTypeAdminService {

    private static final String ADMIN = "ADMIN";

    private final NotificationEventTypeRepository eventTypeRepository;
    private final SubscriptionTargetTypeRepository targetTypeRepository;
    private final NotificationSubscriptionTypeRepository subscriptionTypeRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;

    public List<NotificationEventTypeResponse> findAll(String role) {
        requireAdmin(role);
        return eventTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .map(NotificationEventTypeResponse::from)
                .toList();
    }

    @Transactional
    public NotificationEventTypeResponse create(String role, NotificationEventTypeRequest request) {
        requireAdmin(role);
        if (eventTypeRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("이미 등록된 알림 이벤트 코드입니다.");
        }
        NotificationEventType eventType = new NotificationEventType(
                request.code(), request.displayName(), request.description(), findTargetType(request.targetType()));
        return NotificationEventTypeResponse.from(eventTypeRepository.save(eventType));
    }

    @Transactional
    public NotificationEventTypeResponse update(String role, Long id, NotificationEventTypeRequest request) {
        requireAdmin(role);
        NotificationEventType eventType = findEventType(id);
        if (eventTypeRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("이미 등록된 알림 이벤트 코드입니다.");
        }
        eventType.changeDetails(
                request.code(), request.displayName(), request.description(), findTargetType(request.targetType()));
        return NotificationEventTypeResponse.from(eventType);
    }

    @Transactional
    public void delete(String role, Long id) {
        requireAdmin(role);
        NotificationEventType eventType = findEventType(id);
        if (subscriptionTypeRepository.existsByEventType_Id(id)
                || templateRepository.existsByEventType_Id(id)
                || notificationRepository.existsByEventType_Id(id)) {
            throw new ConflictException("구독·템플릿·발송 이력에서 사용 중인 이벤트는 삭제할 수 없습니다.");
        }
        eventTypeRepository.delete(eventType);
    }

    private NotificationEventType findEventType(Long id) {
        return eventTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("알림 이벤트를 찾을 수 없습니다."));
    }

    private SubscriptionTargetType findTargetType(String targetType) {
        return targetTypeRepository.findByTargetType(targetType)
                .orElseThrow(() -> new NotFoundException("알림 대상 타입을 찾을 수 없습니다."));
    }

    private void requireAdmin(String role) {
        if (!ADMIN.equals(role)) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
    }
}
