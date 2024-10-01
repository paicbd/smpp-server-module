package com.paicbd.module.server;

import com.paicbd.module.components.ServerHandler;
import com.paicbd.module.utils.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ServerHandlerTest {

    @Mock
    private JedisCluster jedisCluster;
    @InjectMocks
    private AppProperties appProperties;

    @InjectMocks
    private ServerHandler serverHandler;

    @BeforeEach
    void setUp() {
        serverHandler = new ServerHandler(jedisCluster, appProperties);
    }

    @Test
    void testManageServerHandler() {
         Mockito.when(jedisCluster.hget(this.appProperties.getConfigurationHash(), this.appProperties.getServerKey()))
                         .thenReturn("{\"state\":\"STARTED\"}");
        serverHandler.manageServerHandler();
        assertEquals("STARTED", serverHandler.getState());
    }

    @Test
    void testJsonSerialization() {
        Mockito.when(jedisCluster.hget(this.appProperties.getConfigurationHash(), this.appProperties.getServerKey()))
                .thenReturn("\"state\":\"STARTED\"}");
        serverHandler.manageServerHandler();
        assertNotEquals("STARTED", serverHandler.getState());
    }
}