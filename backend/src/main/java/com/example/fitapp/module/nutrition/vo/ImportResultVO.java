package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 导入结果：成功数 / 跳过（同名）/ 失败（原因逐条）。
 */
@Data
public class ImportResultVO {

    private int imported;

    private List<String> skipped = new ArrayList<>();

    private List<Failed> failed = new ArrayList<>();

    @Data
    public static class Failed {

        private String name;

        private String reason;

        public Failed() {
        }

        public Failed(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }
}
