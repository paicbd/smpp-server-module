package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.exception.RTException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

@ExtendWith(MockitoExtension.class)
class GeneralSettingsCacheConfigTest {
    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties properties;
    @Mock
    private GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @BeforeEach
    void setUp() {
        generalSettingsCacheConfig = new GeneralSettingsCacheConfig(jedisCluster, properties);
        Mockito.when(this.properties.getSmppGeneralSettingsHash()).thenReturn("general_settings");
        Mockito.when(this.properties.getSmppGeneralSettingsKey()).thenReturn("smpp_http");
        Mockito.when(this.jedisCluster.hget(this.properties.getSmppGeneralSettingsHash(),
                        this.properties.getSmppGeneralSettingsKey()))
                .thenReturn("{\"id\":1,\"validity_period\":60,\"max_validity_period\":240,\"source_addr_ton\":1," +
                        "\"source_addr_npi\":1,\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"encoding_iso88591\":3," +
                        "\"encoding_gsm7\":0,\"encoding_ucs2\":2}");
        generalSettingsCacheConfig.initializeGeneralSettings();
    }

    @Test
    void initializeGeneralSettings() {
        Assertions.assertNotNull(generalSettingsCacheConfig.getCurrentGeneralSettings());
    }

    @Test
    void initializeGeneralSettings_throwException() {
        Mockito.when(this.generalSettingsCacheConfig.getCurrentGeneralSettings()).thenReturn(null);
        Assertions.assertThrows(RTException.class, () -> generalSettingsCacheConfig.initializeGeneralSettings());
    }

    @Test
    void getCurrentGeneralSettings() {
        Assertions.assertNotNull(generalSettingsCacheConfig.getCurrentGeneralSettings());
    }

    @Test
    void updateGeneralSettings() {
        Assertions.assertTrue(generalSettingsCacheConfig.updateGeneralSettings());
    }

    @Test
    void updateGeneralSettings_throwException() {
        Mockito.when(this.generalSettingsCacheConfig.getGeneralSettingFromRedis()).thenReturn(null);
        Assertions.assertFalse(this.generalSettingsCacheConfig.updateGeneralSettings());
    }
}