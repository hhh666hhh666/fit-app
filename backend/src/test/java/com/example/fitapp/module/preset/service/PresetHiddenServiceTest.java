package com.example.fitapp.module.preset.service;

import com.example.fitapp.module.preset.mapper.PresetHiddenMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.trash.service.TrashStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯单元测试：重复删除同一条已下架预置，回收站里不能出现两条恢复入口。 */
@ExtendWith(MockitoExtension.class)
class PresetHiddenServiceTest {

    @Mock
    private PresetHiddenMapper mapper;

    @Mock
    private TrashStore trashStore;

    @Mock
    private TrashItemMapper trashItemMapper;

    private PresetHiddenService service() {
        return new PresetHiddenService(mapper, trashStore, trashItemMapper);
    }

    @Test
    void hide_firstTime_putsTrashEntry() {
        when(mapper.hide(7L, "food", 10L)).thenReturn(1);

        service().hide(7L, "food", 10L, "燕麦", "已下架的预置食物");

        verify(trashStore).put(eq(7L), eq(TrashItem.PRESET_FOOD), eq(10L), eq("燕麦"), any(), eq(0L), any());
    }

    @Test
    void hide_alreadyHidden_noSecondTrashEntry() {
        when(mapper.hide(7L, "exercise", 10L)).thenReturn(0);

        service().hide(7L, "exercise", 10L, "卧推", "已下架的预置动作");

        verify(trashStore, never()).put(anyLong(), anyString(), anyLong(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void trashRestore_unhides() {
        TrashItem item = new TrashItem();
        item.setSourceId(10L);

        PresetHiddenTrash.handler(service(), TrashItem.PRESET_FOOD, "food").restore(7L, item);

        verify(mapper).delete(any());
    }
}
