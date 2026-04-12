package com.streaming.engine.api;

import com.streaming.engine.distributed.NonceStore;
import com.streaming.engine.recastly.RecastlyClient;
import com.streaming.engine.recastly.RecastlyProperties;
import jakarta.servlet.http.HttpServletRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class InboundSecurityService {

    private static final Logger log = LoggerFactory.getLogger(InboundSecurityService.class);
    private static final String HMAC_ALG = "HmacSHA256";

    private final RecastlyClient recastlyClient;
    private final RecastlyProperties properties;
    private final NonceStore nonceStore;
    private final Counter allowedCounter;
    private final Counter deniedRateLimitCounter;
    private final Counter deniedIpCounter;
    private final Counter deniedSecretCounter;
    private final Counter deniedSignatureCounter;
    private final Counter deniedReplayCounter;
    private final Map<String, WindowCounter> rateCounters = new ConcurrentHashMap<>();

    public InboundSecurityService(
            RecastlyClient recastlyClient,
            RecastlyProperties properties,
            NonceStore nonceStore,
            MeterRegistry meterRegistry
    ) {
        this.recastlyClient = recastlyClient;
        this.properties = properties;
        this.nonceStore = nonceStore;
        this.allowedCounter = meterRegistry.counter("stream_engine.inbound_auth.allowed");
        this.deniedRateLimitCounter = meterRegistry.counter("stream_engine.inbound_auth.denied", "reason", "rate_limit");
        this.deniedIpCounter = meterRegistry.counter("stream_engine.inbound_auth.denied", "reason", "ip_not_allowed");
        this.deniedSecretCounter = meterRegistry.counter("stream_engine.inbound_auth.denied", "reason", "secret_mismatch");
        this.deniedSignatureCounter = meterRegistry.counter("stream_engine.inbound_auth.denied", "reason", "signature");
        this.deniedReplayCounter = meterRegistry.counter("stream_engine.inbound_auth.denied", "reason", "replay");
    }

    public AuthResult authorize(HttpServletRequest request) {
        String ip = clientIp(request);
        String secret = inboundSecret(request);
        if (!withinRateLimit(ip)) {
            log.warn("Inbound auth denied reason=rate_limit path={} ip={}", request.getRequestURI(), ip);
            deniedRateLimitCounter.increment();
            return AuthResult.decision(false, HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests");
        }
        if (!isIpAllowed(ip)) {
            log.warn("Inbound auth denied reason=ip_not_allowed path={} ip={}", request.getRequestURI(), ip);
            deniedIpCounter.increment();
            return AuthResult.decision(false, HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!recastlyClient.isInboundAuthorized(secret)) {
            log.warn("Inbound auth denied reason=secret_mismatch path={} ip={}", request.getRequestURI(), ip);
            deniedSecretCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (properties.isInboundSignatureRequired()) {
            AuthResult signatureCheck = validateSignatureHeaders(request, secret, ip);
            if (!signatureCheck.allowed) {
                return signatureCheck;
            }
        }
        allowedCounter.increment();
        return AuthResult.decision(true, HttpStatus.OK, "");
    }

    private AuthResult validateSignatureHeaders(HttpServletRequest request, String secret, String ip) {
        if (secret == null || secret.isBlank()) {
            log.warn("Inbound auth denied reason=missing_secret_for_signature path={} ip={}", request.getRequestURI(), ip);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String tsHeader = headerValue(request, properties.getInboundTimestampHeader());
        String nonceHeader = headerValue(request, properties.getInboundNonceHeader());
        String sigHeader = headerValue(request, properties.getInboundSignatureHeader());
        String bodyHashHeader = headerValue(request, properties.getInboundBodyHashHeader());
        if (tsHeader == null || nonceHeader == null || sigHeader == null || bodyHashHeader == null) {
            log.warn("Inbound auth denied reason=missing_sig_headers path={} ip={}", request.getRequestURI(), ip);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String computedBodyHash;
        try {
            computedBodyHash = sha256Hex(extractBody(request));
        } catch (IOException ex) {
            log.warn("Inbound auth denied reason=body_read_failed path={} ip={}", request.getRequestURI(), ip);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (!MessageDigest.isEqual(computedBodyHash.getBytes(StandardCharsets.UTF_8), bodyHashHeader.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Inbound auth denied reason=body_hash_mismatch path={} ip={}", request.getRequestURI(), ip);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(tsHeader);
        } catch (NumberFormatException ex) {
            log.warn("Inbound auth denied reason=invalid_timestamp path={} ip={} ts={}", request.getRequestURI(), ip, tsHeader);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > properties.getInboundMaxSkewSec()) {
            log.warn("Inbound auth denied reason=timestamp_skew path={} ip={} ts={} now={}", request.getRequestURI(), ip, timestamp, now);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        long nonceTtl = Math.max(properties.getInboundReplayWindowSec(), properties.getInboundMaxSkewSec());
        if (!nonceStore.tryRegister(nonceHeader, now, nonceTtl)) {
            log.warn("Inbound auth denied reason=replay path={} ip={} nonce={}", request.getRequestURI(), ip, nonceHeader);
            deniedReplayCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String payload = request.getMethod() + "\n" + request.getRequestURI() + "\n" + tsHeader + "\n" + nonceHeader + "\n" + bodyHashHeader;
        String expected;
        try {
            expected = hmacHex(secret, payload);
        } catch (GeneralSecurityException ex) {
            log.error("Inbound auth internal error reason=hmac_failed path={} ip={}", request.getRequestURI(), ip);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sigHeader.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Inbound auth denied reason=bad_signature path={} ip={}", request.getRequestURI(), ip);
            deniedSignatureCounter.increment();
            return AuthResult.decision(false, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return AuthResult.decision(true, HttpStatus.OK, "");
    }

    private boolean withinRateLimit(String ip) {
        int max = properties.getInboundRateLimitPerMinute();
        if (max <= 0) {
            return true;
        }
        long window = Instant.now().getEpochSecond() / 60;
        WindowCounter counter = rateCounters.compute(ip, (k, existing) -> {
            if (existing == null || existing.windowMinute != window) {
                return new WindowCounter(window, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return counter.count.get() <= max;
    }

    private boolean isIpAllowed(String ip) {
        String csv = properties.getInboundAllowedIps();
        if (csv == null || csv.isBlank()) {
            return true;
        }
        Set<String> allowedIps = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        return allowedIps.contains(ip);
    }

    private Set<String> trustedProxyIps() {
        String csv = properties.getInboundTrustedProxyIps();
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!properties.isInboundTrustForwardedFor()) {
            return remoteAddr;
        }
        Set<String> trusted = trustedProxyIps();
        if (!trusted.isEmpty() && !trusted.contains(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] parts = forwarded.split(",");
        return parts[0].trim();
    }

    private static String headerValue(HttpServletRequest request, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String inboundSecret(HttpServletRequest request) {
        String configuredHeader = properties.getSecretHeader();
        return headerValue(request, configuredHeader);
    }

    private static String hmacHex(String secret, String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(byte[] data) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
        byte[] bytes = digest.digest(data);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] extractBody(HttpServletRequest request) throws IOException {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            return wrapper.getContentAsByteArray();
        }
        return request.getInputStream().readAllBytes();
    }

    public record AuthResult(boolean allowed, HttpStatus status, String error) {
        static AuthResult decision(boolean allowed, HttpStatus status, String error) {
            return new AuthResult(allowed, status, error);
        }
    }

    private static final class WindowCounter {
        private final long windowMinute;
        private final AtomicInteger count;

        private WindowCounter(long windowMinute, AtomicInteger count) {
            this.windowMinute = windowMinute;
            this.count = count;
        }
    }
}
