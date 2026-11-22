package com.example.fitapp.module.bowel.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.bowel.dto.BowelSaveDTO;
import com.example.fitapp.module.bowel.entity.BowelRecord;
import com.example.fitapp.module.bowel.mapper.BowelRecordMapper;
import com.example.fitapp.module.bowel.vo.BowelStatsVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BowelServiceTest {

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BowelRecord.class);
    }

    @Mock
    private BowelRecordMapper mapper;

    @Mock
    private BowelTrash trash;

    private BowelService service() {
        return new BowelService(mapper, trash);
    }

    private static BowelRecord rec(String kind, String at, Integer bristol) {
        BowelRecord r = new BowelRecord();
        r.setKind(kind);
        r.setOccurredAt(LocalDateTime.parse(at));
        r.setBristol(bristol);
        return r;
    }

    @Test
    void create_defaultsToNowAndDropsBristolForUrine() {
        BowelSaveDTO dto = new BowelSaveDTO();
        dto.setKind("urine");
        dto.setBristol(4);

        service().create(7L, dto);

        ArgumentCaptor<BowelRecord> captor = ArgumentCaptor.forClass(BowelRecord.class);
        verify(mapper).insert(captor.capture());
        assertNotNull(captor.getValue().getOccurredAt());
        assertEquals(0, captor.getValue().getOccurredAt().getSecond(), "只到分");
        assertNull(captor.getValue().getBristol(), "小便没有形态");
    }

    @Test
    void create_withoutKind_throws() {
        assertThrows(BusinessException.class, () -> service().create(7L, new BowelSaveDTO()));
    }

    @Test
    void delete_notOwn_throwsAndDoesNotTrash() {
        BowelRecord other = rec("stool", "2026-11-20T08:00", 4);
        other.setUserId(99L);
        when(mapper.selectById(3L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service().delete(7L, 3L));
        verify(trash, never()).put(any(), any());
    }

    @Test
    void stats_countsPerDayHoursBristolAndIntervals() {
        List<BowelRecord> rows = List.of(
                rec("stool", "2026-11-19T08:00", 4),
                rec("urine", "2026-11-19T09:00", null),
                rec("urine", "2026-11-19T13:00", null),
                rec("stool", "2026-11-20T08:30", 3),
                rec("stool", "2026-11-21T20:30", null));

        BowelStatsVO vo = BowelService.compute(rows, LocalDate.parse("2026-11-19"), LocalDate.parse("2026-11-21"));

        assertEquals(3, vo.getDays().size());
        assertEquals(1, vo.getDays().get(0).getStool());
        assertEquals(2, vo.getDays().get(0).getUrine());
        assertEquals(3, vo.getStool().getCount());
        assertEquals(1.0, vo.getStool().getPerDay());
        // 大便间隔：24.5h 与 36h → 平均 30.25 → 一位小数 30.3
        assertEquals(30.3, vo.getStool().getAvgIntervalHours());
        assertEquals(4.0, vo.getUrine().getAvgIntervalHours());
        assertEquals(2, vo.getStool().getHours()[8]);
        assertEquals(1, vo.getBristol()[3]);
        assertEquals(1, vo.getBristol()[2]);
    }

    @Test
    void stats_singleRecordHasNoInterval() {
        BowelStatsVO vo = BowelService.compute(List.of(rec("stool", "2026-11-21T08:00", null)),
                LocalDate.parse("2026-11-21"), LocalDate.parse("2026-11-21"));
        assertNull(vo.getStool().getAvgIntervalHours());
        assertNull(vo.getUrine().getAvgIntervalHours());
    }
}
