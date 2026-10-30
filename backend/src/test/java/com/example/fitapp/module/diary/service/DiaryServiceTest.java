package com.example.fitapp.module.diary.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.diary.dto.DiarySaveDTO;
import com.example.fitapp.module.diary.entity.DiaryEntry;
import com.example.fitapp.module.diary.mapper.DiaryEntryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯单元测试：全空保存、未来时刻、搜索通配符、改删别人的日记、删除时放掉图片。 */
@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock
    private DiaryEntryMapper entryMapper;

    @Mock
    private DiaryPhotoService photoService;

    private DiaryService service() {
        return new DiaryService(entryMapper, photoService);
    }

    private static DiarySaveDTO dto(LocalDateTime at, String title, String content) {
        DiarySaveDTO d = new DiarySaveDTO();
        d.setWrittenAt(at);
        d.setTitle(title);
        d.setContent(content);
        return d;
    }

    @Test
    void 标题和正文都空不能保存() {
        assertThrows(BusinessException.class, () -> service().create(7L, dto(null, "  ", " \n ")));
        verify(entryMapper, never()).insert(any(DiaryEntry.class));
    }

    @Test
    void 只有标题也能保存_时刻缺省为现在() {
        service().create(7L, dto(null, "  练腿日 ", null));

        ArgumentCaptor<DiaryEntry> saved = ArgumentCaptor.forClass(DiaryEntry.class);
        verify(entryMapper).insert(saved.capture());
        assertEquals("练腿日", saved.getValue().getTitle());
        assertEquals("", saved.getValue().getContent());
        assertEquals(0, saved.getValue().getWrittenAt().getSecond());
    }

    @Test
    void 时刻不能选未来_但容忍手机时钟快一点() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        assertThrows(BusinessException.class, () -> service().create(7L, dto(now.plusHours(1), "", "正文")));
        service().create(7L, dto(now.plusMinutes(1), "", "正文"));

        verify(entryMapper).insert(any(DiaryEntry.class));
    }

    @Test
    void 保存后按正文同步图片引用() {
        DiaryEntry existing = new DiaryEntry();
        existing.setId(3L);
        existing.setUserId(7L);
        existing.setWrittenAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        when(entryMapper.selectOne(any())).thenReturn(existing);

        service().update(7L, 3L, dto(null, "", "![图片](diary-photo:9)"));

        verify(photoService).syncRefs(7L, 3L, "![图片](diary-photo:9)");
        // 没传时刻就保留原来的，不能被改成现在
        assertEquals(LocalDateTime.of(2026, 10, 1, 8, 0), existing.getWrittenAt());
    }

    @Test
    void 批量删除里有别人的日记_整批不动() {
        DiaryEntry mine = new DiaryEntry();
        mine.setId(1L);
        when(entryMapper.selectOne(any())).thenReturn(mine).thenReturn(null);

        assertThrows(BusinessException.class, () -> service().batchDelete(7L, List.of(1L, 2L), false));

        verify(entryMapper, never()).deleteById(any(Long.class));
        verify(photoService, never()).releaseEntry(any(), anyBoolean());
    }

    @Test
    void 批量删除要放掉图片() {
        when(entryMapper.selectOne(any())).thenReturn(new DiaryEntry());

        service().batchDelete(7L, List.of(1L, 1L), true);

        verify(photoService).releaseEntry(1L, true);
        verify(entryMapper).deleteById(1L);
    }

    @Test
    void 搜索词里的通配符按字面算() {
        assertEquals("100\\%", DiaryService.escapeLike("100%"));
        assertEquals("a\\_b", DiaryService.escapeLike("a_b"));
        assertEquals("a\\\\b", DiaryService.escapeLike("a\\b"));
    }

    @Test
    void 月份格式不对直接报错() {
        assertThrows(BusinessException.class, () -> service().list(7L, "2026/10", null, 20, 0));
    }
}
