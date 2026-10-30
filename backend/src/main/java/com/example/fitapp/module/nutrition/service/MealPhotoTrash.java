package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.mapper.MealPhotoMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 餐照进出回收站（ADR 0047）：文件留在原处，到期或彻底删除才删。 */
@Component
@RequiredArgsConstructor
public class MealPhotoTrash implements TrashHandler {

    private static final Map<String, String> MEAL_LABEL = Map.of(
            "breakfast", "早餐", "lunch", "午餐", "dinner", "晚餐", "snack", "加餐");

    private final MealPhotoMapper mealPhotoMapper;
    private final PhotoStorage photoStorage;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.MEAL_PHOTO;
    }

    void put(Long userId, MealPhoto p) {
        String title = TrashStore.DAY.format(p.getLogDate()) + " " + MEAL_LABEL.getOrDefault(p.getMeal(), "");
        long bytes = size(p.getSizeBytes()) + size(p.getThumbBytes());
        store.put(userId, type(), p.getId(), title, "", bytes, p);
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        mealPhotoMapper.insert(store.read(item, MealPhoto.class));
        return null;
    }

    @Override
    public void purge(TrashItem item) {
        String path = store.read(item, MealPhoto.class).getPath();
        if (path != null) {
            photoStorage.delete(path);
        }
    }

    @Override
    public DietService.PhotoData thumb(TrashItem item) {
        MealPhoto p = store.read(item, MealPhoto.class);
        if (p.getPath() == null) {
            return null;
        }
        byte[] t = photoStorage.readThumb(p.getPath());
        if (t != null) {
            return new DietService.PhotoData(t, "image/jpeg");
        }
        byte[] img = photoStorage.readImage(p.getPath(), p.getMime());
        return img == null ? null : new DietService.PhotoData(img, p.getMime());
    }

    private static long size(Integer bytes) {
        return bytes == null ? 0L : bytes;
    }
}
