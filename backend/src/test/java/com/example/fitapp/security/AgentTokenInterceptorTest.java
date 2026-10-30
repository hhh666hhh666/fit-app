package com.example.fitapp.security;

import com.example.fitapp.module.agent.service.AgentTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 外部调用方靠 HTTP 状态码判断成败：这里断言的是真实的 401，不是 App 那套 200 ＋ 业务码。
 */
@ExtendWith(MockitoExtension.class)
class AgentTokenInterceptorTest {

    @Mock
    private AgentTokenService agentTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentTokenInterceptor interceptor() {
        return new AgentTokenInterceptor(agentTokenService, objectMapper);
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void validToken_setsUserAndClearsAfterCompletion() throws IOException {
        when(agentTokenService.authenticate("fit_ok")).thenReturn(7L);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer fit_ok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertTrue(interceptor().preHandle(req, res, new Object()));
        assertEquals(7L, UserContext.getUserId());

        interceptor().afterCompletion(req, res, new Object(), null);
        assertNull(UserContext.getUserId());
    }

    @Test
    void invalidToken_returnsReal401WithEnvelope() throws IOException {
        when(agentTokenService.authenticate("fit_bad")).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer fit_bad");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertFalse(interceptor().preHandle(req, res, new Object()));

        assertEquals(401, res.getStatus());
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertEquals(401, body.get("code").asInt());
        assertNull(UserContext.getUserId());
    }

    @Test
    void missingHeader_returnsReal401() throws IOException {
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertFalse(interceptor().preHandle(new MockHttpServletRequest(), res, new Object()));

        assertEquals(401, res.getStatus());
    }
}
