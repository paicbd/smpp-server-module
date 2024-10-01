package com.paicbd.module.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.utils.Converter;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerHandler {
    private final JedisCluster jedisCluster;
    private final AppProperties appProperties;

    @Getter
    private String state;

    @PostConstruct
    private void init() {
        manageServerHandler();
    }

    public void manageServerHandler() {
        try {
            String serverHandlerJson = jedisCluster.hget(this.appProperties.getConfigurationHash(), this.appProperties.getServerKey());
            if (serverHandlerJson == null) {
                log.error("ServerHandler not found in redis, configurationHash: {}, serverKey: {}", this.appProperties.getConfigurationHash(), this.appProperties.getServerKey());
            }
            Map<String, Object> serverHandlerUpdated = Converter.stringToObject(serverHandlerJson, new TypeReference<>() {});
            this.state = serverHandlerUpdated.get("state").toString();
        } catch (Exception e) {
            log.error("Error on getServerHandler: {}", e.getMessage());
        }
    }
}
