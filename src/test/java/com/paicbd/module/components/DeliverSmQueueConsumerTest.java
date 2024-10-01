package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliverSmQueueConsumerTest {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private JedisCluster jedisCluster;
    @InjectMocks
    private AppProperties appProperties;
    @Mock
    private CdrProcessor cdrProcessor;
    @Mock
    private ServiceProvider sp;
    @Mock
    private ConcurrentMap<Integer, String> networkIdSystemIdMap;
    @Mock
    private GeneralSettingsCacheConfig generalSettingsCacheConfig;
    @Mock(strictness = Mock.Strictness.LENIENT)
    private SpSession spSessionMock;
    @Mock
    private ConcurrentMap<String, SpSession> spSessionMap;
    @InjectMocks
    private DeliverSmQueueConsumer deliverSmQueueConsumer;
    private final ThreadFactory factory = Thread.ofVirtual().name("deliverSm-", 0).factory();
    @Mock
    private final ExecutorService executorService = Executors.newThreadPerTaskExecutor(factory);
    @Mock
    private Session sessionMock;

    @BeforeEach
    void setUp() {
        spSessionMap = new ConcurrentHashMap<>();
        sp = new ServiceProvider();
        sp.setSystemId("systemId123");
        sp.setNetworkId(1);
        sp.setCurrentBindsCount(1);
        SpSession spSession = new SpSession(this.jedisCluster, sp, this.appProperties);
        spSession.getCurrentSmppSessions().add(sessionMock);
        spSessionMap.put("systemId123", spSession);
    }

    @Test
    void startScheduler() throws IllegalAccessException, NoSuchFieldException {
        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig );
        Field executorServiceField = DeliverSmQueueConsumer.class.getDeclaredField("executorService");
        executorServiceField.setAccessible(true);
        executorServiceField.set(deliverSmQueueConsumer, executorService);
        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
        verify(executorService, times(appProperties.getDeliverSmWorkers())).execute(any(Runnable.class));
    }

    @Test
    void queueProcessingBatchThread() {
        String networkId = "1";
        Mockito.doReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"systemId123\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        )).when(this.jedisCluster).lpop("smpp_dlr", 1000);
        Mockito.when(jedisCluster.lpush(networkId.concat("_smpp_pending_dlr"), "")).thenReturn(1L);

        Mockito.when(this.jedisCluster.hget(this.appProperties.getSmppGeneralSettingsHash(),
                        this.appProperties.getSmppGeneralSettingsKey()))
                .thenReturn("{\"id\":1,\"validity_period\":60,\"max_validity_period\":240,\"source_addr_ton\":1," +
                        "\"source_addr_npi\":1,\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"encoding_iso88591\":3," +
                        "\"encoding_gsm7\":0,\"encoding_ucs2\":2}");

        Mockito.doReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"systemId123\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        )).when(this.jedisCluster).lpop("smpp_dlr", 1);

        generalSettingsCacheConfig = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        generalSettingsCacheConfig.initializeGeneralSettings();
        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig );
        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
    }

    @Test
    void queueProcessingBatchThread_serverSessionNull() {
        String networkId = "1";
        Mockito.doReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"systemId123\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        )).when(this.jedisCluster).lpop("smpp_dlr", 1000);
        Mockito.when(jedisCluster.lpush(networkId.concat("_smpp_pending_dlr"), "")).thenReturn(1L);

        SpSession spSession = new SpSession(this.jedisCluster, sp, this.appProperties);
        spSessionMap.put("systemId123", spSession);
        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig );
        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
    }

    @Test
    void queueProcessingBatchThread_spSessionNull() {
        String networkId = "1";
        Mockito.doReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"test\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        )).when(this.jedisCluster).lpop("smpp_dlr", 1000);
        Mockito.when(jedisCluster.lpush(networkId.concat("_smpp_pending_dlr"), "")).thenReturn(1L);

        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig );
        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
    }

    @Test
    void queueProcessingBatchThread_throwException() {
        String networkId = "1";
        Mockito.doReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"systemId123\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        )).when(this.jedisCluster).lpop("smpp_dlr", 1000);
        Mockito.when(jedisCluster.lpush(networkId.concat("_smpp_pending_dlr"), "")).thenReturn(1L);

        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, null, networkIdSystemIdMap, generalSettingsCacheConfig );
        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
    }

    @Test
    void queueProcessingBatchThread_systemIdNull() {
        String networkId = "1";
        Mockito.doReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\": null,\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        )).when(this.jedisCluster).lpop("smpp_dlr", 1000);
        Mockito.when(jedisCluster.lpush(networkId.concat("_smpp_pending_dlr"), "")).thenReturn(1L);
        sp.setSystemId(null);
        SpSession spSession = new SpSession(this.jedisCluster, sp, this.appProperties);
        spSessionMap.put("systemId123", spSession);
        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig );

        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
    }

    @Test
    void startScheduler_queueProcessingBatchThread_throwsException() throws NoSuchFieldException, IllegalAccessException {
        Field executorServiceField = DeliverSmQueueConsumer.class.getDeclaredField("executorService");
        executorServiceField.setAccessible(true);
        executorServiceField.set(deliverSmQueueConsumer, executorService);

        deliverSmQueueConsumer = new DeliverSmQueueConsumer(jedisCluster, cdrProcessor, appProperties, spSessionMap, networkIdSystemIdMap, generalSettingsCacheConfig );
        Assertions.assertDoesNotThrow(() -> deliverSmQueueConsumer.startScheduler());
    }
}