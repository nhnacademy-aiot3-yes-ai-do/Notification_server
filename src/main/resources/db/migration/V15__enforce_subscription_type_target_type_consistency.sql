-- subscription type은 자신이 가리키는 event type과 동일한 target type만 사용할 수 있다.
-- cross-table 제약은 CHECK로 표현할 수 없으므로, 기존 데이터 검증 후 양쪽 변경을 trigger로 강제한다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM notification_subscription_type subscription_type
        JOIN notification_event_type event_type
          ON event_type.id = subscription_type.notification_event_type_id
        WHERE event_type.target_type <> subscription_type.subscription_target_type_id
    ) THEN
        RAISE EXCEPTION
            'notification_subscription_type target type must match notification_event_type target type';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION validate_notification_subscription_type_target_type()
RETURNS TRIGGER AS $$
DECLARE
    event_target_type_id BIGINT;
BEGIN
    SELECT target_type
      INTO event_target_type_id
      FROM notification_event_type
     WHERE id = NEW.notification_event_type_id;

    IF event_target_type_id IS NULL THEN
        RAISE EXCEPTION 'notification event type % does not exist', NEW.notification_event_type_id;
    END IF;

    IF event_target_type_id <> NEW.subscription_target_type_id THEN
        RAISE EXCEPTION
            'notification subscription type target type (%) must match event type target type (%)',
            NEW.subscription_target_type_id, event_target_type_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_notification_subscription_type_target_type
BEFORE INSERT OR UPDATE OF notification_event_type_id, subscription_target_type_id
ON notification_subscription_type
FOR EACH ROW
EXECUTE FUNCTION validate_notification_subscription_type_target_type();

-- event type의 target type 변경도 연결된 모든 subscription type과의 정합성을 보장해야 한다.
CREATE OR REPLACE FUNCTION validate_notification_event_type_target_type()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM notification_subscription_type subscription_type
        WHERE subscription_type.notification_event_type_id = NEW.id
          AND subscription_type.subscription_target_type_id <> NEW.target_type
    ) THEN
        RAISE EXCEPTION
            'notification event type target type (%) must match all linked subscription type target types',
            NEW.target_type;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_notification_event_type_target_type
BEFORE UPDATE OF target_type
ON notification_event_type
FOR EACH ROW
WHEN (OLD.target_type IS DISTINCT FROM NEW.target_type)
EXECUTE FUNCTION validate_notification_event_type_target_type();
