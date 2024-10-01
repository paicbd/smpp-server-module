package com.paicbd.module.server;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompSession;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SessionStateListenerImplTest {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties appProperties;
    @Mock
    private ConcurrentMap<String, SpSession> spSessionMap;

    @Mock
    private StompSession stompSession;
    @InjectMocks
    private GeneralSettings smppGeneralSettings;
    @Mock
    private CdrProcessor cdrProcessor;
    @Mock
    private Session sessionMock;
    @Mock
    private ServiceProvider sp;
    @Mock
    private SpSession spSessionMock;

    @Mock
    private SessionStateListenerImpl sessionStateListener;

    @BeforeEach
    void setUp() {
        Mockito.when(this.appProperties.getRedisNodes()).thenReturn(List.of("localhost:7000", "localhost:7001"));
        Mockito.when(this.appProperties.getRedisMaxTotal()).thenReturn(20);
        Mockito.when(this.appProperties.getRedisMinIdle()).thenReturn(20);
        Mockito.when(this.appProperties.getRedisMaxIdle()).thenReturn(20);
        Mockito.when(this.appProperties.isRedisBlockWhenExhausted()).thenReturn(true);

        cdrProcessor = new CdrProcessor(new UtilsRecords.JedisConfigParams(appProperties.getRedisNodes(), appProperties.getRedisMaxTotal(),
                appProperties.getRedisMinIdle(), appProperties.getRedisMaxIdle(), appProperties.isRedisBlockWhenExhausted()));

        sp = new ServiceProvider();
        sp.setNetworkId(1);
        sp.setCurrentBindsCount(1);
        sp.setSystemId("systemId123");
        spSessionMock = new SpSession(this.jedisCluster, sp, this.appProperties);
        Mockito.when(spSessionMap.get("systemId123")).thenReturn(spSessionMock);
    }

    @Test
    void onStateChange() {
        var key = "systemId123_smpp_pending_dlr";
        Mockito.when(jedisCluster.llen(key)).thenReturn(1L);
        Mockito.when(jedisCluster.lpop(key, 1)).thenReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"systemId123\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        ));
        sp.setCurrentBindsCount(0);
        spSessionMock.getCurrentSmppSessions().add(sessionMock);
        sessionStateListener = new SessionStateListenerImpl("systemId123", spSessionMap, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.CLOSED, sessionMock));
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.CLOSED, sessionMock));
    }

    @Test
    void onStateChange_sessionServerNull() {
        var key = "systemId123_smpp_pending_dlr";
        Mockito.when(jedisCluster.llen(key)).thenReturn(1L);
        Mockito.when(jedisCluster.lpop(key, 1)).thenReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"systemId123\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        ));
        sp.setCurrentBindsCount(0);
        sessionStateListener = new SessionStateListenerImpl("systemId123", spSessionMap, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.CLOSED, sessionMock));
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.CLOSED, sessionMock));
    }

    @Test
    void onStateChange_stoppedStatus() {
        sp.setStatus("STOPPED");
        spSessionMock = new SpSession(this.jedisCluster, sp, this.appProperties);
        sessionStateListener = new SessionStateListenerImpl("systemId123", spSessionMap, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.CLOSED, sessionMock));
    }

    @Test
    void onStateChange_newStatusClosed() {
        sp.setCurrentBindsCount(1);
        spSessionMock = new SpSession(this.jedisCluster, sp, this.appProperties);
        sessionStateListener = new SessionStateListenerImpl("systemId123", spSessionMap, null, jedisCluster, smppGeneralSettings, cdrProcessor);
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.BOUND_TRX, sessionMock));
    }

    @Test
    void onStateChange_zeroBind() {
        sp.setCurrentBindsCount(0);
        spSessionMock = new SpSession(this.jedisCluster, sp, this.appProperties);
        sessionStateListener = new SessionStateListenerImpl("systemId123", spSessionMap, stompSession, jedisCluster, smppGeneralSettings, cdrProcessor);
        assertDoesNotThrow(() ->this.sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.BOUND_TRX, sessionMock));
    }
}