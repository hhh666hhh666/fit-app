package com.example.fitapp.module.nutrition.vo;

import com.example.fitapp.module.nutrition.entity.FoodRecognition;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;

/**
 * 识别明细的一条详情（ADR 0053、0054）：流水的全部字段（平铺）＋ 价格估算。
 */
@Data
public class RecognitionLogDetailVO {

    @JsonUnwrapped
    private FoodRecognition log;

    /** 价格估算；目前只算 DeepSeek 的模型。 */
    private RecognitionPriceVO price;
}
