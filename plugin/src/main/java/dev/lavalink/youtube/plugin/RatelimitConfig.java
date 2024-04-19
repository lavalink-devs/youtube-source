package dev.lavalink.youtube.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@ConfigurationProperties(prefix = "lavalink.server.ratelimit")
@Component
public class RatelimitConfig {
    private List<String> ipBlocks = Collections.emptyList();
    private List<String> excludedIps = Collections.emptyList();
    private String strategy = "RotatingNanoSwitch";
    private int retryLimit = -1;
    private boolean searchTriggersFail = true;

    public List<String> getIpBlocks() {
        return this.ipBlocks;
    }

    public List<String> getExcludedIps() {
        return this.excludedIps;
    }

    public String getStrategy() {
        return this.strategy;
    }

    public int getRetryLimit() {
        return this.retryLimit;
    }

    public boolean getSearchTriggersFail() {
        return this.searchTriggersFail;
    }

    public void setIpBlocks(List<String> ipBlocks) {
        this.ipBlocks = ipBlocks;
    }

    public void setExcludedIps(List<String> excludedIps) {
        this.excludedIps = excludedIps;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public void setSearchTriggersFail(boolean searchTriggersFail) {
        this.searchTriggersFail = searchTriggersFail;
    }
}
