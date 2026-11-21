package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.PhotoData;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 自建食物进出回收站（ADR 0047）。有食物图片的，图片文件留在原处跟着它走（ADR 0057）：
 * 恢复时一起回来，彻底删除时才删文件；在回收站期间照样占照片空间。
 */
@Component
@RequiredArgsConstructor
public class FoodTrash implements TrashHandler {

    private static final int NAME_MAX = 64;

    private final FoodMapper foodMapper;
    private final TrashStore store;
    private final PhotoStorage photoStorage;

    @Override
    public String type() {
        return TrashItem.FOOD;
    }

    void put(Long userId, Food f) {
        String unit = f.getUnit() == null ? "g" : f.getUnit();
        String summary = f.getKcal() == null
                ? ""
                : "每 100" + unit + " " + f.getKcal().stripTrailingZeros().toPlainString() + " 大卡";
        long imageBytes = f.getImageKey() == null || f.getImageBytes() == null ? 0L : f.getImageBytes();
        store.put(userId, type(), f.getId(), f.getName(), summary, imageBytes, f);
    }

    @Override
    public void purge(TrashItem item) {
        String key = FoodImageService.relKey(store.read(item, Food.class));
        if (key != null) {
            photoStorage.delete(key);
        }
    }

    @Override
    public PhotoData thumb(TrashItem item) {
        String key = FoodImageService.relKey(store.read(item, Food.class));
        byte[] t = key == null ? null : photoStorage.readThumb(key);
        return t == null ? null : PhotoData.of(t, "image/jpeg");
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        Food f = store.read(item, Food.class);
        String original = f.getName();
        f.setName(freeName(userId, original));
        f.setInitials(FoodInitials.of(f.getName(), f.getAliases()));
        foodMapper.insert(f);
        return original.equals(f.getName()) ? null : "已有同名食物，改名为「" + f.getName() + "」";
    }

    /** 同用户内食物名有唯一键：撞名就加「（恢复）」，还撞就接着编号。 */
    private String freeName(Long userId, String name) {
        String candidate = name;
        for (int n = 1; isTaken(userId, candidate); n++) {
            String suffix = n == 1 ? "（恢复）" : "（恢复" + n + "）";
            candidate = name.substring(0, Math.min(name.length(), NAME_MAX - suffix.length())) + suffix;
        }
        return candidate;
    }

    private boolean isTaken(Long userId, String name) {
        Long count = foodMapper.selectCount(Wrappers.<Food>query().eq("user_id", userId).eq("name", name));
        return count != null && count > 0;
    }
}
