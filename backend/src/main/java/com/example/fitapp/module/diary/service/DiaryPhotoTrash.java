package com.example.fitapp.module.diary.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.diary.entity.DiaryEntry;
import com.example.fitapp.module.diary.entity.DiaryPhoto;
import com.example.fitapp.module.diary.entity.DiaryPhotoRef;
import com.example.fitapp.module.diary.mapper.DiaryEntryMapper;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
import com.example.fitapp.module.diary.mapper.DiaryPhotoRefMapper;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 相册单删的日记图片进出回收站（ADR 0047）：文件留在原处；正文没动过，
 * 恢复后图回到原位。引用按正文重建：这期间日记可能又保存过，快照里的引用不一定还对。
 */
@Component
@RequiredArgsConstructor
public class DiaryPhotoTrash implements TrashHandler {

    private final DiaryPhotoMapper photoMapper;
    private final DiaryPhotoRefMapper refMapper;
    private final DiaryEntryMapper entryMapper;
    private final PhotoStorage photoStorage;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.DIARY_PHOTO;
    }

    void put(Long userId, DiaryPhoto p) {
        String title = TrashStore.DAY.format(p.getCreatedAt()) + " 上传";
        store.put(userId, type(), p.getId(), title, "", bytesOf(p), p);
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        DiaryPhoto p = store.read(item, DiaryPhoto.class);
        photoMapper.insert(p);
        relink(userId, p.getId());
        return null;
    }

    @Override
    public void purge(TrashItem item) {
        photoStorage.delete(store.read(item, DiaryPhoto.class).getPath());
    }

    @Override
    public DietService.PhotoData thumb(TrashItem item) {
        DiaryPhoto p = store.read(item, DiaryPhoto.class);
        byte[] t = photoStorage.readThumb(p.getPath());
        if (t != null) {
            return new DietService.PhotoData(t, "image/jpeg");
        }
        byte[] img = photoStorage.readImage(p.getPath(), p.getMime());
        return img == null ? null : new DietService.PhotoData(img, p.getMime());
    }

    /** 图回来之后，把正文里写着它的日记都接上引用。 */
    void relink(Long userId, Long photoId) {
        for (DiaryEntry e : entryMapper.selectList(Wrappers.<DiaryEntry>query()
                .select("id")
                .eq("user_id", userId)
                .like("content", "(diary-photo:" + photoId + ")"))) {
            Long linked = refMapper.selectCount(Wrappers.<DiaryPhotoRef>query()
                    .eq("entry_id", e.getId())
                    .eq("photo_id", photoId));
            if (linked == null || linked == 0) {
                refMapper.insert(new DiaryPhotoRef(e.getId(), photoId));
            }
        }
    }

    static long bytesOf(DiaryPhoto p) {
        return (p.getSizeBytes() == null ? 0L : p.getSizeBytes())
                + (p.getThumbBytes() == null ? 0L : p.getThumbBytes());
    }
}
