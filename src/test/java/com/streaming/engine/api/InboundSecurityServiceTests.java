package com.streaming.engine.api;

import com.streaming.engine.distributed.InMemoryNonceStore;
import com.streaming.engine.recastly.RecastlyClient;
import com.streaming.engine.recastly.RecastlyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboundSecurityServiceTests {

    private final RecastlyClient recastlyClient = mock(RecastlyClient.class);
    private RecastlyProperties properties;
    private InboundSecurityService service;

    @BeforeEach
    void setUp() {
        properties = new RecastlyProperties();
        properties.setSecretHeader("X-Stream-Engine-Secret");
        properties.setInboundSignatureRequired(true);
        properties.setInboundMaxSkewSec(300);
        properties.setInboundReplayWindowSec(600);
        properties.setInboundRateLimitPerMinute(120);
        service = new InboundSecurityService(
                recastlyClient,
                properties,
                new InMemoryNonceStore(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void validSignedRequestIsAuthorized() throws Exception {
        String secret = "super-secret";
        when(recastlyClient.isInboundAuthorized(secret)).thenReturn(true);

        MockHttpServletRequest request = signedRequest(secret, "nonce-1", Instant.now().getEpochSecond());
        InboundSecurityService.AuthResult result = service.authorize(request);

        assertTrue(result.allowed());
    }

    @Test
    void replayedNonceIsRejected() throws Exception {
        String secret = "super-secret";
        when(recastlyClient.isInboundAuthorized(secret)).thenReturn(true);
        long now = Instant.now().getEpochSecond();

        MockHttpServletRequest first = signedRequest(secret, "nonce-replay", now);
        MockHttpServletRequest second = signedRequest(secret, "nonce-replay", now);

        assertTrue(service.authorize(first).allowed());
        assertFalse(service.authorize(second).allowed());
    }

    @Test
    void badSignatureIsRejected() throws Exception {
        String secret = "super-secret";
        when(recastlyClient.isInboundAuthorized(secret)).thenReturn(true);

        long ts = Instant.now().getEpochSecond();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sessions");
        request.setRemoteAddr("10.0.0.12");
        request.addHeader("X-Stream-Engine-Secret", secret);
        request.addHeader("X-Stream-Engine-Timestamp", String.valueOf(ts));
        request.addHeader("X-Stream-Engine-Nonce", "nonce-2");
        String bodyHash = sha256Hex(new byte[0]);
        request.addHeader("X-Stream-Engine-Body-SHA256", bodyHash);
        request.addHeader("X-Stream-Engine-Signature", "not-valid");

        assertFalse(service.authorize(request).allowed());
    }

    @Test
    void untrustedForwardedForIsIgnoredByDefault() throws Exception {
        String secret = "super-secret";
        when(recastlyClient.isInboundAuthorized(secret)).thenReturn(true);

        MockHttpServletRequest request = signedRequest(secret, "nonce-3", Instant.now().getEpochSecond());
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        properties.setInboundAllowedIps("203.0.113.10");

        assertFalse(service.authorize(request).allowed());
    }

    @Test
    void trustedForwardedForIsUsedWhenExplicitlyEnabled() throws Exception {
        String secret = "super-secret";
        when(recastlyClient.isInboundAuthorized(secret)).thenReturn(true);
        properties.setInboundTrustForwardedFor(true);
        properties.setInboundTrustedProxyIps("10.0.0.10");
        properties.setInboundAllowedIps("203.0.113.10");

        MockHttpServletRequest request = signedRequest(secret, "nonce-4", Instant.now().getEpochSecond());
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertTrue(service.authorize(request).allowed());
    }

    @Test
    void customSecretHeaderDoesNotFallbackToDefault() throws Exception {
        String secret = "super-secret";
        properties.setSecretHeader("X-Custom-Secret");
        when(recastlyClient.isInboundAuthorized(null)).thenReturn(false);

        long ts = Instant.now().getEpochSecond();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sessions");
        request.setRemoteAddr("10.0.0.12");
        request.addHeader("X-Stream-Engine-Secret", secret);
        request.addHeader("X-Stream-Engine-Timestamp", String.valueOf(ts));
        request.addHeader("X-Stream-Engine-Nonce", "nonce-5");
        String bodyHash = sha256Hex(new byte[0]);
        request.addHeader("X-Stream-Engine-Body-SHA256", bodyHash);
        String payload = "POST\n/api/v1/sessions\n" + ts + "\nnonce-5\n" + bodyHash;
        request.addHeader("X-Stream-Engine-Signature", hmacHex(secret, payload));

        assertFalse(service.authorize(request).allowed());
    }

    private MockHttpServletRequest signedRequest(String secret, String nonce, long ts) throws Exception {
        byte[] body = """
                {"streamId":"demo-stream-1","title":"Demo stream"}
                """.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sessions");
        request.setRemoteAddr("10.0.0.12");
        request.setContentType("application/json");
        request.setContent(body);
        request.addHeader("X-Stream-Engine-Secret", secret);
        request.addHeader("X-Stream-Engine-Timestamp", String.valueOf(ts));
        request.addHeader("X-Stream-Engine-Nonce", nonce);
        String bodyHash = sha256Hex(body);
        request.addHeader("X-Stream-Engine-Body-SHA256", bodyHash);
        String payload = "POST\n/api/v1/sessions\n" + ts + "\n" + nonce + "\n" + bodyHash;
        request.addHeader("X-Stream-Engine-Signature", hmacHex(secret, payload));
        return request;
    }

    private static String hmacHex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(data);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
