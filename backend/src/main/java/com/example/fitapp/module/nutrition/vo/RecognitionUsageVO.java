package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

/**
 * 识别用量汇总（ADR 0018）：「我的」页外卡只露这两个数——累计 token ＋ 请求次数。
 * 不显示费用（拍定口径）；逐条明细走明细分页接口。
 */
@Data
public class RecognitionUsageVO {

    /** 累计 token（输入＋输出；含失败调用——token 真实花了）。 */
    private long totalTokens;

    /** 请求次数（含失败）。 */
    private long totalRequests;
}
