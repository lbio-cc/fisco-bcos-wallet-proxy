package com.fiscobcos.wallet.proxy.service;

import com.fiscobcos.wallet.proxy.config.ProxyProperties;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.ClientImpl;
import org.fisco.bcos.sdk.v3.config.Config;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.exceptions.ConfigException;
import org.fisco.bcos.sdk.v3.contract.precompiled.balance.BalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FiscoClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(FiscoClientRegistry.class);

    private final ProxyProperties properties;
    private final ConcurrentMap<String, BcosSDK> sdks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClientImpl> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BalanceService> balanceServices = new ConcurrentHashMap<>();

    public FiscoClientRegistry(ProxyProperties properties) {
        this.properties = properties;
    }

    public ClientImpl get(String groupName) {
        return clients.computeIfAbsent(groupName, this::createClient);
    }

    public BalanceService getBalanceService(String groupName) {
        return balanceServices.computeIfAbsent(groupName, this::createBalanceService);
    }

    private BalanceService createBalanceService(String groupName) {
        ClientImpl client = get(groupName);
        return new BalanceService(client, client.getCryptoSuite().getCryptoKeyPair());
    }

    private ClientImpl createClient(String groupName) {
        ProxyProperties.Group groupConfig = properties.requireGroup(groupName);
        String groupId = requireGroupId(groupName, groupConfig);
        BcosSDK sdk = sdks.computeIfAbsent(groupName, this::createSdk);
        Client client = sdk.getClient(groupId);
        if (!(client instanceof ClientImpl clientImpl)) {
            throw new IllegalStateException(
                    "Unsupported FISCO BCOS Client implementation: " + client.getClass().getName());
        }
        return clientImpl;
    }

    private BcosSDK createSdk(String groupName) {
        ProxyProperties.Group groupConfig = properties.requireGroup(groupName);
        if (!groupConfig.isEnabled()) {
            throw new IllegalStateException("Proxy group is disabled: " + groupName);
        }
        if (groupConfig.getSdkConfig() == null || groupConfig.getSdkConfig().isBlank()) {
            throw new IllegalStateException("SDK config path is empty for group: " + groupName);
        }

        Path configPath = Path.of(groupConfig.getSdkConfig()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("SDK config does not exist: " + configPath);
        }
        try {
            ConfigOption sdkConfig = Config.load(configPath.toString());
            boolean sdkUsesGm = sdkConfig.getCryptoMaterialConfig().isUseSmCrypto();
            if (sdkUsesGm != groupConfig.isGm()) {
                throw new IllegalStateException(
                        "GM setting mismatch for group "
                                + groupName
                                + ": proxy.groups."
                                + groupName
                                + ".gm="
                                + groupConfig.isGm()
                                + ", but SDK useSMCrypto="
                                + sdkUsesGm);
            }
            return new BcosSDK(sdkConfig);
        } catch (ConfigException e) {
            throw new IllegalStateException(
                    "Unable to load SDK config for group "
                            + groupName
                            + " (groupId="
                            + groupConfig.getGroupId()
                            + "): "
                            + configPath,
                    e);
        }
    }

    private String requireGroupId(String groupName, ProxyProperties.Group groupConfig) {
        if (groupConfig.getGroupId() == null || groupConfig.getGroupId().isBlank()) {
            throw new IllegalStateException(
                    "Group ID is empty for proxy.groups." + groupName + ".group-id");
        }
        return groupConfig.getGroupId();
    }

    @PreDestroy
    public void shutdown() {
        sdks.forEach(
                (groupName, sdk) -> {
                    try {
                        sdk.stopAll();
                    } catch (RuntimeException e) {
                        log.warn("Failed to stop FISCO BCOS SDK for group {}", groupName, e);
                    }
                });
    }
}

