package com.paicbd.module.server;

import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.Constants;
import com.paicbd.module.utils.SpSession;
import com.paicbd.module.utils.StaticMethods;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.Generated;
import com.paicbd.smsc.utils.MessageIDGeneratorImpl;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.SmppUtils;
import com.paicbd.smsc.utils.UtilsEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.BroadcastSm;
import org.jsmpp.bean.CancelBroadcastSm;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.QueryBroadcastSm;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.BroadcastSmResult;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QueryBroadcastSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.session.SubmitMultiResult;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ServerMessageReceiverListenerImpl implements ServerMessageReceiverListener {
    private final MessageIDGenerator messageIDGenerator = new MessageIDGeneratorImpl();
    private final AtomicInteger requestCounter;
    private final SpSession spSession;
    private final GeneralSettingsCacheConfig generalSettingsCacheConfig;
    private final AppProperties properties;
    private final CdrProcessor cdrProcessor;
    private final MultiPartsHandler multiPartsHandler;

    private final Map<Short, Integer> tagMultiPartMessageToIndexMap = Map.of(
            (short) 524, 0,
            (short) 526, 1,
            (short) 527, 2
    );

    private Map<String, Object> udhMap;

    @Override
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession smppServerSession) throws ProcessRequestException {
        byte dataCoding = submitSm.getDataCoding();

        if (!StaticMethods.isValidDataCoding(dataCoding)) {
            log.info("Invalid data coding {} for session: {}", dataCoding, smppServerSession.getSessionId());
            throw new ProcessRequestException("Invalid data coding", SMPPConstant.STAT_ESME_RINVDCS);
        }

        if (Boolean.FALSE.equals(spSession.hasAvailableCredit())) {
            log.info("The credits has been exhausted for session: {}", smppServerSession.getSessionId());
            throw new ProcessRequestException("Throttling error", SMPPConstant.STAT_ESME_RTHROTTLED);
        }

        MessageId messageId = messageIDGenerator.newMessageId();
        addInQ(submitSm, messageId);
        requestCounter.incrementAndGet();
        return new SubmitSmResult(messageId, new OptionalParameter[0]);
    }

    private void addInQ(SubmitSm submitSm, MessageId messageId) {
        var isGSMSpecificFeatureDefault = GSMSpecificFeature.DEFAULT.containedIn(submitSm.getEsmClass());
        ServiceProvider currentServiceProvider = spSession.getCurrentServiceProvider();
        GeneralSettings smppGeneralSettings = generalSettingsCacheConfig.getCurrentGeneralSettings();
        int encodingType = SmppUtils.determineEncodingType(submitSm.getDataCoding(), smppGeneralSettings);
        MessageEvent submitSmEvent = createSubmitSmEvent(submitSm, messageId, currentServiceProvider, encodingType);
        submitSmEvent.setOriginNetworkType("SP");
        submitSmEvent.setOriginProtocol("SMPP");
        submitSmEvent.setUdhi((isGSMSpecificFeatureDefault) ? "0" : "1");

        log.debug("Adding SubmitSm {} to {} queue.", submitSmEvent, properties.getPreMessageList());
        if (isConcatenatedMessage(submitSm, encodingType, submitSmEvent)) {
            multiPartsHandler.processPart(submitSmEvent, udhMap);
            return;
        }
        spSession.getJedisCluster().lpush(properties.getPreMessageList(), submitSmEvent.toString());
        cdrProcessor.putCdrDetailOnRedis(
                submitSmEvent.toCdrDetail(UtilsEnum.Module.SMPP_SERVER, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.RECEIVED, "Received"));
    }

    private boolean isConcatenatedMessage(SubmitSm submitSm, int encodingType, MessageEvent messageEvent) {
        boolean isConcatenated = false;

        if (submitSm.isUdhi()) {
            udhMap = Converter.bytesToUdhMap(submitSm.getShortMessage(), encodingType);
            return udhMap.containsKey(Constants.IEI_CONCATENATED_MESSAGE);
        } else if (isTlvMessagePart(messageEvent)) {
            udhMap = new HashMap<>();
            int[] segment = new int[3];

            messageEvent.getOptionalParameters().stream()
                    .filter(opt -> tagMultiPartMessageToIndexMap.containsKey(opt.tag()))
                    .forEach(opt -> {
                        Integer index = tagMultiPartMessageToIndexMap.get(opt.tag());
                        segment[index] = Integer.parseInt(opt.value());
                    });

            udhMap.put(Constants.IEI_CONCATENATED_MESSAGE, segment);
            udhMap.put("message", messageEvent.getShortMessage());

            return true;
        }

        return isConcatenated;
    }

    private MessageEvent createSubmitSmEvent(SubmitSm submitSm, MessageId messageId,
                                             ServiceProvider currentServiceProvider, int encodingType) {
        MessageEvent event = getSubmitSmEvent(submitSm, encodingType);
        event.setSystemId(currentServiceProvider.getSystemId());
        event.setOriginNetworkId(currentServiceProvider.getNetworkId());
        event.setId(messageId.getValue());
        event.setMessageId(messageId.getValue());
        event.setParentId(messageId.getValue());

        if (submitSm.getOptionalParameters() != null) {
            SmppUtils.setTLV(event, submitSm.getOptionalParameters());
        }

        return event;
    }

    private MessageEvent getSubmitSmEvent(SubmitSm submitSm, int encodingType) {
        String decodedMessage = SmppEncoding.decodeMessage(submitSm.getShortMessage(), encodingType);
        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setRetry(false);
        submitSmEvent.setRetryDestNetworkId("");
        submitSmEvent.setCommandStatus(submitSm.getCommandStatus());
        submitSmEvent.setSequenceNumber(submitSm.getSequenceNumber());
        submitSmEvent.setSourceAddrTon((int) submitSm.getSourceAddrTon());
        submitSmEvent.setSourceAddrNpi((int) submitSm.getSourceAddrNpi());
        submitSmEvent.setSourceAddr(submitSm.getSourceAddr());
        submitSmEvent.setDestAddrTon((int) submitSm.getDestAddrTon());
        submitSmEvent.setDestAddrNpi((int) submitSm.getDestAddrNpi());
        submitSmEvent.setDestinationAddr(submitSm.getDestAddress());
        submitSmEvent.setEsmClass((int) submitSm.getEsmClass());
        submitSmEvent.setStringValidityPeriod(submitSm.getValidityPeriod());
        submitSmEvent.setRegisteredDelivery((int) submitSm.getRegisteredDelivery());
        submitSmEvent.setDataCoding((int) submitSm.getDataCoding());
        submitSmEvent.setSmDefaultMsgId(submitSm.getSmDefaultMsgId());
        submitSmEvent.setShortMessage(decodedMessage);
        return submitSmEvent;
    }

    private boolean isTlvMessagePart(MessageEvent messageEvent) {
        if (Objects.isNull(messageEvent.getOptionalParameters())) {
            return false;
        }

        Set<Short> eventTags = messageEvent.getOptionalParameters().stream()
                .map(UtilsRecords.OptionalParameter::tag)
                .collect(Collectors.toSet());
        return eventTags.containsAll(tagMultiPartMessageToIndexMap.keySet());
    }

    @Generated
    @Override
    public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti, SMPPServerSession smppServerSession) {
        return null;
    }

    @Generated
    @Override
    public QuerySmResult onAcceptQuerySm(QuerySm querySm, SMPPServerSession smppServerSession) {
        return null;
    }

    @Generated
    @Override
    public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession smppServerSession) {
        log.info("ReplaceSm received: {}", replaceSm);
    }

    @Generated
    @Override
    public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession smppServerSession) {
        log.info("CancelSm received: {}", cancelSm);
    }

    @Generated
    @Override
    public BroadcastSmResult onAcceptBroadcastSm(BroadcastSm broadcastSm, SMPPServerSession smppServerSession) {
        return null;
    }

    @Generated
    @Override
    public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession smppServerSession) {
        log.info("CancelBroadcastSm received: {}", cancelBroadcastSm);
    }

    @Generated
    @Override
    public QueryBroadcastSmResult onAcceptQueryBroadcastSm(QueryBroadcastSm queryBroadcastSm, SMPPServerSession smppServerSession) {
        return null;
    }

    @Generated
    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session session) {
        return null;
    }

    @Generated
    @Override
    public void onAcceptEnquireLink(EnquireLink enquireLink, Session source) {
        ServerMessageReceiverListener.super.onAcceptEnquireLink(enquireLink, source);
    }
}
