package com.paicbd.module.server;

import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.components.ServerHandler;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@ExtendWith(MockitoExtension.class)
class SmppServerTest {
    @Mock(strictness = Mock.Strictness.LENIENT) // In handleFrameLogic_invalidDestination test case, IllegalArgumentException is thrown by the method
    private SocketSession socketSession;
    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties appProperties;
    @Mock
    private CdrProcessor cdrProcessor;
    @Mock
    private Set<ServiceProvider> providers;
    @Mock
    private ConcurrentMap<String, SpSession> spSessionMap;
    @Mock
    private ConcurrentMap<Integer, String> networkIdSystemIdMap;
    @InjectMocks
    GeneralSettingsCacheConfig generalSettingsCacheConfig;
    @Mock
    private ServerHandler serverHandler;

    @Mock
    private SmppServer smppServerMock;
    @Mock
    private SpSession spSessionMock;
    @Mock
    private Session sessionMock;

    @BeforeEach
    void setUp() {
        ServiceProvider sp = new ServiceProvider();
        sp.setNetworkId(1);
        sp.setCurrentBindsCount(1);
        sp.setSystemId("systemId123");
        spSessionMock = new SpSession(this.jedisCluster, sp, this.appProperties);
        spSessionMock.getCurrentSmppSessions().add(sessionMock);
        spSessionMap.put("systemId123", spSessionMock);
        smppServerMock = new SmppServer(jedisCluster, cdrProcessor, socketSession, serverHandler, appProperties, providers, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig);
    }

    @Test
    void init() {
        Assertions.assertDoesNotThrow(() -> smppServerMock.init());
    }

    @Test
    void loadServiceProviders() {
        ServiceProvider sp = new ServiceProvider();
        sp.setSystemId("systemId1");
        sp.setNetworkId(1);
        sp.setCurrentBindsCount(1);
        sp.setProtocol("HTTP");

        ServiceProvider sp2 = new ServiceProvider();
        sp2.setNetworkId(1);
        sp2.setSystemId("systemId2");
        sp2.setCurrentBindsCount(1);
        sp.setProtocol("SMPP");

        Map<String, String> spAll = new HashMap<>();
        spAll.put("systemId1", sp.toString());
        spAll.put("systemId2", sp2.toString());
        Mockito.when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        Mockito.when(this.jedisCluster.hgetAll(appProperties.getServiceProvidersHashName())).thenReturn(spAll);

        Assertions.assertDoesNotThrow(() -> smppServerMock.loadServiceProviders());
    }

    @Test
    void loadServiceProviders_throwException() {
        Map<String, String> spAll = new HashMap<>();
        spAll.put("systemId1", "{");
        Mockito.when(this.appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        Mockito.when(this.jedisCluster.hgetAll(appProperties.getServiceProvidersHashName())).thenReturn(spAll);

        Assertions.assertDoesNotThrow(() -> smppServerMock.loadServiceProviders());
    }
}