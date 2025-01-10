package com.paicbd.module;

import com.paicbd.module.components.DeliverSmQueueConsumer;
import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.components.ServerHandler;
import com.paicbd.module.e2e.SmppClientMock;
import com.paicbd.module.server.SmppServer;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SubmitSmResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmppServerModuleApplicationTest {
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
    GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @InjectMocks
    private SmppServer smppServer;

    @InjectMocks
    private DeliverSmQueueConsumer deliverSmQueueConsumer;

    SmppClientMock smppClient;
    SMPPSession smppSession;
    ConcurrentMap<Integer, SpSession> spSessionMapSpy;
    ServiceProvider okServiceProvider;
    ServiceProvider serviceProviderWithoutCredit;
    ServiceProvider stoppedServiceProvider;
    ServiceProvider invalidBindTypeServiceProvider;

    ExecutorService executor;
    List<ServiceProvider> serviceProviders = new ArrayList<>();
    String host = "127.0.0.1";
    int port = 7777;
    int counterSubmit = 0;

    @BeforeEach
    void setUp() throws InterruptedException {
        executor = Executors.newSingleThreadExecutor();
        counterSubmit = 0;

        setDifferentServiceProvider();

        serviceProviders.add(okServiceProvider);
        serviceProviders.add(serviceProviderWithoutCredit);
        serviceProviders.add(stoppedServiceProvider);
        serviceProviders.add(invalidBindTypeServiceProvider);

        startSmppServer(host, port);
        toSleep(1);
        smppClient = new SmppClientMock(host, port);
        smppSession = smppClient.createAndBindSmppSession(okServiceProvider);
        assertTrue(smppSession.getSessionState().isBound());
    }

    @AfterEach
    void tearDown() {
        smppSession.unbindAndClose();
        executor.shutdownNow();
    }

    @Test
    void startSmppServerAndOpenConnectionWhenSendSubmitAndReceiveDeliverSmThenDoItSuccessfully() {
        messageEventProvider().forEach(messageEvent -> {
            sendSubmitSmWithAndWithoutOptionalParameterReturnsExpectedDeliverSm(messageEvent);
            toSleep(2);
        });

        openingConnectionWhenInvalidPasswordThenSpSessionIsNull();
        openingConnectionWhenMaxBindExceededThenSpSessionIsNull();
        openingConnectionWhenServiceProviderDoesNotExistThenSpSessionIsNull();
        openingConnectionWhenServiceProviderIsStatusStoppedThenSpSessionIsNull();
        openingConnectionWhenServiceProviderHasNotCreditThenSubmitIsInvalid();
        openingConnectionWithBindTypeInvalidThenSpSessionIsNull();

        // waiting to close
        toSleep(2);
    }

    private void startSmppServer(String host, int port) throws InterruptedException {
        CountDownLatch serverReadyLatch = new CountDownLatch(1);
        ConcurrentMap<Integer, SpSession> realSpSessionMap = new ConcurrentHashMap<>();
        Set<ServiceProvider> realProviders = new HashSet<>();
        Map<String, String> serviceProviderMapMock = new HashMap<>();

        for (ServiceProvider serviceProvider : serviceProviders) {
            SpSession realSpSession = new SpSession(jedisCluster, serviceProvider, appProperties);
            realSpSession.updateCurrentServiceProvider(serviceProvider);
            SpSession realSpSessionSpy = spy(realSpSession);
            realProviders.add(serviceProvider);
            realSpSessionMap.put(serviceProvider.getNetworkId(), realSpSessionSpy);
            serviceProviderMapMock.put(String.valueOf(serviceProvider.getNetworkId()), serviceProvider.toString());
        }

        spSessionMapSpy = spy(realSpSessionMap);

        when(serverHandler.getState()).thenReturn("STARTED");
        when(appProperties.getSmppServerProcessorDegree()).thenReturn(15);
        when(appProperties.getSmppServerIp()).thenReturn(host);
        when(appProperties.getSmppServerPort()).thenReturn(port);
        when(appProperties.getSmppServerProcessorDegree()).thenReturn(15);
        when(appProperties.getSmppServerQueueCapacity()).thenReturn(1000);
        when(appProperties.getServiceProvidersHashName()).thenReturn("service_providers");
        when(appProperties.getSmppServerTransactionTimer()).thenReturn(5000);
        when(appProperties.getSmppServerWaitForBind()).thenReturn(5000);
        when(socketSession.getStompSession()).thenReturn(null);
        when(generalSettingsCacheConfig.getCurrentGeneralSettings()).thenReturn(getGeneralSettings());
        when(jedisCluster.hgetAll("service_providers")).thenReturn(serviceProviderMapMock);

        smppServer = new SmppServer(jedisCluster, cdrProcessor, socketSession, serverHandler, appProperties, realProviders, spSessionMapSpy, generalSettingsCacheConfig);
        executor.submit(() -> {
            smppServer.init();
            serverReadyLatch.countDown();
        });

        if (!serverReadyLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout server SMPP was not started");
        }

        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.generalSettingsCacheConfig.getCurrentGeneralSettings()).thenReturn(getGeneralSettings());
        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMapSpy, generalSettingsCacheConfig);
    }

    // Sending single submitSm and get deliverSm response
    private void sendSubmitSmWithAndWithoutOptionalParameterReturnsExpectedDeliverSm(MessageEvent submitSmEvent) {
        // sending message
        SubmitSmResult submitSmResult = smppClient.sendSubmit(submitSmEvent, smppSession);
        assertNotNull(submitSmResult.getMessageId());

        // waiting for drl
        toSleep(2);

        // sending deliver sm
        when(this.jedisCluster.lpop(eq("smpp_dlr"), anyInt())).thenReturn(List.of(getDeliverSM(submitSmResult.getMessageId()).toString()));
        this.deliverSmQueueConsumer.startScheduler();
    }

    private void openingConnectionWhenInvalidPasswordThenSpSessionIsNull() {
        ServiceProvider okServiceProviderClone = ServiceProvider.builder()
                .networkId(1)
                .systemId("smppSP")
                .password("invalidPassword")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STARTED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSCEIVER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(true)
                .maxBinds(1)
                .build();

        SMPPSession newMockSmppSession = smppClient.createAndBindSmppSession(okServiceProviderClone);
        assertNull(newMockSmppSession);
    }

    private void openingConnectionWhenMaxBindExceededThenSpSessionIsNull() {
        SMPPSession newMockSmppSession = smppClient.createAndBindSmppSession(okServiceProvider);
        assertNull(newMockSmppSession);
    }

    private void openingConnectionWhenServiceProviderDoesNotExistThenSpSessionIsNull() {
        ServiceProvider invalidServiceprovider = ServiceProvider.builder()
                .networkId(2)
                .systemId("invalidServiceProvider")
                .password("1234")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STARTED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSCEIVER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(true)
                .maxBinds(1)
                .build();

        SMPPSession newMockSmppSession = smppClient.createAndBindSmppSession(invalidServiceprovider);
        assertNull(newMockSmppSession);
    }

    private void openingConnectionWhenServiceProviderIsStatusStoppedThenSpSessionIsNull() {
        SMPPSession newMockSmppSession = smppClient.createAndBindSmppSession(stoppedServiceProvider);
        assertNull(newMockSmppSession);
    }

    private void openingConnectionWhenServiceProviderHasNotCreditThenSubmitIsInvalid() {
        SMPPSession newMockSmppSession = smppClient.createAndBindSmppSession(serviceProviderWithoutCredit);
        assertNotNull(newMockSmppSession);

        // sending message
        SubmitSmResult submitSmResult = smppClient.sendSubmit(getSingleSubmitSmEvent(), newMockSmppSession);
        assertNull(submitSmResult);
    }

    // invalidBindTypeServiceProviderClone in smppServer session is bind type TRANSMITTER
    private void openingConnectionWithBindTypeInvalidThenSpSessionIsNull() {
        ServiceProvider invalidBindTypeServiceProviderClone = ServiceProvider.builder()
                .networkId(4)
                .systemId("invalidBindSP")
                .password("1234")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STARTED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSMITTER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(true)
                .maxBinds(1)
                .build();

        SMPPSession newMockSmppSession = smppClient.createAndBindSmppSession(invalidBindTypeServiceProviderClone);
        assertNull(newMockSmppSession);
    }

    private MessageEvent getSingleSubmitSmEvent() {
        return MessageEvent.builder()
                .systemId(serviceProviderWithoutCredit.getSystemId())
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("1234567890")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("1234567890")
                .esmClass(0)
                .validityPeriod(0)
                .registeredDelivery(0)
                .dataCoding(0)
                .shortMessage("Origin is a service provider without credit")
                .isDlr(false)
                .build();
    }

    private Stream<MessageEvent> messageEventProvider() {
        return Stream.of(
                MessageEvent.builder()
                        .systemId("testSP")
                        .sourceAddrTon(1)
                        .sourceAddrNpi(1)
                        .sourceAddr("1234567890")
                        .destAddrTon(1)
                        .destAddrNpi(1)
                        .destinationAddr("1234567890")
                        .esmClass(0)
                        .validityPeriod(0)
                        .registeredDelivery(0)
                        .dataCoding(0)
                        .shortMessage("This is a single submitSm test with data coding GSM7")
                        .isDlr(false)
                        .build(),
                MessageEvent.builder()
                        .systemId("testSP")
                        .sourceAddrTon(1)
                        .sourceAddrNpi(1)
                        .sourceAddr("1234567890")
                        .destAddrTon(1)
                        .destAddrNpi(1)
                        .destinationAddr("1234567890")
                        .esmClass(0)
                        .validityPeriod(0)
                        .registeredDelivery(0)
                        .dataCoding(3)
                        .shortMessage("This is a single submitSm test with data coding ISO88591")
                        .isDlr(false)
                        .build(),
                MessageEvent.builder()
                        .systemId("testSP")
                        .sourceAddrTon(1)
                        .sourceAddrNpi(1)
                        .sourceAddr("1234567890")
                        .destAddrTon(1)
                        .destAddrNpi(1)
                        .destinationAddr("1234567890")
                        .esmClass(0)
                        .validityPeriod(0)
                        .registeredDelivery(0)
                        .dataCoding(8) // 8 is unicode data coding
                        .shortMessage("This is a multipart test with optionalParameters and data coding unicode - part 1")
                        .isDlr(false)
                        .msgReferenceNumber("1")
                        .segmentSequence(1)
                        .totalSegment(2)
                        .optionalParameters(getOptionalParameters("1"))
                        .build(),
                MessageEvent.builder()
                        .systemId("testSP")
                        .sourceAddrTon(1)
                        .sourceAddrNpi(1)
                        .sourceAddr("1234567890")
                        .destAddrTon(1)
                        .destAddrNpi(1)
                        .destinationAddr("1234567890")
                        .esmClass(0)
                        .validityPeriod(0)
                        .registeredDelivery(0)
                        .dataCoding(8) // 8 is unicode data coding
                        .shortMessage("This is a multipart test with optionalParameters and data coding unicode - part 2")
                        .isDlr(false)
                        .msgReferenceNumber("1")
                        .segmentSequence(2)
                        .totalSegment(2)
                        .optionalParameters(getOptionalParameters("2"))
                        .build(),
                MessageEvent.builder()
                        .systemId("testSP")
                        .sourceAddrTon(1)
                        .sourceAddrNpi(1)
                        .sourceAddr("1234567890")
                        .destAddrTon(1)
                        .destAddrNpi(1)
                        .destinationAddr("1234567890")
                        .esmClass(64) // 64 is UDHI
                        .validityPeriod(0)
                        .registeredDelivery(0)
                        .dataCoding(0) // 8 is unicode data coding
                        .shortMessage("This is a multipart test with data coding unicode and UDHI - part 1")
                        .isDlr(false)
                        .msgReferenceNumber("1")
                        .segmentSequence(1)
                        .totalSegment(2)
                        .build(),
                MessageEvent.builder()
                        .systemId("testSP")
                        .sourceAddrTon(1)
                        .sourceAddrNpi(1)
                        .sourceAddr("1234567890")
                        .destAddrTon(1)
                        .destAddrNpi(1)
                        .destinationAddr("1234567890")
                        .esmClass(64) // 64 is UDHI
                        .validityPeriod(0)
                        .registeredDelivery(0)
                        .dataCoding(0) // 8 is unicode data coding
                        .shortMessage("This is a multipart test with data coding unicode and UDHI - part 2")
                        .isDlr(false)
                        .msgReferenceNumber("1")
                        .segmentSequence(2)
                        .totalSegment(2)
                        .build()
        );
    }

    private MessageEvent getDeliverSM(String messageId) {
        return MessageEvent.builder()
                .id(messageId)
                .deliverSmId("52b3afdb-565f-456c-8dff-97233d3afa88")
                .deliverSmServerId(messageId)
                .systemId("systemId123")
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50558499393")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50511112222")
                .esmClass(5)
                .validityPeriod(60)
                .registeredDelivery(0)
                .dataCoding(0)
                .smDefaultMsgId(0)
                .shortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(1)
                .routingId(1)
                .isDlr(true)
                .delReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .optionalParameters(List.of(new UtilsRecords.OptionalParameter((short) 30, "52b3afdb-565f-456c-8dff-97233d3afa88")))
                .build();
    }

    private static List<UtilsRecords.OptionalParameter> getOptionalParameters(String sequencePart) {
        List<UtilsRecords.OptionalParameter> optionalParameters = new ArrayList<>();
        UtilsRecords.OptionalParameter referenceNumber = new UtilsRecords.OptionalParameter((short) 524, "1");
        UtilsRecords.OptionalParameter totalSegment = new UtilsRecords.OptionalParameter((short) 526, "2");
        UtilsRecords.OptionalParameter sequence = new UtilsRecords.OptionalParameter((short) 527, sequencePart);

        optionalParameters.add(referenceNumber);
        optionalParameters.add(totalSegment);
        optionalParameters.add(sequence);

        return optionalParameters;
    }

    private static GeneralSettings getGeneralSettings() {
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

    private void setDifferentServiceProvider() {
        okServiceProvider = ServiceProvider.builder()
                .networkId(1)
                .systemId("smppSP")
                .password("1234")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STARTED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSCEIVER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(true)
                .maxBinds(1)
                .build();

        serviceProviderWithoutCredit = ServiceProvider.builder()
                .networkId(2)
                .systemId("hasNotCreditSP")
                .password("1234")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STARTED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSCEIVER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(false)
                .maxBinds(1)
                .build();

        stoppedServiceProvider = ServiceProvider.builder()
                .networkId(3)
                .systemId("stoppedSmppSP")
                .password("1234")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STOPPED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSCEIVER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(true)
                .maxBinds(1)
                .build();

        invalidBindTypeServiceProvider = ServiceProvider.builder()
                .networkId(4)
                .systemId("invalidBindSP")
                .password("1234")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status("STARTED")
                .enquireLinkPeriod(5000)
                .pduTimeout(5000)
                .bindType("TRANSCEIVER")
                .systemType("SMPP")
                .addressNpi(1)
                .addressTon(1)
                .addressRange("")
                .interfaceVersion("IF_33")
                .currentBindsCount(0)
                .hasAvailableCredit(true)
                .maxBinds(1)
                .build();
    }

    private static void toSleep(int seconds) {
        await().atMost(seconds, TimeUnit.SECONDS).until(() -> true);
    }
}