package com.example.fitapp.module.body.service;

import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 身体档案进出回收站（ADR 0047）。 */
@Component
@RequiredArgsConstructor
public class BodyTrash implements TrashHandler {

    private final BodyRecordMapper bodyRecordMapper;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.BODY;
    }

    void put(Long userId, BodyRecord r) {
        store.put(userId, type(), r.getId(), TrashStore.DAY_TIME.format(r.getMeasuredAt()), summary(r), 0L, r);
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        bodyRecordMapper.insert(store.read(item, BodyRecord.class));
        return null;
    }

    private static String summary(BodyRecord r) {
        List<String> parts = new ArrayList<>();
        add(parts, "体重", r.getWeight(), " kg");
        add(parts, "体脂", r.getBodyFat(), "%");
        add(parts, "胸围", r.getChest(), " cm");
        add(parts, "腰围", r.getWaist(), " cm");
        add(parts, "臀围", r.getHip(), " cm");
        add(parts, "臂围", r.getArm(), " cm");
        return String.join(" · ", parts);
    }

    private static void add(List<String> parts, String label, BigDecimal value, String unit) {
        if (value != null) {
            parts.add(label + " " + value.stripTrailingZeros().toPlainString() + unit);
        }
    }
}
