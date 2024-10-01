package com.paicbd.module;

import com.paicbd.module.components.CustomFrameHandler;
import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.ws.SocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import redis.clients.jedis.JedisCluster;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SmppServerModuleApplication.class)
class SmppServerModuleApplicationTest {
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private JedisCluster jedisCluster;
    @MockBean
    private CdrProcessor cdrProcessor;
    @MockBean
    private CustomFrameHandler customFrameHandler;
    @MockBean
    private GeneralSettingsCacheConfig generalSettingsCacheConfig;
    @MockBean
    private SocketClient socketClient;

    @BeforeEach
    void setUp() {
        GeneralSettings generalSettings = new GeneralSettings();
        Mockito.when(this.jedisCluster.hget(this.appProperties.getSmppGeneralSettingsHash(), this.appProperties.getSmppGeneralSettingsKey()))
                .thenReturn("{\"id\":1,\"validity_period\":60,\"max_validity_period\":240,\"source_addr_ton\":1," +
                        "\"source_addr_npi\":1,\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"encoding_iso88591\":3," +
                        "\"encoding_gsm7\":0,\"encoding_ucs2\":2}");
        Mockito.when(generalSettingsCacheConfig.getGeneralSettingFromRedis()).thenReturn(generalSettings);
        generalSettingsCacheConfig = new GeneralSettingsCacheConfig(jedisCluster, appProperties);
        generalSettingsCacheConfig.initializeGeneralSettings();
    }

    @Test
    void contextLoads() {
    }
}