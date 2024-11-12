package com.paicbd.module.server;

import com.paicbd.module.components.GeneralSettingsCacheConfig;
import com.paicbd.module.components.ServerHandler;
import com.paicbd.module.utils.SpSession;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.UtilsEnum;
import com.paicbd.smsc.utils.Watcher;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.PDUStringException;
import org.jsmpp.SMPPConstant;
import org.jsmpp.session.SMPPServerSessionListener;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.connection.socket.ServerSocketConnectionFactory;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static com.paicbd.module.utils.Constants.STOPPED;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmppServer implements Runnable {
    private static final AtomicInteger requestCounter = new AtomicInteger();

    private final JedisCluster jedisCluster;
    private final CdrProcessor cdrProcessor;
    private final SocketSession socketSession;
    private final ServerHandler serverHandler;
    private final AppProperties appProperties;
    private final Set<ServiceProvider> providers;
    private final ConcurrentMap<Integer, SpSession> spSessionMap;
    private final GeneralSettingsCacheConfig generalSettingsCacheConfig;
    private final ThreadFactory factory = Thread.ofVirtual().name("server_session-", 0).factory();
    private final ExecutorService execService = Executors.newThreadPerTaskExecutor(factory);

    @PostConstruct
    public void init() {
        log.warn("Starting SmppServer with processorDegree {}, queueCapacity {}", appProperties.getSmppServerProcessorDegree(), appProperties.getSmppServerQueueCapacity());
        this.loadServiceProviders();
        Thread.startVirtualThread(this);
        Thread.startVirtualThread(() -> new Watcher("SMPPWatcher", requestCounter, 1));
    }

    @Override
    public void run() {
        boolean isRunning = true;
        try {
            var sessionListener = createListener();
            while (isRunning) {
                isRunning = manageSession(sessionListener);
            }
        } catch (IOException e) {
            log.error("IO error occurred", e);
        }
    }

    private SMPPServerSessionListener createListener() throws IOException {
        var connectionFactory = new ServerSocketConnectionFactory();
        InetAddress hostAddress = InetAddress.getByName(appProperties.getSmppServerIp());
        var sessionListener = new SMPPServerSessionListener(hostAddress, appProperties.getSmppServerPort(), connectionFactory);
        log.warn("Listening on port {} using processorDegree {},  queueCapacity {}",
                appProperties.getSmppServerPort(),
                appProperties.getSmppServerProcessorDegree(),
                appProperties.getSmppServerQueueCapacity());
        sessionListener.setPduProcessorDegree(appProperties.getSmppServerProcessorDegree());
        sessionListener.setQueueCapacity(appProperties.getSmppServerQueueCapacity());
        return sessionListener;
    }

    private boolean manageSession(SMPPServerSessionListener sessionListener) {
        boolean isRunning = true;
        SMPPServerSession serverSession;
        try {
            serverSession = sessionListener.accept();
            serverSession.setTransactionTimer(appProperties.getSmppServerTransactionTimer());
            log.info("Accepted connection with session {}", serverSession.getSessionId());
            Future<Boolean> bindResultFuture = execService.submit(
                    new WaitBindTask(
                            serverSession, appProperties.getSmppServerWaitForBind(),
                            providers, spSessionMap, jedisCluster,
                            socketSession.getStompSession(), serverHandler,
                            appProperties, generalSettingsCacheConfig, cdrProcessor
                    ));

            boolean isBound = bindResultFuture.get();
            if (isBound) {
                log.info("The session is now in state {}", serverSession.getSessionState());
            } else {
                log.warn("Closing session {} not bound", serverSession.getSessionId());
                serverSession.close();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted WaitBind  task: {}", e.getMessage());
            Thread.currentThread().interrupt();
            isRunning = false;
        } catch (ExecutionException e) {
            log.error("Exception on execute WaitBind task: {}", e.getMessage());
            isRunning = false;
        } catch (IOException e) {
            log.error("IO error occurred while managing session", e);
            isRunning = false;
        }
        return isRunning;
    }

    private record WaitBindTask(
            SMPPServerSession serverSession,
            long timeout,
            Set<ServiceProvider> providers,
            ConcurrentMap<Integer, SpSession> spSessionMap,
            JedisCluster jedisCluster,
            StompSession wsSession,
            ServerHandler serverHandler,
            AppProperties properties,
            GeneralSettingsCacheConfig generalSettingsCacheConfig,
            CdrProcessor cdrProcessor
    ) implements Callable<Boolean> {
        @Override
        public Boolean call() {
            try {
                if (serverHandler.getState().equalsIgnoreCase(STOPPED)) {
                    log.warn("Server is not started, rejecting bind request");
                    return false;
                }

                BindRequest bindRequest = serverSession.waitForBind(timeout);
                return processBindRequest(bindRequest);
            } catch (IllegalStateException e) {
                log.error("System error", e);
            } catch (TimeoutException e) {
                log.warn("Wait for bind has reached timeout", e);
            } catch (IOException e) {
                log.error("Failed accepting bind request for session {}", serverSession.getSessionId());
            }
            serverSession.close();
            return false;
        }

        private boolean processBindRequest(BindRequest bindRequest) throws IOException {
            try {
                Optional<ServiceProvider> serviceProviderOpt = providers.stream()
                        .filter(provider -> provider.getSystemId().equals(bindRequest.getSystemId()))
                        .findFirst();

                if (serviceProviderOpt.isEmpty()) {
                    bindRequest.reject(SMPPConstant.STAT_ESME_RINVSYSID);
                    return false;
                }

                ServiceProvider currentProvider = serviceProviderOpt.get();
                if (currentProvider.getStatus().equals(STOPPED)) {
                    log.info("Provider {} is not available, please try again later", currentProvider.getSystemId());
                    bindRequest.reject(SMPPConstant.STAT_ESME_RSYSERR);
                    return false;
                }

                if (!currentProvider.getPassword().equals(bindRequest.getPassword())) {
                    bindRequest.reject(SMPPConstant.STAT_ESME_RINVPASWD);
                    return false;
                }

                if (currentProvider.getCurrentBindsCount() >= currentProvider.getMaxBinds()) {
                    bindRequest.reject(SMPPConstant.STAT_ESME_RBINDFAIL);
                    return false;
                }

                if (!UtilsEnum.getBindType(currentProvider.getBindType()).equals(bindRequest.getBindType())) {
                    bindRequest.reject(SMPPConstant.STAT_ESME_RBINDFAIL);
                }

                log.warn("Bind request received for {} provider", currentProvider.getSystemId());
                SpSession currentSpSession = spSessionMap.computeIfAbsent(
                        currentProvider.getNetworkId(),
                        key -> new SpSession(jedisCluster, currentProvider, properties)
                );

                log.info("Accepting bind for session {}, interface version {}", serverSession.getSessionId(), bindRequest.getInterfaceVersion());
                serverSession.setMessageReceiverListener(
                        new ServerMessageReceiverListenerImpl(
                                requestCounter,
                                currentSpSession,
                                generalSettingsCacheConfig,
                                properties, cdrProcessor,
                                new MultiPartsHandler(cdrProcessor, currentSpSession, properties)
                        )
                );
                var smppGeneralSettings = generalSettingsCacheConfig.getCurrentGeneralSettings();
                serverSession.addSessionStateListener(
                        new SessionStateListenerImpl(
                                currentProvider.getNetworkId(),
                                spSessionMap,
                                wsSession,
                                jedisCluster,
                                smppGeneralSettings,
                                cdrProcessor));
                bindRequest.accept(currentProvider.getSystemId(), bindRequest.getInterfaceVersion());

                return true;
            } catch (PDUStringException | IOException e) {
                log.error("Invalid system id", e);
                bindRequest.reject(SMPPConstant.STAT_ESME_RSYSERR);
            }
            serverSession.close();
            return false;
        }
    }


    public void loadServiceProviders() {
        var redisProviders = jedisCluster.hgetAll(appProperties.getServiceProvidersHashName());
        List<ServiceProvider> providersToAdd = new ArrayList<>();

        redisProviders.entrySet().parallelStream().forEach(entry -> {
            try {
                String data = String.valueOf(entry.getValue());
                //Using this to skip backslash coming from regex in redis
                data = data.replace("\\", "\\\\");
                ServiceProvider sp = Converter.stringToObject(data, ServiceProvider.class);
                Objects.requireNonNull(sp, "Service provider cannot be null");
                var existingSp = this.providers.stream()
                        .filter(x -> x.getSystemId().equals(sp.getSystemId()))
                        .findFirst()
                        .orElse(null);

                if (existingSp == null && "smpp".equalsIgnoreCase(sp.getProtocol())) {
                    providersToAdd.add(sp);
                }
            } catch (Exception e) {
                log.error("Error loading service provider", e);
            }
        });

        if (!providersToAdd.isEmpty()) {
            this.providers.addAll(providersToAdd);
        }
    }
}