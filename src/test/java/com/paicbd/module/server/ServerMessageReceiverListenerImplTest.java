package com.paicbd.module.server;

import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.Constants;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.MessagePart;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SubmitSmResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerMessageReceiverListenerImplTest {
    @Mock
    AtomicInteger requestCounter;

    @Mock
    SpSession spSession;

    @Mock
    GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @Mock
    AppProperties properties;

    @Mock
    CdrProcessor cdrProcessor;

    @Mock
    MultiPartsHandler multiPartsHandler;

    @InjectMocks
    ServerMessageReceiverListenerImpl serverMessageReceiverListener;

    @Mock
    SMPPServerSession smppServerSession;

    @Mock
    JedisCluster jedisCluster;

    @BeforeEach
    void setUp() {
        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(
                requestCounter,
                spSession,
                generalSettingsCacheConfig,
                properties, cdrProcessor,
                new MultiPartsHandler(cdrProcessor, spSession, properties)
        );
    }

    @Test
    @DisplayName("onAcceptSubmitSm when is not multi parts message and there is a active server session and data coding is ASCII")
    void onAcceptSubmitSmWhenNoMultiPartMessageThenDoItSuccessfully() throws ProcessRequestException {
        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding((byte) 0);
        submitSm.setShortMessage("Test Message".getBytes());
        submitSm.setDestAddress("1234567890");
        submitSm.setSourceAddr("1234567890");
        submitSm.setDestAddrTon((byte) 0x01);
        submitSm.setDestAddrNpi((byte) 0x01);
        submitSm.setSourceAddrTon((byte) 0x01);
        submitSm.setSourceAddrNpi((byte) 0x01);
        submitSm.setEsmClass(GSMSpecificFeature.DEFAULT.value());

        ServiceProvider currentSp = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(Constants.BOUND)
                .currentBindsCount(1)
                .enquireLinkPeriod(5000)
                .build();

        SubmitSm submitSmSpy = spy(submitSm);
        AtomicInteger requestCounterMock = new AtomicInteger();
        AtomicInteger requestCounterSpy = spy(requestCounterMock);

        when(spSession.hasAvailableCredit()).thenReturn(true);
        when(spSession.getCurrentServiceProvider()).thenReturn(currentSp);
        when(spSession.getJedisCluster()).thenReturn(jedisCluster);
        when(properties.getPreMessageList()).thenReturn("preMessage");
        when(generalSettingsCacheConfig.getCurrentGeneralSettings()).thenReturn(getGeneralSettings());

        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(
                requestCounterSpy,
                spSession,
                generalSettingsCacheConfig,
                properties, cdrProcessor,
                multiPartsHandler
        );

        SubmitSmResult submitSmResult = serverMessageReceiverListener.onAcceptSubmitSm(submitSmSpy, smppServerSession);
        assertNotNull(submitSmResult.getMessageId());

        verify(requestCounterSpy).incrementAndGet();
        verify(jedisCluster).lpush(eq("preMessage"), anyString());
        verify(cdrProcessor).putCdrDetailOnRedis(any(UtilsRecords.CdrDetail.class));
    }

    @Test
    @DisplayName("onAcceptSubmitSm when is multi part message and data coding is UCS2 with optional parameters")
    void onAcceptSubmitSmWhenMultiPartMessageAndUnicodeDataCodingThenDoItSuccessfully() throws ProcessRequestException {
        List<OptionalParameter> optionalParameters = getOptionalParameters();
        SubmitSm submitSm = new SubmitSm();
        String message = "This is a string with exactly forty chars.";
        byte[] byteArray = new byte[] {
                0x05, 0x00, 0x03, 0x01, 0x02, 0x01,
                0x00, 0x54, 0x00, 0x68, 0x00, 0x69,
                0x00, 0x73, 0x00, 0x20, 0x00, 0x69,
                0x00, 0x73, 0x00, 0x20, 0x00, 0x61,
                0x00, 0x20, 0x00, 0x73, 0x00, 0x74,
                0x00, 0x72, 0x00, 0x69, 0x00, 0x6e,
                0x00, 0x67, 0x00, 0x20, 0x00, 0x77,
                0x00, 0x69, 0x00, 0x74, 0x00, 0x68,
                0x00, 0x20, 0x00, 0x65, 0x00, 0x78,
                0x00, 0x61, 0x00, 0x63, 0x00, 0x74,
                0x00, 0x6c, 0x00, 0x79, 0x00, 0x20,
                0x00, 0x66, 0x00, 0x6f, 0x00, 0x72,
                0x00, 0x74, 0x00, 0x79, 0x00, 0x20,
                0x00, 0x63, 0x00, 0x68, 0x00, 0x61,
                0x00, 0x72, 0x00, 0x73, 0x00, 0x2e
        };
        submitSm.setDataCoding((byte) 8);
        submitSm.setShortMessage(byteArray);
        submitSm.setDestAddress("1234567890");
        submitSm.setSourceAddr("1234567890");
        submitSm.setDestAddrTon((byte) 0x01);
        submitSm.setDestAddrNpi((byte) 0x01);
        submitSm.setSourceAddrTon((byte) 0x01);
        submitSm.setSourceAddrNpi((byte) 0x01);
        submitSm.setEsmClass(GSMSpecificFeature.UDHI.value());
        submitSm.setOptionalParameters(optionalParameters.toArray(new OptionalParameter[0]));

        ServiceProvider currentSp = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(Constants.BOUND)
                .currentBindsCount(1)
                .enquireLinkPeriod(5000)
                .build();

        SubmitSm submitSmSpy = spy(submitSm);
        AtomicInteger requestCounterMock = new AtomicInteger();
        AtomicInteger requestCounterSpy = spy(requestCounterMock);

        when(spSession.hasAvailableCredit()).thenReturn(true);
        when(spSession.getCurrentServiceProvider()).thenReturn(currentSp);
        when(spSession.getJedisCluster()).thenReturn(jedisCluster);
        when(properties.getPreMessageList()).thenReturn("preMessage");
        when(properties.getMessagePartsHash()).thenReturn("smpp_message_parts");
        when(generalSettingsCacheConfig.getCurrentGeneralSettings()).thenReturn(getGeneralSettings());

        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(
                requestCounterSpy,
                spSession,
                generalSettingsCacheConfig,
                properties, cdrProcessor,
                new MultiPartsHandler(cdrProcessor, spSession, properties)
        );

        SubmitSmResult submitSmResult = serverMessageReceiverListener.onAcceptSubmitSm(submitSmSpy, smppServerSession);
        assertNotNull(submitSmResult.getMessageId());

        // capture the first part to get parent id
        ArgumentCaptor<String> submitSmEventCaptured= ArgumentCaptor.forClass(String.class);
        verify(jedisCluster).hset(eq("smpp_message_parts"), eq("testSP1"), submitSmEventCaptured.capture());
        MessageEvent submitSmEventDecoded = Converter.stringToObject(submitSmEventCaptured.getValue(), MessageEvent.class);
        List<MessagePart> messagePart = submitSmEventDecoded.getMessageParts();
        List<UtilsRecords.OptionalParameter> decodeOptionalParameterDecoded = submitSmEventDecoded.getOptionalParameters();
        String reference = "";
        String totalSegment = "";
        String sequence = "";
        for(UtilsRecords.OptionalParameter optionalParameter : decodeOptionalParameterDecoded) {
            if (optionalParameter.tag() == 524) {
                reference = optionalParameter.value();
            } else if (optionalParameter.tag() == 526) {
                totalSegment = optionalParameter.value();
            } else {
                sequence = optionalParameter.value();
            }
        }

        assertEquals(1, messagePart.size());
        assertEquals(3, decodeOptionalParameterDecoded.size());
        assertEquals("1", reference);
        assertEquals("2", totalSegment);
        assertEquals("1", sequence);
        assertTrue(submitSmEventDecoded.getShortMessage().endsWith(message));

        verify(requestCounterSpy).incrementAndGet();
        verify(jedisCluster, never()).lpush(eq("preMessage"), anyString());
        verify(cdrProcessor).putCdrDetailOnRedis(any(UtilsRecords.CdrDetail.class));
    }

    @Test
    @DisplayName("onAcceptSubmitSm when data coding is invalid")
    void onAcceptSubmitSmWhenDataCodingIsInvalidThenProcessRequestExceptionAndDoNothing() {
        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding((byte) 10); // wrong value

        when(smppServerSession.getSessionId()).thenReturn("session-1234");
        assertThrows(ProcessRequestException.class, () -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));

        verify(requestCounter, never()).incrementAndGet();
        verifyNoInteractions(generalSettingsCacheConfig);
        verifyNoInteractions(properties);
        verifyNoInteractions(spSession);
        verifyNoInteractions(jedisCluster);
        verifyNoInteractions(cdrProcessor);
    }

    @Test
    @DisplayName("onAcceptSubmitSm when spSession has not available credit")
    void onAcceptSubmitSmWhenHasNotAvailableCreditThenProcessRequestExceptionAndDoNothing() {
        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding((byte) 0);

        ServiceProvider currentSp = ServiceProvider.builder()
                .networkId(1)
                .systemId("testSP")
                .protocol("SMPP")
                .binds(new ArrayList<>())
                .enabled(1)
                .status(Constants.BOUND)
                .currentBindsCount(1)
                .enquireLinkPeriod(5000)
                .hasAvailableCredit(false)
                .build();
        SpSession spSessionMock = new SpSession(jedisCluster, currentSp, properties);

        when(smppServerSession.getSessionId()).thenReturn("session-1234");
        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(
                requestCounter,
                spSessionMock,
                generalSettingsCacheConfig,
                properties, cdrProcessor,
                multiPartsHandler
        );
        assertThrows(ProcessRequestException.class, () -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));

        verify(requestCounter, never()).incrementAndGet();
        verifyNoInteractions(generalSettingsCacheConfig);
        verifyNoInteractions(properties);
        verifyNoInteractions(jedisCluster);
        verifyNoInteractions(cdrProcessor);
    }

    private static List<OptionalParameter> getOptionalParameters() {
        List<OptionalParameter> optionalParameters = new ArrayList<>();
        OptionalParameter.Sar_msg_ref_num sarMsgRefNum = new OptionalParameter.Sar_msg_ref_num((short) 1); // Reference
        OptionalParameter.Sar_total_segments sarTotalSegments = new OptionalParameter.Sar_total_segments((byte) 2); // Total segments
        OptionalParameter.Sar_segment_seqnum sarSegmentSeqNum = new OptionalParameter.Sar_segment_seqnum((byte) 1); // sequence
        optionalParameters.add(sarMsgRefNum);
        optionalParameters.add(sarTotalSegments);
        optionalParameters.add(sarSegmentSeqNum);
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
}