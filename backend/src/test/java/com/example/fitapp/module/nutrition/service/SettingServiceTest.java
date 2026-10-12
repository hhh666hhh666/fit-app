package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.module.nutrition.dto.SettingDTO;
import com.example.fitapp.module.nutrition.entity.UserSetting;
import com.example.fitapp.module.nutrition.mapper.UserSettingMapper;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试：行不存在时读默认值；更新必须走显式 set（目标可清空——not_null 策略会跳过 null）。
 */
@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    /** lambdaUpdate().set(...) 立即解析列名，需要 TableInfo 缓存。 */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserSetting.class);
    }

    @Mock
    private UserSettingMapper userSettingMapper;

    private SettingService service() {
        return new SettingService(userSettingMapper);
    }

    private static SettingDTO dto() {
        SettingDTO d = new SettingDTO();
        d.setPhotoQuality("high");
        d.setWaterStepMl(300);
        d.setTargetKcal(new BigDecimal("2200"));
        return d;
    }

    @Test
    void get_defaultsWhenRowAbsent() {
        when(userSettingMapper.selectOne(any())).thenReturn(null);

        SettingVO vo = service().get(7L);

        assertEquals("medium", vo.getPhotoQuality());
        assertEquals(250, vo.getWaterStepMl());
        assertNull(vo.getTargetKcal());
    }

    @Test
    void get_returnsStoredValues() {
        UserSetting s = new UserSetting();
        s.setUserId(7L);
        s.setPhotoQuality("low");
        s.setWaterStepMl(500);
        s.setTargetKcal(new BigDecimal("2000"));
        s.setTargetWater(2500);
        when(userSettingMapper.selectOne(any())).thenReturn(s);

        SettingVO vo = service().get(7L);

        assertEquals("low", vo.getPhotoQuality());
        assertEquals(500, vo.getWaterStepMl());
        assertEquals(Integer.valueOf(2500), vo.getTargetWater());
    }

    @Test
    void update_insertsWhenRowAbsent() {
        when(userSettingMapper.selectOne(any())).thenReturn(null);

        service().update(7L, dto());

        ArgumentCaptor<UserSetting> captor = ArgumentCaptor.forClass(UserSetting.class);
        verify(userSettingMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("high", captor.getValue().getPhotoQuality());
        assertEquals(300, captor.getValue().getWaterStepMl());
        assertEquals(new BigDecimal("2200"), captor.getValue().getTargetKcal());
    }

    @Test
    void update_usesExplicitSetSoTargetsCanBeCleared() {
        UserSetting existing = new UserSetting();
        existing.setUserId(7L);
        existing.setPhotoQuality("medium");
        existing.setWaterStepMl(250);
        existing.setTargetKcal(new BigDecimal("2200"));
        when(userSettingMapper.selectOne(any())).thenReturn(existing);

        service().update(7L, dto());

        // 关键断言：走 update(entity=null, wrapper) 显式 set 路径（目标传 null 要能清掉）。
        verify(userSettingMapper).update(ArgumentMatchers.<UserSetting>isNull(), any());
        verify(userSettingMapper, never()).updateById(any(UserSetting.class));
    }
}
