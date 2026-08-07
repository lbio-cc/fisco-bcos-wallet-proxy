package com.fiscobcos.wallet.proxy;

import com.fiscobcos.wallet.proxy.config.ProxyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProxyProperties.class)
public class FiscoBcosProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FiscoBcosProxyApplication.class, args);
    }
}

