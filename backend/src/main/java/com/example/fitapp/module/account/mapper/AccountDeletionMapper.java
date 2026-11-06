package com.example.fitapp.module.account.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 注销账号时的整批删除（ADR 0050）。表之间没有外键，所以先删没有 user_id、要顺着父表找的子表，
 * 再按 {@code AccountService.USER_TABLES} 逐张删带 user_id 的表。
 *
 * <p>⚠️ 新建表时：带 user_id 的加进 USER_TABLES，不带的子表在这里加一条按父表删的语句——
 * {@code AccountTablesCoverageTest} 会扫迁移脚本，漏了就红。
 */
public interface AccountDeletionMapper {

    @Delete("""
            DELETE ws FROM workout_set ws
              JOIN workout_block wb ON ws.block_id = wb.id
              JOIN workout w ON wb.workout_id = w.id
             WHERE w.user_id = #{userId}
            """)
    int deleteWorkoutSets(@Param("userId") Long userId);

    @Delete("""
            DELETE wb FROM workout_block wb
              JOIN workout w ON wb.workout_id = w.id
             WHERE w.user_id = #{userId}
            """)
    int deleteWorkoutBlocks(@Param("userId") Long userId);

    @Delete("""
            DELETE s FROM plan_exercise_set s
              JOIN plan_exercise pe ON s.plan_exercise_id = pe.id
              JOIN plan p ON pe.plan_id = p.id
             WHERE p.user_id = #{userId}
            """)
    int deletePlanExerciseSets(@Param("userId") Long userId);

    @Delete("""
            DELETE pe FROM plan_exercise pe
              JOIN plan p ON pe.plan_id = p.id
             WHERE p.user_id = #{userId}
            """)
    int deletePlanExercises(@Param("userId") Long userId);

    @Delete("""
            DELETE i FROM food_combo_item i
              JOIN food_combo c ON i.combo_id = c.id
             WHERE c.user_id = #{userId}
            """)
    int deleteFoodComboItems(@Param("userId") Long userId);

    @Delete("""
            DELETE r FROM diary_photo_ref r
              JOIN diary_entry e ON r.entry_id = e.id
             WHERE e.user_id = #{userId}
            """)
    int deleteDiaryPhotoRefs(@Param("userId") Long userId);

    /** 表名只来自 AccountService.USER_TABLES 这个常量清单，不接受外部输入。 */
    @Delete("DELETE FROM ${table} WHERE user_id = #{userId}")
    int deleteByUser(@Param("table") String table, @Param("userId") Long userId);

    @Delete("DELETE FROM `user` WHERE id = #{userId}")
    int deleteUser(@Param("userId") Long userId);
}
