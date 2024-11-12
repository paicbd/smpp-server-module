package com.paicbd.module.utils;

import com.paicbd.smsc.dto.ServiceProvider;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpSessionTest {

    @Mock
    JedisCluster jedisCluster;

    @Mock
    AppProperties appProperties;

    @Mock
    ServiceProvider currentServiceProvider;

    @Mock
    Session mockSession;

    @Mock
    List<Session> currentSmppSessions = new ArrayList<>();

    @InjectMocks
    SpSession spSession;

    @BeforeEach
    public void setUp() throws Exception {
        setUpSessions();
    }

    private void setUpSessions() throws Exception {
        Field field = spSession.getClass().getDeclaredField("currentSmppSessions");
        field.setAccessible(true);
        field.set(spSession, new ArrayList<>(Collections.singletonList(mockSession)));
    }


    @Test
    @DisplayName("init when current service provider has available credit then available credit are set")
    void initWhenCurrentServiceProviderHasAvailableCreditThenHasAvailableCreditIsSet() {
        currentServiceProvider = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(10)
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(true)
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .enabled(1)
                .build();
        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        spSession.init();
        assertEquals(currentServiceProvider.getHasAvailableCredit(), spSession.hasAvailableCredit());
    }

    @Test
    @DisplayName("updateRedis when executed and data is not empty then the update is executed")
    void updateRedisWhenDataIsNotEmptyThenExecuteSet() {
        currentServiceProvider = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(10)
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(true)
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .enabled(1)
                .build();

        when(appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        spSession.updateRedis();
        verify(jedisCluster).hset(appProperties.getServiceProvidersHashName(),
                String.valueOf(currentServiceProvider.getNetworkId()), currentServiceProvider.toString());
    }

    @Test
    @DisplayName("updateCurrentServiceProvider when the method is called then the service provider is updated alongside the redis new data")
    void updateCurrentServiceProviderWhenIsEnabledThenUpdateRedis() {
        currentServiceProvider = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(5)
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(true)
                .enquireLinkPeriod(0)
                .pduTimeout(0)
                .enabled(0)
                .status(Constants.STOPPED)
                .build();

        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);

        ServiceProvider serviceProviderUpdates = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(10)
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(false)
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .enabled(1)
                .build();

        currentSmppSessions.add(mockSession);
        SpSession spSessionSpy = spy(spSession);
        spSessionSpy.updateCurrentServiceProvider(serviceProviderUpdates);
        assertFalse(spSession.updateCurrentServiceProvider(serviceProviderUpdates));
        verify(spSessionSpy).updateRedis();
        assertEquals(serviceProviderUpdates.toString(), spSession.getCurrentServiceProvider().toString());
    }


    @Test
    @DisplayName("updateCurrentServiceProvider when executed and not enabled then update and unbind/close the session")
    void updateCurrentServiceProviderWhenIsNotEnabledThenUnbindAndClose() throws Exception {
        currentServiceProvider = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(5)
                .binds(new ArrayList<>())
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(true)
                .enquireLinkPeriod(0)
                .pduTimeout(0)
                .enabled(1)
                .status(Constants.STARTED)
                .build();

        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        setUpSessions();

        ServiceProvider serviceProviderUpdates = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(10)
                .binds(new ArrayList<>())
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(false)
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .enabled(0)
                .status(Constants.STOPPED)
                .build();

        currentSmppSessions.add(mockSession);
        SpSession spSessionSpy = spy(spSession);
        spSessionSpy.updateCurrentServiceProvider(serviceProviderUpdates);
        verify(spSessionSpy).updateRedis();
        verify(mockSession).unbindAndClose();
        assertEquals(serviceProviderUpdates.toString(), spSession.getCurrentServiceProvider().toString());
    }

    @Test
    @DisplayName("getNextRoundRobinSession when executed and no session then return null")
    void getNextRoundRobinSessionWhenNoSessionThenReturnNull() {
        currentServiceProvider = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(10)
                .binds(new ArrayList<>())
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(false)
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .enabled(0)
                .status(Constants.STOPPED)
                .build();
        spSession = new SpSession(jedisCluster, currentServiceProvider , appProperties);
        assertNull(spSession.getNextRoundRobinSession());
    }

    @Test
    @DisplayName("getNextRoundRobinSession when executed and service provider has session the return next object")
    void getNextRoundRobinSessionWhenNoSessionThenGetNext() {
        currentServiceProvider = ServiceProvider.builder()
                .networkId(10)
                .systemId("smpp_test")
                .password("password")
                .systemType("smpp")
                .interfaceVersion("IF_50")
                .maxBinds(10)
                .binds(new ArrayList<>())
                .currentBindsCount(0)
                .addressTon(1)
                .addressNpi(1)
                .protocol("SMPP")
                .hasAvailableCredit(false)
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .enabled(0)
                .status(Constants.STOPPED)
                .build();
        spSession = new SpSession(jedisCluster, currentServiceProvider, appProperties);
        Session sessionTest = new SMPPSession();
        spSession.getCurrentSmppSessions().add(sessionTest);
        spSession.getCurrentSmppSessions().add(sessionTest);
        assertEquals(sessionTest, spSession.getNextRoundRobinSession());
        spSession.getCurrentSmppSessions().remove(1);
        assertEquals(sessionTest, spSession.getNextRoundRobinSession());
    }
}
