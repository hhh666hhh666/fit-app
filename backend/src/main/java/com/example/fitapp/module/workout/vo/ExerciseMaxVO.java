package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 某动作的历史「最佳一组」——PR 判定的历史起点。
 * 口径（ADR 0024）：**重量优先，同重量比次数**（旧口径为单组 `weight×reps` 最大）。
 * ⚠️ 名字沿用旧称「Max」是**刻意**的（原义＝单组容量最大）——改名会让 ADR 0024 的记载失去可搜性。
 */
@Data
public class ExerciseMaxVO {

    private Long exerciseId;

    /** 该组重量（kg）——先比这一项。 */
    private BigDecimal weight;

    /** 该组次数——重量相同时比这一项。 */
    private Integer reps;
}
