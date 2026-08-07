package com.fiscobcos.wallet.proxy.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    private int maxBatchSize = 20;
    private long maxRequestBytes = 2 * 1024 * 1024;
    private Set<String> allowedMethods = new LinkedHashSet<>();
    private Set<String> passthroughMethods = new LinkedHashSet<>();
    private Map<String, Group> groups = new LinkedHashMap<>();

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public long getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(Set<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public Set<String> getPassthroughMethods() {
        return passthroughMethods;
    }

    public void setPassthroughMethods(Set<String> passthroughMethods) {
        this.passthroughMethods = passthroughMethods;
    }

    public Map<String, Group> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, Group> groups) {
        this.groups = groups;
    }

    public Group requireGroup(String name) {
        Group group = groups.get(name);
        if (group == null) {
            throw new IllegalStateException("Missing proxy.groups." + name + " configuration");
        }
        return group;
    }

    public Set<String> allowedMethodsFor(Group group) {
        return group.getAllowedMethods() == null ? allowedMethods : group.getAllowedMethods();
    }

    public Set<String> passthroughMethodsFor(Group group) {
        return group.getPassthroughMethods() == null
                ? passthroughMethods
                : group.getPassthroughMethods();
    }

    public static class Group {

        private boolean enabled = true;
        private boolean gm;
        private String groupId;
        private String sdkConfig;
        private Set<String> allowedMethods;
        private Set<String> passthroughMethods;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isGm() {
            return gm;
        }

        public void setGm(boolean gm) {
            this.gm = gm;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getSdkConfig() {
            return sdkConfig;
        }

        public void setSdkConfig(String sdkConfig) {
            this.sdkConfig = sdkConfig;
        }

        public Set<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(Set<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public Set<String> getPassthroughMethods() {
            return passthroughMethods;
        }

        public void setPassthroughMethods(Set<String> passthroughMethods) {
            this.passthroughMethods = passthroughMethods;
        }
    }
}

