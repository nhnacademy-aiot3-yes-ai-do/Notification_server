package site.yesaido.notification_server.rabbitmq;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.facade.RabbitMqNotificationFacade;
import site.yesaido.notification_server.rabbitmq.listener.AiRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.CultivationRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.RuleEngineRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.UserRabbitMQConsumer;

class NotificationRabbitMQConsumerTest {

    private static final long DELIVERY_TAG = 42L;

    @Test
    void ruleEngine_threshold_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        RuleEngineRabbitMQConsumer consumer = new RuleEngineRabbitMQConsumer(facade);
        RuleEngineEvent.ThresholdStatusChangedEvent event =
                new RuleEngineEvent.ThresholdStatusChangedEvent(null, null, null, null);

        consumer.consumeThreshold(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void ruleEngine_action_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        RuleEngineRabbitMQConsumer consumer = new RuleEngineRabbitMQConsumer(facade);
        RuleEngineEvent.AutomationStateChangedEvent event =
                new RuleEngineEvent.AutomationStateChangedEvent(null, 0L, null, null, false, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeAction(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void ruleEngine_threshold_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        RuleEngineRabbitMQConsumer consumer = new RuleEngineRabbitMQConsumer(facade);
        RuleEngineEvent.ThresholdStatusChangedEvent event =
                new RuleEngineEvent.ThresholdStatusChangedEvent(null, null, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeThreshold(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void ruleEngine_action_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        RuleEngineRabbitMQConsumer consumer = new RuleEngineRabbitMQConsumer(facade);
        RuleEngineEvent.AutomationStateChangedEvent event =
                new RuleEngineEvent.AutomationStateChangedEvent(null, 0L, null, null, false, null);

        consumer.consumeAction(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void ai_daily_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        AiRabbitMQConsumer consumer = new AiRabbitMQConsumer(facade);
        AiEvent.DailyFeedbackGeneratedEvent event =
                new AiEvent.DailyFeedbackGeneratedEvent(null, 0L, 0L, null, null, null, null);

        consumer.consumeDaily(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void ai_cultivation_complete_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        AiRabbitMQConsumer consumer = new AiRabbitMQConsumer(facade);
        AiEvent.CultivationCompletedEvent event =
                new AiEvent.CultivationCompletedEvent(null, 0L, 0L, null, null, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeCultivationComplete(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void ai_daily_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        AiRabbitMQConsumer consumer = new AiRabbitMQConsumer(facade);
        AiEvent.DailyFeedbackGeneratedEvent event =
                new AiEvent.DailyFeedbackGeneratedEvent(null, 0L, 0L, null, null, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeDaily(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void ai_cultivation_complete_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        AiRabbitMQConsumer consumer = new AiRabbitMQConsumer(facade);
        AiEvent.CultivationCompletedEvent event =
                new AiEvent.CultivationCompletedEvent(null, 0L, 0L, null, null, null, null);

        consumer.consumeCultivationComplete(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void cultivation_harvest_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        CultivationRabbitMQConsumer consumer = new CultivationRabbitMQConsumer(facade);
        CultivationEvent.HarvestCompletedEvent event =
                new CultivationEvent.HarvestCompletedEvent(null, 0L, 0L, null, 0L, null, null);

        consumer.consumeHarvest(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void cultivation_sensor_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        CultivationRabbitMQConsumer consumer = new CultivationRabbitMQConsumer(facade);
        CultivationEvent.SensorDataUnavailableEvent event =
                new CultivationEvent.SensorDataUnavailableEvent(null, 0L, null, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeSensor(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void cultivation_member_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        CultivationRabbitMQConsumer consumer = new CultivationRabbitMQConsumer(facade);
        CultivationEvent.CultivationMemberInvitedEvent event =
                new CultivationEvent.CultivationMemberInvitedEvent(
                        null, 0L, 0L, null, 0L, null, null, null);

        consumer.consumeMember(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void cultivation_harvest_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        CultivationRabbitMQConsumer consumer = new CultivationRabbitMQConsumer(facade);
        CultivationEvent.HarvestCompletedEvent event =
                new CultivationEvent.HarvestCompletedEvent(null, 0L, 0L, null, 0L, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeHarvest(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void cultivation_sensor_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        CultivationRabbitMQConsumer consumer = new CultivationRabbitMQConsumer(facade);
        CultivationEvent.SensorDataUnavailableEvent event =
                new CultivationEvent.SensorDataUnavailableEvent(null, 0L, null, null, null);

        consumer.consumeSensor(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void cultivation_member_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        CultivationRabbitMQConsumer consumer = new CultivationRabbitMQConsumer(facade);
        CultivationEvent.CultivationMemberInvitedEvent event =
                new CultivationEvent.CultivationMemberInvitedEvent(
                        null, 0L, 0L, null, 0L, null, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeMember(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void inquiry_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer consumer = new UserRabbitMQConsumer(facade);
        UserEvent.InquirySubmittedEvent event =
                new UserEvent.InquirySubmittedEvent(null, 0L, null, 0L, null, null, null, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consumeInquiry(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void inquiry_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer consumer = new UserRabbitMQConsumer(facade);
        UserEvent.InquirySubmittedEvent event =
                new UserEvent.InquirySubmittedEvent(null, 0L, null, 0L, null, null, null, null, null);

        consumer.consumeInquiry(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void auth_login_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer.AuthConsumer consumer =
                new UserRabbitMQConsumer.AuthConsumer(facade);
        UserEvent.UserLoginAttemptedEvent event =
                new UserEvent.UserLoginAttemptedEvent(null, 0L, null, false, null, null);

        consumer.consume(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void auth_password_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer.AuthConsumer consumer =
                new UserRabbitMQConsumer.AuthConsumer(facade);
        UserEvent.UserPasswordChangeAttemptedEvent event =
                new UserEvent.UserPasswordChangeAttemptedEvent(null, 0L, null, false, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consume(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void auth_reactivation_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer.AuthConsumer consumer =
                new UserRabbitMQConsumer.AuthConsumer(facade);
        UserEvent.UserAccountReactivationAttemptedEvent event =
                new UserEvent.UserAccountReactivationAttemptedEvent(null, 0L, null, false, null);

        consumer.consume(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void auth_login_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer.AuthConsumer consumer =
                new UserRabbitMQConsumer.AuthConsumer(facade);
        UserEvent.UserLoginAttemptedEvent event =
                new UserEvent.UserLoginAttemptedEvent(null, 0L, null, false, null, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consume(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void auth_password_success_acknowledges_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer.AuthConsumer consumer =
                new UserRabbitMQConsumer.AuthConsumer(facade);
        UserEvent.UserPasswordChangeAttemptedEvent event =
                new UserEvent.UserPasswordChangeAttemptedEvent(null, 0L, null, false, null);

        consumer.consume(event, channel, DELIVERY_TAG);

        verify(facade).handle(event);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void auth_reactivation_failure_rejects_message() throws IOException {
        RabbitMqNotificationFacade facade = mock(RabbitMqNotificationFacade.class);
        Channel channel = mock(Channel.class);
        UserRabbitMQConsumer.AuthConsumer consumer =
                new UserRabbitMQConsumer.AuthConsumer(facade);
        UserEvent.UserAccountReactivationAttemptedEvent event =
                new UserEvent.UserAccountReactivationAttemptedEvent(null, 0L, null, false, null);
        doThrow(new IllegalStateException("processing failed")).when(facade).handle(event);

        consumer.consume(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }
}
