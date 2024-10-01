package com.paicbd.module.server;

import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import org.jsmpp.bean.BroadcastSm;
import org.jsmpp.bean.CancelBroadcastSm;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.QueryBroadcastSm;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.SMPPServerSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ServerMessageReceiverListenerImplTest {
    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties appProperties;
    @Mock
    private CdrProcessor cdrProcessor;
    @Mock
    private GeneralSettingsCacheConfig generalSettingsCacheConfig;
    @Mock
    private SpSession spSession;
    @Mock
    private AtomicInteger requestCounter;
    @Mock
    private MultiPartsHandler multiPartsHandler;

    @InjectMocks
    private ServerMessageReceiverListenerImpl serverMessageReceiverListener;

    @Mock
    private SMPPServerSession smppServerSession;

    @Mock
    private ServiceProvider sp;

    @BeforeEach
    void setUp() {
        generalSettingsCacheConfig = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        Mockito.when(this.appProperties.getSmppGeneralSettingsHash()).thenReturn("general_settings");
        Mockito.when(this.appProperties.getSmppGeneralSettingsKey()).thenReturn("smpp_http");
        Mockito.when(this.jedisCluster.hget(this.appProperties.getSmppGeneralSettingsHash(),
                        this.appProperties.getSmppGeneralSettingsKey()))
                .thenReturn("{\"id\":1,\"validity_period\":60,\"max_validity_period\":240,\"source_addr_ton\":1," +
                        "\"source_addr_npi\":1,\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"encoding_iso88591\":3," +
                        "\"encoding_gsm7\":0,\"encoding_ucs2\":2}");
        generalSettingsCacheConfig.initializeGeneralSettings();

        sp = new ServiceProvider();
        sp.setNetworkId(1);
        sp.setCurrentBindsCount(1);
        spSession = new SpSession(this.jedisCluster, sp, this.appProperties);
        multiPartsHandler = new MultiPartsHandler(cdrProcessor, spSession, appProperties);

        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(requestCounter, spSession, generalSettingsCacheConfig, appProperties, cdrProcessor, multiPartsHandler);
    }

    @Test
    void onAcceptSubmitSm() {
        sp = new ServiceProvider();
        sp.setNetworkId(1);
        sp.setCurrentBindsCount(1);
        sp.setHasAvailableCredit(true);
        spSession = new SpSession(this.jedisCluster, sp, this.appProperties);

        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(requestCounter, spSession, generalSettingsCacheConfig, appProperties, cdrProcessor, multiPartsHandler);

        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding((byte) 0x12);
        submitSm.setDataCoding((byte) 0x12);
        submitSm.setShortMessage("Test Message".getBytes());
        submitSm.setDestAddress("1234567890");
        submitSm.setSourceAddr("1234567890");
        submitSm.setDestAddrTon((byte) 0x01);
        submitSm.setDestAddrNpi((byte) 0x01);
        submitSm.setSourceAddrTon((byte) 0x01);
        submitSm.setSourceAddrNpi((byte) 0x01);

        Mockito.when(requestCounter.incrementAndGet()).thenReturn(1).thenReturn(2).thenReturn(3);
        assertThrows(ProcessRequestException.class, () -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));

        submitSm.setDataCoding((byte) 0x00);
        assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));

        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        submitSm.setOptionalParameters(messageReceiptId);
        assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));

        submitSm.setEsmClass((byte) 64);
        submitSm.setUdhi();
        byte [] udhMessage = {0x05, 0x00, 0x03, 0x01, 0x02, 0x01, 0x48, 0x6f, 0x6c};
        submitSm.setShortMessage(udhMessage);
        assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));

        sp.setHasAvailableCredit(false);
        spSession = new SpSession(this.jedisCluster, sp, this.appProperties);
        serverMessageReceiverListener = new ServerMessageReceiverListenerImpl(requestCounter, spSession, generalSettingsCacheConfig, appProperties, cdrProcessor, multiPartsHandler);
        Assertions.assertThrows(ProcessRequestException.class, () -> serverMessageReceiverListener.onAcceptSubmitSm(submitSm, smppServerSession));
    }

    @Test
    void onAcceptSubmitMulti() {
        SubmitMulti submitMulti = new SubmitMulti();
        Assertions.assertNull(serverMessageReceiverListener.onAcceptSubmitMulti(submitMulti, smppServerSession));
    }

    @Test
    void onAcceptQuerySm() {
        QuerySm querySm = new QuerySm();
        Assertions.assertNull(serverMessageReceiverListener.onAcceptQuerySm(querySm, smppServerSession));
    }

    @Test
    void onAcceptReplaceSm() {
        ReplaceSm replaceSm = new ReplaceSm();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptReplaceSm(replaceSm, smppServerSession));
    }

    @Test
    void onAcceptCancelSm() {
        CancelSm cancelSm = new CancelSm();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptCancelSm(cancelSm, smppServerSession));
    }

    @Test
    void onAcceptBroadcastSm() {
        BroadcastSm broadcastSm = new BroadcastSm();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptBroadcastSm(broadcastSm, smppServerSession));
    }

    @Test
    void onAcceptCancelBroadcastSm() {
        CancelBroadcastSm cancelBroadcastSm = new CancelBroadcastSm();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptCancelBroadcastSm(cancelBroadcastSm, smppServerSession));
    }

    @Test
    void onAcceptQueryBroadcastSm() {
        QueryBroadcastSm queryBroadcastSm = new QueryBroadcastSm();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptQueryBroadcastSm(queryBroadcastSm, smppServerSession));
    }

    @Test
    void onAcceptDataSm() {
        DataSm dataSm = new DataSm();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptDataSm(dataSm, smppServerSession));
    }

    @Test
    void onAcceptEnquireLink() {
        EnquireLink enquireLink = new EnquireLink();
        Assertions.assertDoesNotThrow(() -> serverMessageReceiverListener.onAcceptEnquireLink(enquireLink, smppServerSession));
    }
}