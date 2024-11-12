package com.paicbd.module.components;

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
    private final ConcurrentMap<Integer, SpSession> spSessionMap;
    private final JedisCluster jedisCluster;
    private final AppProperties appProperties;
    private final Set<ServiceProvider> providers;
    private final ServerHandler serverHandler;

    @Override
    public void handleFrameLogic(StompHeaders headers, Object payload) {
        String networkId = payload.toString();
        String destination = headers.getDestination();
        Objects.requireNonNull(networkId, "Network ID cannot be null");
        Objects.requireNonNull(destination, "Destination cannot be null");

        switch (destination) {
            case UPDATE_SERVICE_PROVIDER_ENDPOINT -> updateServiceProvider(networkId);
            case UPDATE_SERVER_HANDLER_ENDPOINT -> updateServerHandler();
            case SERVICE_PROVIDER_DELETED_ENDPOINT -> deleteServiceProvider(networkId);
            case GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT -> generalSettingUpdate();
            default -> log.info("Received Notification for unknown destination");
        }
    }

    private void updateServiceProvider(String networkId) {
        try {
            log.warn("Received Notification for updateServiceProvider");
            Integer.parseInt(networkId);
            this.processUpdateServiceProvider(networkId);
            this.socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
        } catch (NumberFormatException e) {
            log.error("Error networkId value", e);
            throw new IllegalArgumentException();
        }
    }

    private void updateServerHandler() {
        log.warn("Received Notification for updateServerHandler");
        this.serverHandler.manageServerHandler();
        this.socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    private void deleteServiceProvider(String networkId) {
        try {
            Integer.parseInt(networkId);
            this.deleteSp(networkId);
            this.socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
            log.warn("Received Notification for serviceProviderDeleted");
        } catch (NumberFormatException e) {
            log.error("Error networkId value", e);
            throw new IllegalArgumentException();
        }
    }

    private void generalSettingUpdate() {
        if (generalSettingsCacheConfig.updateGeneralSettings()) {
            log.info("General settings has been updates successfully");
        }
        socketSession.getStompSession().send(RESPONSE_SMPP_SERVER_ENDPOINT, "OK");
    }

    private void processUpdateServiceProvider(String networkId) {
        log.warn("Processing updateServiceProvider for networkId {}", networkId);

        String serviceProviderJson = jedisCluster.hget(this.appProperties.getServiceProvidersHashName(), networkId);
        if (serviceProviderJson == null) {
            log.error("Service provider not found in redis for networkId {}", networkId);
            return;
        }

        ServiceProvider sp = Converter.stringToObject(serviceProviderJson, ServiceProvider.class);
        if (Objects.isNull(sp)) {
            throw new IllegalArgumentException("Service provider json string invalid");
        }

        SpSession spSession = spSessionMap.get(Integer.parseInt(networkId));
        //updateCurrentServiceProvider return true only if status is changed to STOPPED, another status send notification to websocket independently
        if (spSession == null) {
            spSession = new SpSession(jedisCluster, sp, appProperties);
            this.spSessionMap.put(Integer.valueOf(networkId), spSession);
        }

        if (spSession.updateCurrentServiceProvider(sp)) {
            log.warn("Sending notification to websocket for service provider {} on processUpdateServiceProvider", networkId);
            this.socketSession.getStompSession().send(WEBSOCKET_STATUS_ENDPOINT, String.format("%s,%s,%s,%s", TYPE, networkId, PARAM_UPDATE_STATUS, STOPPED));
        }

        // Replace provider in providers list
        this.providers.removeIf(provider -> String.valueOf(provider.getNetworkId()).equals(networkId));
        this.providers.add(spSession.getCurrentServiceProvider());
    }

    private void deleteSp(String networkId) {
        log.warn("Deleting service provider with networkId {}", networkId);
        SpSession spSession = spSessionMap.get(Integer.parseInt(networkId));

        if (spSession != null) {
            spSession.getCurrentSmppSessions().forEach(session -> {
                log.warn("Unbinding session {} for service provider with networkId {}", session.getSessionId(), networkId);
                session.unbindAndClose();
            });
            spSession.autoDestroy();
            this.spSessionMap.remove(Integer.parseInt(networkId));
        }

        this.jedisCluster.hdel(this.appProperties.getServiceProvidersHashName(), networkId);
        this.providers.removeIf(sp -> sp.getNetworkId() == Integer.parseInt(networkId));
    }
}
