package com.example.fitapp.module.body.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.body.dto.BodySaveDTO;
import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.body.vo.BodyOverviewVO;
import com.example.fitapp.module.body.vo.BodyRecordVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 全空录入混库；「阶段最低」在单条时误亮；较上周取错基准（cutoff 之后的记录被当成上周）。
 */
@ExtendWith(MockitoExtension.class)
class BodyServiceTest {

    @Mock
    private BodyRecordMapper bodyRecordMapper;

    private BodyService service() {
        return new BodyService(bodyRecordMapper);
    }

    private static BodyRecord rec(long id, LocalDate date, String weight,
                                  String chest, String waist, String hip, String arm, String note) {
        BodyRecord r = new BodyRecord();
        r.setId(id);
        r.setUserId(7L);
        r.setMeasuredAt(date);
        r.setWeight(weight == null ? null : new BigDecimal(weight));
        r.setChest(chest == null ? null : new BigDecimal(chest));
        r.setWaist(waist == null ? null : new BigDecimal(waist));
        r.setHip(hip == null ? null : new BigDecimal(hip));
        r.setArm(arm == null ? null : new BigDecimal(arm));
        r.setNote(note == null ? "" : note);
        return r;
    }

    private static BodySaveDTO save(String weight, String chest, String waist, String hip, String arm) {
        BodySaveDTO dto = new BodySaveDTO();
        dto.setWeight(weight == null ? null : new BigDecimal(weight));
        dto.setChest(chest == null ? null : new BigDecimal(chest));
        dto.setWaist(waist == null ? null : new BigDecimal(waist));
        dto.setHip(hip == null ? null : new BigDecimal(hip));
        dto.setArm(arm == null ? null : new BigDecimal(arm));
        return dto;
    }

    @Test
    void create_allEmpty_throws() {
        assertThrows(BusinessException.class, () -> service().create(7L, save(null, null, null, null, null)));
    }

    @Test
    void create_defaultsToday_andTrimsNote() {
        BodySaveDTO dto = save("72.5", null, null, null, null);
        dto.setNote("  晨起空腹  ");

        service().create(7L, dto);

        ArgumentCaptor<BodyRecord> captor = ArgumentCaptor.forClass(BodyRecord.class);
        org.mockito.Mockito.verify(bodyRecordMapper).insert(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().getMeasuredAt());
        assertEquals("晨起空腹", captor.getValue().getNote());
    }

    // 数值范围由 Bean Validation 在 web 层拦（DTO @DecimalMin/@DecimalMax + Controller @Valid），
    // 不属于 service 业务逻辑，不在此测试。

    @Test
    void overview_latestWeightedRecord_girthFromAnyRecord() {
        LocalDate d1 = LocalDate.of(2026, 10, 4);
        LocalDate d2 = LocalDate.of(2026, 10, 8);
        LocalDate d3 = LocalDate.of(2026, 10, 11);
        // 10-08 只记了围度（不算体重序列与 latest）
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, d1, "73.0", null, null, null, null, "晨起空腹"),
                rec(2, d2, null, "98", "82", "95", "36", "围度测定"),
                rec(3, d3, "72.5", null, null, null, null, "晨起空腹")
        ));

        BodyOverviewVO vo = service().overview(7L);

        assertEquals(d3, vo.getLatest().getDate());
        assertEquals(0, vo.getLatest().getWeight().compareTo(new BigDecimal("72.5")));
        assertEquals(2, vo.getWeightSeries().size());
        // 围度来自 10-08 的记录
        assertEquals(d2, vo.getGirth().getDate());
        assertEquals(0, vo.getGirth().getChest().compareTo(new BigDecimal("98")));
    }

    @Test
    void overview_isLowest_needsTwoPoints_andLatestIsMin() {
        LocalDate d1 = LocalDate.of(2026, 10, 4);
        LocalDate d2 = LocalDate.of(2026, 10, 11);
        // 单条：没有「阶段」可言
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, d2, "72.5", null, null, null, null, "")));
        assertFalse(service().overview(7L).getIsLowest());

        // 两条且最新＝最低
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, d1, "73.0", null, null, null, null, ""),
                rec(2, d2, "72.5", null, null, null, null, "")));
        assertTrue(service().overview(7L).getIsLowest());

        // 两条但最新更重
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, d1, "72.0", null, null, null, null, ""),
                rec(2, d2, "72.5", null, null, null, null, "")));
        assertFalse(service().overview(7L).getIsLowest());
    }

    @Test
    void overview_weekDiff_usesLatestBeforeCutoff() {
        // latest=10-11（72.5），cutoff=10-04；10-05 的记录在 cutoff 之后，不能当「上周」
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, LocalDate.of(2026, 10, 4), "73.0", null, null, null, null, ""),
                rec(2, LocalDate.of(2026, 10, 5), "72.8", null, null, null, null, ""),
                rec(3, LocalDate.of(2026, 10, 11), "72.5", null, null, null, null, "")
        ));

        BodyOverviewVO vo = service().overview(7L);

        // 72.5 − 73.0 = −0.5（基准是 10-04，不是 10-05）
        assertEquals(0, vo.getWeekDiff().compareTo(new BigDecimal("-0.5")));
    }

    @Test
    void records_descWithAdjacentDiff() {
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, LocalDate.of(2026, 10, 4), "73.0", null, null, null, null, ""),
                rec(2, LocalDate.of(2026, 10, 8), "72.7", null, null, null, null, ""),
                rec(3, LocalDate.of(2026, 10, 11), "72.5", null, null, null, null, "")
        ));

        List<BodyRecordVO> rows = service().records(7L, 20);

        assertEquals(3, rows.size());
        assertEquals(LocalDate.of(2026, 10, 11), rows.get(0).getDate());
        assertEquals(0, rows.get(0).getDiff().compareTo(new BigDecimal("-0.2")));
        assertEquals(0, rows.get(1).getDiff().compareTo(new BigDecimal("-0.3")));
        assertNull(rows.get(2).getDiff()); // 首条没有「上一条」
    }
}
