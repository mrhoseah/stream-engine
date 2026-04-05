package com.streaming.engine.recastly;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recastly")
public class RecastlyProperties {

    private boolean enabled = false;
    private boolean enforceHttps = true;
    private boolean inboundAuthRequired = true;
    private boolean inboundSignatureRequired = true;
    private String baseUrl = "";
    private String sharedSecret = "";
    private String secretHeader = "X-Stream-Engine-Secret";
    private String inboundTimestampHeader = "X-Stream-Engine-Timestamp";
    private String inboundSignatureHeader = "X-Stream-Engine-Signature";
    private String inboundNonceHeader = "X-Stream-Engine-Nonce";
    private String inboundBodyHashHeader = "X-Stream-Engine-Body-SHA256";
    private int inboundMaxSkewSec = 300;
    private int inboundReplayWindowSec = 600;
    private int inboundRateLimitPerMinute = 120;
    private String inboundAllowedIps = "";
    private boolean inboundTrustForwardedFor = false;
    private String inboundTrustedProxyIps = "";
    private String validatePath = "/stream-engine/validate-key";
    private String webhookPath = "/stream-engine/webhook";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isInboundAuthRequired() {
        return inboundAuthRequired;
    }

    public void setInboundAuthRequired(boolean inboundAuthRequired) {
        this.inboundAuthRequired = inboundAuthRequired;
    }

    public boolean isEnforceHttps() {
        return enforceHttps;
    }

    public void setEnforceHttps(boolean enforceHttps) {
        this.enforceHttps = enforceHttps;
    }

    public boolean isInboundSignatureRequired() {
        return inboundSignatureRequired;
    }

    public void setInboundSignatureRequired(boolean inboundSignatureRequired) {
        this.inboundSignatureRequired = inboundSignatureRequired;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getSecretHeader() {
        return secretHeader;
    }

    public void setSecretHeader(String secretHeader) {
        this.secretHeader = secretHeader;
    }

    public String getInboundTimestampHeader() {
        return inboundTimestampHeader;
    }

    public void setInboundTimestampHeader(String inboundTimestampHeader) {
        this.inboundTimestampHeader = inboundTimestampHeader;
    }

    public String getInboundSignatureHeader() {
        return inboundSignatureHeader;
    }

    public void setInboundSignatureHeader(String inboundSignatureHeader) {
        this.inboundSignatureHeader = inboundSignatureHeader;
    }

    public String getInboundNonceHeader() {
        return inboundNonceHeader;
    }

    public void setInboundNonceHeader(String inboundNonceHeader) {
        this.inboundNonceHeader = inboundNonceHeader;
    }

    public String getInboundBodyHashHeader() {
        return inboundBodyHashHeader;
    }

    public void setInboundBodyHashHeader(String inboundBodyHashHeader) {
        this.inboundBodyHashHeader = inboundBodyHashHeader;
    }

    public int getInboundMaxSkewSec() {
        return inboundMaxSkewSec;
    }

    public void setInboundMaxSkewSec(int inboundMaxSkewSec) {
        this.inboundMaxSkewSec = inboundMaxSkewSec;
    }

    public int getInboundReplayWindowSec() {
        return inboundReplayWindowSec;
    }

    public void setInboundReplayWindowSec(int inboundReplayWindowSec) {
        this.inboundReplayWindowSec = inboundReplayWindowSec;
    }

    public int getInboundRateLimitPerMinute() {
        return inboundRateLimitPerMinute;
    }

    public void setInboundRateLimitPerMinute(int inboundRateLimitPerMinute) {
        this.inboundRateLimitPerMinute = inboundRateLimitPerMinute;
    }

    public String getInboundAllowedIps() {
        return inboundAllowedIps;
    }

    public void setInboundAllowedIps(String inboundAllowedIps) {
        this.inboundAllowedIps = inboundAllowedIps;
    }

    public boolean isInboundTrustForwardedFor() {
        return inboundTrustForwardedFor;
    }

    public void setInboundTrustForwardedFor(boolean inboundTrustForwardedFor) {
        this.inboundTrustForwardedFor = inboundTrustForwardedFor;
    }

    public String getInboundTrustedProxyIps() {
        return inboundTrustedProxyIps;
    }

    public void setInboundTrustedProxyIps(String inboundTrustedProxyIps) {
        this.inboundTrustedProxyIps = inboundTrustedProxyIps;
    }

    public String getValidatePath() {
        return validatePath;
    }

    public void setValidatePath(String validatePath) {
        this.validatePath = validatePath;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
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
