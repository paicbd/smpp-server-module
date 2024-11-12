package com.paicbd.module.server;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.Constants;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.MessagePart;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.MessageIDGeneratorImpl;
import com.paicbd.smsc.utils.UtilsEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class MultiPartsHandler {
    private final MessageIDGenerator messageIDGenerator = new MessageIDGeneratorImpl();
    private final ConcurrentMap<String, MessageEvent> messagesEventQueue = new ConcurrentHashMap<>(); // key:  systemId + msgReferenceNumber
    private final Map<String, AtomicLong> messageQueueCount = new ConcurrentHashMap<>();
    private final CdrProcessor cdrProcessor;
    private final SpSession spSession;
    private final AppProperties appProperties;

    public void processPart(MessageEvent submitSmEvent, Map<String, Object> mapUdh) {
        try {
            MessagePart messagePartEvent = new MessagePart();
            String key = submitSmEvent.getSystemId();
            int totalSegment;
            int segmentSequence;
            int[] udh = (int[]) mapUdh.get(Constants.IEI_CONCATENATED_MESSAGE);
            String msgReferenceNumber = String.valueOf(udh[0]);
            totalSegment = udh[1];
            segmentSequence = udh[2];

            String messagePart = Converter.udhMapToJson(mapUdh);
            String message = mapUdh.get("message").toString();
            messagePartEvent.setMessageId(submitSmEvent.getMessageId());
            messagePartEvent.setUdhJson(messagePart);
            messagePartEvent.setShortMessage(message);
            messagePartEvent.setMsgReferenceNumber(msgReferenceNumber);
            messagePartEvent.setTotalSegment(totalSegment);
            messagePartEvent.setSegmentSequence(segmentSequence);

            key = key + msgReferenceNumber;
            if (!messagesEventQueue.containsKey(key)) {
                MessageId parentMessageId = messageIDGenerator.newMessageId();
                submitSmEvent.setId(parentMessageId.toString());
                submitSmEvent.setMessageId(parentMessageId.toString());
                submitSmEvent.setParentId(parentMessageId.toString());
                messagesEventQueue.put(key, submitSmEvent);
            }

            MessageEvent parentSubmitSmEvent = this.updateMessagesEventQueue(key, messagePartEvent);
            this.messageCounterHandler(key, messagePartEvent.getTotalSegment(), parentSubmitSmEvent);
        } catch (Exception e) {
            log.error("Error to process part message: -> {}", e.getMessage());
        }
    }

    private MessageEvent updateMessagesEventQueue(String key, MessagePart newMessagePart) {
        return messagesEventQueue.computeIfPresent(key, (k, messageEvent) -> {
            if (Objects.isNull(messageEvent.getMessageParts())) {
                messageEvent.setMessageParts(new ArrayList<>());
            }
            messageEvent.getMessageParts().add(newMessagePart);
            return messageEvent;
        });
    }

    private void messageCounterHandler(String key, int totalParts, MessageEvent submitSmEvent) {
        long totalPartsReceived = messageQueueCount.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(1L);
        String cdrComment = String.format("MULTIPART MESSAGE RECEIVED %s OF %s", totalParts, totalPartsReceived);

        if (totalParts == totalPartsReceived) {
            spSession.getJedisCluster().lpush(appProperties.getPreMessageList(), submitSmEvent.toString());
            cdrProcessor.putCdrDetailOnRedis(
                    submitSmEvent.toCdrDetail(UtilsEnum.Module.SMPP_SERVER, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.RECEIVED, cdrComment));
            this.cleaningObjects(key);
            return;
        }

        spSession.getJedisCluster().hset(appProperties.getMessagePartsHash(), key, submitSmEvent.toString());
        cdrProcessor.putCdrDetailOnRedis(
                submitSmEvent.toCdrDetail(UtilsEnum.Module.SMPP_SERVER, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.ENQUEUE, cdrComment));
    }

    private void cleaningObjects(String key) {
        messagesEventQueue.remove(key);
        messageQueueCount.remove(key);
        spSession.getJedisCluster().hdel(appProperties.getMessagePartsHash(), key);
    }
}
