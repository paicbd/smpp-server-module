package com.paicbd.module.utils;

import com.paicbd.smsc.utils.Generated;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Generated
@Component
public class AppProperties {
    // Redis
    @Value("#{'${redis.cluster.nodes}'.split(',')}")
    private List<String> redisNodes;

    @Value("${redis.threadPool.maxTotal}")
    private int redisMaxTotal;

    @Value("${redis.threadPool.maxIdle}")
    private int redisMaxIdle;

    @Value("${redis.threadPool.minIdle}")
    private int redisMinIdle;

    @Value("${redis.threadPool.blockWhenExhausted}")
    private boolean redisBlockWhenExhausted;

    // Websocket
    @Value("${websocket.server.host}")
    private String wsHost;

    @Value("${websocket.server.port}")
    private int wsPort;

    @Value("${websocket.server.path}")
    private String wsPath;

    @Value("${websocket.server.enabled}")
    private boolean wsEnabled;

    // Hashes
    @Value("${smpp.serviceProvidersHashName}")
    private String serviceProvidersHashName;

    @Value("${smpp.server.configurationHashName}")
    private String configurationHash;

    @Value("${smpp.server.keyName}")
    private String serverKey;

    @Value("${smpp.server.general.settings.hash}")
    private String smppGeneralSettingsHash;

    @Value("${smpp.server.general.settings.key}")
    private String smppGeneralSettingsKey;

    @Value("${websocket.header.name}")
    private String websocketHeaderName;

    @Value("${websocket.header.value}")
    private String websocketHeaderValue;

    @Value("${websocket.server.retryInterval}")
    private int websocketRetryInterval; // seconds

    @Value("${spring.application.name}")
    private String instanceName;

    @Value("${server.ip}")
    private String instanceIp;

    @Value("${server.port}")
    private String instancePort;

    @Value("${instance.initial.status}")
    private String instanceInitialStatus;

    @Value("${instance.protocol}")
    private String instanceProtocol;

    @Value("${instance.scheme}")
    private String instanceScheme;

    @Value("${instance.ratingRequest.apiKey}")
    private String httpRequestApiKey;

    // DeliverSmQueueConsumer
    @Value("${redis.deliverSm.queue}")
    private String deliverSmQueue;

    @Value("${queue.consumer.workers}")
    private int deliverSmWorkers;

    @Value("${queue.consumer.batch.size}")
    private int deliverSmBatchSizePerWorker;

    // Message Lists
    @Value("${redis.preMessageList}")
    private String preMessageList;

    @Value("${queue.smpp.messageParts}")
    private String messagePartsHash;

    // SMPP Server
    @Value("${smpp.server.ip}")
    private String smppServerIp;

    @Value("${smpp.server.port}")
    private int smppServerPort;

    @Value("${smpp.server.processorDegree}")
    private int smppServerProcessorDegree;

    @Value("${smpp.server.queueCapacity}")
    private int smppServerQueueCapacity;

    @Value("${smpp.server.transactionTimer}")
    private int smppServerTransactionTimer;

    @Value("${smpp.server.waitForBind}")
    private int smppServerWaitForBind;
}
