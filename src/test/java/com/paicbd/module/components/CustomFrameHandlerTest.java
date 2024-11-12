package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.Constants;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import static com.paicbd.module.utils.Constants.GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.RESPONSE_SMPP_SERVER_ENDPOINT;
import static com.paicbd.module.utils.Constants.SERVICE_PROVIDER_DELETED_ENDPOINT;
import static com.paicbd.module.utils.Constants.STOPPED;
import static com.paicbd.module.utils.Constants.TYPE;
import static com.paicbd.module.utils.Constants.UPDATE_SERVER_HANDLER_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT;
import static com.paicbd.module.utils.Constants.WEBSOCKET_STATUS_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFrameHandlerTest {

    @Mock
    SocketSession socketSession;

    @Mock
    GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @Mock
    ConcurrentMap<Integer, SpSession> spSessionMap;

    @Mock
    JedisCluster jedisCluster;

    @Mock
    AppProperties appProperties;

    @Mock
    Set<ServiceProvider> providers;

    @Mock
    ServerHandler serverHandler;

    @InjectMocks
    CustomFrameHandler customFrameHandler;

    @Mock
    StompHeaders stompHeaders;

    @Mock
    StompSession stompSession;

    @BeforeEach
    void setUp() {
        generalSettingsCacheConfig = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        serverHandler = new ServerHandler(jedisCluster, appProperties);
        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMap, jedisCluster, appProperties, providers, serverHandler);
    }

    @Test
    @DisplayName("handleFrameLogic creating service provider")
    void handleFrameLogicWhenCreateServiceProviderThenDoItSuccessfully() {
        int networkId = 1;
        String payload = String.valueOf(networkId);

        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .password("password")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .enquireLinkPeriod(5000)
                .build();

        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);
        Set<ServiceProvider> realProviders = new HashSet<>();
        Set<ServiceProvider> providersSpy = spy(realProviders);

        when(socketSession.getStompSession()).thenReturn(stompSession);
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.jedisCluster.hget("service_providers", payload)).thenReturn(serviceProviderMock.toString());

        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMapSpy, jedisCluster, appProperties, providersSpy, serverHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // compare stored data
        verify(spSessionMapSpy).put(eq(networkId), any(SpSession.class));
        verify(providersSpy).add(any(ServiceProvider.class));

        SpSession spSessionStored = spSessionMapSpy.get(networkId);
        assertEquals(spSessionStored.getCurrentServiceProvider().getNetworkId(), serviceProviderMock.getNetworkId());
        assertEquals(Constants.STOPPED, spSessionStored.getCurrentServiceProvider().getStatus());

        boolean containsExpectedProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == networkId);
        assertTrue(containsExpectedProvider);

        // socket notification parameters
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stompSession, times(2)).send(destinationCaptor.capture(), payloadCaptor.capture());
        assertEquals(WEBSOCKET_STATUS_ENDPOINT, destinationCaptor.getAllValues().get(0));
        assertEquals(String.format("%s,%s,%s,%s", TYPE, payload, PARAM_UPDATE_STATUS, STOPPED), payloadCaptor.getAllValues().get(0));
        assertEquals(RESPONSE_SMPP_SERVER_ENDPOINT, destinationCaptor.getAllValues().get(1));
        assertEquals("OK", payloadCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("handleFrameLogic updating service provider from stopped to started")
    @SuppressWarnings("unchecked")
    void handleFrameLogicWhenUpdateServiceProviderThenDoItSuccessfully() {
        int networkId = 1;
        String payload = String.valueOf(networkId);

        ServiceProvider currentSpMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .status(Constants.STOPPED)
                .enquireLinkPeriod(5000)
                .build();

        Session session = new SMPPSession();
        session.setEnquireLinkTimer(currentSpMock.getEnquireLinkPeriod());

        ServiceProvider updatedSpMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("smppSP")
                .protocol("SMPP")
                .systemType("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(Constants.STARTED)
                .enquireLinkPeriod(15000)
                .build();

        SpSession spSessionData = new SpSession(this.jedisCluster, currentSpMock, this.appProperties);
        spSessionData.getCurrentSmppSessions().add(session);

        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);
        Set<ServiceProvider> realProviders = new HashSet<>();
        Set<ServiceProvider> providersSpy = spy(realProviders);

        when(socketSession.getStompSession()).thenReturn(stompSession);
        when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.jedisCluster.hget("service_providers", payload)).thenReturn(updatedSpMock.toString());
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        when(spSessionMapSpy.get(Integer.valueOf(payload))).thenReturn(spSessionData);

        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMapSpy, jedisCluster, appProperties, providersSpy, serverHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // spSession map updated
        verify(spSessionMapSpy).get(networkId);
        verify(spSessionMapSpy, never()).put(eq(networkId), any(SpSession.class));
        SpSession spSessionStored = spSessionMapSpy.get(networkId);
        assertEquals(Constants.STARTED, spSessionStored.getCurrentServiceProvider().getStatus());
        assertEquals(updatedSpMock.getEnquireLinkPeriod(), spSessionStored.getCurrentServiceProvider().getEnquireLinkPeriod());
        assertEquals(updatedSpMock.getSystemId(), spSessionStored.getCurrentServiceProvider().getSystemId());
        assertEquals(updatedSpMock.getSystemType(), spSessionStored.getCurrentServiceProvider().getSystemType());

        // testing set removeIf function
        ArgumentCaptor<Predicate<ServiceProvider>> captor = ArgumentCaptor.forClass(Predicate.class);
        verify(providersSpy).removeIf(captor.capture());
        Predicate<ServiceProvider> predicate = captor.getValue();
        assertTrue(predicate.test(currentSpMock)); // deleted and added again

        // set providers updated
        verify(providersSpy).add(any(ServiceProvider.class));
        ServiceProvider updatedSp = providersSpy.stream()
                .filter(provider -> provider.getNetworkId() == networkId)
                .findFirst().orElse(null);
        assertNotNull(updatedSp);
        assertEquals(Constants.STARTED, updatedSp.getStatus());

        // socket notification parameters
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @Test
    @DisplayName("handleFrameLogic updating service provider when does not exists in Redis")
    void handleFrameLogicWhenUpdateServiceProviderAndDoesNotExistsThenDoNothing() {
        int networkId = 1;
        String payload = String.valueOf(networkId);

        when(socketSession.getStompSession()).thenReturn(stompSession);
        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.jedisCluster.hget("service_providers", payload)).thenReturn(null);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // verify updating service provider
        verifyNoMoreInteractions(spSessionMap);
        verifyNoMoreInteractions(providers);
        verifyNoMoreInteractions(jedisCluster);

        // socket notification parameters
        verify(stompSession, never()).send(eq(WEBSOCKET_STATUS_ENDPOINT), anyString());
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @Test
    @DisplayName("handleFrameLogic updating service provider with Redis has a invalid json string")
    void handleFrameLogicWhenUpdateServiceProviderAndRedisHasInvalidJsonThenThrowIllegalArgumentException() {
        String payload = "1";

        when(stompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.jedisCluster.hget(this.appProperties.getServiceProvidersHashName(), payload)).thenReturn("2:45}");
        assertThrows(IllegalArgumentException.class, () -> customFrameHandler.handleFrameLogic(stompHeaders, payload));

        // updating service provider
        verifyNoMoreInteractions(socketSession);
        verifyNoMoreInteractions(spSessionMap);
        verifyNoMoreInteractions(providers);

        // socket notification parameters
        verifyNoMoreInteractions(stompSession);
    }

    @Test
    @DisplayName("handleFrameLogic deleting service providers")
    @SuppressWarnings("unchecked")
    void handleFrameLogicWhenDeleteServiceProviderThenDoItSuccessfully() {
        int networkId = 10;
        String payload = String.valueOf(networkId);
        ServiceProvider currentSp = ServiceProvider.builder()
                .networkId(10)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(Constants.STARTED)
                .enquireLinkPeriod(5000)
                .build();

        Session session = new SMPPSession();
        SpSession spSessionData = new SpSession(this.jedisCluster, currentSp, this.appProperties);
        spSessionData.getCurrentSmppSessions().add(session);

        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);
        spSessionMapSpy.put(networkId, spSessionData);
        Set<ServiceProvider> realProviders = new HashSet<>();
        Set<ServiceProvider> providersSpy = spy(realProviders);
        providersSpy.add(currentSp);

        when(this.socketSession.getStompSession()).thenReturn(stompSession);
        when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.stompHeaders.getDestination()).thenReturn(SERVICE_PROVIDER_DELETED_ENDPOINT);

        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMapSpy, jedisCluster, appProperties, providersSpy, serverHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // count element after remove
        assertEquals(0, spSessionMapSpy.size());
        assertEquals(0, providersSpy.size());

        // processing spSession and providers list
        verify(spSessionMapSpy).get(10);
        verify(spSessionMapSpy).remove(10);
        verify(providersSpy).removeIf(any(Predicate.class));

        // redis
        verify(jedisCluster).hdel("service_providers", payload);

        // socket notification parameters
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @Test
    @DisplayName("handleFrameLogic deleting service providers when networkId does not exists")
    @SuppressWarnings("unchecked")
    void handleFrameLogicWhenDeleteServiceProviderAndNetworkIdNotExistsThenDoNothing() {
        int networkId = 2;
        String payload = String.valueOf(networkId);
        ServiceProvider currentSp = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .status(Constants.STOPPED)
                .enquireLinkPeriod(5000)
                .build();

        Session session = new SMPPSession();
        session.setEnquireLinkTimer(currentSp.getEnquireLinkPeriod());
        SpSession spSessionData = new SpSession(this.jedisCluster, currentSp, this.appProperties);
        spSessionData.getCurrentSmppSessions().add(session);

        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);
        spSessionMapSpy.put(currentSp.getNetworkId(), spSessionData);
        Set<ServiceProvider> realProviders = new HashSet<>();
        Set<ServiceProvider> providersSpy = spy(realProviders);
        providersSpy.add(currentSp);

        when(this.socketSession.getStompSession()).thenReturn(stompSession);
        when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(this.stompHeaders.getDestination()).thenReturn(SERVICE_PROVIDER_DELETED_ENDPOINT);

        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMapSpy, jedisCluster, appProperties, providersSpy, serverHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // count init and count after remove was not changed
        assertEquals(1, spSessionMapSpy.size());
        assertEquals(1, providersSpy.size());

        // processing spSession and providers list
        verify(spSessionMapSpy).get(networkId);
        verify(spSessionMapSpy, never()).remove(networkId);
        verify(providersSpy).removeIf(any(Predicate.class));

        // socket notification parameters
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @ParameterizedTest
    @ValueSource(strings = {UPDATE_SERVICE_PROVIDER_ENDPOINT, SERVICE_PROVIDER_DELETED_ENDPOINT})
    @DisplayName("handleFrameLogic when networkId is not number")
    void handleFrameLogicWhenInvalidNetworkIdThenIllegalArgumentException(String destination) {
        String payload = "test";
        when(this.stompHeaders.getDestination()).thenReturn(destination);
        assertThrows(IllegalArgumentException.class, () -> customFrameHandler.handleFrameLogic(stompHeaders, payload));

        // verify not updating or deleting service provider
        verifyNoMoreInteractions(spSessionMap);
        verifyNoMoreInteractions(socketSession);
        verifyNoMoreInteractions(providers);
        verifyNoMoreInteractions(jedisCluster);
        verifyNoMoreInteractions(stompSession);
    }

    @Test
    @DisplayName("handleFrameLogic updating server handler")
    void handleFrameLogicWhenUpdateServerHandlerThenDoItSuccessfully() {
        String payload = "STOPPED";
        when(this.socketSession.getStompSession()).thenReturn(stompSession);
        when(this.stompHeaders.getDestination()).thenReturn(UPDATE_SERVER_HANDLER_ENDPOINT);
        when(this.appProperties.getServerKey()).thenReturn("smpp_server");
        when(this.appProperties.getConfigurationHash()).thenReturn("configurations");
        when(this.jedisCluster.hget("configurations", "smpp_server")).thenReturn("{\"state\":\"STOPPED\"}");

        ServerHandler realServerHandler = new ServerHandler(jedisCluster, appProperties);
        realServerHandler.manageServerHandler();
        // before updating
        assertNotNull(realServerHandler.getState());
        assertEquals("STOPPED", realServerHandler.getState());

        // updating
        when(this.jedisCluster.hget("configurations", "smpp_server")).thenReturn("{\"state\":\"STARTED\"}");
        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMap, jedisCluster, appProperties, providers, realServerHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);
        assertEquals("STARTED", realServerHandler.getState());

        // verify
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @Test
    @DisplayName("handleFrameLogic updating server handler and does not exists in Redis")
    void handleFrameLogicWhenUpdateServerHandlerAndDoesNotExistsThenDoNothing() {
        String payload = "STOPPED";
        when(this.socketSession.getStompSession()).thenReturn(stompSession);
        when(this.stompHeaders.getDestination()).thenReturn(UPDATE_SERVER_HANDLER_ENDPOINT);
        when(this.appProperties.getServerKey()).thenReturn("smpp_server");
        when(this.appProperties.getConfigurationHash()).thenReturn("configurations");

        when(this.jedisCluster.hget("configurations", "smpp_server")).thenReturn("{\"state\":\"STOPPED\"}");
        ServerHandler realServerHandler = new ServerHandler(jedisCluster, appProperties);
        realServerHandler.manageServerHandler();
        // before updating
        assertNotNull(realServerHandler.getState());
        assertEquals("STOPPED", realServerHandler.getState());

        // updating
        when(this.jedisCluster.hget("configurations", "smpp_server")).thenReturn(null);
        customFrameHandler = new CustomFrameHandler(socketSession, generalSettingsCacheConfig, spSessionMap, jedisCluster, appProperties, providers, realServerHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // after updating state was not changed
        assertEquals("STOPPED", realServerHandler.getState());

        // verify
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }
    
    @Test
    @DisplayName("handleFrameLogic updating general settings")
    void handleFrameLogicWhenUpdateGeneralSettingsThenDoItSuccessfully() {
        String payload = "updated";
        GeneralSettings currentGeneralSettingsMock = GeneralSettings.builder()
                .id(1)
                .validityPeriod(60)
                .maxValidityPeriod(240)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .destAddrTon(1)
                .destAddrNpi(1)
                .encodingIso88591(3)
                .encodingGsm7(0)
                .encodingUcs2(2)
                .build();

        GeneralSettings updatedGeneralSettings = GeneralSettings.builder()
                .id(1)
                .validityPeriod(120)
                .maxValidityPeriod(300)
                .sourceAddrTon(3)
                .sourceAddrNpi(5)
                .destAddrTon(1)
                .destAddrNpi(1)
                .encodingIso88591(3)
                .encodingGsm7(1)
                .encodingUcs2(2)
                .build();

        when(socketSession.getStompSession()).thenReturn(stompSession);
        when(this.appProperties.getSmppGeneralSettingsHash()).thenReturn("general_settings");
        when(this.appProperties.getSmppGeneralSettingsKey()).thenReturn("smpp_http");
        when(stompHeaders.getDestination()).thenReturn(GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT);

        when(this.jedisCluster.hget("general_settings", "smpp_http"))
                .thenReturn(currentGeneralSettingsMock.toString());
        GeneralSettingsCacheConfig realGeneralSettings = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        realGeneralSettings.initializeGeneralSettings();

        // before update
        assertNotEquals(updatedGeneralSettings.toString(), realGeneralSettings.getCurrentGeneralSettings().toString());

        // updating
        when(this.jedisCluster.hget("general_settings", "smpp_http"))
                .thenReturn(updatedGeneralSettings.toString());
        customFrameHandler = new CustomFrameHandler(socketSession, realGeneralSettings, spSessionMap, jedisCluster, appProperties, providers, serverHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);
        assertEquals(updatedGeneralSettings.toString(), realGeneralSettings.getCurrentGeneralSettings().toString());

        // socket notification parameters
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @Test
    @DisplayName("handleFrameLogic updating general settings when not exists in Redis")
    void handleFrameLogicWhenUpdateGeneralSettingsAndNotExistsThenDoNothing() {
        String payload = "updated";
        GeneralSettings currentGeneralSettingsMock = GeneralSettings.builder()
                .id(1)
                .validityPeriod(60)
                .maxValidityPeriod(240)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .destAddrTon(1)
                .destAddrNpi(1)
                .encodingIso88591(3)
                .encodingGsm7(0)
                .encodingUcs2(2)
                .build();

        when(socketSession.getStompSession()).thenReturn(stompSession);
        when(this.appProperties.getSmppGeneralSettingsHash()).thenReturn("general_settings");
        when(this.appProperties.getSmppGeneralSettingsKey()).thenReturn("smpp_http");
        when(stompHeaders.getDestination()).thenReturn(GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT);
        when(this.jedisCluster.hget("general_settings", "smpp_http"))
                .thenReturn(currentGeneralSettingsMock.toString());

        GeneralSettingsCacheConfig realGeneralSettings = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        realGeneralSettings.initializeGeneralSettings();

        // before update is not null
        assertNotNull(realGeneralSettings.getCurrentGeneralSettings());

        // updating
        when(this.jedisCluster.hget("general_settings", "smpp_http"))
                .thenReturn(null);
        customFrameHandler = new CustomFrameHandler(socketSession, realGeneralSettings, spSessionMap, jedisCluster, appProperties, providers, serverHandler);
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // after updating general settings was not changed
        assertEquals(currentGeneralSettingsMock.toString(), realGeneralSettings.getCurrentGeneralSettings().toString());

        // socket notification parameters
        verify(stompSession).send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    @Test
    @DisplayName("handleFrameLogic getting request with destination invalid")
    void handleFrameLogicWhenGettingInvalidDestinationThenDoNothing() {
        String payload = "1";
        when(stompHeaders.getDestination()).thenReturn("INVALID_DESTINATION");
        customFrameHandler.handleFrameLogic(stompHeaders, payload);

        // verify deleting execution
        verifyNoMoreInteractions(spSessionMap);
        verifyNoMoreInteractions(socketSession);
        verifyNoMoreInteractions(providers);
        verifyNoMoreInteractions(jedisCluster);
        verifyNoMoreInteractions(stompSession);
    }
}