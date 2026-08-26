package site.yesaido.notification_server.validation;

/**
 * HTTP 입력 검증에서 반복 사용하는 메시지 모음.
 * 사용자에게 노출되는 문구를 한곳에서 관리해 Controller마다 표현이 달라지지 않게 한다.
 */
public final class ValidationMessages {

    public static final String USER_ID_POSITIVE = "사용자 ID는 1 이상이어야 합니다.";
    public static final String ENDPOINT_ID_POSITIVE = "알림 수신 경로 ID는 1 이상이어야 합니다.";
    public static final String SUBSCRIPTION_ID_POSITIVE = "알림 구독 ID는 1 이상이어야 합니다.";
    public static final String CULTIVATION_ID_POSITIVE = "재배지 ID는 1 이상이어야 합니다.";
    public static final String CULTIVATION_IDS_NOT_EMPTY = "재배지 ID 목록은 비어 있을 수 없습니다.";
    public static final String SUMMARY_DATE_REQUIRED = "조회 날짜는 필수입니다.";
    public static final String SUMMARY_START_DATE_REQUIRED = "조회 시작일은 필수입니다.";
    public static final String SUMMARY_END_DATE_REQUIRED = "조회 종료일은 필수입니다.";
    public static final String SUMMARY_DATE_RANGE_INVALID = "조회 시작일은 종료일보다 이후일 수 없습니다.";

    private ValidationMessages() {
    }
}
