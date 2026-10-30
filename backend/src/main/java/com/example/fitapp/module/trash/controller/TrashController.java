package com.example.fitapp.module.trash.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.trash.dto.AbandonedSessionDTO;
import com.example.fitapp.module.trash.dto.TrashIdsDTO;
import com.example.fitapp.module.trash.service.TrashService;
import com.example.fitapp.module.trash.vo.TrashItemVO;
import com.example.fitapp.module.trash.vo.TrashRestoreVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 回收站（ADR 0047）。 */
@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    public Result<List<TrashItemVO>> list(@RequestParam(required = false) List<String> types,
                                          @RequestParam(required = false) Integer offset,
                                          @RequestParam(required = false) Integer limit) {
        return Result.success(trashService.list(UserContext.requireUserId(), types, offset, limit));
    }

    @PostMapping("/restore")
    public Result<List<TrashRestoreVO>> restore(@Valid @RequestBody TrashIdsDTO body) {
        return Result.success(trashService.restore(UserContext.requireUserId(), body.getIds()));
    }

    @PostMapping("/purge")
    public Result<Void> purge(@Valid @RequestBody TrashIdsDTO body) {
        trashService.purge(UserContext.requireUserId(), body.getIds());
        return Result.success();
    }

    @PostMapping("/clear")
    public Result<Void> clear() {
        trashService.clear(UserContext.requireUserId());
        return Result.success();
    }

    /** 收下「开新的」丢掉的训练；重复传只留一条。 */
    @PostMapping("/sessions")
    public Result<Void> putSession(@Valid @RequestBody AbandonedSessionDTO body) {
        trashService.putAbandonedSession(UserContext.requireUserId(), body);
        return Result.success();
    }

    /** 列表里的缩略图；直出二进制，没有返回真实的 404。 */
    @GetMapping("/{id}/thumb")
    public ResponseEntity<byte[]> thumb(@PathVariable Long id) {
        DietService.PhotoData d = trashService.thumb(UserContext.requireUserId(), id);
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
