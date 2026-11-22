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

    static String summary(BowelRecord r) {
        String s = BowelRecord.STOOL.equals(r.getKind()) ? "大便" : "小便";
        if (r.getBristol() != null) {
            s += " · " + r.getBristol() + " 型";
        }
        if (r.getNote() != null && !r.getNote().isBlank()) {
            s += " · " + r.getNote();
        }
        return s;
    }
}
