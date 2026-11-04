package com.example.fitapp.module.preset.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.preset.entity.PresetHidden;
import com.example.fitapp.module.preset.mapper.PresetHiddenMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

/**
 * 用户隐藏已下架的预置（ADR 0048）：这就是用户眼里的「删除」。
 * 隐藏进回收站——恢复＝取消隐藏，到期或彻底删除后一直隐藏；重新上架时由管理端整批清掉。
 */
@Service
@RequiredArgsConstructor
public class PresetHiddenService {

    private final PresetHiddenMapper mapper;
    private final TrashStore trashStore;
    private final TrashItemMapper trashItemMapper;

    /** 隐藏一条，并在回收站留一条恢复入口。title / summary 是回收站列表里显示的文字。 */
    @Transactional
    public void hide(Long userId, String kind, Long refId, String title, String summary) {
        if (mapper.hide(userId, kind, refId) > 0) {
            trashStore.put(userId, trashType(kind), refId, title, summary, 0L, Map.of());
        }
    }

    public boolean isHidden(Long userId, String kind, Long refId) {
        Long n = mapper.selectCount(Wrappers.<PresetHidden>lambdaQuery()
                .eq(PresetHidden::getUserId, userId)
                .eq(PresetHidden::getKind, kind)
                .eq(PresetHidden::getRefId, refId));
        return n != null && n > 0;
    }

    /** 取消隐藏（回收站恢复时调用）。 */
    public void unhide(Long userId, String kind, Long refId) {
        mapper.delete(Wrappers.<PresetHidden>lambdaQuery()
                .eq(PresetHidden::getUserId, userId)
                .eq(PresetHidden::getKind, kind)
                .eq(PresetHidden::getRefId, refId));
    }

    /** 重新上架：所有用户对这些预置的隐藏作废，回收站里对应的条目一并清掉。 */
    @Transactional
    public void clearAll(String kind, Collection<Long> refIds) {
        if (refIds == null || refIds.isEmpty()) {
            return;
        }
        mapper.delete(Wrappers.<PresetHidden>lambdaQuery()
                .eq(PresetHidden::getKind, kind)
                .in(PresetHidden::getRefId, refIds));
        trashItemMapper.delete(Wrappers.<TrashItem>query()
                .eq("type", trashType(kind))
                .in("source_id", refIds));
    }

    static String trashType(String kind) {
        return PresetHidden.FOOD.equals(kind) ? TrashItem.PRESET_FOOD : TrashItem.PRESET_EXERCISE;
    }
}
