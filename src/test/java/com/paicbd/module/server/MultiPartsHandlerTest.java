package com.paicbd.module.server;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.ServiceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class MultiPartsHandlerTest {
    @Mock
    private JedisCluster jedisCluster;
    @Mock
    private SpSession spSession;
    @Mock
    private CdrProcessor cdrProcessor;
    @InjectMocks
    private AppProperties appProperties;

    @InjectMocks
    private MultiPartsHandler multiPartsHandler;

    Map<String, Object> mapUdh = new HashMap<>();

    @BeforeEach
    void setUp() {
        ServiceProvider sp = new ServiceProvider();
        sp.setSystemId("test");
        sp.setNetworkId(1);
        spSession = new SpSession(this.jedisCluster, sp, this.appProperties);
        multiPartsHandler = new MultiPartsHandler(cdrProcessor, spSession, this.appProperties);
    }

    @Test
    void processPart_handleException() {
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setSystemId("test");
        Assertions.assertDoesNotThrow(() -> multiPartsHandler.processPart(messageEvent, mapUdh));
    }

    @Test
    void processFirstPart() {
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setSystemId("test");
        mapUdh.put("message", "Hello_Word");
        mapUdh.put("0x00", List.of(1,2,1));
        Assertions.assertDoesNotThrow(() -> multiPartsHandler.processPart(messageEvent, mapUdh));
        mapUdh.put("0x00", List.of(1,2,2));
        Assertions.assertDoesNotThrow(() -> multiPartsHandler.processPart(messageEvent, mapUdh));
        mapUdh.put("0x00", List.of());
        Assertions.assertDoesNotThrow(() -> multiPartsHandler.processPart(messageEvent, mapUdh));
    }
}