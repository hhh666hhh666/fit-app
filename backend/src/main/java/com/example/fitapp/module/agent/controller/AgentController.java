package com.example.fitapp.module.agent.controller;

import com.example.fitapp.module.agent.service.AgentDataService;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.security.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 外部 agent 只读取数（ADR 0043）：除 {@code /docs} 公开外，都凭 agent 令牌访问，不认登录态。
 * 接口说明在 {@code resources/agent/agent-api.md}，改接口要同改那份文档。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";
    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final AgentDataService agentDataService;
    private final DietService dietService;

    @GetMapping("/current")
    public void current(HttpServletResponse response) throws IOException {
        Long userId = UserContext.requireUserId();
        response.setContentType(JSON_UTF8);
        agentDataService.writeCurrent(userId, response.getOutputStream());
    }

    /** 不设时间上限，所以直接写响应流、不经 Result 信封的整体序列化：堆上放不下多年数据。 */
    @GetMapping("/history")
    public void history(@RequestParam(required = false) String from,
                        @RequestParam(required = false) String to,
                        HttpServletResponse response) throws IOException {
        Long userId = UserContext.requireUserId();
        response.setContentType(JSON_UTF8);
        agentDataService.writeHistory(userId, from, to, response.getOutputStream());
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return photoResponse(dietService.readPhoto(UserContext.requireUserId(), id, false));
    }

    @GetMapping("/photos/{id}/thumb")
    public ResponseEntity<byte[]> photoThumb(@PathVariable Long id) {
        return photoResponse(dietService.readPhoto(UserContext.requireUserId(), id, true));
    }

    /** 接口文档（Markdown）：公开，不含任何用户数据；App 的文档页与 agent 读的是同一份。 */
    @GetMapping("/docs")
    public ResponseEntity<String> docs() throws IOException {
        String text = new ClassPathResource("agent/agent-api.md").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MARKDOWN).body(text);
    }

    private static ResponseEntity<byte[]> photoResponse(DietService.PhotoData d) {
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mime()))
                .contentLength(d.data().length)
                .cacheControl(CacheControl.noStore())
                .body(d.data());
    }
}
