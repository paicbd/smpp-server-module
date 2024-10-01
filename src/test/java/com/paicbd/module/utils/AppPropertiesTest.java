package com.paicbd.module.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AppPropertiesTest {
    @InjectMocks
    private AppProperties appProperties;

    @BeforeEach
    void setUp() throws Exception {
        injectField("redisNodes", Arrays.asList("node1", "node2", "node3"));
        injectField("redisMaxTotal", 20);
        injectField("redisMaxIdle", 20);
        injectField("redisMinIdle", 1);
        injectField("redisBlockWhenExhausted", true);
        injectField("wsHost", "localhost");
        injectField("wsPort", 8080);
        injectField("wsPath", "/ws");
        injectField("wsEnabled", true);
        injectField("serviceProvidersHashName", "service_providers");
        injectField("configurationHash", "configuration");
        injectField("serverKey", "smpp_server");
        injectField("smppGeneralSettingsHash", "general_settings");
        injectField("smppGeneralSettingsKey", "smpp_http");
        injectField("websocketHeaderName", "Authorization");
        injectField("websocketHeaderValue", "Authorization");
        injectField("websocketRetryInterval", 10);
        injectField("instanceName", "smpp-server");
        injectField("instanceIp", "127.0.0.1");
        injectField("instancePort", "9908");
        injectField("instanceInitialStatus", "STARTED");
        injectField("instanceProtocol", "SMPP");
        injectField("instanceScheme", "");
        injectField("httpRequestApiKey", "123");
        injectField("deliverSmQueue", "smpp_dlr");
        injectField("deliverSmWorkers", 10);
        injectField("deliverSmBatchSizePerWorker", 1000);
        injectField("smppServerIp", "127.0.0.1");
        injectField("smppServerPort", 5054);
        injectField("smppServerProcessorDegree", 3);
        injectField("smppServerQueueCapacity", 100);
        injectField("smppServerTransactionTimer", 5000);
        injectField("smppServerWaitForBind", 5000);
        injectField("preMessageList", "PreMessage");
        injectField("httpRequestApiKey", "123");
        injectField("messagePartsHash", "smpp_message_parts");
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AppProperties.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(appProperties, value);
    }

    @Test
    void testAppPropertiesRedisAndSocket() {
        List<String> expectedRedisNodes = Arrays.asList("node1", "node2", "node3");
        assertEquals(expectedRedisNodes, appProperties.getRedisNodes());
        assertEquals(20, appProperties.getRedisMaxTotal());
        assertEquals(20, appProperties.getRedisMaxIdle());
        assertEquals(1, appProperties.getRedisMinIdle());
        assertTrue(appProperties.isRedisBlockWhenExhausted());
        assertEquals("localhost", appProperties.getWsHost());
        assertEquals(8080, appProperties.getWsPort());
        assertEquals("/ws", appProperties.getWsPath());
        assertTrue(appProperties.isWsEnabled());
        assertEquals("Authorization", appProperties.getWebsocketHeaderName());
        assertEquals("Authorization", appProperties.getWebsocketHeaderValue());
        assertEquals(10, appProperties.getWebsocketRetryInterval());
    }

    @Test
    void testAppPropertiesInstance() {
        assertEquals("smpp-server", appProperties.getInstanceName());
        assertEquals("127.0.0.1", appProperties.getInstanceIp());
        assertEquals("9908", appProperties.getInstancePort());
        assertEquals("STARTED", appProperties.getInstanceInitialStatus());
        assertEquals("SMPP", appProperties.getInstanceProtocol());
        assertEquals("", appProperties.getInstanceScheme());
    }

    @Test
    void testAppPropertiesList() {
        assertEquals("smpp_dlr", appProperties.getDeliverSmQueue());
        assertEquals("PreMessage", appProperties.getPreMessageList());
        assertEquals("service_providers", appProperties.getServiceProvidersHashName());
        assertEquals("configuration", appProperties.getConfigurationHash());
        assertEquals("smpp_server", appProperties.getServerKey());
        assertEquals("general_settings", appProperties.getSmppGeneralSettingsHash());
        assertEquals("smpp_http", appProperties.getSmppGeneralSettingsKey());
        assertEquals("smpp_message_parts", appProperties.getMessagePartsHash());
    }

    @Test
    void testAppPropertiesParameter() {
        assertEquals("127.0.0.1", appProperties.getSmppServerIp());
        assertEquals("123", appProperties.getHttpRequestApiKey());
        assertEquals(10, appProperties.getDeliverSmWorkers());
        assertEquals(1000, appProperties.getDeliverSmBatchSizePerWorker());
        assertEquals(3, appProperties.getSmppServerProcessorDegree());
        assertEquals(5054, appProperties.getSmppServerPort());
        assertEquals(100, appProperties.getSmppServerQueueCapacity());
        assertEquals(5000, appProperties.getSmppServerTransactionTimer());
        assertEquals(5000, appProperties.getSmppServerWaitForBind());
    }
}