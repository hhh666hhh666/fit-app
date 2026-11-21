package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.conditions.interfaces.Join;
import lombok.Getter;

/**
 * 食物搜索的关键词怎么变成 SQL 参数（ADR 0056，分档与拼音见 ADR 0058）。
 *
 * <p>筛选：关键词里的字按顺序出现在名字或别名里就算命中（「猪肠」命中「猪大肠」），中间可以隔别的字；
 * 关键词全是字母时，名字或别名的拼音首字母连着包含它也算命中（{@code zr} 命中「猪肉」）。
 * 排序只分两档：名字、名字方括号里的叫法、某个别名或某段首字母<b>完全一样</b>的在前，其余命中的在后；
 * 档内再比收藏、记录次数、自建（见 FoodMapper.search）。</p>
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
    /** 连着包含：{@code %kw%}。 */
    private final String contains;
    /** 按字顺序：{@code %猪%肠%}，空白不算字。筛选用它。 */
    private final String fuzzy;
    /** 关键词全是字母时，某段首字母完全一样：{@code %|zr|%}；否则 null（不走首字母）。 */
    private final String initialsExact;
    /** 关键词全是字母时，首字母连着包含：{@code %zr%}；否则 null。 */
    private final String initialsContains;

    private FoodSearch(String kw) {
        String e = escape(kw);
        this.kw = kw;
        this.exactAlias = "%|" + e + "|%";
        this.contains = "%" + e + "%";
        if (kw.matches("[A-Za-z]+")) {
            String lower = kw.toLowerCase();
            this.initialsExact = "%|" + lower + "|%";
            this.initialsContains = "%" + lower + "%";
        } else {
            this.initialsExact = null;
            this.initialsContains = null;
        }
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

    /** 关键词全是字母时的筛选：在 {@link #FILTER} 之外，首字母 LIKE {1} 也算命中。 */
    static final String FILTER_WITH_INITIALS =
            "(food.name LIKE {0}" + ESCAPE + " OR food.aliases LIKE {0}" + ESCAPE + " OR food.initials LIKE {1})";

    /**
     * 把筛选条件放进 wrapper：名字或别名 LIKE {@code pattern}（App 给 {@link #getFuzzy()}、管理端给 {@link #getContains()}），
     * 关键词全是字母时再加首字母。列表和计数必须走同一个方法，否则食物库页滚动到底的判断会错。
     */
    public void filter(Join<?> w, String pattern) {
        if (initialsContains == null) {
            w.apply(FILTER, pattern);
        } else {
            w.apply(FILTER_WITH_INITIALS, pattern, initialsContains);
        }
    }

    /**
     * ⚠️ 别名列带 TypeHandler：lambda 条件（{@code like(Food::getAliases, ..)}、{@code eq}）会拿它去转参数，
     * 传字符串直接报 ClassCastException。查别名一律走 {@code apply} 绑字符串，就像 {@link #FILTER}。
     */
    static String escape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
