package com.example.fitapp.module.nutrition.service;

import net.sourceforge.pinyin4j.PinyinHelper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 食物名字和别名的拼音首字母（ADR 0058）：关键词全是字母时，拿它来比（{@code zr} 搜出「猪肉」）。
 *
 * <p>存法：名字按 ［ ］ 、 ， 切成几段（和 {@link FoodSearch#SEGMENTS} 一样，方括号里的叫法算别名），
 * 再加上每个别名，每段算出首字母串，用 | 连起来、两头补 |：「番茄［西红柿］」→ {@code |fq|xhs|}。</p>
 *
 * <p>多音字每种读法都算：「重庆」→ {@code |zq|cq|}。一段的组合数超过 {@link #MAX_VARIANTS} 时，
 * 后面的多音字只取第一种读法，免得长名字组合爆炸。字母、数字原样小写保留（「AD钙奶」→ {@code adgn}），
 * 其余符号丢掉。</p>
 */
public final class FoodInitials {

    /** 一段最多列几种读法组合。 */
    static final int MAX_VARIANTS = 8;
    /** 和 V44 的列宽一致。 */
    static final int MAX_LENGTH = 1000;

    private FoodInitials() {
    }

    /** 算整条食物的首字母串；没有任何可算的字时返回 {@code "|"}（不是 null，null 留给「还没算」）。 */
    public static String of(String name, List<String> aliases) {
        Set<String> all = new LinkedHashSet<>();
        if (name != null) {
            for (String seg : name.split("[［］、，]")) {
                all.addAll(variants(seg));
            }
        }
        if (aliases != null) {
            for (String a : aliases) {
                all.addAll(variants(a));
            }
        }
        StringBuilder sb = new StringBuilder("|");
        for (String v : all) {
            if (sb.length() + v.length() + 1 > MAX_LENGTH) {
                break;
            }
            sb.append(v).append('|');
        }
        return sb.toString();
    }

    /** 一段文字的所有首字母串（去重、保持先后）；没有可算的字时为空。 */
    static List<String> variants(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        out.add("");
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            List<Character> initials = initialsOf(cp);
            if (initials.isEmpty()) {
                continue;
            }
            if (out.size() * initials.size() > MAX_VARIANTS) {
                initials = initials.subList(0, 1);
            }
            List<String> next = new ArrayList<>(out.size() * initials.size());
            for (String prefix : out) {
                for (char c : initials) {
                    next.add(prefix + c);
                }
            }
            out = next;
        }
        out.removeIf(String::isEmpty);
        return out.stream().distinct().toList();
    }

    /** 一个字的首字母：汉字取各读音的首字母（去重，常用读音在前），字母数字取它自己，其余为空。 */
    private static List<Character> initialsOf(int cp) {
        if ((cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9')) {
            return List.of((char) cp);
        }
        if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN || Character.isSupplementaryCodePoint(cp)) {
            return List.of();
        }
        String[] readings = PinyinHelper.toHanyuPinyinStringArray((char) cp);
        if (readings == null) {
            return List.of();
        }
        List<Character> out = new ArrayList<>(2);
        for (String r : readings) {
            if (r != null && !r.isEmpty()) {
                char c = Character.toLowerCase(r.charAt(0));
                if (c >= 'a' && c <= 'z' && !out.contains(c)) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
