package com.example.fitapp.module.diary.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.diary.dto.DiaryBatchDeleteDTO;
import com.example.fitapp.module.diary.dto.DiarySaveDTO;
import com.example.fitapp.module.diary.service.DiaryPhotoService;
import com.example.fitapp.module.diary.service.DiaryRefService;
import com.example.fitapp.module.diary.service.DiaryService;
import com.example.fitapp.module.diary.vo.DiaryEntryVO;
import com.example.fitapp.module.diary.vo.DiaryListItemVO;
import com.example.fitapp.module.diary.vo.DiaryPhotoVO;
import com.example.fitapp.module.diary.vo.DiaryRefVO;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 日记接口（ADR 0044）：列表与搜索／全文／写／改／批量删、图片、记录引用。 */
@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final DiaryPhotoService photoService;
    private final DiaryRefService refService;

    /** 按月列表（month 为空＝当月）；有 keyword 时搜全部日记，不看 month。 */
    @GetMapping
    public Result<List<DiaryListItemVO>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return Result.success(diaryService.list(
                UserContext.requireUserId(), month, keyword, Math.min(limit, 100), offset));
    }

    @GetMapping("/{id}")
    public Result<DiaryEntryVO> detail(@PathVariable Long id) {
        return Result.success(diaryService.detail(UserContext.requireUserId(), id));
    }

    @PostMapping
    public Result<DiaryEntryVO> create(@Valid @RequestBody DiarySaveDTO body) {
        return Result.success(diaryService.create(UserContext.requireUserId(), body));
    }

    @PutMapping("/{id}")
    public Result<DiaryEntryVO> update(@PathVariable Long id, @Valid @RequestBody DiarySaveDTO body) {
        return Result.success(diaryService.update(UserContext.requireUserId(), id, body));
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody DiaryBatchDeleteDTO body) {
        diaryService.batchDelete(UserContext.requireUserId(), body.getIds());
        return Result.success();
    }

    // ── 图片 ──

    /** 插图时立刻上传，返回图片 id，由前端写进正文。 */
    @PostMapping("/photos")
    public Result<Long> uploadPhoto(@RequestParam("file") MultipartFile file) {
        return Result.success(photoService.upload(UserContext.requireUserId(), file));
    }

    @GetMapping("/photos")
    public Result<List<DiaryPhotoVO>> album(@RequestParam(required = false) Integer offset,
                                            @RequestParam(required = false) Integer limit) {
        return Result.success(photoService.album(UserContext.requireUserId(), offset, limit));
    }

    /** 直出图片二进制（不走 Result 信封）；图已删返回真实的 404，前端据此显示「图片已删除」。 */
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return photoResponse(photoService.read(UserContext.requireUserId(), id, false));
    }

    @GetMapping("/photos/{id}/thumb")
    public ResponseEntity<byte[]> photoThumb(@PathVariable Long id) {
        return photoResponse(photoService.read(UserContext.requireUserId(), id, true));
    }

    @DeleteMapping("/photos/{id}")
    public Result<Void> deletePhoto(@PathVariable Long id) {
        photoService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    // ── 记录引用 ──

    /** 把正文里的引用键解析成卡片数据；记录没了给 exists=false，不报错。 */
    @GetMapping("/refs")
    public Result<List<DiaryRefVO>> refs(@RequestParam List<String> keys) {
        return Result.success(refService.resolve(UserContext.requireUserId(), keys));
    }

    /** 某天可引用的记录。 */
    @GetMapping("/refs/candidates")
    public Result<List<DiaryRefVO>> candidates(@RequestParam String date) {
        return Result.success(refService.candidates(UserContext.requireUserId(), date));
    }

    private static ResponseEntity<byte[]> photoResponse(DietService.PhotoData d) {
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mime()))
                .contentLength(d.data().length)
                .body(d.data());
    }
}
