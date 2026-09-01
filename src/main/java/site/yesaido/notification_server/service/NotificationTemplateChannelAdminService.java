package site.yesaido.notification_server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.common.exception.client.ConflictException;
import site.yesaido.common.exception.client.ForbiddenException;
import site.yesaido.common.exception.client.NotFoundException;
import site.yesaido.notification_server.dto.admin.ChannelTypeRequest;
import site.yesaido.notification_server.dto.admin.ChannelTypeResponse;
import site.yesaido.notification_server.dto.admin.NotificationTemplateRequest;
import site.yesaido.notification_server.dto.admin.NotificationTemplateResponse;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationTemplate;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;
import site.yesaido.notification_server.repository.SubscriptionChannelRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationTemplateChannelAdminService {
    private final NotificationTemplateRepository templateRepository;
    private final NotificationEventTypeRepository eventRepository;
    private final ChannelTypeRepository channelRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final SubscriptionChannelRepository subscriptionChannelRepository;

    public List<NotificationTemplateResponse> templates(String role) {
        admin(role);
        return templateRepository.findAll(Sort.by("id")).stream().map(NotificationTemplateResponse::from).toList();
    }

    @Transactional
    public NotificationTemplateResponse createTemplate(String role, NotificationTemplateRequest r) {
        admin(role);
        var event = eventRepository.findById(r.eventTypeId()).orElseThrow(() -> new NotFoundException("알림 이벤트를 찾을 수 없습니다."));
        var channel = channelRepository.findById(r.channelTypeId()).orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다."));
        int version = r.version() == null ? 1 : r.version();
        if (templateRepository.findFirstByEventType_IdAndChannelType_IdOrderByVersionDesc(event.getId(), channel.getId()).filter(t -> t.getVersion() == version).isPresent())
            throw new ConflictException("같은 이벤트·채널·버전의 Template이 이미 존재합니다.");
        return NotificationTemplateResponse.from(templateRepository.save(new NotificationTemplate(event, channel, r.bodyTemplate(), version)));
    }

    @Transactional
    public NotificationTemplateResponse updateTemplate(String role, Long id, NotificationTemplateRequest r) {
        admin(role);
        var t = templateRepository.findById(id).orElseThrow(() -> new NotFoundException("Template을 찾을 수 없습니다."));
        t.changeBodyTemplate(r.bodyTemplate());
        return NotificationTemplateResponse.from(t);
    }

    @Transactional
    public void deleteTemplate(String role, Long id) {
        admin(role);
        if (!templateRepository.existsById(id)) throw new NotFoundException("Template을 찾을 수 없습니다.");
        if (deliveryRepository.existsByTemplate_Id(id))
            throw new ConflictException("발송 이력에서 사용 중인 Template은 삭제할 수 없습니다.");
        templateRepository.deleteById(id);
    }

    public List<ChannelTypeResponse> channels(String role) {
        admin(role);
        return channelRepository.findAll(Sort.by("id")).stream().map(ChannelTypeResponse::from).toList();
    }

    @Transactional
    public ChannelTypeResponse createChannel(String role, ChannelTypeRequest r) {
        admin(role);
        if (channelRepository.findByCodeAndDeletedFalse(r.code()).isPresent())
            throw new ConflictException("이미 등록된 채널 코드입니다.");
        return ChannelTypeResponse.from(channelRepository.save(new ChannelType(r.code(), r.displayName())));
    }

    @Transactional
    public ChannelTypeResponse updateChannel(String role, Long id, ChannelTypeRequest r) {
        admin(role);
        var c = channelRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("활성 채널을 찾을 수 없습니다."));
        if (channelRepository.existsByCodeAndDeletedFalseAndIdNot(r.code(), id))
            throw new ConflictException("이미 등록된 채널 코드입니다.");
        c.changeDetails(r.code(), r.displayName());
        return ChannelTypeResponse.from(c);
    }

    @Transactional
    public void deleteChannel(String role, Long id) {
        admin(role);
        var c = channelRepository.findById(id).orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다."));
        c.softDelete();
    }

    @Transactional
    public void restoreChannel(String role, Long id) {
        admin(role);
        var c = channelRepository.findById(id).orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다."));
        if (channelRepository.findByCodeAndDeletedFalse(c.getCode()).isPresent())
            throw new ConflictException("이미 활성화된 채널 코드입니다.");
        c.restore();
    }

    private void admin(String role) {
        if (!"ADMIN".equals(role)) throw new ForbiddenException("관리자 권한이 필요합니다.");
    }
}
