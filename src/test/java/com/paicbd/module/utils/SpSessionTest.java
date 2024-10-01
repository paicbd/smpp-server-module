package com.paicbd.module.utils;

import com.paicbd.smsc.dto.ServiceProvider;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

@ExtendWith(MockitoExtension.class)
class SpSessionTest {

    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties appProperties;
    @Mock
    private ServiceProvider currentServiceProvider;

    @InjectMocks
    private SpSession spSession;

    @Test
    void init() {
        Mockito.when(currentServiceProvider.getHasAvailableCredit()).thenReturn(true);
        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        Assertions.assertDoesNotThrow(() -> spSession.init());
        Assertions.assertEquals(true, spSession.getHasAvailableCredit());
        Assertions.assertEquals(this.appProperties, spSession.getAppProperties());
        Assertions.assertNotNull(spSession.getFactory());
        Assertions.assertEquals(0, spSession.getCurrentIndexRoundRobin());
        Assertions.assertNotNull(spSession.getDeliveryExecService());
    }

    @Test
    void updateRedis() {
        Mockito.when(currentServiceProvider.getSystemId()).thenReturn("smpptest");
        Mockito.when(currentServiceProvider.getHasAvailableCredit()).thenReturn(true);
        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        Assertions.assertDoesNotThrow(() -> spSession.updateRedis());
        Mockito.doReturn("").when(currentServiceProvider).toString();
        Assertions.assertDoesNotThrow(() -> spSession.updateRedis());
    }

    @Test
    void updateCurrentServiceProvider() {
        ServiceProvider sp = new ServiceProvider();
        sp.setEnabled(1);
        sp.setHasAvailableCredit(true);
        spSession = new SpSession(jedisCluster, new ServiceProvider(), appProperties);
        Assertions.assertFalse(spSession.updateCurrentServiceProvider(sp));
    }

    @Test
    void updateCurrentServiceProvider_closeSession() {
        ServiceProvider sp = new ServiceProvider();
        sp.setEnabled(0);
        sp.setHasAvailableCredit(true);
        spSession = new SpSession(jedisCluster, new ServiceProvider(), appProperties);
        Session sessionTest = new SMPPSession();
        spSession.getCurrentSmppSessions().add(sessionTest);
        Assertions.assertTrue(spSession.updateCurrentServiceProvider(sp));
    }

    @Test
    void getNextRoundRobinSession_spSessionEmpty() {
        spSession = new SpSession(jedisCluster, new ServiceProvider(), appProperties);
        Assertions.assertNull(spSession.getNextRoundRobinSession());
    }

    @Test
    void getNextRoundRobinSession() {
        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        Session sessionTest = new SMPPSession();
        spSession.getCurrentSmppSessions().add(sessionTest);
        spSession.getCurrentSmppSessions().add(sessionTest);
        Assertions.assertEquals(sessionTest, spSession.getNextRoundRobinSession());
        spSession.getCurrentSmppSessions().remove(1);
        Assertions.assertEquals(sessionTest, spSession.getNextRoundRobinSession());
    }
}