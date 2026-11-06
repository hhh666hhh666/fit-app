package com.example.fitapp.module.nutrition.vo;

import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 照片识别结果（ADR 0018）：**草稿，不落库**——保存由既有的 POST /api/foods 完成
 * （用户在校对页确认后走原路径，识别功能不碰写入逻辑）。
 */
@Data
public class FoodDraftVO {

    /** 预填表单的草稿（字段与「编辑食物」页一一对应）。 */
    private FoodSaveDTO draft;

    /** 低置信度字段 key（前端在这些字段旁标「待核对」）。 */
    private List<String> uncertain = new ArrayList<>();

    /**
     * 标签上没找到的必填字段 key（4+1 中的；草稿里对应值为 null）。前端在这些字段旁标「待补」，
     * 用户补齐才能保存。全缺不会走到这里——那是「没认出营养成分表」，直接报错。
     */
    private List<String> missing = new ArrayList<>();

    /** 撞名：用户自建库里已有同名食物的 id（null＝不撞）。撞名给「编辑已有的 / 改名新建」二选一。 */
    private Long duplicateFoodId;

    /** 撞名时已有食物的名字（冗余带回，前端弹层少查一次）。 */
    private String duplicateFoodName;
}
