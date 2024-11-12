package com.paicbd.module.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.module.utils.SpSession;
import com.paicbd.module.utils.StaticMethods;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.Generated;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.springframework.messaging.simp.stomp.StompSession;
import redis.clients.jedis.JedisCluster;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.paicbd.module.utils.Constants.BINDING;
import static com.paicbd.module.utils.Constants.BOUND;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_SESSIONS;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.STARTED;
import static com.paicbd.module.utils.Constants.STOPPED;
import static com.paicbd.module.utils.Constants.TYPE;
import static com.paicbd.module.utils.Constants.UNBINDING;
import static com.paicbd.module.utils.Constants.WEBSOCKET_STATUS_ENDPOINT;

@Slf4j
public class SessionStateListenerImpl implements SessionStateListener {
    private final SpSession spSession;
    private final StompSession stompSession;
    private final JedisCluster jedisCluster;
    private final GeneralSettings smppGeneralSettings;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final EnumSet<SessionState> BOUND_STATES = EnumSet.of(
            SessionState.BOUND_RX,
            SessionState.BOUND_TX,
            SessionState.BOUND_TRX
    );

    private final boolean notifyWs;
    private final ServiceProvider currentProvider;
    private final CdrProcessor cdrProcessor;

    public SessionStateListenerImpl(Integer networkId, ConcurrentMap<Integer, SpSession> spSessionMap, StompSession stompSession, JedisCluster jedisCluster, GeneralSettings smppGeneralSettings, CdrProcessor cdrProcessor) {
        this.stompSession = stompSession;
        this.jedisCluster = jedisCluster;
        this.smppGeneralSettings = smppGeneralSettings;
        this.cdrProcessor = cdrProcessor;
        this.spSession = spSessionMap.get(networkId);
        this.currentProvider = spSession.getCurrentServiceProvider();
        this.notifyWs = stompSession != null;
        log.info("SessionStateListenerImpl created for networkId {}", networkId);
    }

    @Override
    public synchronized void onStateChange(SessionState newState, SessionState oldState, Session source) {
        log.debug("SMPP session state changed from {} to {} for session {}", oldState, newState, source.getSessionId());

        if (STOPPED.equalsIgnoreCase(spSession.getCurrentServiceProvider().getStatus())) {
            log.warn("Service provider {} is stopped, ignoring state change", currentProvider.getSystemId());
            return;
        }

        if (isBoundState(newState)) {
            boundStateProcessor(source);
            this.updateOnRedis();
        } else if (newState == SessionState.CLOSED) {
            closeStateProcessor(source);
            this.updateOnRedis();
        }
    }

    private boolean isBoundState(SessionState state) {
        return BOUND_STATES.contains(state);
    }

    private void sendStompMessage(String param, String value) {
        synchronized (stompSession) {
            String message = this.message(String.valueOf(currentProvider.getNetworkId()), param, value);
            log.info(WEBSOCKET_STATUS_ENDPOINT + " -> {}", message);
            stompSession.send(WEBSOCKET_STATUS_ENDPOINT, message);
        }
    }

    private String message(String networkId, String param, String value) {
        return String.format("%s,%s,%s,%s", TYPE, networkId, param, value);
    }

    public void updateOnRedis() {
        String data = currentProvider.toString();
        //Using this to skip backslash coming from regex in redis
        data = data.replace("\\\\", "\\");
        jedisCluster.hset("service_providers", String.valueOf(currentProvider.getNetworkId()), data);
    }

    private void boundStateProcessor(Session source) {
        spSession.getCurrentSmppSessions().add(source);
        if (spSession.getCurrentServiceProvider().getCurrentBindsCount() == 0) { // First bind request
            this.currentProvider.setStatus(BINDING);
            this.waitAndSendViaSocket(PARAM_UPDATE_STATUS, BINDING);

        }

        currentProvider.setCurrentBindsCount(currentProvider.getCurrentBindsCount() + 1);
        currentProvider.getBinds().add(source.getSessionId());
        this.waitAndSendViaSocket(PARAM_UPDATE_SESSIONS, String.valueOf(currentProvider.getCurrentBindsCount()));

        if (spSession.getCurrentServiceProvider().getCurrentBindsCount() == 1) {
            this.currentProvider.setStatus(BOUND);
            this.waitAndSendViaSocket(PARAM_UPDATE_STATUS, BOUND);

            this.executorService.execute(this::handlePendingDeliverSm);
        }
    }

    private void closeStateProcessor(Session source) {
        spSession.getCurrentSmppSessions().remove(source);
        if (spSession.getCurrentServiceProvider().getCurrentBindsCount() == 1) {
            this.currentProvider.setStatus(UNBINDING);
            this.waitAndSendViaSocket(PARAM_UPDATE_STATUS, UNBINDING);
        }

        currentProvider.setCurrentBindsCount(currentProvider.getCurrentBindsCount() - 1);
        currentProvider.getBinds().remove(source.getSessionId());

        this.waitAndSendViaSocket(PARAM_UPDATE_SESSIONS, String.valueOf(currentProvider.getCurrentBindsCount()));

        if (spSession.getCurrentServiceProvider().getCurrentBindsCount() == 0) {
            this.currentProvider.setStatus(STARTED);
            this.waitAndSendViaSocket(PARAM_UPDATE_STATUS, STARTED);
        }
    }

    private void waitAndSendViaSocket(String param, String value) {
        if (notifyWs) {
            this.waitForSessionState();
            this.sendStompMessage(param, value);
        }
    }

    @Generated
    private void waitForSessionState() { // This method is used to wait for sending the next message to the websocket
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error("An error has occurred: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void handlePendingDeliverSm() {
        var key = String.valueOf(currentProvider.getNetworkId()).concat("_smpp_pending_dlr");
        long size = jedisCluster.llen(key);
        if (size < 1) {
            log.debug("The state of {} networkId is BOUND but there are no pending deliver_sm", currentProvider.getNetworkId());
            return;
        }
        var pendingDeliverSm = jedisCluster.lpop(key, (int) size);
        var deliverSmEvents = pendingDeliverSm.stream()
                .map(this::castToDeliverSmEvent)
                .filter(Objects::nonNull)
                .toList();

        deliverSmEvents.parallelStream().forEach(deliverSmEvent -> {
            var systemId = deliverSmEvent.getSystemId();
            var serverSession = (SMPPServerSession) spSession.getNextRoundRobinSession();
            if (Objects.isNull(serverSession)) {
                log.warn("No active session to send deliver_sm with id {}", deliverSmEvent.getId());
                jedisCluster.lpush(systemId.concat("_smpp_pending_dlr"), deliverSmEvent.toString());
                return;
            }
            StaticMethods.sendDeliverSm(serverSession, deliverSmEvent, smppGeneralSettings, cdrProcessor);
        });
    }

    private MessageEvent castToDeliverSmEvent(String deliverSmRaw) {
        return Converter.stringToObject(deliverSmRaw, new TypeReference<>() {
        });
    }
}
