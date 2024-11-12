package com.paicbd.module.server;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.MessagePart;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiPartsHandlerTest {
    @Mock
    private CdrProcessor cdrProcessor;

    @Mock
    private SpSession spSession;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private MultiPartsHandler multiPartsHandler;

    @Mock
    private JedisCluster jedisCluster;

    @Test
    @DisplayName("processPart with multi parts message")
    void processPartWhenMessageHasTwoPartsThenDoItSuccessfully() {
        when(appProperties.getPreMessageList()).thenReturn("preMessage");
        when(appProperties.getMessagePartsHash()).thenReturn("smpp_message_parts");
        when(spSession.getJedisCluster()).thenReturn(jedisCluster);

        String msgReferenceNumber = "1";
        List<MessagePart> messagePartList = new ArrayList<>();
        String key = "smppSp" + msgReferenceNumber;

        // first part
        String firstPart = "Hello I hope you are doing well I wanted to remind you that our meeting is tomorrow at three in the afternoon Please remember to bring the documents";

        Map<String, Object> firstMapUdh = new HashMap<>();
        firstMapUdh.put("message", firstPart);
        int[] udhFirstPageArray = {Integer.parseInt(msgReferenceNumber), 2, 1};
        firstMapUdh.put("0x00", udhFirstPageArray);

        List<UtilsRecords.OptionalParameter> firstOptionalParameters = new ArrayList<>();
        firstOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 524, msgReferenceNumber)); // Sar message reference number
        firstOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 526, "2")); // Sar total segments
        firstOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 527, "1")); // Sar segment sequence number

        MessageEvent firstSubmitSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .messageId("1719421854353-11028072268459")
                .systemId("smppSp")
                .commandStatus(0)
                .sequenceNumber(0)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(64)
                .validityPeriod(60)
                .registeredDelivery(1)
                .dataCoding(0)
                .smDefaultMsgId(0)
                .shortMessage(firstPart)
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(1)
                .routingId(1)
                .isRetry(false)
                .isLastRetry(false)
                .isNetworkNotifyError(false)
                .dueDelay(0)
                .checkSriResponse(false)
                .isDlr(false)
                .sequenceNumber(1)
                .optionalParameters(firstOptionalParameters)
                .build();

        messagePartList.add(this.createMessagePart(firstMapUdh, firstSubmitSmEvent.getMessageId(),  1));
        multiPartsHandler.processPart(firstSubmitSmEvent, firstMapUdh);

        // second part
        String secondPart = "If you have any questions or need to change the time let me know I am here to help you Looking forward to seeing you soon take care and have a great day";

        Map<String, Object> secondMapUdh = new HashMap<>();
        secondMapUdh.put("message", secondPart);
        int[] udhSecondPageArray = {Integer.parseInt(msgReferenceNumber), 2, 2};
        secondMapUdh.put("0x00", udhSecondPageArray);

        List<UtilsRecords.OptionalParameter> secondOptionalParameters = new ArrayList<>();
        secondOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 524, msgReferenceNumber)); // Sar message reference number
        secondOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 526, "2")); // Sar total segments
        secondOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 527, "2")); // Sar segment sequence number

        MessageEvent secondSubmitSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072000000")
                .messageId("1719421854353-11028072000000")
                .systemId("smppSp")
                .commandStatus(0)
                .sequenceNumber(0)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(64)
                .validityPeriod(60)
                .registeredDelivery(1)
                .dataCoding(0)
                .smDefaultMsgId(0)
                .shortMessage(secondPart)
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(1)
                .routingId(1)
                .isRetry(false)
                .isLastRetry(false)
                .isNetworkNotifyError(false)
                .dueDelay(0)
                .checkSriResponse(false)
                .isDlr(false)
                .sequenceNumber(1)
                .optionalParameters(secondOptionalParameters)
                .build();

        messagePartList.add(this.createMessagePart(secondMapUdh, secondSubmitSmEvent.getMessageId(), 2));
        multiPartsHandler.processPart(secondSubmitSmEvent, secondMapUdh);

        // capture the first part to get parent id
        ArgumentCaptor<String> firstSubmitSmEventCaptured= ArgumentCaptor.forClass(String.class);
        verify(jedisCluster).hset(eq("smpp_message_parts"), eq(key), firstSubmitSmEventCaptured.capture());

        // submit event result
        MessageEvent submitSmCaptured = Converter.stringToObject(firstSubmitSmEventCaptured.getValue(), MessageEvent.class);
        String messageParentId = submitSmCaptured.getParentId();
        MessageEvent multipartSubmitSmEventResult = MessageEvent.builder()
                .id(messageParentId)
                .messageId(messageParentId)
                .parentId(messageParentId)
                .systemId("smppSp")
                .commandStatus(0)
                .sequenceNumber(0)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(64)
                .validityPeriod(60)
                .registeredDelivery(1)
                .dataCoding(0)
                .smDefaultMsgId(0)
                .shortMessage(firstPart)
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(1)
                .routingId(1)
                .isRetry(false)
                .isLastRetry(false)
                .isNetworkNotifyError(false)
                .dueDelay(0)
                .checkSriResponse(false)
                .isDlr(false)
                .sequenceNumber(1)
                .optionalParameters(firstOptionalParameters)
                .messageParts(messagePartList)
                .build();

        // verify submitSmEvent sent to preMessage List
        verify(jedisCluster).lpush("preMessage", multipartSubmitSmEventResult.toString());

        verify(jedisCluster).hdel("smpp_message_parts", key);
        verify(cdrProcessor, times(2)).putCdrDetailOnRedis(any(UtilsRecords.CdrDetail.class));
    }

    @Test
    @DisplayName("processPart when map UDH is null")
    void processPartWhenMapUDHIsNullThenDoNothing() {
        String msgReferenceNumber = "1";

        // first part
        String firstPart = "Hello I hope you are doing well I wanted to remind you that our meeting is tomorrow at three in the afternoon Please remember to bring the documents";
        List<UtilsRecords.OptionalParameter> firstOptionalParameters = new ArrayList<>();
        firstOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 524, msgReferenceNumber)); //  Sar message reference number
        firstOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 526, "2")); // Sar total segments
        firstOptionalParameters.add(new UtilsRecords.OptionalParameter((short) 527, "1")); // Sar segment sequence number

        MessageEvent firstSubmitSmEvent = MessageEvent.builder()
                .id("1719421854353-11028072268459")
                .messageId("1719421854353-11028072268459")
                .systemId("smppSp")
                .commandStatus(0)
                .sequenceNumber(0)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .sourceAddr("50510201020")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .esmClass(64)
                .validityPeriod(60)
                .registeredDelivery(1)
                .dataCoding(0)
                .smDefaultMsgId(0)
                .shortMessage(firstPart)
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .originNetworkId(2)
                .destNetworkType("GW")
                .destProtocol("SMPP")
                .destNetworkId(1)
                .routingId(1)
                .isRetry(false)
                .isLastRetry(false)
                .isNetworkNotifyError(false)
                .dueDelay(0)
                .checkSriResponse(false)
                .isDlr(false)
                .sequenceNumber(1)
                .optionalParameters(firstOptionalParameters)
                .build();

        multiPartsHandler.processPart(firstSubmitSmEvent, null);

        verifyNoInteractions(appProperties);
        verifyNoInteractions(spSession);
        verifyNoInteractions(jedisCluster);
        verifyNoInteractions(cdrProcessor);
    }

    private MessagePart createMessagePart(Map<String, Object> mapUdh, String messageId, int segmentSequence) {
        MessagePart messagePartEvent = new MessagePart();

        String messagePart = Converter.udhMapToJson(mapUdh);
        String message = mapUdh.get("message").toString();
        messagePartEvent.setMessageId(messageId);
        messagePartEvent.setUdhJson(messagePart);
        messagePartEvent.setShortMessage(message);
        messagePartEvent.setMsgReferenceNumber("1");
        messagePartEvent.setTotalSegment(2);
        messagePartEvent.setSegmentSequence(segmentSequence);

        return messagePartEvent;
    }
}