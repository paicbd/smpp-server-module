package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AutoRegisterTest {

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private AutoRegister autoRegister;

    private String instance;

    @BeforeEach
    void setUp() {
        when(this.appProperties.getInstanceName()).thenReturn("smpp-server");
        when(this.appProperties.getInstanceIp()).thenReturn("127.0.0.1");
        when(this.appProperties.getInstancePort()).thenReturn("8000");
        when(this.appProperties.getInstanceProtocol()).thenReturn("smpp");
        when(this.appProperties.getInstanceScheme()).thenReturn("smpp");
        when(this.appProperties.getHttpRequestApiKey()).thenReturn("12345678");
        when(this.appProperties.getInstanceInitialStatus()).thenReturn("STARTED");
        instance = String.format("{\"name\":\"%s\",\"ip\":\"%s\",\"port\":\"%s\",\"protocol\":\"%s\",\"scheme\":\"%s\",\"apiKey\":\"%s\",\"state\":\"%s\"}", appProperties.getInstanceName(), appProperties.getInstanceIp(), appProperties.getInstancePort(), appProperties.getInstanceProtocol(), appProperties.getInstanceScheme(), appProperties.getHttpRequestApiKey(), appProperties.getInstanceInitialStatus());
    }

    @Test
    void register() {
        when(this.appProperties.getConfigurationHash()).thenReturn("configuration");
        this.autoRegister.init();
        verify(jedisCluster).hset("configuration", "smpp-server", instance);
    }

    @Test
    void unregister() {
        String instanceMock = this.autoRegister.createInstance("STOPPED");
        when(this.appProperties.getConfigurationHash()).thenReturn("configuration");
        assertNotNull(instanceMock);
        this.autoRegister.unregister();
        verify(jedisCluster).hset("configuration", "smpp-server", instanceMock);
        verify(jedisCluster).hdel("configuration", "smpp-server");
    }
}