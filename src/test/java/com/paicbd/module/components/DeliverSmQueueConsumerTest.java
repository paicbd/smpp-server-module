package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.UtilsEnum;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.DataCoding;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.SMPPServerSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_SECOND;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverSmQueueConsumerTest {
    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private CdrProcessor cdrProcessor;

    @Mock
    private AppProperties appProperties;

    @Mock
    private ConcurrentMap<Integer, SpSession> spSessionMap;

    @Mock
    private GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @InjectMocks
    private DeliverSmQueueConsumer deliverSmQueueConsumer;

    @Mock
    private SpSession spSessionMock;

    @Mock
    private SMPPServerSession serverSession;

    @Test
    @DisplayName("Testing complete flow of sending DeliverSm to service provider")
    void startSchedulerWhenExecuteCompleteFlowThenDoItSuccessfully() throws ResponseTimeoutException, PDUException, IOException, InvalidResponseException, NegativeResponseException {
        int destNetworkId = 1;
        MessageEvent deliverSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .deliverSmId("52b3afdb-565f-456c-8dff-97233d3afa88")
                .deliverSmServerId("1719421854353-11028072268459")
                .systemId("systemId123")
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
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
                .destNetworkId(destNetworkId)
                .routingId(1)
                .isDlr(true)
                .delReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .optionalParameters(List.of(new UtilsRecords.OptionalParameter((short) 30, "52b3afdb-565f-456c-8dff-97233d3afa88")))
                .build();


        GeneralSettings generalSettingsMock = GeneralSettings.builder()
                .id(1)
                .validityPeriod(60)
                .maxValidityPeriod(240)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .destAddrTon(1)
                .destAddrNpi(1)
                .encodingIso88591(SmppEncoding.ISO88591)
                .encodingGsm7(SmppEncoding.GSM7)
                .encodingUcs2(SmppEncoding.UCS2)
                .build();

        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.spSessionMock.getNextRoundRobinSession()).thenReturn(serverSession);
        when(this.spSessionMap.get(1)).thenReturn(spSessionMock);
        when(this.jedisCluster.lpop("smpp_dlr", 1)).thenReturn(List.of(deliverSmEvent.toString()));
        when(this.generalSettingsCacheConfig.getCurrentGeneralSettings()).thenReturn(generalSettingsMock);

        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, generalSettingsCacheConfig );
        this.deliverSmQueueConsumer.startScheduler();
        toSleep();

        verify(this.spSessionMap).get(1);
        verify(this.generalSettingsCacheConfig).getCurrentGeneralSettings();
        verify(this.cdrProcessor).putCdrDetailOnRedis(any(UtilsRecords.CdrDetail.class));
        verify(this.cdrProcessor).createCdr(deliverSmEvent.getMessageId());

        // verify deliverSmEvent got from Redis vs sent
        ArgumentCaptor<String> sourceAddrCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TypeOfNumber> sourceAddrTonCaptor = ArgumentCaptor.forClass(TypeOfNumber.class);
        ArgumentCaptor<NumberingPlanIndicator> sourceAddrNpiCaptor = ArgumentCaptor.forClass(NumberingPlanIndicator.class);
        ArgumentCaptor<String> destAddrCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TypeOfNumber> destAddrTonCaptor = ArgumentCaptor.forClass(TypeOfNumber.class);
        ArgumentCaptor<NumberingPlanIndicator> destAddrNpiCaptor = ArgumentCaptor.forClass(NumberingPlanIndicator.class);
        ArgumentCaptor<DataCoding> dataCodingCaptor = ArgumentCaptor.forClass(DataCoding.class);
        ArgumentCaptor<byte[]> encodedShortMessageCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<OptionalParameter> optionalParameterArgumentCaptor = ArgumentCaptor.forClass(OptionalParameter.class);
        ArgumentCaptor<ESMClass> esmClassArgumentCaptor = ArgumentCaptor.forClass(ESMClass.class);

        verify(this.serverSession).deliverShortMessage(
                eq(""),
                sourceAddrTonCaptor.capture(),
                sourceAddrNpiCaptor.capture(),
                sourceAddrCaptor.capture(),
                destAddrTonCaptor.capture(),
                destAddrNpiCaptor.capture(),
                destAddrCaptor.capture(),
                esmClassArgumentCaptor.capture(),
                eq((byte)0),
                eq((byte)0),
                eq(new RegisteredDelivery(0)),
                dataCodingCaptor.capture(),
                encodedShortMessageCaptor.capture(),
                optionalParameterArgumentCaptor.capture()
        );

        assertEquals(deliverSmEvent.getSourceAddr(), sourceAddrCaptor.getValue());
        assertEquals(UtilsEnum.getTypeOfNumber(deliverSmEvent.getSourceAddrTon()), sourceAddrTonCaptor.getValue());
        assertEquals(deliverSmEvent.getDestinationAddr(), destAddrCaptor.getValue());
        assertEquals(UtilsEnum.getTypeOfNumber(deliverSmEvent.getDestAddrTon()), destAddrTonCaptor.getValue());
        assertEquals(UtilsEnum.getNumberingPlanIndicator(deliverSmEvent.getDestAddrNpi()), destAddrNpiCaptor.getValue());
        assertEquals(UtilsEnum.getNumberingPlanIndicator(deliverSmEvent.getSourceAddrNpi()), sourceAddrNpiCaptor.getValue());
        assertEquals(SmppEncoding.getDataCoding(deliverSmEvent.getDataCoding()), dataCodingCaptor.getValue());
        assertArrayEquals(deliverSmEvent.getDelReceipt().getBytes(), encodedShortMessageCaptor.getValue());
        assertEquals(30, optionalParameterArgumentCaptor.getValue().tag);
        assertTrue(MessageType.SMSC_DEL_RECEIPT.containedIn(esmClassArgumentCaptor.getValue()));
    }

    @Test
    @DisplayName("Testing the DeliverSm send flow when there is no active server session")
    void startSchedulerWhenNoActiveServerSessionThenNotSendDeliverSm() {
        int destNetworkId = 1;
        MessageEvent deliverSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .deliverSmId("52b3afdb-565f-456c-8dff-97233d3afa88")
                .deliverSmServerId("1719421854353-11028072268459")
                .systemId("systemId123")
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(5)
                .validityPeriod(60)
                .registeredDelivery(0)
                .dataCoding(0)
                .shortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(destNetworkId)
                .routingId(1)
                .isDlr(true)
                .delReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .build();

        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.jedisCluster.lpop("smpp_dlr", 1)).thenReturn(List.of(deliverSmEvent.toString()));
        when(this.spSessionMock.getNextRoundRobinSession()).thenReturn(null);
        when(this.spSessionMap.get(destNetworkId)).thenReturn(spSessionMock);

        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, generalSettingsCacheConfig );
        this.deliverSmQueueConsumer.startScheduler();
        toSleep();

        // Redis SMS in pending queue
        verify(this.jedisCluster).lpush("1_smpp_pending_dlr", deliverSmEvent.toString());

        // verify execution
        verify(this.spSessionMap).get(1);
        verify(this.generalSettingsCacheConfig, never()).getCurrentGeneralSettings();
        verifyNoMoreInteractions(this.cdrProcessor);
        verifyNoMoreInteractions(this.serverSession);
    }

    @Test
    @DisplayName("Testing the DeliverSm send flow when there is no SpSession registered")
    void startSchedulerWhenNoSpSessionRegisteredThenNotFindServerSession() {
        int destNetworkId = 1;
        MessageEvent deliverSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .deliverSmId("52b3afdb-565f-456c-8dff-97233d3afa88")
                .deliverSmServerId("1719421854353-11028072268459")
                .systemId("systemId123")
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(5)
                .validityPeriod(60)
                .registeredDelivery(0)
                .dataCoding(0)
                .shortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(destNetworkId)
                .routingId(1)
                .isDlr(true)
                .delReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .build();

        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.jedisCluster.lpop("smpp_dlr", 1)).thenReturn(List.of(deliverSmEvent.toString()));
        when(this.spSessionMap.get(destNetworkId)).thenReturn(null);

        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, generalSettingsCacheConfig );
        this.deliverSmQueueConsumer.startScheduler();
        toSleep();

        // Redis SMS in pending queue
        verify(this.jedisCluster).lpush("1_smpp_pending_dlr", deliverSmEvent.toString());

        // never executed
        verifyNoMoreInteractions(this.spSessionMock);
        verifyNoMoreInteractions(this.generalSettingsCacheConfig);
        verifyNoMoreInteractions(this.cdrProcessor);
        verifyNoMoreInteractions(this.serverSession);
    }

    @Test
    @DisplayName("Testing the DeliverSm send flow when Redis queue is empty")
    void startSchedulerWhenRedisQueueIsEmptyThenDoNothing() {
        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.jedisCluster.lpop("smpp_dlr", 1)).thenReturn(null);

        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, null, generalSettingsCacheConfig );
        this.deliverSmQueueConsumer.startScheduler();
        toSleep();

        //Redis
        verify(this.jedisCluster).lpop("smpp_dlr", 1);

        // never executed
        verifyNoMoreInteractions(this.spSessionMap);
        verifyNoMoreInteractions(this.jedisCluster);
        verifyNoMoreInteractions(this.spSessionMock);
        verifyNoMoreInteractions(this.generalSettingsCacheConfig);
        verifyNoMoreInteractions(this.cdrProcessor);
        verifyNoMoreInteractions(this.serverSession);
    }

    @Test
    @DisplayName("Testing the DeliverSm send flow when Redis has a deliverSmEvent with invalid json string")
    void startSchedulerWhenProcessDeliverSmAndDeliverSmEventInvalidFormatThenNotTryToSend() {
        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.jedisCluster.lpop("smpp_dlr", 1)).thenReturn(List.of("incorrect:json}"));

        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, generalSettingsCacheConfig );
        this.deliverSmQueueConsumer.startScheduler();
        toSleep();

        //Redis
        verify(this.jedisCluster).lpop("smpp_dlr", 1);

        // never executed
        verifyNoMoreInteractions(this.spSessionMap);
        verifyNoMoreInteractions(this.jedisCluster);
        verifyNoMoreInteractions(this.spSessionMock);
        verifyNoMoreInteractions(this.generalSettingsCacheConfig);
        verifyNoMoreInteractions(this.cdrProcessor);
        verifyNoMoreInteractions(this.serverSession);
    }

    @Test
    @DisplayName("Testing the DeliverSm send flow when getting exception to get active server session")
    void startSchedulerWhenProcessDeliverSmAndGettingExceptionThenNotTryToSend() {
        int destNetworkId = 1;
        MessageEvent deliverSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .deliverSmId("52b3afdb-565f-456c-8dff-97233d3afa88")
                .deliverSmServerId("1719421854353-11028072268459")
                .systemId("systemId123")
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(5)
                .validityPeriod(60)
                .registeredDelivery(0)
                .dataCoding(0)
                .shortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(destNetworkId)
                .routingId(1)
                .isDlr(true)
                .delReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message")
                .build();

        when(this.appProperties.getDeliverSmWorkers()).thenReturn(1);
        when(this.appProperties.getDeliverSmBatchSizePerWorker()).thenReturn(1);
        when(this.appProperties.getDeliverSmQueue()).thenReturn("smpp_dlr");
        when(this.jedisCluster.lpop("smpp_dlr", 1)).thenReturn(List.of(deliverSmEvent.toString()));
        when(this.spSessionMap.get(destNetworkId)).thenReturn(spSessionMock);
        when(this.spSessionMock.getNextRoundRobinSession()).thenThrow(new RuntimeException("exception to get getNextRoundRobinSession"));

        this.deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, generalSettingsCacheConfig );
        this.deliverSmQueueConsumer.startScheduler();
        toSleep();

        // Redis SMS in pending queue
        verifyNoMoreInteractions(this.jedisCluster);
        verifyNoMoreInteractions(this.generalSettingsCacheConfig);
        verifyNoMoreInteractions(this.cdrProcessor);
        verifyNoMoreInteractions(this.serverSession);
    }

    private static void toSleep() {
        await().atMost(ONE_SECOND).until(() -> true);
    }

}