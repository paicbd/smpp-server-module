package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

@ExtendWith(MockitoExtension.class)
class AutoRegisterTest {

    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private AutoRegister autoRegister;

    @BeforeEach
    void setUp() {
        Mockito.when(this.appProperties.getInstanceName()).thenReturn("smpp-server");
        Mockito.when(this.appProperties.getInstanceIp()).thenReturn("127.0.0.1");
        Mockito.when(this.appProperties.getInstancePort()).thenReturn("8000");
        Mockito.when(this.appProperties.getInstanceProtocol()).thenReturn("smpp");
        Mockito.when(this.appProperties.getInstanceScheme()).thenReturn("smpp");
        Mockito.when(this.appProperties.getHttpRequestApiKey()).thenReturn("12345678");
        String instance = String.format("{\"name\":\"%s\",\"ip\":\"%s\",\"port\":\"%s\",\"protocol\":\"%s\",\"scheme\":\"%s\",\"apiKey\":\"%s\",\"state\":\"%s\"}", appProperties.getInstanceName(), appProperties.getInstanceIp(), appProperties.getInstancePort(), appProperties.getInstanceProtocol(), appProperties.getInstanceScheme(), appProperties.getHttpRequestApiKey(), "STOPPED");
        Mockito.when(this.autoRegister.createInstance("STOPPED")).thenReturn(instance);
    }

    @Test
    void register() {
        Mockito.when(this.appProperties.getConfigurationHash()).thenReturn("configuration");
        Assertions.assertDoesNotThrow(() -> this.autoRegister.init());
    }

    @Test
    void createInstance() {
        Assertions.assertNotNull(this.autoRegister.createInstance("STOPPED"));
    }

    @Test
    void unregister() {
        Mockito.when(this.jedisCluster.hset(appProperties.getConfigurationHash(), appProperties.getInstanceName(), "")).thenReturn(1L);
        Mockito.when(this.jedisCluster.hdel(appProperties.getConfigurationHash(), appProperties.getInstanceName())).thenReturn(1L);
        Assertions.assertNotEquals(0, this.jedisCluster.hset(appProperties.getConfigurationHash(), appProperties.getInstanceName(), ""));
        Assertions.assertNotEquals(0, this.jedisCluster.hdel(appProperties.getConfigurationHash(), appProperties.getInstanceName()));
        Assertions.assertDoesNotThrow(() -> this.autoRegister.unregister());
    }
}