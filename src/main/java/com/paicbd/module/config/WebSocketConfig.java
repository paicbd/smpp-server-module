package com.paicbd.module.config;

import com.paicbd.module.components.CustomFrameHandler;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Generated;
import com.paicbd.smsc.ws.SocketClient;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.paicbd.module.utils.Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_SERVER_HANDLER_ENDPOINT;
import static com.paicbd.module.utils.Constants.SERVICE_PROVIDER_DELETED_ENDPOINT;
import static com.paicbd.module.utils.Constants.GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT;

@Slf4j
@Generated
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {
    private final AppProperties appProperties;
    private final SocketSession socketSession;
    private final CustomFrameHandler customFrameHandler;

    @Bean
    public SocketClient socketClient() {
        List<String> topicsToSubscribe = List.of(
                UPDATE_SERVICE_PROVIDER_ENDPOINT,
                UPDATE_SERVER_HANDLER_ENDPOINT,
                SERVICE_PROVIDER_DELETED_ENDPOINT,
                GENERAL_SETTINGS_SMPP_HTTP_ENDPOINT
        );

        UtilsRecords.WebSocketConnectionParams wsp = new UtilsRecords.WebSocketConnectionParams(
                appProperties.isWsEnabled(),
                appProperties.getWsHost(),
                appProperties.getWsPort(),
                appProperties.getWsPath(),
                topicsToSubscribe,
                appProperties.getWebsocketHeaderName(),
                appProperties.getWebsocketHeaderValue(),
                appProperties.getWebsocketRetryInterval(),
                "SMPP-SERVER" // Current SMSC Module
        );

        return new SocketClient(customFrameHandler, wsp, socketSession);
    }
}
