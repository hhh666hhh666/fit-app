package com.example.fitapp.module.preset;

/**
 * 「一条预置对某个用户是否可见」的 SQL 片段（ADR 0048），给 MyBatis-Plus 的 {@code apply} 用，
 * 占位符 {@code {0}} 是用户 id（同一个占位符可以出现多次）。
 *
 * <p>规则：上架的预置人人可见；已下架的预置只有<b>用过</b>它、而且没有自己隐藏它的人看得到。
 * 「用过」——食物：这个用户的饮食记录或食物组合引用过；动作：这个用户的训练或计划引用过。
 * 回收站里的记录已经从原表移走（ADR 0047），自然不算。</p>
 *
 * <p>片段直接引用表名（{@code food.id}、{@code exercise.id}），只能用在以该表为主表、且没有别名的查询里。</p>
 */
public final class PresetSql {

    private PresetSql() {
    }

    /** 这个用户用过这条食物（{@code food} 为外层表）。 */
    public static final String FOOD_USED =
            "(EXISTS (SELECT 1 FROM meal_log ml WHERE ml.user_id = {0} AND ml.food_id = food.id)"
                    + " OR EXISTS (SELECT 1 FROM food_combo_item ci JOIN food_combo fc ON fc.id = ci.combo_id"
                    + " WHERE fc.user_id = {0} AND ci.food_id = food.id))";

    /** 这个用户用过这个动作（{@code exercise} 为外层表）。 */
    public static final String EXERCISE_USED =
            "(EXISTS (SELECT 1 FROM workout_block wb JOIN workout w ON w.id = wb.workout_id"
                    + " WHERE w.user_id = {0} AND wb.exercise_id = exercise.id)"
                    + " OR EXISTS (SELECT 1 FROM plan_exercise pe JOIN plan p ON p.id = pe.plan_id"
                    + " WHERE p.user_id = {0} AND pe.exercise_id = exercise.id))";

    /** 食物库列表、搜索、计数里，这一行对这个用户可见：自建的，或可见的预置。 */
    public static final String FOOD_VISIBLE =
            "(food.user_id = {0} OR (food.user_id IS NULL AND (food.archived = 0 OR ("
                    + "NOT EXISTS (SELECT 1 FROM preset_hidden ph WHERE ph.user_id = {0}"
                    + " AND ph.kind = 'food' AND ph.ref_id = food.id) AND " + FOOD_USED + "))))";

    /** 只看预置部分（计数里「预置几条」用）。 */
    public static final String FOOD_PRESET_VISIBLE =
            "(food.user_id IS NULL AND (food.archived = 0 OR ("
                    + "NOT EXISTS (SELECT 1 FROM preset_hidden ph WHERE ph.user_id = {0}"
                    + " AND ph.kind = 'food' AND ph.ref_id = food.id) AND " + FOOD_USED + ")))";

    /** 动作库列表里，这一行对这个用户可见：没删的自定义动作，或可见的预置。 */
    public static final String EXERCISE_VISIBLE =
            "((exercise.user_id = {0} AND exercise.archived = 0) OR (exercise.user_id IS NULL AND (exercise.archived = 0 OR ("
                    + "NOT EXISTS (SELECT 1 FROM preset_hidden ph WHERE ph.user_id = {0}"
                    + " AND ph.kind = 'exercise' AND ph.ref_id = exercise.id) AND " + EXERCISE_USED + "))))";
}
