package com.paicbd.module.utils;

import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SmppUtilsTest {
    @Test
    void testPrivateConstructor() throws NoSuchMethodException {
        Constructor<SmppUtils> constructor = SmppUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Assertions.assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void determineEncodingType() {
        GeneralSettings smppGeneralSettingsMock = new GeneralSettings();
        smppGeneralSettingsMock.setEncodingGsm7(1);
        smppGeneralSettingsMock.setEncodingUcs2(2);
        smppGeneralSettingsMock.setEncodingIso88591(3);

        Assertions.assertEquals(3, SmppUtils.determineEncodingType(3, smppGeneralSettingsMock));
        Assertions.assertEquals(2, SmppUtils.determineEncodingType(8, smppGeneralSettingsMock));
        Assertions.assertEquals(1, SmppUtils.determineEncodingType(0, smppGeneralSettingsMock));

        Assertions.assertThrows(IllegalStateException.class, () -> SmppUtils.determineEncodingType(1, smppGeneralSettingsMock));
    }

    @Test
    void setTLV() {
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        OptionalParameter[] optionalParameters = new OptionalParameter[]{messageReceiptId};
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        List<UtilsRecords.OptionalParameter> currentParam = submitSmEvent.getOptionalParameters();
        SmppUtils.setTLV(submitSmEvent, optionalParameters);
        Assertions.assertNotEquals(currentParam, submitSmEvent.getOptionalParameters());
    }

    @Test
    void getTLV_nullTag() {
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        OptionalParameter[] optionalParameters = new OptionalParameter[]{messageReceiptId};
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        SmppUtils.setTLV(submitSmEvent, optionalParameters);

        MockedStatic<OptionalParameter.Tag> optionalParameterMock = Mockito.mockStatic(OptionalParameter.Tag.class);
        optionalParameterMock.when(() -> OptionalParameter.Tag.valueOf(submitSmEvent.getOptionalParameters().getFirst().tag()))
                        .thenAnswer(invocation -> null);

        Assertions.assertNotNull(SmppUtils.getTLV(submitSmEvent));
        optionalParameterMock.close();
    }

    @Test
    void getTLV() {
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        OptionalParameter[] optionalParameters = new OptionalParameter[]{messageReceiptId};
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        SmppUtils.setTLV(submitSmEvent, optionalParameters);

        MockedStatic<OptionalParameters> optionalParametersMock = Mockito.mockStatic(OptionalParameters.class);
        optionalParametersMock.when(() -> OptionalParameters.deserialize(submitSmEvent.getOptionalParameters().getFirst().tag(),
                        submitSmEvent.getOptionalParameters().getFirst().value().getBytes()))
                .thenAnswer(invocation -> {
                    byte[] bytes = submitSmEvent.getOptionalParameters().getFirst().value().getBytes();
                    return new OptionalParameter.Receipted_message_id(bytes);
                });

        Assertions.assertNotNull(SmppUtils.getTLV(submitSmEvent));
        optionalParametersMock.close();
    }

    @Test
    void getTLV_SAR_MSG_REF_NUM() {
        OptionalParameter sarMsgRefNum = new OptionalParameter.Sar_msg_ref_num((short) '1');
        OptionalParameter[] optionalParameters = new OptionalParameter[]{sarMsgRefNum};
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        SmppUtils.setTLV(submitSmEvent, optionalParameters);

        MockedStatic<OptionalParameters> optionalParametersMock = Mockito.mockStatic(OptionalParameters.class);
        optionalParametersMock.when(() -> OptionalParameters.deserialize(submitSmEvent.getOptionalParameters().getFirst().tag(),
                        submitSmEvent.getOptionalParameters().getFirst().value().getBytes()))
                .thenAnswer(invocation -> {
                    byte[] bytes = submitSmEvent.getOptionalParameters().getFirst().value().getBytes();
                    return new OptionalParameter.Receipted_message_id(bytes);
                });

        Assertions.assertNotNull(SmppUtils.getTLV(submitSmEvent));
        optionalParametersMock.close();
    }

    @Test
    void getTLV_ALERT_ON_MESSAGE_DELIVERY() {
        OptionalParameter alertOnMessageDelivery = new OptionalParameter.Alert_on_message_delivery((byte) 1);
        OptionalParameter[] optionalParameters = new OptionalParameter[]{alertOnMessageDelivery};
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        SmppUtils.setTLV(submitSmEvent, optionalParameters);

        MockedStatic<OptionalParameters> optionalParametersMock = Mockito.mockStatic(OptionalParameters.class);
        optionalParametersMock.when(() -> OptionalParameters.deserialize(submitSmEvent.getOptionalParameters().getFirst().tag(),
                        submitSmEvent.getOptionalParameters().getFirst().value().getBytes()))
                .thenAnswer(invocation -> {
                    byte[] bytes = submitSmEvent.getOptionalParameters().getFirst().value().getBytes();
                    return new OptionalParameter.Receipted_message_id(bytes);
                });

        Assertions.assertNotNull(SmppUtils.getTLV(submitSmEvent));
        optionalParametersMock.close();
    }

    @Test
    void getTLV_default() {
        OptionalParameter sarMsgRefNum = new OptionalParameter.Broadcast_error_status(1);
        OptionalParameter[] optionalParameters = new OptionalParameter[]{sarMsgRefNum};
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        SmppUtils.setTLV(submitSmEvent, optionalParameters);

        MockedStatic<OptionalParameters> optionalParametersMock = Mockito.mockStatic(OptionalParameters.class);
        optionalParametersMock.when(() -> OptionalParameters.deserialize(submitSmEvent.getOptionalParameters().getFirst().tag(),
                        submitSmEvent.getOptionalParameters().getFirst().value().getBytes()))
                .thenAnswer(invocation -> {
                    byte[] bytes = submitSmEvent.getOptionalParameters().getFirst().value().getBytes();
                    return new OptionalParameter.Receipted_message_id(bytes);
                });

        Assertions.assertNotNull(SmppUtils.getTLV(submitSmEvent));
        optionalParametersMock.close();
    }

    @Test
    void setTLV_throwException() {
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        OptionalParameter[] optionalParameters = new OptionalParameter[]{messageReceiptId};
        Assertions.assertDoesNotThrow(() -> SmppUtils.setTLV(null, optionalParameters));
    }

    @Test
    void getTLV_throwException() {
        Assertions.assertDoesNotThrow(() -> SmppUtils.getTLV(null));
    }

    /**
     * only for static method deserializeTLV
     */

    @Test
    void testDeserializeTlv() {
        long var10000 = System.currentTimeMillis();
        String messageId = var10000 + "-" + System.nanoTime();

        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setMessageId(messageId);
        submitSmEvent.setShortMessage("test");

        OptionalParameter optionalParameter = new OptionalParameter.Dest_addr_subunit((byte) 1);
        OptionalParameter optionalParameter1 = new OptionalParameter.Dest_network_type((byte) 1);
        OptionalParameter optionalParameter2 = new OptionalParameter.Dest_bearer_type((byte) 1);
        OptionalParameter optionalParameter3 = new OptionalParameter.Dest_telematics_id((byte) 1);
        OptionalParameter optionalParameter4 = new OptionalParameter.Source_addr_subunit((byte) 1);
        OptionalParameter optionalParameter5 = new OptionalParameter.Source_network_type((byte) 1);
        OptionalParameter optionalParameter6 = new OptionalParameter.Source_bearer_type((byte) 1);
        OptionalParameter optionalParameter7 = new OptionalParameter.Source_telematics_id((byte) 1);
        OptionalParameter optionalParameter8 = new OptionalParameter.Qos_time_to_live((byte) 1);
        OptionalParameter optionalParameter9 = new OptionalParameter.Payload_type((byte) 1);
        OptionalParameter optionalParameter10 = new OptionalParameter.Additional_status_info_text("test");
        OptionalParameter optionalParameter11 = new OptionalParameter.Ms_msg_wait_facilities((byte) 1);
        OptionalParameter optionalParameter12 = new OptionalParameter.Privacy_indicator((byte) 1);
        OptionalParameter optionalParameter13 = new OptionalParameter.Source_subaddress(new byte[]{0,1});
        OptionalParameter optionalParameter14 = new OptionalParameter.Dest_subaddress(new byte[]{0,1});
        OptionalParameter optionalParameter15 = new OptionalParameter.User_response_code(new byte[]{0,1});
        OptionalParameter optionalParameter16 = new OptionalParameter.Source_port(new byte[]{0,1});
        OptionalParameter optionalParameter17 = new OptionalParameter.User_message_reference(new byte[]{0,1});
        OptionalParameter optionalParameter18 = new OptionalParameter.Destination_port(new byte[]{0,1});
        OptionalParameter optionalParameter19 = new OptionalParameter.Language_indicator(new byte[]{0,1});
        OptionalParameter optionalParameter20 = new OptionalParameter.Sar_total_segments(new byte[]{0,1});
        OptionalParameter optionalParameter21 = new OptionalParameter.User_response_code(new byte[]{0,1});
        OptionalParameter optionalParameter22 = new OptionalParameter.Sar_segment_seqnum(new byte[]{0,1});
        OptionalParameter optionalParameter23 = new OptionalParameter.Sc_interface_version(new byte[]{0,1});
        OptionalParameter optionalParameter24 = new OptionalParameter.Callback_num_pres_ind(new byte[]{0,1});
        OptionalParameter optionalParameter25 = new OptionalParameter.Callback_num_atag(new byte[]{0,1});
        OptionalParameter optionalParameter26 = new OptionalParameter.Number_of_messages(new byte[]{0,1});
        OptionalParameter optionalParameter27 = new OptionalParameter.Callback_num(new byte[]{0,1});
        OptionalParameter optionalParameter28 = new OptionalParameter.Dpf_result(new byte[]{0,1});
        OptionalParameter optionalParameter29 = new OptionalParameter.Set_dpf(new byte[]{0,1});
        OptionalParameter optionalParameter30 = new OptionalParameter.Ms_availability_status(new byte[]{0,1});
        OptionalParameter optionalParameter31 = new OptionalParameter.Network_error_code(new byte[]{0,1});
        OptionalParameter optionalParameter32 = new OptionalParameter.Message_payload(new byte[]{0,1});
        OptionalParameter optionalParameter33 = new OptionalParameter.Delivery_failure_reason(new byte[]{0,1});
        OptionalParameter optionalParameter34 = new OptionalParameter.More_messages_to_send(new byte[]{0,1});
        OptionalParameter optionalParameter35 = new OptionalParameter.Message_state(new byte[]{0,1});
        OptionalParameter optionalParameter36 = new OptionalParameter.Congestion_state(new byte[]{0,1});
        OptionalParameter optionalParameter37 = new OptionalParameter.Ussd_service_op(new byte[]{0,1});
        OptionalParameter optionalParameter38 = new OptionalParameter.Broadcast_channel_indicator(new byte[]{0,1});
        OptionalParameter optionalParameter39 = new OptionalParameter.Broadcast_content_type(new byte[]{0,1});
        OptionalParameter optionalParameter40 = new OptionalParameter.Broadcast_message_class(new byte[]{0,1});
        OptionalParameter optionalParameter41 = new OptionalParameter.Broadcast_rep_num(new byte[]{0,1});
        OptionalParameter optionalParameter42 = new OptionalParameter.Broadcast_frequency_interval(new byte[]{0,1});
        OptionalParameter optionalParameter43 = new OptionalParameter.Broadcast_area_identifier(new byte[]{0,1});
        OptionalParameter optionalParameter44 = new OptionalParameter.Broadcast_area_success(new byte[]{0,1});
        OptionalParameter optionalParameter45 = new OptionalParameter.Broadcast_end_time(new byte[]{0,1});
        OptionalParameter optionalParameter46 = new OptionalParameter.Broadcast_service_group(new byte[]{0,1});
        OptionalParameter optionalParameter47 = new OptionalParameter.Billing_identification(new byte[]{0,1});
        OptionalParameter optionalParameter48 = new OptionalParameter.Source_network_id(new byte[]{0,1});
        OptionalParameter optionalParameter49 = new OptionalParameter.Dest_network_id(new byte[]{0,1});
        OptionalParameter optionalParameter50 = new OptionalParameter.Source_node_id(new byte[]{0,1});
        OptionalParameter optionalParameter51 = new OptionalParameter.Dest_node_id(new byte[]{0,1});
        OptionalParameter optionalParameter52 = new OptionalParameter.Dest_addr_np_resolution(new byte[]{0,1});
        OptionalParameter optionalParameter53 = new OptionalParameter.Dest_addr_np_information(new byte[]{0,1});
        OptionalParameter optionalParameter54 = new OptionalParameter.Dest_addr_np_country(new byte[]{0,1});
        OptionalParameter optionalParameter55 = new OptionalParameter.Display_time(new byte[]{0,1});
        OptionalParameter optionalParameter56 = new OptionalParameter.Sms_signal(new byte[]{0,1});
        OptionalParameter optionalParameter57 = new OptionalParameter.Ms_validity(new byte[]{0,1});
        OptionalParameter optionalParameter58 = new OptionalParameter.Its_reply_type(new byte[]{0,1});
        OptionalParameter optionalParameter59 = new OptionalParameter.Its_session_info(new byte[]{0,1});
        OptionalParameter optionalParameter60 = new OptionalParameter.Vendor_specific_source_msc_addr(new byte[]{0,1});
        OptionalParameter optionalParameter61 = new OptionalParameter.Vendor_specific_dest_msc_addr(new byte[]{0,1});
        OptionalParameter optionalParameter62 = new OptionalParameter.Broadcast_content_type_info(new byte[]{0,1});

        OptionalParameter[] optionalParameters = new OptionalParameter[]{
                optionalParameter,
                optionalParameter1,
                optionalParameter2,
                optionalParameter3,
                optionalParameter4,
                optionalParameter5,
                optionalParameter6,
                optionalParameter7,
                optionalParameter8,
                optionalParameter9,
                optionalParameter10,
                optionalParameter11,
                optionalParameter12,
                optionalParameter13,
                optionalParameter14,
                optionalParameter15,
                optionalParameter16,
                optionalParameter17,
                optionalParameter18,
                optionalParameter19,
                optionalParameter20,
                optionalParameter21,
                optionalParameter22,
                optionalParameter23,
                optionalParameter24,
                optionalParameter25,
                optionalParameter26,
                optionalParameter27,
                optionalParameter28,
                optionalParameter29,
                optionalParameter30,
                optionalParameter31,
                optionalParameter32,
                optionalParameter33,
                optionalParameter34,
                optionalParameter35,
                optionalParameter36,
                optionalParameter37,
                optionalParameter38,
                optionalParameter39,
                optionalParameter40,
                optionalParameter41,
                optionalParameter42,
                optionalParameter43,
                optionalParameter44,
                optionalParameter45,
                optionalParameter46,
                optionalParameter47,
                optionalParameter48,
                optionalParameter49,
                optionalParameter50,
                optionalParameter51,
                optionalParameter52,
                optionalParameter53,
                optionalParameter54,
                optionalParameter55,
                optionalParameter56,
                optionalParameter57,
                optionalParameter58,
                optionalParameter59,
                optionalParameter60,
                optionalParameter61,
                optionalParameter62
        };
        SmppUtils.setTLV(submitSmEvent, optionalParameters);
        Assertions.assertNotNull(SmppUtils.getTLV(submitSmEvent));
    }
}