package com.streaming.engine.api;

import com.streaming.engine.recastly.RecastlyClient;
import com.streaming.engine.session.SessionManager;
import com.streaming.engine.session.SessionState;
import com.streaming.engine.session.StreamSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionManager sessionManager;

    @MockBean
    private RecastlyClient recastlyClient;

    @MockBean
    private InboundSecurityService inboundSecurityService;

    @Test
    void startConflictReturns409() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(true, org.springframework.http.HttpStatus.OK, ""));
        when(recastlyClient.validateKey("stream-1")).thenReturn(new RecastlyClient.ValidationResult(true, ""));
        when(sessionManager.start("stream-1", "stream-1", "Title"))
                .thenReturn(SessionManager.StartResult.ALREADY_RUNNING);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"streamId":"stream-1","title":"Title"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void startRed5FailureReturns502() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(true, org.springframework.http.HttpStatus.OK, ""));
        when(recastlyClient.validateKey("stream-1")).thenReturn(new RecastlyClient.ValidationResult(true, ""));
        when(sessionManager.start("stream-1", "stream-1", "Title"))
                .thenReturn(SessionManager.StartResult.RED5_FAILED);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"streamId":"stream-1","title":"Title"}
                                """))
                .andExpect(status().isBadGateway());
    }

    @Test
    void stopNotFoundReturns404() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(true, org.springframework.http.HttpStatus.OK, ""));
        when(sessionManager.stop("missing"))
                .thenReturn(SessionManager.StopResult.of(SessionManager.StopStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/sessions/missing/stop"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stopRed5FailureReturns502() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(true, org.springframework.http.HttpStatus.OK, ""));
        when(sessionManager.stop("stream-2"))
                .thenReturn(SessionManager.StopResult.of(SessionManager.StopStatus.RED5_FAILED));

        mockMvc.perform(post("/api/v1/sessions/stream-2/stop"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void unauthorizedRequestReturns401() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(false, org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized"));

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"streamId":"stream-1","title":"Title"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUnauthorizedReturns401() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(false, org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized"));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listOmitsStreamKey() throws Exception {
        when(inboundSecurityService.authorize(any()))
                .thenReturn(new InboundSecurityService.AuthResult(true, org.springframework.http.HttpStatus.OK, ""));
        when(sessionManager.list()).thenReturn(List.of(
                new StreamSession("stream-1", "secret-key", "Title", SessionState.RUNNING, Instant.parse("2026-03-24T12:00:00Z"))
        ));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("streamKey"))));
    }
}
