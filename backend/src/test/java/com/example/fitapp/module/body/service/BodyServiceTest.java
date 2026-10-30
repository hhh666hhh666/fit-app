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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 全空录入混库；「阶段最低」在单条时误亮；较上周取错基准（cutoff 之后的记录被当成上周）；
 * 序列漏掉按日取最小；明细漏掉纯围度记录；删除/修改放过别人的记录。
 */
@ExtendWith(MockitoExtension.class)
class BodyServiceTest {

    @Mock
    private BodyTrash bodyTrash;

    @Mock
    private BodyRecordMapper bodyRecordMapper;

    private BodyService service() {
        return new BodyService(bodyRecordMapper, bodyTrash);
    }

    private static BodyRecord rec(long id, LocalDateTime at, String weight,
                                  String chest, String waist, String hip, String arm, String note) {
        BodyRecord r = new BodyRecord();
        r.setId(id);
        r.setUserId(7L);
        r.setMeasuredAt(at);
        r.setWeight(weight == null ? null : new BigDecimal(weight));
        r.setChest(chest == null ? null : new BigDecimal(chest));
        r.setWaist(waist == null ? null : new BigDecimal(waist));
        r.setHip(hip == null ? null : new BigDecimal(hip));
        r.setArm(arm == null ? null : new BigDecimal(arm));
        r.setNote(note == null ? "" : note);
        return r;
    }

    /** 当天 8:00，省得每个用例都写时刻。 */
    private static LocalDateTime at(int day, int hour) {
        return LocalDateTime.of(2026, 10, day, hour, 0);
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
    void create_defaultsNow_andTrimsNote() {
        BodySaveDTO dto = save("72.5", null, null, null, null);
        dto.setNote("  晨起空腹  ");

        service().create(7L, dto);

        ArgumentCaptor<BodyRecord> captor = ArgumentCaptor.forClass(BodyRecord.class);
        verify(bodyRecordMapper).insert(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().getMeasuredAt().toLocalDate());
        assertEquals("晨起空腹", captor.getValue().getNote());
    }

    /** 不传 note 时必须是空串——note 列 NOT NULL，插 null 会炸。 */
    @Test
    void create_noteAbsent_becomesEmptyString() {
        service().create(7L, save("72.5", null, null, null, null));

        ArgumentCaptor<BodyRecord> captor = ArgumentCaptor.forClass(BodyRecord.class);
        verify(bodyRecordMapper).insert(captor.capture());
        assertEquals("", captor.getValue().getNote());
    }

    // 数值范围由 Bean Validation 在 web 层拦（DTO @DecimalMin/@DecimalMax + Controller @Valid），
    // 不属于 service 业务逻辑，不在此测试。

    @Test
    void overview_latestWeightedRecord_girthFromAnyRecord() {
        LocalDateTime d1 = at(4, 8);
        LocalDateTime d2 = at(8, 8);
        LocalDateTime d3 = at(11, 8);
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
        LocalDateTime d1 = at(4, 8);
        LocalDateTime d2 = at(11, 8);
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
                rec(1, at(4, 8), "73.0", null, null, null, null, ""),
                rec(2, at(5, 8), "72.8", null, null, null, null, ""),
                rec(3, at(11, 8), "72.5", null, null, null, null, "")
        ));

        BodyOverviewVO vo = service().overview(7L);

