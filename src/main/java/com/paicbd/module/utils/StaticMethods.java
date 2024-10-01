package com.paicbd.module.utils;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.UtilsEnum;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.bean.DataCoding;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.session.SMPPServerSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class StaticMethods {
    private StaticMethods() {
        throw new IllegalStateException("Utility Class");
    }

    private static final Set<Integer> validDataCodings = Set.of(SmppEncoding.GSM7, SmppEncoding.ISO88591, 8);

    public static void sendDeliverSm(SMPPServerSession serverSession, MessageEvent deliverSmEvent, GeneralSettings smppGeneralSettings, CdrProcessor cdrProcessor) {
        try {
            List<UtilsRecords.OptionalParameter> optionalParameters = new ArrayList<>();
            if (deliverSmEvent.getOptionalParameters() != null) {
                optionalParameters = deliverSmEvent.getOptionalParameters();
                replaceOptionalParameter(optionalParameters, deliverSmEvent);
                deliverSmEvent.setOptionalParameters(optionalParameters);
            }

            int dataCodingDlr = Objects.isNull(deliverSmEvent.getDataCoding()) ? 0 : deliverSmEvent.getDataCoding() ;

            int encodingType = SmppUtils.determineEncodingType(dataCodingDlr, smppGeneralSettings);
            DataCoding dataCoding = SmppEncoding.getDataCoding(dataCodingDlr);
            byte[] encodedShortMessage = SmppEncoding.encodeMessage(deliverSmEvent.getDelReceipt(), encodingType);

            serverSession.deliverShortMessage(
                    "",
                    UtilsEnum.getTypeOfNumber(deliverSmEvent.getSourceAddrTon()),
                    UtilsEnum.getNumberingPlanIndicator(deliverSmEvent.getSourceAddrNpi()),
                    deliverSmEvent.getSourceAddr(),
                    UtilsEnum.getTypeOfNumber(deliverSmEvent.getDestAddrTon()),
                    UtilsEnum.getNumberingPlanIndicator(deliverSmEvent.getDestAddrNpi()),
                    deliverSmEvent.getDestinationAddr(),
                    getEsmClass(deliverSmEvent.getEsmClass(), deliverSmEvent.getUdhi()),
                    (byte)0,
                    (byte)0,
                    new RegisteredDelivery(0),
                    dataCoding,
                    encodedShortMessage,
                    optionalParameters.isEmpty() ? null : SmppUtils.getTLV(deliverSmEvent));

            cdrDetailToDeliver(deliverSmEvent, UtilsEnum.CdrStatus.SENT, cdrProcessor);
        } catch (Exception e) {
            cdrDetailToDeliver(deliverSmEvent, UtilsEnum.CdrStatus.FAILED, cdrProcessor);
            log.error("Error on process deliverSm {} ex -> {}", deliverSmEvent, e.getMessage());
        }
    }

    private static void replaceOptionalParameter(
            List<UtilsRecords.OptionalParameter> optionalParameters,
            MessageEvent deliverSmEvent) {
        UtilsRecords.OptionalParameter currentRecord = null;
        UtilsRecords.OptionalParameter newRecord = null;
        if (!optionalParameters.isEmpty()) {
            for (UtilsRecords.OptionalParameter op : optionalParameters) {
                if (op.tag() == 30) {
                    currentRecord = op;
                    newRecord = new UtilsRecords.OptionalParameter(op.tag(), deliverSmEvent.getDeliverSmServerId());
                    break;
                }
            }
        }
        if (Objects.nonNull(newRecord)) {
            optionalParameters.remove(currentRecord);
            optionalParameters.add(newRecord);
        }
    }

    private static void cdrDetailToDeliver(MessageEvent deliverSmEvent, UtilsEnum.CdrStatus cdrStatus, CdrProcessor cdrProcessor) {
        cdrProcessor.putCdrDetailOnRedis(
                deliverSmEvent.toCdrDetail(UtilsEnum.Module.SMPP_SERVER, UtilsEnum.MessageType.DELIVER, cdrStatus, "Sent to SP"));
        cdrProcessor.createCdr(deliverSmEvent.getMessageId());
    }

    public static boolean isValidDataCoding(int dataCoding) {
        return validDataCodings.contains(dataCoding);
    }

    private static ESMClass getEsmClass(Integer esmClass, String udhi) {
        if (Objects.nonNull(esmClass)) {
            var esmeClass = new ESMClass(esmClass);
            esmeClass.setSpecificFeature(("1".equals(udhi)) ? GSMSpecificFeature.UDHI: GSMSpecificFeature.DEFAULT);
            return esmeClass;
        }
        return new ESMClass(MessageMode.DEFAULT, MessageType.SMSC_DEL_RECEIPT, GSMSpecificFeature.DEFAULT);
    }
}
