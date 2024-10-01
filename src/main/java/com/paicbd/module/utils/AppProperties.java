package com.paicbd.module.utils;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Component
public class AppProperties {
    // Redis
    @Value("#{'${redis.cluster.nodes}'.split(',')}")
    private List<String> redisNodes = List.of("localhost:7000", "localhost:7001");

    @Value("${redis.threadPool.maxTotal}")
    private int redisMaxTotal = 20;

    @Value("${redis.threadPool.maxIdle}")
    private int redisMaxIdle = 20;

    @Value("${redis.threadPool.minIdle}")
    private int redisMinIdle = 1;

    @Value("${redis.threadPool.blockWhenExhausted}")
    private boolean redisBlockWhenExhausted = true;

    // Websocket
    @Value("${websocket.server.host}")
    private String wsHost = "localhost";

    @Value("${websocket.server.port}")
    private int wsPort = 9976;

    @Value("${websocket.server.path}")
    private String wsPath;

    @Value("${websocket.server.enabled}")
    private boolean wsEnabled = false;

    // Hashes
    @Value("${smpp.serviceProvidersHashName}")
    private String serviceProvidersHashName = "service_providers";

    @Value("${smpp.server.configurationHashName}")
    private String configurationHash = "configuration";

    @Value("${smpp.server.keyName}")
    private String serverKey = "smpp_server";

    @Value("${smpp.server.general.settings.hash}")
    private String smppGeneralSettingsHash = "general_settings";

    @Value("${smpp.server.general.settings.key}")
    private String smppGeneralSettingsKey = "smpp_http";

    @Value("${websocket.header.name}")
    private String websocketHeaderName = "Authorization";

    @Value("${websocket.header.value}")
    private String websocketHeaderValue = "Authorization";

    @Value("${websocket.server.retryInterval}")
    private int websocketRetryInterval = 10; // seconds

    @Value("${spring.application.name}")
    private String instanceName = "smpp-server";

    @Value("${server.ip}")
    private String instanceIp = "127.0.0.1";

    @Value("${server.port}")
    private String instancePort = "9908";

    @Value("${instance.initial.status}")
    private String instanceInitialStatus = "STARTED";

    @Value("${instance.protocol}")
    private String instanceProtocol = "SMPP";

    @Value("${instance.scheme}")
    private String instanceScheme = "";

    @Value("${instance.ratingRequest.apiKey}")
    private String httpRequestApiKey = "123";

    // DeliverSmQueueConsumer
    @Value("${redis.deliverSm.queue}")
    private String deliverSmQueue = "smpp_dlr";

    @Value("${queue.consumer.workers}")
    private int deliverSmWorkers = 10;

    @Value("${queue.consumer.batch.size}")
    private int deliverSmBatchSizePerWorker = 1000;

    // Message Lists
    @Value("${redis.preMessageList}")
    private String preMessageList = "PreMessage";

    @Value("${queue.smpp.messageParts}")
    private String messagePartsHash = "smpp_message_parts";

    // SMPP Server
    @Value("${smpp.server.ip}")
    private String smppServerIp = "127.0.0.1";

    @Value("${smpp.server.port}")
    private int smppServerPort = 5054;

    @Value("${smpp.server.processorDegree}")
    private int smppServerProcessorDegree = 3;

    @Value("${smpp.server.queueCapacity}")
    private int smppServerQueueCapacity = 100;

    @Value("${smpp.server.transactionTimer}")
    private int smppServerTransactionTimer = 5000;

    @Value("${smpp.server.waitForBind}")
    private int smppServerWaitForBind = 5000;
}
