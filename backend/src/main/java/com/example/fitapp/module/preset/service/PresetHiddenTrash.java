package com.example.fitapp.module.preset.service;

import com.example.fitapp.module.preset.entity.PresetHidden;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 隐藏的已下架预置进出回收站（ADR 0048）。不存快照：恢复只是删掉那条隐藏记录；
 * 到期或彻底删除时什么都不用做——隐藏记录留着，于是一直隐藏下去。
 * 食物和动作各注册一个处理器（{@link TrashHandler} 一个类型对应一个）。
 */
@Configuration
public class PresetHiddenTrash {

    @Bean
    TrashHandler presetFoodTrash(PresetHiddenService service) {
        return handler(service, TrashItem.PRESET_FOOD, PresetHidden.FOOD);
    }

    @Bean
    TrashHandler presetExerciseTrash(PresetHiddenService service) {
        return handler(service, TrashItem.PRESET_EXERCISE, PresetHidden.EXERCISE);
    }

    static TrashHandler handler(PresetHiddenService service, String type, String kind) {
        return new TrashHandler() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public String restore(Long userId, TrashItem item) {
                service.unhide(userId, kind, item.getSourceId());
                return null;
            }
        };
    }
}
