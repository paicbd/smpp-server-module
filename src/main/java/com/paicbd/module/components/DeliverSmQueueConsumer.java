package com.paicbd.module.components;

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
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.JedisCluster;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliverSmQueueConsumer {
    private final AtomicInteger redisCounterPerSecond = new AtomicInteger(0);
    private final AtomicInteger deliverSmCounterPerSecond = new AtomicInteger(0);

    private final JedisCluster jedisCluster;
    private final CdrProcessor cdrProcessor;
    private final AppProperties appProperties;
    private final ConcurrentMap<Integer, SpSession> spSessionMap;
    private final GeneralSettingsCacheConfig generalSettingsCacheConfig;

    @PostConstruct
    private void startQueueProcessing() {
        log.warn("Starting DeliverSmQueueConsumer with {} workers", appProperties.getDeliverSmWorkers());
        Thread.startVirtualThread(() -> new Watcher("RedisWatcher", redisCounterPerSecond, 1));
        Thread.startVirtualThread(() -> new Watcher("DeliverSmWatcher", deliverSmCounterPerSecond, 1));
    }

    @Async
    @Scheduled(fixedDelayString = "${queue.consumer.scheduler}")
    public void startScheduler() {
        Flux.range(0, appProperties.getDeliverSmWorkers())
                .flatMap(worker -> Flux.defer(this::queueProcessingBatchThread).subscribeOn(Schedulers.boundedElastic()))
                .subscribe();
    }

    private Flux<Void> queueProcessingBatchThread() {
        return Flux.fromIterable(pullDeliverSmRawListFromRedis())
                .parallel(appProperties.getDeliverSmWorkers())
                .runOn(Schedulers.parallel())
                .flatMap(this::processDeliverSm)
                .sequential();
    }

    private List<String> pullDeliverSmRawListFromRedis() {
        List<String> list = jedisCluster.lpop(appProperties.getDeliverSmQueue(),
                appProperties.getDeliverSmBatchSizePerWorker());
        if (Objects.nonNull(list)) {
            redisCounterPerSecond.getAndAdd(list.size());
            return list;
        }
        return Collections.emptyList();
    }

    private Flux<Void> processDeliverSm(String deliverSmRaw) {
        return Flux.defer(() -> {
            try {
                log.debug("Processing deliver_sm {}", deliverSmRaw);
                MessageEvent deliverSmEvent = Converter.stringToObject(deliverSmRaw, MessageEvent.class);
                if (Objects.isNull(deliverSmEvent)) {
                    return Flux.empty();
                }

                int networkId = deliverSmEvent.getDestNetworkId();
                SpSession spSession = spSessionMap.get(networkId);
                if (Objects.isNull(spSession)) {
                    log.warn("No session found for service provider with network id {}, putting deliver_sm in queue for later processing", networkId);
                    jedisCluster.lpush(String.valueOf(networkId).concat("_smpp_pending_dlr"), deliverSmRaw);
                    return Flux.empty();
                }

                SMPPServerSession serverSession = (SMPPServerSession) spSession.getNextRoundRobinSession();
                if (serverSession != null) {
                    GeneralSettings smppGeneralSettings = generalSettingsCacheConfig.getCurrentGeneralSettings();
                    StaticMethods.sendDeliverSm(serverSession, deliverSmEvent, smppGeneralSettings, cdrProcessor);
                    deliverSmCounterPerSecond.getAndIncrement();
                } else {
                    jedisCluster.lpush(String.valueOf(networkId).concat("_smpp_pending_dlr"), deliverSmRaw);
                    log.warn("No active session to send deliver_sm with id {}", deliverSmEvent.getId());
                }
                return Flux.empty();
            } catch (Exception e) {
                log.error("Error on process deliverSm {} on method processDeliverSm", e.getMessage());
                throw new RTException("Error on process deliverSm");
            }
        });
    }
}
