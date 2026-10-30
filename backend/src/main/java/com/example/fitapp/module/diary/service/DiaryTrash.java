package com.example.fitapp.module.diary.service;

import com.example.fitapp.module.diary.entity.DiaryEntry;
import com.example.fitapp.module.diary.entity.DiaryPhoto;
import com.example.fitapp.module.diary.mapper.DiaryEntryMapper;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日记进出回收站（ADR 0047）。勾了「同时删除图片」时，只属于它的图算进这一条，恢复时一起回来
 * （ADR 0046）。恢复时按正文重算引用：这期间相册里可能又删了别的图。
 */
@Component
@RequiredArgsConstructor
public class DiaryTrash implements TrashHandler {

    record Snapshot(DiaryEntry entry, List<DiaryPhoto> photos) {
    }

    private static final int TITLE_CHARS = 30;

    private final DiaryEntryMapper entryMapper;
    private final DiaryPhotoMapper photoMapper;
    private final DiaryPhotoService photoService;
    private final DiaryPhotoTrash photoTrash;
    private final PhotoStorage photoStorage;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.DIARY;
    }

    void put(Long userId, DiaryEntry e, List<DiaryPhoto> photos) {
        long bytes = photos.stream().mapToLong(DiaryPhotoTrash::bytesOf).sum();
        String summary = TrashStore.DAY_TIME.format(e.getWrittenAt())
                + (photos.isEmpty() ? "" : " · 含 " + photos.size() + " 张图");
        store.put(userId, type(), e.getId(), titleOf(e), summary, bytes, new Snapshot(e, photos));
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        Snapshot s = store.read(item, Snapshot.class);
        entryMapper.insert(s.entry());
        s.photos().forEach(photoMapper::insert);
        photoService.syncRefs(userId, s.entry().getId(), s.entry().getContent());
        s.photos().forEach(p -> photoTrash.relink(userId, p.getId()));
        return null;
    }

    @Override
    public void purge(TrashItem item) {
        store.read(item, Snapshot.class).photos().forEach(p -> photoStorage.delete(p.getPath()));
    }

    private static String titleOf(DiaryEntry e) {
        if (e.getTitle() != null && !e.getTitle().isBlank()) {
            return e.getTitle();
        }
        String text = e.getContent() == null ? "" : e.getContent()
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) {
            return "没有文字的日记";
        }
        return text.length() <= TITLE_CHARS ? text : text.substring(0, TITLE_CHARS) + "…";
    }
}
