package com.paicbd.module.utils;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.session.SMPPServerSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StaticMethodsTest {
    @Mock
    private SMPPServerSession smppServerSession;
    @InjectMocks
    private GeneralSettings smppGeneralSettings;
    @Mock
    private CdrProcessor cdrProcessor;
    @Mock
    private MessageEvent deliverSmEventMock;

    @BeforeEach
    void setUp() {
        GeneralSettings smppGeneralSettingsMock = new GeneralSettings();
        smppGeneralSettingsMock.setEncodingGsm7(1);
        smppGeneralSettingsMock.setEncodingUcs2(2);
        smppGeneralSettingsMock.setEncodingIso88591(3);
    }

    @Test
    void testPrivateConstructor() throws NoSuchMethodException {
        Constructor<StaticMethods> constructor = StaticMethods.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void sendDeliverSm() {
        MessageEvent deliverSmEvent = getDeliverSmEventMock();
        Assertions.assertDoesNotThrow(() -> StaticMethods.sendDeliverSm(smppServerSession, deliverSmEvent, smppGeneralSettings, cdrProcessor));
    }

    @Test
    void sendDeliverSm_differentTag() {
        OptionalParameter messageReceiptId = new OptionalParameter.Broadcast_error_status(1);
        OptionalParameter[] optionalParameters = new OptionalParameter[]{messageReceiptId};

        MessageEvent deliverSmEvent = getDeliverSmEventMock();
        deliverSmEvent.setShortMessage("test");
        deliverSmEvent.setDataCoding(0);
        deliverSmEvent.setEsmClass(3);
        deliverSmEvent.setUdhi("0");
        deliverSmEvent.setDelReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message");
        SmppUtils.setTLV(deliverSmEvent, optionalParameters);

        Assertions.assertDoesNotThrow(() -> StaticMethods.sendDeliverSm(smppServerSession, deliverSmEvent, smppGeneralSettings, cdrProcessor));
    }

    @Test
    void sendDeliverSm_withouOptionalParameters() {
        MessageEvent deliverSmEvent = getDeliverSmEventMock();
        deliverSmEvent.setShortMessage("test");
        deliverSmEvent.setDataCoding(0);
        deliverSmEvent.setEsmClass(null);
        deliverSmEvent.setDelReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message");
        deliverSmEvent.setOptionalParameters(null);
        Assertions.assertDoesNotThrow(() -> StaticMethods.sendDeliverSm(smppServerSession, deliverSmEvent, smppGeneralSettings, cdrProcessor));
    }

    @Test
    void sendDeliverSm_throwException() {
        Mockito.when(deliverSmEventMock.getDataCoding()).thenReturn(5);
        Assertions.assertDoesNotThrow(() -> StaticMethods.sendDeliverSm(smppServerSession, deliverSmEventMock, smppGeneralSettings, cdrProcessor));
    }

    @Test
    void isValidDataCoding() {
        int dataCoding = 0;
        Assertions.assertTrue(StaticMethods.isValidDataCoding(dataCoding));
        dataCoding = 11;
        Assertions.assertFalse(StaticMethods.isValidDataCoding(dataCoding));
    }

    private MessageEvent getDeliverSmEventMock() {
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        OptionalParameter[] optionalParameters = new OptionalParameter[]{messageReceiptId};
        String messageId = System.currentTimeMillis() + "-" + System.nanoTime();
        MessageEvent deliverSmEvent = new MessageEvent();
        deliverSmEvent.setMessageId(messageId);
        deliverSmEvent.setShortMessage("test");
        deliverSmEvent.setDataCoding(0);
        deliverSmEvent.setEsmClass(3);
        deliverSmEvent.setUdhi("1");
        deliverSmEvent.setSourceAddrTon(1);
        deliverSmEvent.setSourceAddrNpi(1);
        deliverSmEvent.setDestAddrNpi(1);
        deliverSmEvent.setDestAddrTon(1);
        deliverSmEvent.setSourceAddr("11111111");
        deliverSmEvent.setDestinationAddr("22222222");
        deliverSmEvent.setUdhi("1");
        deliverSmEvent.setDelReceipt("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message");
        SmppUtils.setTLV(deliverSmEvent, optionalParameters);
        return deliverSmEvent;
    }

}