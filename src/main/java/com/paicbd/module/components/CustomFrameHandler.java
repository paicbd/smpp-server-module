package com.paicbd.module.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.FrameHandler;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.paicbd.module.utils.Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_SERVER_HANDLER_ENDPOINT;
import static com.paicbd.module.utils.Constants.SERVICE_PROVIDER_DELETED_ENDPOINT;
import static com.paicbd.module.utils.Constants.GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT;

import static com.paicbd.module.utils.Constants.RESPONSE_SMPP_SERVER_ENDPOINT;
import static com.paicbd.module.utils.Constants.WEBSOCKET_STATUS_ENDPOINT;
import static com.paicbd.module.utils.Constants.TYPE;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.STOPPED;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFrameHandler implements FrameHandler {
    private final SocketSession socketSession;
    private final GeneralSettingsCacheConfig generalSettingsCacheConfig;
    private final ConcurrentMap<String, SpSession> spSessionMap;
    private final JedisCluster jedisCluster;
    private final AppProperties appProperties;
    private final ConcurrentMap<Integer, String> networkIdSystemIdMap;
    private final Set<ServiceProvider> providers;
    private final ServerHandler serverHandler;

    @Override
    public void handleFrameLogic(StompHeaders headers, Object payload) {
        String systemId = payload.toString();
        String destination = headers.getDestination();
        Objects.requireNonNull(systemId, "System ID cannot be null");
        Objects.requireNonNull(destination, "Destination cannot be null");

        log.warn(destination);
        switch (destination) {
            case UPDATE_SERVICE_PROVIDER_ENDPOINT -> updateServiceProvider(systemId);
            case UPDATE_SERVER_HANDLER_ENDPOINT -> updateServerHandler();
            case SERVICE_PROVIDER_DELETED_ENDPOINT -> deleteServiceProvider(systemId);
            case GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT -> generalSettingUpdate();
            default -> log.info("Received Notification for unknown destination");
        }
    }

    private void updateServiceProvider(String systemId) {
        log.warn("Received Notification for updateServiceProvider");
        this.processUpdateServiceProvider(systemId);
        this.socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    private void updateServerHandler() {
        log.warn("Received Notification for updateServerHandler");
        this.serverHandler.manageServerHandler();
        this.socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    private void deleteServiceProvider(String systemId) {
        this.deleteSp(systemId);
        this.socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
        log.warn("Received Notification for serviceProviderDeleted");
    }

    private void generalSettingUpdate() {
        if (generalSettingsCacheConfig.updateGeneralSettings()) {
            log.info("General settings has been updates successfully");
        }
        socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    private void processUpdateServiceProvider(String nameSp) {
        log.warn("Processing updateServiceProvider for systemId {}", nameSp);
        if (this.spSessionMap == null) {
            log.error("SpSessionMap is null, cannot handle updateServiceProvider");
            return;
        }

        SpSession spSession = spSessionMap.get(nameSp);
        String serviceProviderJson = jedisCluster.hget(this.appProperties.getServiceProvidersHashName(), nameSp);

        if (serviceProviderJson == null) {
            log.error("Service provider not found in redis for systemId {}", nameSp);
            return;
        }
        ServiceProvider sp = Converter.stringToObject(serviceProviderJson, new TypeReference<>() {
        });

        //updateCurrentServiceProvider return true only if status is changed to STOPPED, another status send notification to websocket independently
        if (spSession == null) {
            spSession = new SpSession(jedisCluster, sp, appProperties);
            this.spSessionMap.put(nameSp, spSession);
            this.networkIdSystemIdMap.put(sp.getNetworkId(), sp.getSystemId());
            if (this.providers.stream().noneMatch(provider -> provider.getSystemId().equals(nameSp))) {
                this.providers.add(sp);
            }
        }

        if (spSession.updateCurrentServiceProvider(sp)) {
            log.warn("Sending notification to websocket for service provider {} on processUpdateServiceProvider", nameSp);
            this.socketSession.getStompSession().send(WEBSOCKET_STATUS_ENDPOINT, String.format("%s,%s,%s,%s", TYPE, nameSp, PARAM_UPDATE_STATUS, STOPPED));
        }

        // Replace provider in providers list
        this.providers.removeIf(provider -> provider.getSystemId().equals(nameSp));
        this.providers.add(spSession.getCurrentServiceProvider());
    }

    private void deleteSp(String systemId) {
        log.warn("Deleting service provider {}", systemId);
        SpSession spSession = spSessionMap.get(systemId);

        if (spSession != null) {
            this.networkIdSystemIdMap.remove(spSession.getCurrentServiceProvider().getNetworkId());
            spSession.getCurrentSmppSessions().forEach(session -> {
                log.warn("Unbinding session {} for service provider {}", session.getSessionId(), systemId);
                session.unbindAndClose();
            });
            spSession.autoDestroy();
            this.spSessionMap.remove(systemId);
        }

        this.jedisCluster.hdel(this.appProperties.getServiceProvidersHashName(), systemId);
        this.providers.removeIf(sp -> sp.getSystemId().equals(systemId));
    }
}
