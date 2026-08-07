package com.fiscobcos.wallet.proxy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiscobcos.wallet.proxy.FiscoBcosProxyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = FiscoBcosProxyApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChainConfigurationTest {

    @Autowired private ProxyProperties properties;

    @Test
    void loadsDefaultChainConfiguration() {
        assertThat(properties.getAllowedMethods()).contains("getBlockNumber", "getBalance");
        assertThat(properties.getGroups()).containsKeys("example");
        assertThat(properties.requireGroup("example").getSdkConfig())
                .isEqualTo("config/fisco/example/config.toml");
    }
}

