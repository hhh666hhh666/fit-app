package com.example.fitapp.module.agent.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档随后端发布，打包漏了或改接口忘了同改，App 的文档页和 agent 都会读到错的说明。
 */
class AgentControllerTest {

    @Test
    void docs_describeEveryEndpoint() throws IOException {
        String body = new AgentController(null, null).docs().getBody();

        for (String path : new String[]{"/api/agent/current", "/api/agent/history", "/api/agent/photos/", "/api/agent/docs"}) {
            assertTrue(body.contains(path), "文档缺接口：" + path);
        }
    }
}
