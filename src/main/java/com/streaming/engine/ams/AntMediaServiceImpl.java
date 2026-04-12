package com.streaming.engine.ams;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

@Service
public class AntMediaServiceImpl implements AntMediaService {
    private static final Logger log = LoggerFactory.getLogger(AntMediaServiceImpl.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String startPath;
    private final String stopPath;
    private final String sharedSecret;
    private final String secretHeader;

        public AntMediaServiceImpl(
            CloseableHttpClient outboundHttpClient,
            @Value("${ams.base-url:http://localhost:5080/LiveApp/rest/v2}") String baseUrl,
            @Value("${ams.start-path:/broadcasts/create}") String startPath,
            @Value("${ams.stop-path:/broadcasts/stop}") String stopPath,
            @Value("${ams.shared-secret:}") String sharedSecret,
            @Value("${ams.secret-header:X-Stream-Engine-Secret}") String secretHeader,
            @Value("${ams.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ams.read-timeout-ms:5000}") int readTimeoutMs
        ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ams.base-url must be set");
        }
        this.baseUrl = baseUrl;
        this.startPath = startPath;
        this.stopPath = stopPath;
        this.sharedSecret = sharedSecret;
        this.secretHeader = secretHeader;
        HttpComponentsClientHttpRequestFactory rf = new HttpComponentsClientHttpRequestFactory(outboundHttpClient);
        rf.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        rf.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
            .requestFactory(rf)
            .build();
        }

    @Override

    public boolean startStream(String streamId, String title) {
        if (streamId == null || streamId.isBlank() || title == null || title.isBlank()) {
            log.error("Invalid input: streamId and title must be non-blank");
            return false;
        }
        return postToAms(startPath, Map.of("name", title, "streamId", streamId), "start");
    }

    @Override
    public boolean stopStream(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            log.error("Invalid input: streamId must be non-blank");
            return false;
        }
        return postToAms(stopPath, Map.of("id", streamId), "stop");
    }

    private boolean postToAms(String path, Map<String, Object> payload, String action) {
        String url = joinUrl(baseUrl, path);
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!sharedSecret.isBlank()) {
                            headers.set(secretHeader, sharedSecret);
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.error("AMS {} failed for url={} payload={} reason={}", action, url, payload, ex.getMessage(), ex);
            return false;
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        String left = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String right = path.startsWith("/") ? path : "/" + path;
        return left + right;
    }
}
