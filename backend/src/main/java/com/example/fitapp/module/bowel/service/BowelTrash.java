package com.example.fitapp.module.bowel.service;

import com.example.fitapp.module.bowel.entity.BowelRecord;
import com.example.fitapp.module.bowel.mapper.BowelRecordMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 排便记录进出回收站（ADR 0061、0047）。 */
@Component
@RequiredArgsConstructor
public class BowelTrash implements TrashHandler {

    private final BowelRecordMapper mapper;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.BOWEL;
    }

    void put(Long userId, BowelRecord r) {
        store.put(userId, type(), r.getId(), TrashStore.DAY_TIME.format(r.getOccurredAt()), summary(r), 0L, r);
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        mapper.insert(store.read(item, BowelRecord.class));
        return null;
    }

    /** 小便颜色 1–7 的叫法，和 App 的选项一致。 */
    static final String[] URINE_COLORS = {"无色透明", "浅黄", "黄", "深黄", "琥珀", "茶褐", "红／粉"};

    static String summary(BowelRecord r) {
        String s = BowelRecord.STOOL.equals(r.getKind()) ? "大便" : "小便";
        if (r.getBristol() != null) {
            s += " · " + r.getBristol() + " 型";
        }
        if (r.getColor() != null && r.getColor() >= 1 && r.getColor() <= URINE_COLORS.length) {
            s += " · " + URINE_COLORS[r.getColor() - 1];
        }
        if (r.getNote() != null && !r.getNote().isBlank()) {
            s += " · " + r.getNote();
        }
        return s;
    }
}
