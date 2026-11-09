package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 餐次 AI 识别的结果（ADR 0052）：一份**临时食物**的草稿，不落库——用户在记录弹层里核对、
 * 补齐后由 {@code POST /api/diet/meals/custom} 记成一条临时条目。
 *
 * <p>营养一律是每 100（g 或 ml），{@link #grams} 是「这一份」有多少；前端按需换算成这一份的数。</p>
 */
@Data
public class MealDraftVO {

    /** photo＝饭菜照片（数值是估算）；label＝营养成分表（数值照表转录后换算）。 */
    private String kind;

    /** 名称；没认出时为空串。 */
    private String name;

    /** g 或 ml。 */
    private String unit;

    /**
     * 这一份的量：照片＝估算的整份重量；营养成分表＝表上印的每份量（按 100 印的为 null，用户自己填吃了多少）。
     */
    private BigDecimal grams;

    private BigDecimal kcal;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carb;
    /** 钠（mg）。 */
    private BigDecimal sodium;

    /** 不确定的字段 key（name / grams / kcal / protein / fat / carb / sodium），前端标「待核对」。 */
    private List<String> uncertain = new ArrayList<>();

    /** 没能给出的营养字段 key（kcal / protein / fat / carb / sodium），值为空，前端标「待补」。 */
    private List<String> missing = new ArrayList<>();
}
