package com.streaming.engine.red5;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "red5")
public class Red5Properties {

    private String mode = "http";
    private boolean stubEnabled = false;
    private boolean enforceHttps = true;
    private String baseUrl = "";
    private String startPath = "/stream-engine/start";
    private String stopPath = "/stream-engine/stop";
    private String secretHeader = "X-Stream-Engine-Secret";
    private String sharedSecret = "";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isStubEnabled() {
        return stubEnabled;
    }

    public void setStubEnabled(boolean stubEnabled) {
        this.stubEnabled = stubEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnforceHttps() {
        return enforceHttps;
    }

    public void setEnforceHttps(boolean enforceHttps) {
        this.enforceHttps = enforceHttps;
    }

    public String getStartPath() {
        return startPath;
    }

    public void setStartPath(String startPath) {
        this.startPath = startPath;
    }

    public String getStopPath() {
        return stopPath;
    }

    public void setStopPath(String stopPath) {
        this.stopPath = stopPath;
    }

    public String getSecretHeader() {
        return secretHeader;
    }

    public void setSecretHeader(String secretHeader) {
        this.secretHeader = secretHeader;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
