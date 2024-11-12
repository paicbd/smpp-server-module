package com.paicbd.module.server;

import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.components.ServerHandler;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmppServerTest {
    @Mock
    JedisCluster jedisCluster;

    @Mock
    CdrProcessor cdrProcessor;

    @Mock
    SocketSession socketSession;

    @Mock
    ServerHandler serverHandler;

    @Mock
    AppProperties appProperties;

    @Mock
    ConcurrentMap<Integer, SpSession> spSessionMap;

    @Mock
    GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @InjectMocks
    private SmppServer smppServerMock;

    @Test
    @DisplayName("Initializing providers list when service_provider list in Redis is not empty")
    void initProviderListWhenServiceProviderIsNotEmptyThenDoItSuccessfully() {
        ServiceProvider firstServiceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("smppSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .enquireLinkPeriod(5000)
                .build();

        ServiceProvider secondServiceProviderMock = ServiceProvider.builder()
                .networkId(2)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .enquireLinkPeriod(5000)
                .build();

        Map<String, String> serviceProviderMapMock = new HashMap<>();
        serviceProviderMapMock.put("1", firstServiceProviderMock.toString());
        serviceProviderMapMock.put("2", secondServiceProviderMock.toString());

        Set<ServiceProvider> realProviders = new HashSet<>();
        realProviders.add(firstServiceProviderMock);
        Set<ServiceProvider> providersSpy = spy(realProviders);

        when(appProperties.getSmppServerProcessorDegree()).thenReturn(15);
        when(appProperties.getSmppServerQueueCapacity()).thenReturn(1000);
        when(appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(jedisCluster.hgetAll("service_providers")).thenReturn(serviceProviderMapMock);

        smppServerMock = new SmppServer(jedisCluster, cdrProcessor, socketSession, serverHandler, appProperties, providersSpy, spSessionMap, generalSettingsCacheConfig);
        smppServerMock.init();
        verify(providersSpy).addAll(anyCollection());

        assertEquals(2, providersSpy.size());
        boolean containsFirsProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == firstServiceProviderMock.getNetworkId());
        assertTrue(containsFirsProvider);
        boolean containsSecondProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == secondServiceProviderMock.getNetworkId());
        assertTrue(containsSecondProvider);
    }

    @Test
    @DisplayName("testing to simulate a error when reading service_provider list")
    void loadServiceProvidersWhenUnexpectedErrorOccursThenServiceProviderWithErrorIsIgnored() {
        ServiceProvider firstServiceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("smppSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .enquireLinkPeriod(5000)
                .build();

        ServiceProvider secondServiceProviderMock = ServiceProvider.builder()
                .networkId(2)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .enquireLinkPeriod(5000)
                .build();

        Map<String, String> serviceProviderMapMock = new HashMap<>();
        serviceProviderMapMock.put("1", firstServiceProviderMock.toString());
        serviceProviderMapMock.put("3", "{");
        serviceProviderMapMock.put("2", secondServiceProviderMock.toString());

        Set<ServiceProvider> realProviders = new HashSet<>();
        Set<ServiceProvider> providersSpy = spy(realProviders);

        when(appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(jedisCluster.hgetAll("service_providers")).thenReturn(serviceProviderMapMock);

        smppServerMock = new SmppServer(jedisCluster, cdrProcessor, socketSession, serverHandler, appProperties, providersSpy, spSessionMap, generalSettingsCacheConfig);
        smppServerMock.loadServiceProviders();

        verify(providersSpy).addAll(anyCollection());
        assertEquals(2, providersSpy.size());

        // verify that service provider with networkId = 3 was not included
        boolean containsWrongNetworkIdProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == 3);
        assertFalse(containsWrongNetworkIdProvider);

        // other if exists
        boolean containsFirstProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == firstServiceProviderMock.getNetworkId());
        assertTrue(containsFirstProvider);

        boolean containsSecondProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == secondServiceProviderMock.getNetworkId());
        assertTrue(containsSecondProvider);
    }

    @Test
    @DisplayName("testing load service provider when protocol is not SMPP")
    void loadServiceProvidersWhenProtocolIsNotSMPPThenServiceProviderIsIgnored() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("HTTP")
                .binds(new ArrayList<>())
                .enabled(0)
                .enquireLinkPeriod(5000)
                .build();

        Map<String, String> serviceProviderMapMock = new HashMap<>();
        serviceProviderMapMock.put("1", serviceProviderMock.toString());

        Set<ServiceProvider> realProviders = new HashSet<>();
        Set<ServiceProvider> providersSpy = spy(realProviders);

        when(appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(jedisCluster.hgetAll("service_providers")).thenReturn(serviceProviderMapMock);

        smppServerMock = new SmppServer(jedisCluster, cdrProcessor, socketSession, serverHandler, appProperties, providersSpy, spSessionMap, generalSettingsCacheConfig);
        smppServerMock.loadServiceProviders();

        verify(providersSpy, never()).addAll(anySet());
        assertEquals(0, providersSpy.size());

        // verify that service provider with HTTP protocol was not included
        boolean containsProvider = providersSpy.stream()
                .anyMatch(provider -> provider.getNetworkId() == serviceProviderMock.getNetworkId());
        assertFalse(containsProvider);
    }
}