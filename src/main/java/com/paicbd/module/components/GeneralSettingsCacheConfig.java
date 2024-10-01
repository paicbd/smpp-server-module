package com.paicbd.module.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.dto.GeneralSettings;
import com.paicbd.smsc.exception.RTException;
import com.paicbd.smsc.utils.Converter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralSettingsCacheConfig {
    private final JedisCluster jedisCluster;
    private final AppProperties properties;
    private GeneralSettings generalSettingsCache;

    @PostConstruct
    public void initializeGeneralSettings() {
        this.generalSettingsCache = getGeneralSettingFromRedis();
        if (Objects.isNull(this.generalSettingsCache)) {
            throw new RTException("GeneralSettings can not be null");
        }
    }

    public GeneralSettings getCurrentGeneralSettings() {
        return this.generalSettingsCache;
    }

    public boolean updateGeneralSettings() {
        var generalSettingsUpdated = getGeneralSettingFromRedis();
        if (Objects.isNull(generalSettingsUpdated)) {
            log.error("GeneralSettings is null");
            return false;
        }
        this.generalSettingsCache = generalSettingsUpdated;
        return true;
    }

    public GeneralSettings getGeneralSettingFromRedis() {
        String value = this.jedisCluster.hget(this.properties.getSmppGeneralSettingsHash(), this.properties.getSmppGeneralSettingsKey());
        TypeReference<GeneralSettings> valueTypeRef = new TypeReference<>() {};
        try {
            return Converter.stringToObject(value, valueTypeRef);
        } catch (Exception e) {
            return null;
        }
    }
}
