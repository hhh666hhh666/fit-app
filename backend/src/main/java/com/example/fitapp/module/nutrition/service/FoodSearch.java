package com.example.fitapp.module.nutrition.service;

import lombok.Getter;

/**
 * 食物搜索的关键词怎么变成 SQL 参数（ADR 0056）。
 *
 * <p>筛选：关键词里的字按顺序出现在名字或别名里就算命中（「猪肠」命中「猪大肠」），中间可以隔别的字。
 * 排序先看匹配度，名字和每个别名取最好的那一档：完全一样 ＞ 开头一样 ＞ 连着包含 ＞ 只是按字顺序对得上。</p>
 *
 * <p>LIKE 的转义符用 {@code !}，不用反斜杠——反斜杠在 MySQL 的 NO_BACKSLASH_ESCAPES 模式下不是转义符。
 * 这里的字段是给 Mapper 注解 SQL 用的，一律走参数绑定，不拼进 SQL。</p>
 */
@Getter
public final class FoodSearch {

    /**
     * 分档用的「叫法串」：名字 ＋ 别名，按 | 切成一段一段，两头也补上 |。
     * 名字里的 ［ ］ 、 ， 也换成 |，于是「番茄［西红柿］」里的「西红柿」和别名一样算整段相等——
     * 预置名字方括号里的叫法本来就是别名（ADR 0056），不能比别名字段里的低一档。
     * 只用于排序分档，不影响筛选；名字圆括号里的逗号也会被切开，代价只是那一段也能算「开头一样」。
     * 是常量 SQL 片段，Mapper 里用 ${@...@SEGMENTS} 引用。
     */
    public static final String SEGMENTS = "CONCAT('|', REPLACE(REPLACE(REPLACE(REPLACE(food.name, '［', '|'), '］', '|'),"
            + " '、', '|'), '，', '|'), '|', IFNULL(food.aliases, ''), '|')";

    /** SQL 里 LIKE 要带的转义子句。 */
    public static final String ESCAPE = " ESCAPE '!'";

    /** 去掉首尾空白的原词：名字完全一样就用它比。 */
    private final String kw;
    /** 某个别名完全一样：{@code %|kw|%}，比的是 {@link #SEGMENTS}。 */
    private final String exactAlias;
    /** 名字开头一样：{@code kw%}。 */
    private final String prefix;
    /** 某个别名开头一样：{@code %|kw%}，比的是 {@link #SEGMENTS}。 */
    private final String prefixAlias;
    /** 连着包含：{@code %kw%}。 */
    private final String contains;
    /** 按字顺序：{@code %猪%肠%}，空白不算字。筛选用它。 */
    private final String fuzzy;

    private FoodSearch(String kw) {
        String e = escape(kw);
        this.kw = kw;
        this.exactAlias = "%|" + e + "|%";
        this.prefix = e + "%";
        this.prefixAlias = "%|" + e + "%";
        this.contains = "%" + e + "%";
        StringBuilder sb = new StringBuilder("%");
        kw.codePoints().filter(cp -> !Character.isWhitespace(cp)).forEach(cp ->
                sb.append(escape(new String(Character.toChars(cp)))).append('%'));
        this.fuzzy = sb.toString();
    }

    /** 没有关键词（null 或全是空白）返回 null。 */
    public static FoodSearch of(String q) {
        if (q == null) {
            return null;
        }
        String kw = q.strip();
        return kw.isEmpty() ? null : new FoodSearch(kw);
    }

    /**
     * 筛选条件（给 wrapper 的 apply 用）：名字或别名 LIKE {0}。
     * App 绑 {@link #getFuzzy()}（按字顺序命中），管理端绑 {@link #getContains()}（连着包含）。
     */
    public static final String FILTER =
            "(food.name LIKE {0}" + ESCAPE + " OR food.aliases LIKE {0}" + ESCAPE + ")";

    /**
     * ⚠️ 别名列带 TypeHandler：lambda 条件（{@code like(Food::getAliases, ..)}、{@code eq}）会拿它去转参数，
     * 传字符串直接报 ClassCastException。查别名一律走 {@code apply} 绑字符串，就像 {@link #FILTER}。
     */
    static String escape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
