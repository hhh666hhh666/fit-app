package com.example.fitapp.module.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建 / 更新计划（前端「保存修改」整体提交）：名称 ＋ 按顺序的完整动作编排。
 * 更新为整体替换（先清空再插入），不做增量 diff——编排数据量小，简单可靠优先。
 * 目标按组设置：`sets` 的**长度即组数**（ADR 0027），`targetSets` 由服务端同步为长度。
 */
@Data
public class PlanSaveDTO {

    @NotBlank(message = "计划名不能为空")
    @Size(max = 32, message = "计划名过长")
    private String name;

    @NotEmpty(message = "至少需要一个动作")
    @Valid
    private List<ItemDTO> exercises;

    @Data
    public static class ItemDTO {

        @NotNull(message = "动作不能为空")
        private Long exerciseId;

        /** 逐组目标；空/null = 不设目标（组数留空）。有值时按数组顺序即组序。 */
        @Size(max = 20, message = "组数过多")
        @Valid
        private List<SetDTO> sets;

        /**
         * 旧版客户端的兼容入口：只传目标组数、不传 sets 时，服务端按它展开成同值的 N 行。
         * 新版客户端不传本字段（组数以 sets 长度为准）。
         */
        @Min(value = 1, message = "目标组数至少为 1")
        @Max(value = 20, message = "目标组数过多")
        private Integer targetSets;

        /** 旧版兼容：展开 sets 时每行的默认次数。 */
        @Min(value = 1, message = "目标次数至少为 1")
        @Max(value = 100, message = "目标次数过多")
        private Integer targetReps;

        /** 旧版兼容：展开 sets 时每行的默认重量。 */
        @DecimalMin(value = "0", message = "目标重量不能为负")
        @DecimalMax(value = "999.99", message = "目标重量过大")
        private BigDecimal targetWeight;

        /** 循环组号；null = 不参与循环。多个动作同号即成一组，组内顺序＝数组顺序（ADR 0028）。 */
        @Min(value = 1, message = "循环组号至少为 1")
        @Max(value = 9, message = "循环组号过大")
        private Integer circuitNo;
    }

    /** 某一组的目标：次数／重量／层数都可为 null（组数由数组长度表达）。 */
    @Data
    public static class SetDTO {

        @Min(value = 1, message = "该组次数至少为 1")
        @Max(value = 100, message = "该组次数过多")
        private Integer reps;

        @DecimalMin(value = "0", message = "该组重量不能为负")
        @DecimalMax(value = "999.99", message = "该组重量过大")
        private BigDecimal weight;

        /**
         * 该组目标层数：只对 `metric = floors` 的动作（爬楼）有意义（ADR 0031）。
         * 上限取 51102（主人 2026-10-19 拍定），前端同一口径。
         */
        @Min(value = 1, message = "该组层数至少为 1")
        @Max(value = 51102, message = "该组层数过大")
        private Integer floors;
    }
}
