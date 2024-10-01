package com.paicbd.module.config;

import com.paicbd.module.server.SessionStateListenerImpl;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
@RequiredArgsConstructor
public class BeansDefinition {

    private final AppProperties appProperties;

    @Bean
    public Set<ServiceProvider> providers() {
        return ConcurrentHashMap.newKeySet();
    }

    @Bean
    public ConcurrentMap<String, SpSession> spSessionMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentMap<Integer, String> networkIdSystemIdMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public JedisCluster jedisCluster() {
        return Converter.paramsToJedisCluster(getJedisClusterParams(appProperties.getRedisNodes(), appProperties.getRedisMaxTotal(),
                appProperties.getRedisMinIdle(), appProperties.getRedisMaxIdle(), appProperties.isRedisBlockWhenExhausted()));
    }

    @Bean
    public ConcurrentMap<String, SessionStateListenerImpl> sessionStateListenerBySp() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public SocketSession socketSession() {
        return new SocketSession("sp");
    }

    private UtilsRecords.JedisConfigParams getJedisClusterParams(List<String> nodes, int maxTotal, int minIdle, int maxIdle, boolean blockWhenExhausted) {
        return new UtilsRecords.JedisConfigParams(nodes, maxTotal, minIdle, maxIdle, blockWhenExhausted);
    }

    @Bean
    public CdrProcessor cdrProcessor(JedisCluster jedisCluster) {
        return new CdrProcessor(jedisCluster);
    }
}
