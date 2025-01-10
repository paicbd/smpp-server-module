package com.paicbd.module.config;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.Generated;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisCluster;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Generated
@Configuration
@RequiredArgsConstructor
public class BeansDefinition {
    private final AppProperties appProperties;

    @Bean
    public Set<ServiceProvider> providers() {
        return ConcurrentHashMap.newKeySet();
    }

    @Bean
    public ConcurrentMap<Integer, SpSession> spSessionMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentMap<Integer, String> networkIdSystemIdMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public JedisCluster jedisCluster() {
        return Converter.paramsToJedisCluster(
                new UtilsRecords.JedisConfigParams(appProperties.getRedisNodes(), appProperties.getRedisMaxTotal(),
                        appProperties.getRedisMaxIdle(), appProperties.getRedisMinIdle(),
                        appProperties.isRedisBlockWhenExhausted(), appProperties.getRedisConnectionTimeout(),
                        appProperties.getRedisSoTimeout(), appProperties.getRedisMaxAttempts(),
                        appProperties.getRedisUser(), appProperties.getRedisPassword())
        );
    }

    @Bean
    public SocketSession socketSession() {
        return new SocketSession("sp");
    }

    @Bean
    public CdrProcessor cdrProcessor(JedisCluster jedisCluster) {
        return new CdrProcessor(jedisCluster);
    }
}
