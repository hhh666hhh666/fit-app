package com.example.fitapp.module.trash.service;

import com.example.fitapp.module.trash.entity.TrashItem;
import org.springframework.stereotype.Component;

/** 「开新的」丢掉的训练：数据本来就只在手机上，恢复时把快照交回去，这里不用写库（ADR 0047）。 */
@Component
public class SessionTrash implements TrashHandler {

    @Override
    public String type() {
        return TrashItem.SESSION;
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        return null;
    }
}
