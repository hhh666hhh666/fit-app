package com.example.fitapp.module.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提交一次完整训练（会话页「结束训练」调它）：标题 ＋ 时长 ＋ 动作（含组次）。
 * 总容量由服务端按组次累加，不信任前端传来的数字。
 */
@Data
public class WorkoutCreateDTO {

    /** 来源计划（弱引用，可空；自由训练不传）。服务端校验归属，非法则忽略——不阻断训练入库。 */
    private Long planId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题过长")
    private String title;

    @Min(value = 0, message = "时长不正确")
    private Integer durationSec = 0;

    @Size(max = 500, message = "备注过长")
    private String note = "";

    @NotEmpty(message = "至少需要一个动作")
    @Valid
    private List<BlockDTO> blocks;

    @Data
    public static class BlockDTO {

        private Long exerciseId;

        @NotBlank(message = "动作名不能为空")
        @Size(max = 64, message = "动作名过长")
        private String name;

        @NotEmpty(message = "至少需要一组")
        @Valid
        private List<SetDTO> sets;
    }

    @Data
    public static class SetDTO {

        @NotNull(message = "重量不能为空")
        @DecimalMin(value = "0", message = "重量不能为负")
        private BigDecimal weight;

        @NotNull(message = "次数不能为空")
        @Min(value = 1, message = "次数至少为 1")
        private Integer reps;

        /** 该组净用时（秒，剔除暂停段，ADR 0022）；不传或 null ＝ 无用时（编辑时新增的组即此类）。 */
        @Min(value = 0, message = "组用时不能为负")
        private Integer durationSec;
    }
}
