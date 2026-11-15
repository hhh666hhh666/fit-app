package com.example.fitapp.module.nutrition.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 食物别名的规整与校验（ADR 0056）：顿号、逗号、分号、斜杠、竖线或空白都当分隔；
 * 去掉首尾空白和重复的，和这条食物名字一样的直接丢掉。最多 {@value #MAX_COUNT} 个，每个不超过 {@value #MAX_LEN} 个字。
 */
public final class FoodAliases {

    public static final int MAX_COUNT = 10;
    public static final int MAX_LEN = 16;
    /** 库里的分隔符；搜索的 SQL 也靠它判断「整个别名相等」「别名开头」。 */
    public static final String SEP = "|";

    private static final Pattern SPLIT = Pattern.compile("[、，,；;/|\\s]+");

    private FoodAliases() {
    }

    /** 规整结果：error 不为空时 aliases 无意义。aliases 为 null＝没有别名。 */
    public record Parsed(List<String> aliases, String error) {
    }

    public static Parsed parse(List<String> raw, String name) {
        if (raw == null || raw.isEmpty()) {
            return new Parsed(null, null);
        }
        String self = name == null ? "" : name.trim();
        Set<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            for (String part : SPLIT.split(item.strip())) {
                String a = part.strip();
                if (a.isEmpty() || a.equals(self)) {
                    continue;
                }
                if (a.codePointCount(0, a.length()) > MAX_LEN) {
                    return new Parsed(null, "别名「" + a + "」超过 " + MAX_LEN + " 个字");
                }
                out.add(a);
            }
        }
        if (out.size() > MAX_COUNT) {
            return new Parsed(null, "别名最多 " + MAX_COUNT + " 个");
        }
        return new Parsed(out.isEmpty() ? null : new ArrayList<>(out), null);
    }

    /** 存库的写法；没有别名返回 null。 */
    public static String join(List<String> aliases) {
        return aliases == null || aliases.isEmpty() ? null : String.join(SEP, aliases);
    }

    /** 库里的写法读回来；空串或 null 返回 null。 */
    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String a : text.split(Pattern.quote(SEP))) {
            if (!a.isBlank()) {
                out.add(a);
            }
        }
        return out.isEmpty() ? null : out;
    }
}
