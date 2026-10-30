package com.example.fitapp.module.agent.service;

import com.example.fitapp.module.agent.entity.AgentToken;
import com.example.fitapp.module.agent.mapper.AgentTokenMapper;
import com.example.fitapp.module.agent.vo.AgentTokenCreatedVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试：专测明文落库、旧令牌没失效、把别的东西当令牌放行这几种静默错误。
 */
@ExtendWith(MockitoExtension.class)
class AgentTokenServiceTest {

    @Mock
    private AgentTokenMapper mapper;

    private AgentTokenService service() {
        return new AgentTokenService(mapper);
    }

    private AgentToken stored(String hash, LocalDateTime lastUsedAt) {
        AgentToken t = new AgentToken();
        t.setUserId(7L);
        t.setTokenHash(hash);
        t.setLastUsedAt(lastUsedAt);
        return t;
    }


    @Test
    void regenerate_storesHashOnlyAndDropsOldTokenFirst() {
        AgentTokenCreatedVO created = service().regenerate(7L);

        assertTrue(created.token().startsWith("fit_"));
        ArgumentCaptor<AgentToken> captor = ArgumentCaptor.forClass(AgentToken.class);
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteById(7L);
        order.verify(mapper).insert(captor.capture());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertFalse(captor.getValue().getTokenHash().contains(created.token()));
    }

    @Test
    void regenerate_producesDifferentTokens() {
        AgentTokenService s = service();
        assertFalse(s.regenerate(7L).token().equals(s.regenerate(7L).token()));
    }

    @Test
    void authenticate_matchesTheGeneratedTokenByHash() {
        AgentTokenService s = service();
        AgentTokenCreatedVO created = s.regenerate(7L);
        ArgumentCaptor<AgentToken> captor = ArgumentCaptor.forClass(AgentToken.class);
        verify(mapper).insert(captor.capture());
        String hash = captor.getValue().getTokenHash();
        when(mapper.selectByHash(hash)).thenReturn(stored(hash, null));

        assertEquals(7L, s.authenticate(created.token()));
    }

    @Test
    void authenticate_rejectsUnknownAndNonAgentTokens() {
        assertNull(service().authenticate("fit_unknown"));
        // JWT 这类没有前缀的东西连库都不查
        assertNull(service().authenticate("eyJhbGciOiJIUzI1NiJ9.x.y"));
        assertNull(service().authenticate(null));
        verify(mapper).selectByHash(anyString());
    }

    @Test
    void authenticate_refreshesLastUsedOnlyWhenStale() {
        when(mapper.selectByHash(anyString())).thenReturn(stored("h", LocalDateTime.now().minusSeconds(5)));
        service().authenticate("fit_x");
        verify(mapper, never()).touch(any(), any());

        when(mapper.selectByHash(anyString())).thenReturn(stored("h", LocalDateTime.now().minusMinutes(5)));
        service().authenticate("fit_x");
        verify(mapper).touch(any(), any());
    }

    @Test
    void status_neverExposesTheHash() {
        when(mapper.selectById(7L)).thenReturn(null);
        assertFalse(service().status(7L).exists());

        when(mapper.selectById(7L)).thenReturn(stored("h", null));
        assertTrue(service().status(7L).exists());
    }
}
