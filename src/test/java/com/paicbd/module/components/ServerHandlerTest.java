package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerHandlerTest {

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private ServerHandler serverHandler;


    @BeforeEach
    void setUp() {
        when(this.appProperties.getConfigurationHash()).thenReturn("configurations");
        when(this.appProperties.getServerKey()).thenReturn("smpp_server");
    }

    @Test
    @DisplayName("manageServerHandler initializing server state")
    void manageServerHandlerWhenInitServerStateThenDoItSuccessfully() {
        when(jedisCluster.hget("configurations", "smpp_server"))
                         .thenReturn("{\"state\":\"STARTED\"}");

        serverHandler.manageServerHandler();
        assertEquals("STARTED", serverHandler.getState());
        verify(jedisCluster).hget("configurations", "smpp_server");
    }

    @Test
    @DisplayName("manageServerHandler initializing server state and does not exists in Redis")
    void manageServerHandlerWhenInitServerStateAndDoesNotExistsThenDoNothing() {
        when(jedisCluster.hget("configurations", "smpp_server"))
                .thenReturn(null);

        serverHandler.manageServerHandler();
        assertNull(serverHandler.getState());
        verify(jedisCluster).hget("configurations", "smpp_server");
    }

    @Test
    @DisplayName("manageServerHandler updating server state")
    void manageServerHandlerWhenUpdateServerStateThenDoItSuccessfully() {
        when(jedisCluster.hget("configurations", "smpp_server")).thenReturn("{\"state\":\"STARTED\"}");
        serverHandler.manageServerHandler();
        assertEquals("STARTED", serverHandler.getState());

        when(jedisCluster.hget("configurations", "smpp_server")).thenReturn("{\"state\":\"STOPPED\"}");
        serverHandler.manageServerHandler();

        assertEquals("STOPPED", serverHandler.getState());
        verify(jedisCluster, times(2)).hget("configurations", "smpp_server");
    }

    @Test
    @DisplayName("manageServerHandler updating server state with invalid json string")
    void manageServerHandlerWhenUpdateServerStateAndInvalidJsonStringThenDoNothing() {
        // initializing state
        when(jedisCluster.hget("configurations", "smpp_server")).thenReturn("{\"state\":\"STARTED\"}");
        serverHandler.manageServerHandler();
        assertEquals("STARTED", serverHandler.getState());

        // updating with invalid json
        when(jedisCluster.hget(anyString(), anyString())).thenReturn("\"state\":\"STOPPED\"}");
        serverHandler.manageServerHandler();
        assertEquals("STARTED", serverHandler.getState());

        verify(jedisCluster, times(2)).hget("configurations", "smpp_server");
    }
}
