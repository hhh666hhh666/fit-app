package com.example.fitapp.module.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.admin.vo.ImportPreviewVO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.exercise.service.ExerciseService;
import com.example.fitapp.module.exercise.storage.ExerciseMediaStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 预置动作的 JSON 导入导出（ADR 0055）。按 ADR 0049，批量变动预置只走这里，不写迁移脚本。
 *
 * <p>每一条按下面的顺序认「是不是已有的那条」：
 * <ol>
 *   <li>带 {@code id}：必须是现有预置，整体覆盖（名字、肌群和资料）；</li>
 *   <li>带 {@code sourceId}、库里已有同编号的预置：同上；</li>
 *   <li>库里有<b>同名</b>、还没有编号的预置：认领它——只补资料和编号，名字、肌群不动
 *       （手工建的老预置被人练过，改了肌群会让历史统计变口径）；</li>
 *   <li>都不是：新建，排到最后，记录口径按重量×次数。</li>
 * </ol>
 * 记录口径、上下架、排序一律不动。无效的条目跳过，不挡其余的。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminExerciseImport {

    static final int NAME_MAX = 64;
    static final int NAME_EN_MAX = 96;
    static final int SHORT_MAX = 32;
    static final int SECONDARY_MAX = 128;
    static final int STEPS_MAX = 4000;
    private static final Pattern SOURCE_ID = Pattern.compile("^\\d{4}$");

    private final ExerciseMapper exerciseMapper;
    private final ObjectMapper objectMapper;

    /** 导出全部预置（含已下架），格式就是导入格式。 */
    public List<Map<String, Object>> export() {
        return exerciseMapper.selectList(Wrappers.<Exercise>lambdaQuery()
                        .isNull(Exercise::getUserId)
                        .orderByAsc(Exercise::getSortOrder)
                        .orderByAsc(Exercise::getId))
                .stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("sourceId", e.getSourceId());
                    m.put("name", e.getName());
                    m.put("nameEn", e.getNameEn());
                    m.put("muscle", e.getMuscle());
                    m.put("metric", e.getMetric());
                    m.put("equipment", e.getEquipment());
                    m.put("target", e.getTarget());
                    m.put("secondary", split(e.getSecondary(), "、"));
                    m.put("steps", split(e.getSteps(), "\n"));
                    m.put("media", e.getMedia());
                    m.put("archived", Boolean.TRUE.equals(e.getArchived()));
                    return m;
                })
                .toList();
    }

    /** 预检：只读不写。 */
    public ImportPreviewVO preview(String text) {
        return toVO(plan(text));
    }

    /** 正式导入：重新预检一遍，有效的在一个事务里写入，出错整批回滚。 */
    @Transactional
    public ImportPreviewVO importText(String text) {
        Plan plan = plan(text);
        int sort = nextSortOrder();
        for (Planned p : plan.items()) {
            Item it = p.item();
            if (p.target() == null) {
                Exercise e = new Exercise();
                e.setUserId(null);
                e.setName(it.name());
                e.setMuscle(it.muscle());
                e.setMetric(AdminExerciseService.METRIC_DEFAULT);
                e.setArchived(false);
                e.setSortOrder(sort++);
                fill(e, it);
                exerciseMapper.insert(e);
            } else {
                var w = Wrappers.<Exercise>lambdaUpdate()
                        .eq(Exercise::getId, p.target().getId())
                        .isNull(Exercise::getUserId)
                        .set(Exercise::getSourceId, it.sourceId())
                        .set(Exercise::getNameEn, it.nameEn())
                        .set(Exercise::getEquipment, it.equipment())
                        .set(Exercise::getTarget, it.target())
                        .set(Exercise::getSecondary, it.secondary())
                        .set(Exercise::getSteps, it.steps())
                        .set(Exercise::getMedia, it.media());
                if (!p.adopt()) {
                    w.set(Exercise::getName, it.name()).set(Exercise::getMuscle, it.muscle());
                }
                exerciseMapper.update(null, w);
            }
        }
        return toVO(plan);
    }

    // ── 预检 ──

    record Item(String sourceId, String name, String nameEn, String muscle, String equipment, String target,
                String secondary, String steps, String media) {
    }

    /** target 为空＝新建；adopt＝按同名认领的老预置（只补资料）。 */
    record Planned(Exercise target, boolean adopt, Item item) {
    }

    record Plan(List<Planned> items, List<ImportPreviewVO.Invalid> invalid) {
    }

    Plan plan(String text) {
        List<JsonNode> nodes = nodes(text);
        List<Exercise> presets = exerciseMapper.selectList(Wrappers.<Exercise>lambdaQuery()
                .select(Exercise.class, f -> !"steps".equals(f.getColumn()))
                .isNull(Exercise::getUserId));
        Map<Long, Exercise> byId = new HashMap<>();
        Map<String, Exercise> bySource = new HashMap<>();
        Map<String, Exercise> byName = new HashMap<>();
        for (Exercise e : presets) {
            byId.put(e.getId(), e);
            if (e.getSourceId() != null) bySource.put(e.getSourceId(), e);
            byName.putIfAbsent(e.getName(), e);
        }

        List<Planned> items = new ArrayList<>();
        List<ImportPreviewVO.Invalid> invalid = new ArrayList<>();
        Set<Long> claimed = new HashSet<>();
        Set<String> seenSource = new HashSet<>();
        Set<String> seenName = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode n = nodes.get(i);
            int index = i + 1;
            String name = text(n, "name");
            Item item;
            try {
                item = read(n, name);
            } catch (IllegalArgumentException ex) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, ex.getMessage()));
                continue;
            }
            if (item.sourceId() != null && !seenSource.add(item.sourceId())) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, "sourceId " + item.sourceId() + " 在文件里出现了不止一次"));
                continue;
            }
            if (!seenName.add(item.name())) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, "名字在文件里出现了不止一次"));
                continue;
            }

            Exercise target = null;
            boolean adopt = false;
            JsonNode idNode = n.get("id");
            if (idNode != null && !idNode.isNull()) {
                target = idNode.canConvertToLong() ? byId.get(idNode.asLong()) : null;
                if (target == null) {
                    invalid.add(new ImportPreviewVO.Invalid(index, name, "id " + idNode.asText() + " 不是现有的预置动作"));
                    continue;
                }
            } else if (item.sourceId() != null && bySource.containsKey(item.sourceId())) {
                target = bySource.get(item.sourceId());
            } else {
                Exercise same = byName.get(item.name());
                if (same != null && same.getSourceId() == null) {
                    target = same;
                    adopt = true;
                }
            }
            if (target != null && !claimed.add(target.getId())) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, "和文件里前面的条目对上了同一个动作「" + target.getName() + "」"));
                continue;
            }
            // 换了编号会撞上另一行的唯一键
            if (target != null && item.sourceId() != null) {
                Exercise holder = bySource.get(item.sourceId());
                if (holder != null && !holder.getId().equals(target.getId())) {
                    invalid.add(new ImportPreviewVO.Invalid(index, name, "sourceId " + item.sourceId() + " 已经属于「" + holder.getName() + "」"));
                    continue;
                }
            }
            // 名字不能和别的预置撞上（认领时名字不变，不用查）
            if (!adopt) {
                Exercise same = byName.get(item.name());
                if (same != null && (target == null || !same.getId().equals(target.getId()))) {
                    invalid.add(new ImportPreviewVO.Invalid(index, name, "和现有预置「" + same.getName() + "」重名"));
                    continue;
                }
            }
            items.add(new Planned(target, adopt, item));
        }
        return new Plan(items, invalid);
    }

    private Item read(JsonNode n, String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("缺少名字");
        if (name.length() > NAME_MAX) throw new IllegalArgumentException("名字过长");
        String muscle = text(n, "muscle");
        if (!ExerciseService.MUSCLES.contains(muscle)) throw new IllegalArgumentException("肌群不正确：" + muscle);
        String sourceId = blankToNull(text(n, "sourceId"));
        if (sourceId != null && !SOURCE_ID.matcher(sourceId).matches()) {
            throw new IllegalArgumentException("sourceId 应为 4 位数字");
        }
        String media = blankToNull(text(n, "media"));
        if (media != null && !ExerciseMediaStorage.KEY.matcher(media).matches()) {
            throw new IllegalArgumentException("media 格式不正确");
        }
        String secondary = join(n.get("secondary"), "、");
        String steps = join(n.get("steps"), "\n");
        return new Item(sourceId, name,
                limit(blankToNull(text(n, "nameEn")), NAME_EN_MAX, "英文名"), muscle,
                limit(blankToNull(text(n, "equipment")), SHORT_MAX, "器械"),
                limit(blankToNull(text(n, "target")), SHORT_MAX, "目标肌"),
                limit(secondary, SECONDARY_MAX, "辅助肌群"),
                limit(steps, STEPS_MAX, "要领"),
                media);
    }

    private List<JsonNode> nodes(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 格式不正确");
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root != null && root.isArray()) {
            root.forEach(nodes::add);
        } else if (root != null && root.isObject()) {
            nodes.add(root);
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 顶层应为数组");
        }
        return nodes;
    }

    private static ImportPreviewVO toVO(Plan plan) {
        int update = (int) plan.items().stream().filter(p -> p.target() != null).count();
        return new ImportPreviewVO(plan.items().size() - update, update, plan.invalid());
    }

    private static void fill(Exercise e, Item it) {
        e.setSourceId(it.sourceId());
        e.setNameEn(it.nameEn());
        e.setEquipment(it.equipment());
        e.setTarget(it.target());
        e.setSecondary(it.secondary());
        e.setSteps(it.steps());
        e.setMedia(it.media());
    }

    private int nextSortOrder() {
        Exercise last = exerciseMapper.selectOne(Wrappers.<Exercise>lambdaQuery()
                .select(Exercise::getSortOrder)
                .isNull(Exercise::getUserId)
                .orderByDesc(Exercise::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    /** 数组按分隔符拼起来（也接受直接给一个字符串）；空的返回 null。 */
    private static String join(JsonNode v, String sep) {
        if (v == null || v.isNull()) return null;
        List<String> parts = new ArrayList<>();
        if (v.isArray()) {
            v.forEach(x -> {
                String s = x.asText("").replace("\n", " ").trim();
                if (!s.isEmpty()) parts.add(s);
            });
        } else {
            String s = v.asText("").trim();
            if (!s.isEmpty()) parts.add(s);
        }
        return parts.isEmpty() ? null : String.join(sep, parts);
    }

    private static String limit(String s, int max, String label) {
        if (s != null && s.length() > max) throw new IllegalArgumentException(label + "过长");
        return s;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    private static List<String> split(String s, String sep) {
        if (!StringUtils.hasText(s)) return List.of();
        return Arrays.stream(s.split(Pattern.quote(sep))).filter(StringUtils::hasText).toList();
    }
}
