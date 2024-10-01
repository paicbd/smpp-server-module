package com.paicbd.module.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.SpSession;
import com.paicbd.module.utils.StaticMethods;
import com.paicbd.smsc.exception.RTException;
import com.paicbd.smsc.utils.Watcher;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.utils.Converter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.session.SMPPServerSession;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliverSmQueueConsumer {
    private final JedisCluster jedisCluster;
    private final CdrProcessor cdrProcessor;
    private final AppProperties appProperties;
    private final ConcurrentMap<String, SpSession> spSessionMap;
    private final ConcurrentMap<Integer, String> networkIdSystemIdMap;
    private final GeneralSettingsCacheConfig generalSettingsCacheConfig;
    private final AtomicInteger requestPerSecond = new AtomicInteger(0);
    private final ThreadFactory factory = Thread.ofVirtual().name("deliverSm-", 0).factory();
    private final ExecutorService executorService = Executors.newThreadPerTaskExecutor(factory);


    @PostConstruct
    private void startQueueProcessing() {
        log.warn("Starting DeliverSmQueueConsumer with {} workers", appProperties.getDeliverSmWorkers());
        Thread.startVirtualThread(() -> new Watcher("DeliverSmWatcher", requestPerSecond, 1));
    }

    @Async
    @Scheduled(fixedDelayString = "${queue.consumer.scheduler}")
    public void startScheduler() {
        IntStream.range(0, appProperties.getDeliverSmWorkers())
                .parallel()
                .forEach(index -> executorService.execute(this.queueProcessingBatchThread()));
    }

    private Runnable queueProcessingBatchThread() {
        return () -> {
            List<String> deliverSmItems = jedisCluster.lpop(appProperties.getDeliverSmQueue(), appProperties.getDeliverSmBatchSizePerWorker());
            if (deliverSmItems != null) {
                deliverSmItems.parallelStream().forEach(deliverSmItemRaw -> {
                    requestPerSecond.getAndIncrement();
                    processDeliverSm(deliverSmItemRaw);
                });
            }
        };
    }

    private void processDeliverSm(String deliverSmRaw) {
        try {
            log.debug("Processing deliver_sm {}", deliverSmRaw);

            TypeReference<MessageEvent> valueTypeRef = new TypeReference<>() {
            };
            MessageEvent deliverSmEvent = Converter.stringToObject(deliverSmRaw, valueTypeRef);

            boolean isMessage = Objects.isNull(deliverSmEvent.getSystemId());
            if (isMessage) {
                deliverSmEvent.setSystemId(networkIdSystemIdMap.get(deliverSmEvent.getDestNetworkId()));
            }

            var systemId = deliverSmEvent.getSystemId();
            var spSession = spSessionMap.get(systemId);
            if (Objects.isNull(spSession)) {
                log.warn("No session found for systemId {}", systemId);
                jedisCluster.lpush(systemId.concat("_smpp_pending_dlr"), deliverSmRaw);
                return;
            }

            var serverSession = (SMPPServerSession) spSession.getNextRoundRobinSession();
            if (serverSession != null) {
                GeneralSettings smppGeneralSettings = generalSettingsCacheConfig.getCurrentGeneralSettings();
                StaticMethods.sendDeliverSm(serverSession, deliverSmEvent, smppGeneralSettings, cdrProcessor);
            } else {
                jedisCluster.lpush(systemId.concat("_smpp_pending_dlr"), deliverSmRaw);
                log.warn("No active session to send deliver_sm with id {}", deliverSmEvent.getId());
            }
        } catch (Exception e) {
            log.error("Error on process deliverSm {} on method processDeliverSm", e.getMessage());
            throw new RTException("Error on process deliverSm");
        }
    }
}
