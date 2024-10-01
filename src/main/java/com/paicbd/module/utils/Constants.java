package com.paicbd.module.utils;

public class Constants {
    private Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final String STARTED = "STARTED";
    public static final String STOPPED = "STOPPED";
    public static final String BINDING = "BINDING";
    public static final String BOUND = "BOUND";
    public static final String UNBINDING = "UNBINDING";
    public static final String TYPE = "sp";
    public static final String WEBSOCKET_STATUS_ENDPOINT = "/app/handler-status";
    public static final String SESSION_CONFIRM_ENDPOINT = "/app/session-confirm";
    public static final String UPDATE_SERVICE_PROVIDER_ENDPOINT = "/app/smpp/updateServiceProvider";
    public static final String RESPONSE_SMPP_SERVER_ENDPOINT = "/app/response-smpp-server";
    public static final String UPDATE_SERVER_HANDLER_ENDPOINT = "/app/updateServerHandler";
    public static final String SERVICE_PROVIDER_DELETED_ENDPOINT = "/app/smpp/serviceProviderDeleted";
    public static final String PARAM_UPDATE_STATUS = "status";
    public static final String PARAM_UPDATE_SESSIONS = "sessions";
    public static final String GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT = "/app/generalSettings";
    public static final String IEI_CONCATENATED_MESSAGE = "0x00";
}
