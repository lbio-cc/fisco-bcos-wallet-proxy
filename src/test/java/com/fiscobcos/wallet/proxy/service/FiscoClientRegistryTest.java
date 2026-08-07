package com.fiscobcos.wallet.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiscobcos.wallet.proxy.config.ProxyProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FiscoClientRegistryTest {

    @Test
    void throwsWhenGroupIsNotConfigured() {
        ProxyProperties properties = new ProxyProperties();
        FiscoClientRegistry registry = new FiscoClientRegistry(properties);

        assertThatThrownBy(() -> registry.get("missing-group"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing proxy.groups.missing-group");
    }

    @Test
    void throwsWhenGroupIsDisabled() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setEnabled(false);
        group.setGroupId("group0");
        group.setSdkConfig("config/fisco/local/config.toml");

        ProxyProperties properties = new ProxyProperties();
        properties.setGroups(Map.of("group0", group));
        FiscoClientRegistry registry = new FiscoClientRegistry(properties);

        assertThatThrownBy(() -> registry.get("group0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Proxy group is disabled: group0");
    }

    @Test
    void throwsWhenGroupIdIsMissing() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setSdkConfig("config/fisco/local/config.toml");

        ProxyProperties properties = new ProxyProperties();
        properties.setGroups(Map.of("group0", group));
        FiscoClientRegistry registry = new FiscoClientRegistry(properties);

        assertThatThrownBy(() -> registry.get("group0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Group ID is empty for proxy.groups.group0.group-id");
    }

    @Test
    void throwsWhenSdkConfigPathIsBlank() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("group0");

        ProxyProperties properties = new ProxyProperties();
        properties.setGroups(Map.of("group0", group));
        FiscoClientRegistry registry = new FiscoClientRegistry(properties);

        assertThatThrownBy(() -> registry.get("group0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SDK config path is empty for group: group0");
    }

    @Test
    void throwsWhenSdkConfigFileDoesNotExist() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("group0");
        group.setSdkConfig("config/fisco/does-not-exist/config.toml");

        ProxyProperties properties = new ProxyProperties();
        properties.setGroups(Map.of("group0", group));
        FiscoClientRegistry registry = new FiscoClientRegistry(properties);

        assertThatThrownBy(() -> registry.get("group0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SDK config does not exist");
    }

    @Test
    void shutdownIsSafeWhenNoSdkHasBeenCreated() {
        ProxyProperties properties = new ProxyProperties();
        FiscoClientRegistry registry = new FiscoClientRegistry(properties);

        assertThat(registry).isNotNull();
        registry.shutdown();
    }
}

