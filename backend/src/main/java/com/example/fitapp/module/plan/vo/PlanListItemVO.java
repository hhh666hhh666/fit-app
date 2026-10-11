package com.example.fitapp.module.plan.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划列表卡：名字、动作数与摘要（最多 3 个名字，前端补「+N」）、上次训练与容量。
 * 前三个字段来自 SQL，exerciseNames 由服务层填充。
 */
@Data
public class PlanListItemVO {

    private Long id;

    private String name;

    private Integer exerciseCount;

    /** 动作名摘要（至多 3 个）。 */
    private List<String> exerciseNames = new ArrayList<>();

    /** 上次练该计划的时间；从未练过为 null。 */
    private LocalDateTime lastTrainedAt;

    /** 上次练该计划的总容量 kg；从未练过为 null。 */
    private BigDecimal lastVolume;
}
