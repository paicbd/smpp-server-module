package com.paicbd.module.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {
    @Test
    void testPrivateConstructor() throws NoSuchMethodException {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void testConstantsValues() {
        assertEquals("STOPPED", Constants.STOPPED);
        assertEquals("BINDING", Constants.BINDING);
        assertEquals("BOUND", Constants.BOUND);
        assertEquals("UNBINDING", Constants.UNBINDING);
        assertEquals("sp", Constants.TYPE);
        assertEquals("/app/handler-status", Constants.WEBSOCKET_STATUS_ENDPOINT);
        assertEquals("/app/session-confirm", Constants.SESSION_CONFIRM_ENDPOINT);
        assertEquals("/app/smpp/updateServiceProvider", Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT);
        assertEquals("/app/response-smpp-server", Constants.RESPONSE_SMPP_SERVER_ENDPOINT);
        assertEquals("/app/updateServerHandler", Constants.UPDATE_SERVER_HANDLER_ENDPOINT);
        assertEquals("/app/smpp/serviceProviderDeleted", Constants.SERVICE_PROVIDER_DELETED_ENDPOINT);
        assertEquals("/app/generalSettings", Constants.GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT);
        assertEquals("status", Constants.PARAM_UPDATE_STATUS);
        assertEquals("sessions", Constants.PARAM_UPDATE_SESSIONS);
    }
}