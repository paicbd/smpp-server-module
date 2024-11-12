package com.paicbd.module.server;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.UtilsEnum;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.DataCoding;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompSession;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.paicbd.module.utils.Constants.BINDING;
import static com.paicbd.module.utils.Constants.BOUND;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_SESSIONS;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.STARTED;
import static com.paicbd.module.utils.Constants.STOPPED;
import static com.paicbd.module.utils.Constants.TYPE;
import static com.paicbd.module.utils.Constants.UNBINDING;
import static com.paicbd.module.utils.Constants.WEBSOCKET_STATUS_ENDPOINT;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_SECOND;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionStateListenerImplTest {

    @Mock
    StompSession stompSession;

    @Mock
    JedisCluster jedisCluster;

    @Mock
    GeneralSettings smppGeneralSettings;

    @Mock
    CdrProcessor cdrProcessor;

    @Mock
    AppProperties appProperties;

    @Mock
    Session sessionMock;

    @Mock
    SMPPServerSession serverSession;

    SessionStateListenerImpl sessionStateListener;

    @Test
    @DisplayName("onStateChange when current service provider is BOUND status and new session state is CLOSED")
    void onStateChangeWhenCurrentStateIsBOUNDAndNewStateIsCLOSEDThenDoItSuccessfully() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(BOUND)
                .enquireLinkPeriod(5000)
                .currentBindsCount(1)
                .build();

        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        realSpSession.getCurrentSmppSessions().add(sessionMock);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);

        // before on change state
        assertEquals(1, spSessionMapSpy.size());
        assertEquals(1, spSessionSpy.getCurrentServiceProvider().getCurrentBindsCount());
        assertEquals(1, spSessionSpy.getCurrentSmppSessions().size());
        assertEquals(BOUND, spSessionSpy.getCurrentServiceProvider().getStatus());

        // updating state
        this.sessionStateListener.onStateChange(SessionState.UNBOUND, SessionState.BOUND_TRX, sessionMock);
        this.sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.UNBOUND, sessionMock);

        // after on change state
        ServiceProvider closedServiceProvider = spSessionSpy.getCurrentServiceProvider();
        assertEquals(0, closedServiceProvider.getCurrentBindsCount());
        assertEquals(STARTED, closedServiceProvider.getStatus());
        assertTrue(closedServiceProvider.getBinds().isEmpty());
        assertTrue(spSessionSpy.getCurrentSmppSessions().isEmpty());

        // Redis
        verify(jedisCluster).hset("service_providers", String.valueOf(closedServiceProvider.getNetworkId()), closedServiceProvider.toString());

        // different socket notifications
        String message = String.format("%s,%s,%s,%s", TYPE, closedServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, UNBINDING);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, closedServiceProvider.getNetworkId(), PARAM_UPDATE_SESSIONS, "0");
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, closedServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, STARTED);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);
    }

    @Test
    @DisplayName("onStateChange When the current service provider has more than 1 open binds and new session state is CLOSED and StompSession is null")
    void onStateChangeWhenCurrentServiceProviderHasMoreThanOneOpenBindsAndNewStateIsCLOSEDThenDoItSuccessfully() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>(Arrays.asList("id-1234", "id-5678")))
                .enabled(1)
                .status(BOUND)
                .enquireLinkPeriod(5000)
                .currentBindsCount(2)
                .build();

        Session secondSessionMock = mock(Session.class);
        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        realSpSession.getCurrentSmppSessions().add(sessionMock);
        realSpSession.getCurrentSmppSessions().add(secondSessionMock);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, null, jedisCluster, smppGeneralSettings, cdrProcessor);

        // before on change state
        assertEquals(1, spSessionMapSpy.size());
        assertEquals(2, spSessionSpy.getCurrentServiceProvider().getCurrentBindsCount());
        assertEquals(2, spSessionSpy.getCurrentSmppSessions().size());
        assertEquals(BOUND, spSessionSpy.getCurrentServiceProvider().getStatus());

        // updating state
        when(sessionMock.getSessionId()).thenReturn("id-1234");
        this.sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.BOUND_TRX, sessionMock);

        // after on change state
        ServiceProvider closedServiceProvider = spSessionSpy.getCurrentServiceProvider();
        assertEquals(1, closedServiceProvider.getCurrentBindsCount());
        assertEquals(BOUND, closedServiceProvider.getStatus());
        assertEquals(1, closedServiceProvider.getBinds().size());
        assertEquals(1, spSessionSpy.getCurrentSmppSessions().size());

        // executions
        verify(jedisCluster).hset("service_providers", String.valueOf(closedServiceProvider.getNetworkId()), closedServiceProvider.toString());
        verifyNoMoreInteractions(stompSession);
    }

    @Test
    @DisplayName("onStateChange when opening new binds and sending pending deliverSmEvent")
    void onStateChangeWhenOpenNewConnectionAndSendingPendingDeliverSmThenDoItSuccessfully() throws ResponseTimeoutException, PDUException, IOException, InvalidResponseException, NegativeResponseException {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(STARTED)
                .enquireLinkPeriod(5000)
                .currentBindsCount(0)
                .build();

        MessageEvent deliverSmEvent = getDeliverSmEvent(serviceProviderMock.getNetworkId());

        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, stompSession, jedisCluster, getGeneralSettings(), cdrProcessor);

        // Before the change of state there are no open connections
        assertTrue(spSessionSpy.getCurrentSmppSessions().isEmpty());
        assertEquals(0, spSessionSpy.getCurrentServiceProvider().getCurrentBindsCount());
        assertEquals(STARTED, spSessionSpy.getCurrentServiceProvider().getStatus());

        // updating state and sending pending message
        when(jedisCluster.llen("1_smpp_pending_dlr")).thenReturn(1L);
        when(jedisCluster.lpop("1_smpp_pending_dlr", 1))
                .thenReturn(List.of(deliverSmEvent.toString()));
        when(sessionMock.getSessionId()).thenReturn("id-12345");
        when(spSessionSpy.getNextRoundRobinSession()).thenReturn(serverSession);
        this.sessionStateListener.onStateChange(SessionState.BOUND_RX, SessionState.UNBOUND, sessionMock);
        toSleep();

        // After the change of state there is an open connection
        ServiceProvider boundServiceProvider = spSessionSpy.getCurrentServiceProvider();
        assertEquals(1, boundServiceProvider.getCurrentBindsCount());
        assertEquals(BOUND, boundServiceProvider.getStatus());
        assertFalse(boundServiceProvider.getBinds().isEmpty());
        assertTrue(boundServiceProvider.getBinds().contains("id-12345"));
        assertEquals(1, spSessionSpy.getCurrentSmppSessions().size());

        // verify Redis service provider updated
        verify(jedisCluster).hset("service_providers", String.valueOf(boundServiceProvider.getNetworkId()), boundServiceProvider.toString());
        verify(jedisCluster).llen("1_smpp_pending_dlr");
        verify(jedisCluster).lpop("1_smpp_pending_dlr", 1);

        // verify different socket notifications
        String message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BINDING);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_SESSIONS, "1");
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BOUND);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        // Verifying Delivery Shipment of Queued DeliverSmEvent
        this.verifyDeliverShortMessage(deliverSmEvent);
        verify(cdrProcessor).putCdrDetailOnRedis(any(UtilsRecords.CdrDetail.class));
    }

    @Test
    @DisplayName("onStateChange when opening new connection binds and there are no DeliverySmEvent pending delivery")
    void onStateChangeWhenOpenNewConnectionAndThereAreNoDeliverySmEventThenDoItSuccessfully() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(STARTED)
                .enquireLinkPeriod(5000)
                .currentBindsCount(0)
                .build();

        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, stompSession, jedisCluster, getGeneralSettings(), cdrProcessor);

        // Before the change of state there are no open connections
        assertTrue(spSessionSpy.getCurrentSmppSessions().isEmpty());
        assertEquals(0, spSessionSpy.getCurrentServiceProvider().getCurrentBindsCount());
        assertEquals(STARTED, spSessionSpy.getCurrentServiceProvider().getStatus());

        // updating state and sending pending message
        when(jedisCluster.llen("1_smpp_pending_dlr")).thenReturn(0L);
        when(sessionMock.getSessionId()).thenReturn("id-12345");
        this.sessionStateListener.onStateChange(SessionState.BOUND_RX, SessionState.UNBOUND, sessionMock);

        // After the change of state there is an open connection
        ServiceProvider boundServiceProvider = spSessionSpy.getCurrentServiceProvider();
        assertEquals(1, boundServiceProvider.getCurrentBindsCount());
        assertEquals(BOUND, boundServiceProvider.getStatus());
        assertFalse(boundServiceProvider.getBinds().isEmpty());
        assertTrue(boundServiceProvider.getBinds().contains("id-12345"));
        assertEquals(1, spSessionSpy.getCurrentSmppSessions().size());

        // verify Redis service provider updated
        verify(jedisCluster).hset("service_providers", String.valueOf(boundServiceProvider.getNetworkId()), boundServiceProvider.toString());
        verify(jedisCluster).llen("1_smpp_pending_dlr");
        verify(jedisCluster, never()).lpop(eq("1_smpp_pending_dlr"), anyInt());

        // verify different socket notifications
        String message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BINDING);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_SESSIONS, "1");
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BOUND);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        // Verifying Delivery Shipment of Queued DeliverSmEvent
        verifyNoMoreInteractions(serverSession);
        verifyNoMoreInteractions(cdrProcessor);
    }

    @Test
    @DisplayName("onStateChange when opening new binds and there are deliverSmEvent enqueue and there are not server session ")
    void onStateChangeWhenOpenNewConnectionAndNoServerSessionThenDoItSuccessfully() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(STARTED)
                .enquireLinkPeriod(5000)
                .currentBindsCount(0)
                .build();

        MessageEvent deliverSmEvent = getDeliverSmEvent(serviceProviderMock.getNetworkId());

        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, stompSession, jedisCluster, getGeneralSettings(), cdrProcessor);

        // Before the change of state there are no open connections
        assertTrue(spSessionSpy.getCurrentSmppSessions().isEmpty());
        assertEquals(0, spSessionSpy.getCurrentServiceProvider().getCurrentBindsCount());
        assertEquals(STARTED, spSessionSpy.getCurrentServiceProvider().getStatus());

        // updating state and sending pending message
        when(jedisCluster.llen("1_smpp_pending_dlr")).thenReturn(1L);
        when(jedisCluster.lpop("1_smpp_pending_dlr", 1))
                .thenReturn(List.of(deliverSmEvent.toString()));
        when(sessionMock.getSessionId()).thenReturn("id-12345");
        when(spSessionSpy.getNextRoundRobinSession()).thenReturn(null);
        this.sessionStateListener.onStateChange(SessionState.BOUND_RX, SessionState.UNBOUND, sessionMock);
        toSleep();

        // After the change of state there is an open connection
        ServiceProvider boundServiceProvider = spSessionSpy.getCurrentServiceProvider();
        assertEquals(1, boundServiceProvider.getCurrentBindsCount());
        assertEquals(BOUND, boundServiceProvider.getStatus());
        assertFalse(boundServiceProvider.getBinds().isEmpty());
        assertTrue(boundServiceProvider.getBinds().contains("id-12345"));
        assertEquals(1, spSessionSpy.getCurrentSmppSessions().size());

        // verify Redis service provider updated
        verify(jedisCluster).hset("service_providers", String.valueOf(boundServiceProvider.getNetworkId()), boundServiceProvider.toString());
        verify(jedisCluster).llen("1_smpp_pending_dlr");
        verify(jedisCluster).lpop("1_smpp_pending_dlr", 1);

        // verify different socket notifications
        String message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BINDING);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_SESSIONS, "1");
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BOUND);
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        // Verifying Delivery Shipment of Queued DeliverSmEvent
        verify(spSessionSpy).getNextRoundRobinSession();
        verifyNoMoreInteractions(serverSession);
        verifyNoMoreInteractions(cdrProcessor);
    }

    @Test
    @DisplayName("onStateChange when there are open binds and the new session state is BOUND_RX, then a new bind should be added")
    void onStateChangeWhenBindsAreOpenAndNewStateIsBOUNDRXThenAddNewBinds() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>(List.of("id-1234")))
                .enabled(1)
                .status(BOUND)
                .enquireLinkPeriod(5000)
                .currentBindsCount(1)
                .build();

        Session secondSessionMock = mock(Session.class);
        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        realSpSession.getCurrentSmppSessions().add(secondSessionMock);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);

        // before on change state
        assertEquals(1, spSessionSpy.getCurrentSmppSessions().size());
        assertEquals(1, spSessionSpy.getCurrentServiceProvider().getCurrentBindsCount());
        assertEquals(BOUND, spSessionSpy.getCurrentServiceProvider().getStatus());

        // updating state
        when(sessionMock.getSessionId()).thenReturn("id-5678");
        this.sessionStateListener.onStateChange(SessionState.BOUND_RX, SessionState.BOUND_RX, sessionMock);

        // after on change state
        ServiceProvider boundServiceProvider = spSessionSpy.getCurrentServiceProvider();
        assertEquals(2, boundServiceProvider.getCurrentBindsCount());
        assertEquals(BOUND, boundServiceProvider.getStatus());
        assertEquals(2, boundServiceProvider.getBinds().size());
        assertTrue(boundServiceProvider.getBinds().contains("id-5678"));
        assertEquals(2, spSessionSpy.getCurrentSmppSessions().size());

        // Redis
        verify(jedisCluster).hset("service_providers", String.valueOf(boundServiceProvider.getNetworkId()), boundServiceProvider.toString());

        // different socket notifications
        String message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BINDING);
        verify(stompSession, never()).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_SESSIONS, "2");
        verify(stompSession).send(WEBSOCKET_STATUS_ENDPOINT, message);

        message = String.format("%s,%s,%s,%s", TYPE, boundServiceProvider.getNetworkId(), PARAM_UPDATE_STATUS, BOUND);
        verify(stompSession, never()).send(WEBSOCKET_STATUS_ENDPOINT, message);
    }

    @Test
    @DisplayName("onStateChange when STOPPED the current service provider")
    void onStateChangeWhenSTOPPEDCurrentServiceProviderAndStompSessionIsnullThenDoNothing() {
        ServiceProvider serviceProviderMock = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(0)
                .status(STOPPED)
                .enquireLinkPeriod(5000)
                .currentBindsCount(0)
                .build();

        SpSession realSpSession = new SpSession(jedisCluster, serviceProviderMock, appProperties);
        SpSession spSessionSpy = spy(realSpSession);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        realSpSessionMap.put(serviceProviderMock.getNetworkId(), spSessionSpy);
        ConcurrentMap<Integer, SpSession> spSessionMapSpy = spy(realSpSessionMap);

        sessionStateListener = new SessionStateListenerImpl(1, spSessionMapSpy, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);

        // before on change state
        assertEquals(1, spSessionMapSpy.size());
        assertEquals(STOPPED, spSessionSpy.getCurrentServiceProvider().getStatus());

        // Opening connection from service provider in STOPPED status
        this.sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.CLOSED, sessionMock);

        // after spSession was not changed
        assertEquals(1, spSessionMapSpy.size());
        assertEquals(STOPPED, spSessionSpy.getCurrentServiceProvider().getStatus());

        verifyNoMoreInteractions(stompSession);
        verifyNoMoreInteractions(jedisCluster);
    }

    private void verifyDeliverShortMessage(MessageEvent deliverSmEvent) throws ResponseTimeoutException, PDUException, IOException, InvalidResponseException, NegativeResponseException {
        // verify deliverSmEvent got from Redis vs sent
        ArgumentCaptor<String> sourceAddrCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TypeOfNumber> sourceAddrTonCaptor = ArgumentCaptor.forClass(TypeOfNumber.class);
        ArgumentCaptor<NumberingPlanIndicator> sourceAddrNpiCaptor = ArgumentCaptor.forClass(NumberingPlanIndicator.class);
        ArgumentCaptor<String> destAddrCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TypeOfNumber> destAddrTonCaptor = ArgumentCaptor.forClass(TypeOfNumber.class);
        ArgumentCaptor<NumberingPlanIndicator> destAddrNpiCaptor = ArgumentCaptor.forClass(NumberingPlanIndicator.class);
        ArgumentCaptor<DataCoding> dataCodingCaptor = ArgumentCaptor.forClass(DataCoding.class);
        ArgumentCaptor<byte[]> encodedShortMessageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(this.serverSession).deliverShortMessage(
                eq(""),
                sourceAddrTonCaptor.capture(),
                sourceAddrNpiCaptor.capture(),
                sourceAddrCaptor.capture(),
                destAddrTonCaptor.capture(),
                destAddrNpiCaptor.capture(),
                destAddrCaptor.capture(),
                any(ESMClass.class),
                eq((byte)0),
                eq((byte)0),
                eq(new RegisteredDelivery(0)),
                dataCodingCaptor.capture(),
                encodedShortMessageCaptor.capture(),
                eq((OptionalParameter) null)
        );
        assertEquals(deliverSmEvent.getSourceAddr(), sourceAddrCaptor.getValue());
        assertEquals(UtilsEnum.getTypeOfNumber(deliverSmEvent.getSourceAddrTon()), sourceAddrTonCaptor.getValue());
        assertEquals(deliverSmEvent.getDestinationAddr(), destAddrCaptor.getValue());
        assertEquals(UtilsEnum.getTypeOfNumber(deliverSmEvent.getDestAddrTon()), destAddrTonCaptor.getValue());
        assertEquals(UtilsEnum.getNumberingPlanIndicator(deliverSmEvent.getDestAddrNpi()), destAddrNpiCaptor.getValue());
        assertEquals(UtilsEnum.getNumberingPlanIndicator(deliverSmEvent.getSourceAddrNpi()), sourceAddrNpiCaptor.getValue());
        assertEquals(SmppEncoding.getDataCoding(deliverSmEvent.getDataCoding()), dataCodingCaptor.getValue());
        assertArrayEquals(deliverSmEvent.getDelReceipt().getBytes(), encodedShortMessageCaptor.getValue());
    }

    private GeneralSettings getGeneralSettings() {
        return GeneralSettings.builder()
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
    }

    private MessageEvent getDeliverSmEvent(int destNetworkId) {
        return MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .messageId("1719421854353-11028072268459")
                .systemId("systemId123")
                .commandStatus(0)
                .sequenceNumber(0)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(0)
                .validityPeriod(60)
                .registeredDelivery(1)
                .dataCoding(0)
                .smDefaultMsgId(0)
                .shortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(destNetworkId)
                .routingId(1)
                .isRetry(false)
                .isLastRetry(false)
                .isNetworkNotifyError(false)
                .dueDelay(0)
                .checkSriResponse(false)
                .isDlr(false)
                .sequenceNumber(1)
                .delReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .optionalParameters(null)
                .build();
    }

    private static void toSleep() {
        await().atMost(ONE_SECOND).until(() -> true);
    }
}