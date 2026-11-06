package com.example.fitapp.module.account.service;

import com.example.fitapp.common.PhotoData;
import com.example.fitapp.module.account.entity.Avatar;
import com.example.fitapp.module.account.mapper.AvatarMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从历史里删掉的头像进出回收站（ADR 0051、0047）：文件全留在原处；恢复后回到历史里，
 * 但不会自动变回当前头像——要用的话再点它、重新裁一次。
 */
@Component
@RequiredArgsConstructor
public class AvatarTrash implements TrashHandler {

    private final AvatarMapper avatarMapper;
    private final PhotoStorage photoStorage;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.AVATAR;
    }

    void put(Long userId, Avatar a) {
        String title = "头像 · " + TrashStore.DAY.format(a.getCreatedAt()) + " 上传";
        store.put(userId, type(), a.getId(), title, "", bytesOf(a), a);
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        avatarMapper.insert(store.read(item, Avatar.class));
        return null;
    }

    @Override
    public void purge(TrashItem item) {
        photoStorage.delete(store.read(item, Avatar.class).getPath());
    }

    @Override
    public PhotoData thumb(TrashItem item) {
        Avatar a = store.read(item, Avatar.class);
        byte[] t = photoStorage.readSide(a.getPath(), PhotoStorage.CROP_SUFFIX);
        return t == null ? null : PhotoData.of(t, "image/jpeg");
    }

    static long bytesOf(Avatar a) {
        return (a.getSizeBytes() == null ? 0L : a.getSizeBytes())
                + (a.getExtraBytes() == null ? 0L : a.getExtraBytes());
    }
}
