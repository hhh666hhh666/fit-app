package com.example.fitapp.module.trash.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.trash.dto.AbandonedSessionDTO;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.trash.vo.TrashItemVO;
import com.example.fitapp.module.trash.vo.TrashRestoreVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯单元测试：恢复与彻底删除的归属校验、到期清理不互相牵连、丢掉的训练去重。 */
@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    @Mock
    private TrashItemMapper mapper;

    @Mock
    private TrashHandler photoHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TrashService service() {
        when(photoHandler.type()).thenReturn(TrashItem.MEAL_PHOTO);
        return new TrashService(mapper, new TrashStore(mapper, objectMapper), objectMapper,
                List.of(photoHandler, new SessionTrash()));
    }

    private static TrashItem item(long id, String type, String snapshot) {
        TrashItem i = new TrashItem();
        i.setId(id);
        i.setUserId(7L);
        i.setType(type);
        i.setSnapshot(snapshot);
        i.setDeletedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        return i;
    }

    @Test
    void 恢复后条目从回收站消失_提示原样带回() {
        TrashItem i = item(1L, TrashItem.MEAL_PHOTO, "{}");
        when(mapper.selectList(any())).thenReturn(List.of(i));
        when(photoHandler.restore(7L, i)).thenReturn("改了名");

        List<TrashRestoreVO> out = service().restore(7L, List.of(1L, 1L));

        assertEquals("改了名", out.get(0).getNote());
        verify(mapper).deleteById(1L);
    }

    @Test
    void 恢复丢掉的训练把快照交回前端() {
        TrashItem i = item(2L, TrashItem.SESSION, "{\"abandonedAt\":5,\"session\":{\"startedAt\":1}}");
        when(mapper.selectList(any())).thenReturn(List.of(i));

        List<TrashRestoreVO> out = service().restore(7L, List.of(2L));

        assertEquals(5L, out.get(0).getSession().path("abandonedAt").asLong());
        verify(mapper).deleteById(2L);
    }

    @Test
    void 有一条不归属就整批不动() {
        when(mapper.selectList(any())).thenReturn(List.of(item(1L, TrashItem.MEAL_PHOTO, "{}")));

        assertThrows(BusinessException.class, () -> service().restore(7L, List.of(1L, 2L)));

        verify(mapper, never()).deleteById(any(Long.class));
        verify(photoHandler, never()).restore(any(), any());
    }

    @Test
    void 彻底删除先删行再清文件() {
        TrashItem i = item(1L, TrashItem.MEAL_PHOTO, "{}");
        when(mapper.selectList(any())).thenReturn(List.of(i));
        when(mapper.deleteById(1L)).thenReturn(1);

        service().purge(7L, List.of(1L));

        verify(photoHandler).purge(i);
    }

    @Test
    void 行没删掉就不碰文件() {
        TrashItem i = item(1L, TrashItem.MEAL_PHOTO, "{}");
        when(mapper.selectList(any())).thenReturn(List.of(i));
        when(mapper.deleteById(1L)).thenReturn(0);

        service().purge(7L, List.of(1L));

        verify(photoHandler, never()).purge(any());
    }

    @Test
    void 到期清理单条失败不影响后面的() {
        TrashItem a = item(1L, TrashItem.MEAL_PHOTO, "{}");
        TrashItem b = item(2L, TrashItem.MEAL_PHOTO, "{}");
        when(mapper.selectList(any())).thenReturn(List.of(a, b));
        when(mapper.deleteById(1L)).thenThrow(new RuntimeException("库抖了"));
        when(mapper.deleteById(2L)).thenReturn(1);

        assertEquals(1, service().purgeExpired());
        verify(photoHandler).purge(b);
    }

    @Test
    void 列表显示剩余天数_到期当天也至少是一天() {
        TrashItem fresh = item(1L, TrashItem.MEAL_PHOTO, "{}");
        TrashItem old = item(2L, TrashItem.WORKOUT, "{}");
        old.setDeletedAt(fresh.getDeletedAt().minusDays(40));
        when(mapper.selectList(any())).thenReturn(List.of(fresh, old));

        List<TrashItemVO> rows = service().list(7L, null, null, null);

        assertEquals(30L, rows.get(0).getRemainingDays());
        assertTrue(rows.get(0).getHasThumb());
        assertEquals(1L, rows.get(1).getRemainingDays());
    }

    private AbandonedSessionDTO session() throws Exception {
        AbandonedSessionDTO dto = new AbandonedSessionDTO();
        dto.setAbandonedAt(9L);
        dto.setSession(objectMapper.readTree("""
                {"startedAt":1790000000000,"title":"推胸","blocks":[{"sets":[{"done":true},{"done":false}]}]}
                """));
        return dto;
    }

    @Test
    void 丢掉的训练按开始时刻去重() throws Exception {
        when(mapper.selectCount(any())).thenReturn(1L);

        service().putAbandonedSession(7L, session());

        verify(mapper, never()).insert(any(TrashItem.class));
    }

    @Test
    void 丢掉的训练存下摘要和完整快照() throws Exception {
        when(mapper.selectCount(any())).thenReturn(0L);

        service().putAbandonedSession(7L, session());

        ArgumentCaptor<TrashItem> saved = ArgumentCaptor.forClass(TrashItem.class);
        verify(mapper).insert(saved.capture());
        assertEquals("推胸", saved.getValue().getTitle());
        assertTrue(saved.getValue().getSummary().endsWith("已完成 1 组"));
        assertEquals("session:1790000000000", saved.getValue().getDedupeKey());
        assertTrue(saved.getValue().getSnapshot().contains("\"abandonedAt\":9"));
    }

    @Test
    void 并发补传撞唯一键算成功() throws Exception {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(TrashItem.class))).thenThrow(new DuplicateKeyException("重复"));

        service().putAbandonedSession(7L, session());
    }

    @Test
    void 没有开始时刻的训练数据不收() throws Exception {
        AbandonedSessionDTO dto = new AbandonedSessionDTO();
        dto.setAbandonedAt(9L);
        dto.setSession(objectMapper.readTree("{\"title\":\"推胸\"}"));

        assertThrows(BusinessException.class, () -> service().putAbandonedSession(7L, dto));
    }
}
