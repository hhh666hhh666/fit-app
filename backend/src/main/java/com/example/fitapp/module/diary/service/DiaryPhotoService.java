package com.example.fitapp.module.diary.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.diary.entity.DiaryPhoto;
import com.example.fitapp.module.diary.entity.DiaryPhotoRef;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
import com.example.fitapp.module.diary.mapper.DiaryPhotoRefMapper;
import com.example.fitapp.module.diary.vo.DiaryPhotoVO;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日记图片（ADR 0044）：上传、读取、相册、按正文同步引用。
 *
 * <p>图片常驻相册（ADR 0046）：从正文去掉或删除日记都不会动它，只有主人明确要删才删。
 * 引用表只用来判断一张图是不是「只属于这篇」。
 * 文件一律在数据库提交之后才删：最坏留一个无害的孤儿文件（ADR 0026）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryPhotoService {

    /** 正文里的图片语法；⚠️ 这是写进主人正文的长期格式，改它要迁移数据。 */
    private static final Pattern PHOTO_SYNTAX = Pattern.compile("!\\[[^\\]]*]\\(diary-photo:(\\d+)\\)");

    private final DiaryPhotoMapper photoMapper;
    private final DiaryPhotoRefMapper refMapper;
    private final DietService dietService;
    private final PhotoStorage photoStorage;

    /** 上传一张图，返回它的 id。 */
    public Long upload(Long userId, MultipartFile file) {
        byte[] bytes = DietService.readValidPhoto(file);
        dietService.checkPhotoQuota(userId, bytes.length);
        byte[] thumb = DietService.makeThumb(bytes);
        String mime = file.getContentType();
        String relKey = userId + "/diary/" + UUID.randomUUID().toString().replace("-", "");
        try {
            photoStorage.write(relKey, mime, bytes, thumb);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "照片保存失败，请稍后重试");
        }
        DiaryPhoto p = new DiaryPhoto();
        p.setUserId(userId);
        p.setMime(mime);
        p.setSizeBytes(bytes.length);
        p.setThumbBytes(thumb == null ? null : thumb.length);
        p.setPath(relKey);
        try {
            photoMapper.insert(p);
        } catch (RuntimeException e) {
            photoStorage.delete(relKey);
            throw e;
        }
        return p.getId();
    }

    /** 读图；图已删或文件丢失返回 null，由 Controller 转 404。没有缩略图时回退原图。 */
    public DietService.PhotoData read(Long userId, Long id, boolean wantThumb) {
        DiaryPhoto p = photoMapper.selectOne(Wrappers.<DiaryPhoto>lambdaQuery()
                .eq(DiaryPhoto::getId, id)
                .eq(DiaryPhoto::getUserId, userId));
        if (p == null) {
            return null;
        }
        if (wantThumb) {
            byte[] t = photoStorage.readThumb(p.getPath());
            if (t != null) {
                return new DietService.PhotoData(t, "image/jpeg");
            }
        }
        byte[] img = photoStorage.readImage(p.getPath(), p.getMime());
        return img == null ? null : new DietService.PhotoData(img, p.getMime());
    }

    /** 相册「日记」栏：按上传时间倒序。 */
    public List<DiaryPhotoVO> album(Long userId, Integer offset, Integer limit) {
        int size = (limit == null || limit < 1) ? 60 : Math.min(limit, 200);
        int start = (offset == null || offset < 0) ? 0 : offset;
        return photoMapper.selectList(Wrappers.<DiaryPhoto>lambdaQuery()
                        .select(DiaryPhoto::getId, DiaryPhoto::getSizeBytes, DiaryPhoto::getCreatedAt)
                        .eq(DiaryPhoto::getUserId, userId)
                        .orderByDesc(DiaryPhoto::getCreatedAt)
                        .orderByDesc(DiaryPhoto::getId)
                        .last("LIMIT " + start + ", " + size))
                .stream().map(p -> {
                    DiaryPhotoVO vo = new DiaryPhotoVO();
                    vo.setId(p.getId());
                    vo.setSizeBytes(p.getSizeBytes());
                    vo.setCreatedAt(p.getCreatedAt());
                    return vo;
                }).toList();
    }

    /** 从相册删一张：引用它的日记之后显示「图片已删除」，正文不动。 */
    @Transactional
    public void delete(Long userId, Long id) {
        DiaryPhoto p = photoMapper.selectOne(Wrappers.<DiaryPhoto>lambdaQuery()
                .eq(DiaryPhoto::getId, id)
                .eq(DiaryPhoto::getUserId, userId));
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "照片不存在");
        }
        refMapper.delete(Wrappers.<DiaryPhotoRef>lambdaQuery().eq(DiaryPhotoRef::getPhotoId, id));
        photoMapper.deleteById(id);
        deleteFilesAfterCommit(List.of(p.getPath()));
    }

    /** 保存日记后按正文同步引用。只认这个用户自己的图：正文里写别人的 id 不算引用，读取时也取不到。 */
    @Transactional
    public void syncRefs(Long userId, Long entryId, String content) {
        Set<Long> wanted = ownedPhotoIds(userId, content);
        Set<Long> current = new HashSet<>();
        for (DiaryPhotoRef r : refsOf(entryId)) {
            current.add(r.getPhotoId());
        }
        for (Long id : wanted) {
            if (!current.contains(id)) {
                refMapper.insert(new DiaryPhotoRef(entryId, id));
            }
        }
        for (Long id : current) {
            if (!wanted.contains(id)) {
                refMapper.delete(Wrappers.<DiaryPhotoRef>lambdaQuery()
                        .eq(DiaryPhotoRef::getEntryId, entryId)
                        .eq(DiaryPhotoRef::getPhotoId, id));
            }
        }
    }

    /**
     * 删除一篇日记时调用：图默认留在相册；{@code withPhotos} 时只删只属于它的图，
     * 其他日记还在用的图保留。
     */
    @Transactional
    public void releaseEntry(Long entryId, boolean withPhotos) {
        List<DiaryPhotoRef> refs = refsOf(entryId);
        refMapper.delete(Wrappers.<DiaryPhotoRef>lambdaQuery().eq(DiaryPhotoRef::getEntryId, entryId));
        if (!withPhotos) {
            return;
        }
        List<String> paths = new ArrayList<>();
        for (DiaryPhotoRef r : refs) {
            if (refCount(r.getPhotoId()) == 0) {
                DiaryPhoto p = photoMapper.selectById(r.getPhotoId());
                if (p != null) {
                    photoMapper.deleteById(p.getId());
                    paths.add(p.getPath());
                }
            }
        }
        deleteFilesAfterCommit(paths);
    }

    /** 正文里写到的、确实属于这个用户的图。 */
    private Set<Long> ownedPhotoIds(Long userId, String content) {
        Set<Long> mentioned = new HashSet<>();
        Matcher m = PHOTO_SYNTAX.matcher(content == null ? "" : content);
        while (m.find()) {
            try {
                mentioned.add(Long.valueOf(m.group(1)));
            } catch (NumberFormatException ignored) {
                // 手写了一个超长数字，不可能是真的 id
            }
        }
        if (mentioned.isEmpty()) {
            return Set.of();
        }
        Set<Long> owned = new HashSet<>();
        for (DiaryPhoto p : photoMapper.selectList(Wrappers.<DiaryPhoto>lambdaQuery()
                .select(DiaryPhoto::getId)
                .eq(DiaryPhoto::getUserId, userId)
                .in(DiaryPhoto::getId, mentioned))) {
            owned.add(p.getId());
        }
        return owned;
    }

    private List<DiaryPhotoRef> refsOf(Long entryId) {
        return refMapper.selectList(Wrappers.<DiaryPhotoRef>lambdaQuery().eq(DiaryPhotoRef::getEntryId, entryId));
    }

    private long refCount(Long photoId) {
        return refMapper.selectCount(Wrappers.<DiaryPhotoRef>lambdaQuery().eq(DiaryPhotoRef::getPhotoId, photoId));
    }

    private void deleteFilesAfterCommit(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paths.forEach(photoStorage::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paths.forEach(photoStorage::delete);
            }
        });
    }
}
