package com.paicbd.module.config;

import com.paicbd.module.server.SessionStateListenerImpl;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeansDefinitionTest {

    @Mock
    AppProperties appProperties;
    @Mock
    JedisCluster jedisCluster;

    @InjectMocks
    BeansDefinition beansDefinition;

    @Test
    void testSpSessionMap() {
        ConcurrentMap<String, SpSession> spSessionMap =  beansDefinition.spSessionMap();
        assertNotNull(spSessionMap);
        assertTrue(spSessionMap.isEmpty());
    }

    @Test
    void testProviders() {
        Set<ServiceProvider> providers =  beansDefinition.providers();
        assertNotNull(providers);
        assertTrue(providers.isEmpty());
    }

    @Test
    void testNetworkIdSystemIdMap() {
        ConcurrentMap<Integer, String> networkIdSystemIdMap =  beansDefinition.networkIdSystemIdMap();
        assertNotNull(networkIdSystemIdMap);
    }

    @Test
    void testSocketSession() {
        SocketSession socketSession =  beansDefinition.socketSession();
        assertNotNull(socketSession);
    }

    @Test
    void testSessionStateListenerByGateway() {
        ConcurrentMap<String, SessionStateListenerImpl> connectionManagers = beansDefinition.sessionStateListenerBySp();
        assertNotNull(connectionManagers);
        assertTrue(connectionManagers.isEmpty());
    }

    @Test
    void testJedisClusterCreation() {
        when(appProperties.getRedisNodes()).thenReturn(List.of("paicbd:6379", "paicbd:6380"));
        when(appProperties.getRedisMaxTotal()).thenReturn(10);
        when(appProperties.getRedisMinIdle()).thenReturn(1);
        when(appProperties.getRedisMaxIdle()).thenReturn(5);
        when(appProperties.isRedisBlockWhenExhausted()).thenReturn(true);

        Assertions.assertDoesNotThrow(() -> beansDefinition.jedisCluster());
    }

    @Test
    void testCdrProcessorConfigCreation() {
        assertNotNull(beansDefinition.cdrProcessor(jedisCluster));
    }
}