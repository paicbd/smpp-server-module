package com.paicbd.module.utils;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.SmppUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.DataCoding;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.SMPPServerSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.paicbd.smsc.utils.SmppEncoding.DCS_0;
import static com.paicbd.smsc.utils.SmppEncoding.DCS_3;
import static com.paicbd.smsc.utils.SmppEncoding.DCS_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Slf4j
class StaticMethodsTest {

    @Mock
    private CdrProcessor cdrProcessor;

    @ParameterizedTest
    @MethodSource("sendDeliverSmParameters")
    @DisplayName("""
            sendDeliverSm when delReceipt is not null then the method is executed
            replacing optional parameters if needed, parsing the EsmClass and creating the proper CDR entry,
            when delReceipt is null then the sendDeliverSm is not executed and the method's catch part is processed
            """)
    void sendDeliverSm(Integer esmClass, String udh, ESMClass esmClassExpected,
                       List<UtilsRecords.OptionalParameter> optionalParameters, Integer dataCoding,
                       String delReceipt)
            throws ResponseTimeoutException, PDUException, IOException,
            InvalidResponseException, NegativeResponseException {

        GeneralSettings generalSettings = GeneralSettings.builder()
                .encodingGsm7(SmppEncoding.UTF8)
                .encodingUcs2(SmppEncoding.UCS2)
                .encodingIso88591(SmppEncoding.ISO88591)
                .build();

        MessageEvent messageEvent = MessageEvent.builder()
                .messageId("1")
                .shortMessage("Test Message")
                .delReceipt(delReceipt)
                .dataCoding(dataCoding)
                .esmClass(3)
                .sourceAddrTon(1)
                .sourceAddrNpi(1)
                .destAddrTon(1)
                .destAddrNpi(1)
                .sourceAddr("1234")
                .destinationAddr("5678")
                .udhi(udh)
                .esmClass(esmClass)
                .optionalParameters(optionalParameters)
                .build();

        boolean isReplacementOptionalParameterTest = esmClass != null && esmClass == 3
                && (optionalParameters != null && !optionalParameters.isEmpty());

        if (isReplacementOptionalParameterTest) {
            OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
            OptionalParameter[] optionalParameterList = new OptionalParameter[]{messageReceiptId};
            SmppUtils.setTLV(messageEvent, optionalParameterList);
        }

        ArgumentCaptor<TypeOfNumber> sourceAddressTON = ArgumentCaptor.forClass(TypeOfNumber.class);
        ArgumentCaptor<NumberingPlanIndicator> sourceAddressNPI = ArgumentCaptor.forClass(NumberingPlanIndicator.class);
        ArgumentCaptor<String> sourceAddress = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TypeOfNumber> destinationAddressTON = ArgumentCaptor.forClass(TypeOfNumber.class);
        ArgumentCaptor<NumberingPlanIndicator> destinationAddressNPI = ArgumentCaptor.forClass(NumberingPlanIndicator.class);
        ArgumentCaptor<String> destinationAddress = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ESMClass> esmClassCaptor = ArgumentCaptor.forClass(ESMClass.class);
        ArgumentCaptor<DataCoding> dataCodingCaptor = ArgumentCaptor.forClass(DataCoding.class);
        ArgumentCaptor<byte[]> shortMessageCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<OptionalParameter[]> optionalParametersCaptor = ArgumentCaptor.forClass(OptionalParameter[].class);

        SMPPServerSession session = mock(SMPPServerSession.class);
        StaticMethods.sendDeliverSm(session, messageEvent, generalSettings, cdrProcessor);
        messageEvent.setDeliverSmServerId("UpdatedValue");

        // When delReceipt is null then the exception part is evaluated so deliverShortMessage() won't be executed
        if (delReceipt != null) {
            verify(session).deliverShortMessage(
                    eq(""),
                    sourceAddressTON.capture(),
                    sourceAddressNPI.capture(),
                    sourceAddress.capture(),
                    destinationAddressTON.capture(),
                    destinationAddressNPI.capture(),
                    destinationAddress.capture(),
                    esmClassCaptor.capture(),
                    eq((byte) 0),
                    eq((byte) 0),
                    eq(new RegisteredDelivery(0)),
                    dataCodingCaptor.capture(),
                    shortMessageCaptor.capture(),
                    optionalParametersCaptor.capture()
            );
            ESMClass esm = esmClassCaptor.getValue();
            assertEquals(esmClassExpected.value(), esm.value());
        }
    }

    static Stream<Arguments> sendDeliverSmParameters() {
        String delReceipt = "id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message";
        ESMClass esmDefaultDefaultUdh = new ESMClass(MessageMode.DEFAULT, MessageType.DEFAULT, GSMSpecificFeature.UDHI);
        ESMClass esmDefault = new ESMClass(MessageMode.DEFAULT, MessageType.DEFAULT, GSMSpecificFeature.DEFAULT);
        ESMClass esmDefaultReceiptDefault = new ESMClass(MessageMode.DEFAULT, MessageType.SMSC_DEL_RECEIPT, GSMSpecificFeature.DEFAULT);
        ESMClass esmStoreDefaultUdh = new ESMClass(MessageMode.STORE_AND_FORWARD, MessageType.DEFAULT, GSMSpecificFeature.UDHI);
        UtilsRecords.OptionalParameter unknownOptionalParameter = new UtilsRecords.OptionalParameter((short) 31, "1");
        UtilsRecords.OptionalParameter receiptedMessageId = new UtilsRecords.OptionalParameter((short) 30, "1");

        return Stream.of(
                Arguments.of(0, "1", esmDefaultDefaultUdh, null, 0, delReceipt),
                Arguments.of(0, "0", esmDefault, null, 0, delReceipt),
                Arguments.of(null, "0", esmDefaultReceiptDefault, null, 0, delReceipt),
                Arguments.of(0, "1", esmDefaultDefaultUdh, List.of(unknownOptionalParameter), 0, delReceipt),
                Arguments.of(null, "0", esmDefaultReceiptDefault, null, null, delReceipt),
                Arguments.of(3, "1", esmStoreDefaultUdh, List.of(receiptedMessageId), 0, delReceipt),
                Arguments.of(3, "1", esmStoreDefaultUdh, new ArrayList<>(), 0, null)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {DCS_0, DCS_8, DCS_3})
    @DisplayName("isValidDataCoding when data coding is valid then return true")
    void isValidDataCodingWhenValidThenReturnTrue(int dataCoding) {
        assertTrue(StaticMethods.isValidDataCoding(dataCoding));
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 98, 67, 87})
    @DisplayName("isValidDataCoding when data coding is not valid then return false")
    void isValidDataCodingWhenInValidThenReturnFalse(int dataCoding) {
        assertFalse(StaticMethods.isValidDataCoding(dataCoding));
    }
}