        // 72.5 − 73.0 = −0.5（基准是 10-04，不是 10-05）
        assertEquals(0, vo.getWeekDiff().compareTo(new BigDecimal("-0.5")));
    }

    /** 同一天多条：序列必须并成一个点、取当天最小（ADR 0017）。 */
    @Test
    void overview_weightSeries_sameDayTakesMin() {
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, at(11, 8), "72.6", null, null, null, null, "晨起"),
                rec(2, at(11, 20), "72.3", null, null, null, null, "练后"),
                rec(3, at(12, 8), "72.9", null, null, null, null, "")
        ));

        BodyOverviewVO vo = service().overview(7L);

        assertEquals(2, vo.getWeightSeries().size());
        assertEquals(LocalDate.of(2026, 10, 11), vo.getWeightSeries().get(0).getDate());
        assertEquals(0, vo.getWeightSeries().get(0).getWeight().compareTo(new BigDecimal("72.3")));
        assertEquals(LocalDate.of(2026, 10, 12), vo.getWeightSeries().get(1).getDate());
    }

    /** 围度序列：四个部位**各自**取当天最小，没量的部位保持 null。 */
    @Test
    void overview_girthSeries_sameDayTakesMinPerPart() {
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, at(11, 8), null, "98", "82", null, null, ""),   // 只量了胸、腰
                rec(2, at(11, 20), null, "97", "83", "95", null, ""),  // 同日再量：胸更小、腰更大
                rec(3, at(12, 8), null, "96", null, null, "35", "")
        ));

        BodyOverviewVO vo = service().overview(7L);

        assertEquals(2, vo.getGirthSeries().size());
        BodyOverviewVO.GirthPoint d11 = vo.getGirthSeries().get(0);
        assertEquals(LocalDate.of(2026, 10, 11), d11.getDate());
        assertEquals(0, d11.getChest().compareTo(new BigDecimal("97")));  // min(98, 97)
        assertEquals(0, d11.getWaist().compareTo(new BigDecimal("82")));  // min(82, 83)
        assertEquals(0, d11.getHip().compareTo(new BigDecimal("95")));    // 只有第二条有
        assertNull(d11.getArm());                                         // 当天没量臂围

        BodyOverviewVO.GirthPoint d12 = vo.getGirthSeries().get(1);
        assertEquals(0, d12.getChest().compareTo(new BigDecimal("96")));
        assertNull(d12.getWaist());
        assertEquals(0, d12.getArm().compareTo(new BigDecimal("35")));
    }

    @Test
    void records_descWithAdjacentDiff() {
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, at(4, 8), "73.0", null, null, null, null, ""),
                rec(2, at(8, 8), "72.7", null, null, null, null, ""),
                rec(3, at(11, 8), "72.5", null, null, null, null, "")
        ));

        List<BodyRecordVO> rows = service().records(7L, 20, 0);

        assertEquals(3, rows.size());
        assertEquals(at(11, 8), rows.get(0).getDate());
        assertEquals(0, rows.get(0).getDiff().compareTo(new BigDecimal("-0.2")));
        assertEquals(0, rows.get(1).getDiff().compareTo(new BigDecimal("-0.3")));
        assertNull(rows.get(2).getDiff()); // 首条没有「上一条」
    }

    /** offset 跳过最新的 N 条——前端触底分页靠它，跳错会导致翻页时重复或漏行。 */
    @Test
    void records_offset_skipsNewest() {
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, at(4, 8), "73.0", null, null, null, null, ""),
                rec(2, at(8, 8), "72.7", null, null, null, null, ""),
                rec(3, at(11, 8), "72.5", null, null, null, null, "")
        ));

        List<BodyRecordVO> page2 = service().records(7L, 20, 1);

        assertEquals(2, page2.size());
        assertEquals(at(8, 8), page2.get(0).getDate());
        assertEquals(at(4, 8), page2.get(1).getDate());
    }

    /** 明细不再只查体重：只记围度的那条必须出现（否则它既看不见也删不掉）。 */
    @Test
    void records_includesGirthOnlyRow_andDiffSkipsIt() {
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, at(4, 8), "73.0", null, null, null, null, ""),
                rec(2, at(8, 8), null, "98", "82", "95", "36", "围度测定"),
                rec(3, at(11, 8), "72.5", null, null, null, null, "")
        ));

        List<BodyRecordVO> rows = service().records(7L, 20, 0);

        assertEquals(3, rows.size());
        // 最新在前：10-11 → 10-08（纯围度）→ 10-04
        assertEquals(at(11, 8), rows.get(0).getDate());
        assertEquals(0, rows.get(0).getDiff().compareTo(new BigDecimal("-0.5"))); // 跳过了纯围度那条

        BodyRecordVO girthOnly = rows.get(1);
        assertNull(girthOnly.getWeight());
        assertEquals(0, girthOnly.getChest().compareTo(new BigDecimal("98")));
        assertNull(girthOnly.getDiff()); // 本条没体重，没有差值
    }

    @Test
    void update_overwritesValues_andRejectsMissingOrForeign() {
        when(bodyRecordMapper.selectById(1L)).thenReturn(rec(1, at(11, 8), "72.5", null, null, null, null, "旧备注"));

        BodySaveDTO dto = save("71.8", "97", null, null, null);
        dto.setMeasuredAt(at(11, 20));
        dto.setNote("");
        BodyRecordVO vo = service().update(7L, 1L, dto);

        assertEquals(at(11, 20), vo.getDate());
        assertEquals(0, vo.getWeight().compareTo(new BigDecimal("71.8")));
        assertEquals(0, vo.getChest().compareTo(new BigDecimal("97")));
        assertEquals("", vo.getNote());
        // 显式 update(null, wrapper)：not_null 策略下 updateById 跳不过空值，清空要靠它
        verify(bodyRecordMapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));

        // 不存在的 id
        when(bodyRecordMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service().update(7L, 9L, save("72", null, null, null, null)));

        // 别人的记录
        BodyRecord foreign = rec(2, at(11, 8), "72.5", null, null, null, null, "");
        foreign.setUserId(8L);
        when(bodyRecordMapper.selectById(2L)).thenReturn(foreign);
        assertThrows(BusinessException.class, () -> service().update(7L, 2L, save("72", null, null, null, null)));
    }

    /** 空值在查库之前就被拒——所以这里不打 selectById 的桩（打了反而说明顺序错了）。 */
    @Test
    void update_allEmpty_throws() {
        assertThrows(BusinessException.class, () -> service().update(7L, 1L, save(null, null, null, null, null)));
        verify(bodyRecordMapper, never()).updateById(any(BodyRecord.class));
    }

    /** 批量删除：任一条不归属则整批不动（不能留「删了一半」）。 */
    @Test
    void batchDelete_foreignId_rollsBackWholeBatch() {
        when(bodyRecordMapper.selectById(1L)).thenReturn(rec(1, at(11, 8), "72.5", null, null, null, null, ""));
        BodyRecord foreign = rec(2, at(12, 8), "72.4", null, null, null, null, "");
        foreign.setUserId(8L);
        when(bodyRecordMapper.selectById(2L)).thenReturn(foreign);

        assertThrows(BusinessException.class, () -> service().batchDelete(7L, List.of(1L, 2L)));

        // 显式写 Long.class：BaseMapper 同时有 deleteById(Serializable) 与 deleteById(T)，裸 any() 会歧义
        verify(bodyRecordMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void batchDelete_ownIds_deletesEachOnceAfterDistinct() {
        when(bodyRecordMapper.selectById(1L)).thenReturn(rec(1, at(11, 8), "72.5", null, null, null, null, ""));
        when(bodyRecordMapper.selectById(2L)).thenReturn(rec(2, at(12, 8), "72.4", null, null, null, null, ""));

        // 重复 id 只删一次
        service().batchDelete(7L, List.of(1L, 2L, 1L));

        verify(bodyRecordMapper, times(1)).deleteById(1L);
        verify(bodyRecordMapper, times(1)).deleteById(2L);
    }

    @Test
    void delete_foreignId_throws() {
        BodyRecord foreign = rec(1, at(11, 8), "72.5", null, null, null, null, "");
        foreign.setUserId(8L);
        when(bodyRecordMapper.selectById(1L)).thenReturn(foreign);

        assertThrows(BusinessException.class, () -> service().delete(7L, 1L));
        verify(bodyRecordMapper, never()).deleteById(any(Long.class));
    }
}
