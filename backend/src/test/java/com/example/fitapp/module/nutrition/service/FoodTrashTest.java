package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.trash.service.TrashStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 恢复一定成功（ADR 0047）：重名时改名，不报错。 */
@ExtendWith(MockitoExtension.class)
class FoodTrashTest {

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private TrashItemMapper trashItemMapper;

    private FoodTrash trash() {
        return new FoodTrash(foodMapper, new TrashStore(trashItemMapper, new ObjectMapper()));
    }


    @Test
    void 没重名就按原名原_id_写回() {
        when(foodMapper.selectCount(any())).thenReturn(0L);
        // 快照里多出来的字段（列后来被迁移删了）不能挡住恢复
        TrashItem i = new TrashItem();
        i.setSnapshot("{\"id\":5,\"userId\":7,\"name\":\"燕麦\"}");

        assertNull(trash().restore(7L, i));

        ArgumentCaptor<Food> saved = ArgumentCaptor.forClass(Food.class);
        verify(foodMapper).insert(saved.capture());
        assertEquals(5L, saved.getValue().getId());
        assertEquals("燕麦", saved.getValue().getName());
    }

    @Test
    void 重名加后缀_还撞就接着编号() {
        when(foodMapper.selectCount(any())).thenReturn(1L, 1L, 0L);
        TrashItem i = new TrashItem();
        i.setSnapshot("{\"id\":5,\"userId\":7,\"name\":\"燕麦\"}");

        String note = trash().restore(7L, i);

        assertTrue(note.contains("燕麦（恢复2）"));
    }

    @Test
    void 名字太长时截断原名_不超过列宽() {
        String longName = "长".repeat(64);
        when(foodMapper.selectCount(any())).thenReturn(1L, 0L);
        TrashItem i = new TrashItem();
        i.setSnapshot("{\"id\":5,\"userId\":7,\"name\":\"" + longName + "\"}");

        trash().restore(7L, i);

        ArgumentCaptor<Food> saved = ArgumentCaptor.forClass(Food.class);
        verify(foodMapper).insert(saved.capture());
        assertEquals(64, saved.getValue().getName().length());
        assertTrue(saved.getValue().getName().endsWith("（恢复）"));
    }
}
