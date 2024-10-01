package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.exception.RTException;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import redis.clients.jedis.JedisCluster;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.paicbd.module.utils.Constants.GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT;
import static com.paicbd.module.utils.Constants.SERVICE_PROVIDER_DELETED_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_SERVER_HANDLER_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFrameHandlerTest {
    @Mock(strictness = Mock.Strictness.LENIENT) // In handleFrameLogic_invalidDestination test case, IllegalArgumentException is thrown by the method
    private SocketSession socketSession;
    @Mock
    private StompHeaders stompHeaders;
    @Mock
    private StompSession stompSession;
    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties appProperties;
    @InjectMocks
    GeneralSettingsCacheConfig generalSettingsCacheConfig;
    @Mock
    private ConcurrentMap<String, SpSession> spSessionMap;
    @Mock
    private ConcurrentMap<Integer, String> networkIdSystemIdMap;

    @InjectMocks
    CustomFrameHandler customFrameHandler;
    @Mock
    private Set<ServiceProvider> providers;
    @InjectMocks
    private ServerHandler serverHandler;

    @BeforeEach
    void setUp() {
        generalSettingsCacheConfig = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMap, jedisCluster, appProperties, networkIdSystemIdMap, providers, serverHandler);
        when(socketSession.getStompSession()).thenReturn(stompSession);
    }

    @Test
    void handleFrameLogic_updateServiceProvider() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        Mockito.when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.jedisCluster.hget(this.appProperties.getServiceProvidersHashName(), payload)).thenReturn("{}");
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_updateServiceProvider_spJsonNull() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_updateServiceProvider_spJsonNull_throwException() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        Mockito.when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.jedisCluster.hget(this.appProperties.getServiceProvidersHashName(), payload)).thenReturn("2:45}");
        assertThrows(RTException.class, () -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_deleteServiceProvider() {
        String payload = "systemId123";
        Session session = new SMPPSession();
        session.setEnquireLinkTimer(20);
        ServiceProvider sp = new ServiceProvider();
        sp.setNetworkId(1);
        SpSession spSessionData = new SpSession(this.jedisCluster, sp, this.appProperties);
        spSessionData.getCurrentSmppSessions().add(session);
        when(stompHeaders.getDestination()).thenReturn(SERVICE_PROVIDER_DELETED_ENDPOINT);
        when(spSessionMap.get(payload)).thenReturn(spSessionData);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_updatedServerHandler() {
        String payload = "systemId123";
        Mockito.when(this.appProperties.getServerKey()).thenReturn("smpp_server");
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVER_HANDLER_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
        Mockito.when(this.jedisCluster.hget(this.appProperties.getConfigurationHash(), this.appProperties.getServerKey()))
                .thenReturn("{\"state\":\"STOPPED\"}");
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_generalSettings() {
        String payload = "systemId123";
        Mockito.when(this.appProperties.getSmppGeneralSettingsHash()).thenReturn("general_settings");
        Mockito.when(this.appProperties.getSmppGeneralSettingsKey()).thenReturn("smpp_http");
        Mockito.when(this.jedisCluster.hget(this.appProperties.getSmppGeneralSettingsHash(),
                        this.appProperties.getSmppGeneralSettingsKey()))
                .thenReturn("{\"id\":1,\"validity_period\":60,\"max_validity_period\":240,\"source_addr_ton\":1," +
                        "\"source_addr_npi\":1,\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"encoding_iso88591\":3," +
                        "\"encoding_gsm7\":0,\"encoding_ucs2\":2}");
        when(stompHeaders.getDestination()).thenReturn(GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_invalidDestination() {
        String payload = "networkId123";
        when(stompHeaders.getDestination()).thenReturn("INVALID_DESTINATION");
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_updateSP_spSessionMapNull() {
        CustomFrameHandler customFrameHandler2 = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, null, jedisCluster, appProperties, networkIdSystemIdMap, providers, serverHandler);
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler2.handleFrameLogic(stompHeaders, payload));
    }
}