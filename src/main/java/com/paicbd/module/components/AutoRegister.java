package com.paicbd.module.components;

import com.paicbd.module.utils.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

/**
 * Class responsible for registering and unregistering an instance in redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoRegister {
    private final AppProperties properties;
    private final JedisCluster jedisCluster;

    @PostConstruct
    public void init() {
        register();
    }

    /**
     * Registers the instance in the service registry.
     * This method adds the instance details to the service registry using Redis.
     * The instance details include the instance name, IP, port, protocol, scheme, API key, and state.
     */
    public void register() {
        log.info("Registering instance in service registry");
        String instance = createInstance("");
        jedisCluster.hset(properties.getConfigurationHash(), properties.getInstanceName(), instance);
    }

    public String createInstance(String state) {
        return String.format("{\"name\":\"%s\",\"ip\":\"%s\",\"port\":\"%s\",\"protocol\":\"%s\",\"scheme\":\"%s\",\"apiKey\":\"%s\",\"state\":\"%s\"}", properties.getInstanceName(), properties.getInstanceIp(), properties.getInstancePort(), properties.getInstanceProtocol(), properties.getInstanceScheme(), properties.getHttpRequestApiKey(), state.isEmpty() ? properties.getInstanceInitialStatus() : state);
    }

    /**
     * Unregisters the instance from the service registry.
     * This method removes the instance details from the service registry using Redis.
     * It also sends processed data to the rating service.
     */
    @PreDestroy
    public void unregister() {
        log.info("Unregistering instance from service registry");
        String instance = createInstance("STOPPED");
        jedisCluster.hset(properties.getConfigurationHash(), properties.getInstanceName(), instance);
        jedisCluster.hdel(properties.getConfigurationHash(), properties.getInstanceName());
    }
}
