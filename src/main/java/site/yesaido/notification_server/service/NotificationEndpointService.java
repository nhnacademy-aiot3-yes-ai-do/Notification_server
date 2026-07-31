package site.yesaido.notification_server.service;

import java.util.List;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;

public interface NotificationEndpointService {

    EndpointResponse create(Long userId, EndpointCreateRequest request);

    List<EndpointResponse> findAll(Long userId);

    EndpointResponse update(Long userId, Long endpointId, EndpointUpdateRequest request);

    EndpointResponse changeEnabled(Long userId, Long endpointId, boolean enabled);

    void delete(Long userId, Long endpointId);
}
