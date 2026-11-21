package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 给还没算拼音首字母的食物补算（ADR 0058，随应用启动执行）。
 *
 * <p>为什么不放在迁移脚本里：MySQL 没有汉字转拼音，只有应用里有拼音库。V44 只加列，实际的值由这里补上。</p>
 *
 * <p>幂等：只挑 {@code initials IS NULL} 的行，算完就不再是 NULL；常态下一行都挑不到。
 * 不阻断启动：出错只记日志，没算上的下次启动再补，期间只是这些食物不能用首字母搜到。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FoodInitialsBackfill implements ApplicationRunner {

    private final FoodMapper foodMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<Food> pending;
        try {
            pending = foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                    .select(Food::getId, Food::getName, Food::getAliases)
                    .isNull(Food::getInitials));
        } catch (Exception e) {
            log.warn("查询待补拼音首字母的食物失败，跳过：{}", e.toString());
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        int done = 0;
        for (Food f : pending) {
            try {
                foodMapper.update(null, Wrappers.<Food>lambdaUpdate()
                        .eq(Food::getId, f.getId())
                        .isNull(Food::getInitials)
                        .set(Food::getInitials, FoodInitials.of(f.getName(), f.getAliases())));
                done++;
            } catch (Exception e) {
                log.warn("食物 {} 补拼音首字母失败：{}", f.getId(), e.toString());
            }
        }
        log.info("补算拼音首字母：{} / {} 条食物", done, pending.size());
    }
}
