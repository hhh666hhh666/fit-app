package com.example.fitapp.module.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.Result;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.agent.vo.AgentCurrentVO;
import com.example.fitapp.module.agent.vo.AgentHistoryRows;
import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.nutrition.entity.DietDaily;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.mapper.DietDailyMapper;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.mapper.MealPhotoMapper;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.service.FoodService;
import com.example.fitapp.module.nutrition.service.SettingService;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import com.example.fitapp.module.plan.service.PlanService;
import com.example.fitapp.module.plan.service.ScheduleService;
import com.example.fitapp.module.plan.vo.PlanDetailVO;
import com.example.fitapp.module.plan.vo.PlanListItemVO;
import com.example.fitapp.module.plan.vo.ScheduleDayVO;
import com.example.fitapp.module.profile.entity.UserProfile;
import com.example.fitapp.module.profile.mapper.UserProfileMapper;
import com.example.fitapp.module.profile.service.EnergyService;
import com.example.fitapp.module.profile.vo.EnergyVO;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.entity.WorkoutBlock;
import com.example.fitapp.module.workout.entity.WorkoutSet;
import com.example.fitapp.module.workout.mapper.WorkoutBlockMapper;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.mapper.WorkoutSetMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外部 agent 取数（ADR 0043）：把散在各模块的数据拼成一份**语义化**的输出，自己不存任何东西。
 *
 * <p>{@code history} 不设时间上限，而生产后端堆上限只有 256 MB，所以一律分批读库、边读边写，
 * 只有每日汇总和被引用的 id 集合留在内存里（都随天数线性增长，多年也很小）。</p>
 */
@Service
public class AgentDataService {

    static final int SCHEMA_VERSION = 1;
    private static final String TIMEZONE = "Asia/Shanghai";

    /** 每批行数：一次训练要带动作与组次，批小一些；其余都是单行。 */
    private static final int WORKOUT_BATCH = 100;
    private static final int ROW_BATCH = 500;
    private static final int FOOD_BATCH = 200;

    private static final String MUSCLE_CARDIO = "cardio";
    private static final String METRIC_FLOORS = "floors";
    private static final String METRIC_DEFAULT = "weight_reps";
    private static final String SOURCE_PRESET = "preset";
    private static final String SOURCE_CUSTOM = "custom";
    private static final List<String> MEAL_ORDER = List.of("breakfast", "lunch", "dinner", "snack");

    /** 口径说明：agent 拿不到本仓库文档，读不懂这些数字就会分析错。 */
    static final List<String> DEFINITIONS = List.of(
            "时间都是 Asia/Shanghai 本地时间，不带时区；日期归属按业务发生时间，训练按开始时刻（跨零点算开始那天）。",
            "单位：重量 kg，身高与围度 cm，热量 kcal，三大营养素 g，饮水 ml，时长秒。",
            "总容量：一次训练里力量动作已完成组的 Σ(重量×次数)，有氧不计入；只宜同动作或同计划纵向对比，不是可横向比较的成绩。",
            "PR：组次的 pr 标记表示提交或编辑保存时刷新了该动作的历史最重一组（重量优先，同重量比次数），不是估算 1RM；有氧动作不判定。",
            "记录口径：metric=weight_reps 看重量和次数；metric=floors 是爬楼，只看层数，重量和次数恒为 null。",
            "训练消耗（calories）由主人手填，系统不估算；null 表示没填，不代表没消耗。",
            "每日总消耗＝基础代谢（Mifflin-St Jeor）× 生活活动系数 ＋ 当天手填的训练消耗。系数只含日常生活，不含专门训练。"
                    + "体重取那天结束前最后一次测得的值；身高、性别、活动量只有当前值，所以历史消耗是近似值。",
            "热量缺口（gapKcal）＝每日总消耗 − 摄入，正数是缺口，负数是盈余。饮食记录可能不全，没记不等于没吃，缺口只是粗略参考；"
                    + "请结合 mealEntryCount 与 mealsLogged 判断这天记全了没有。",
            "体脂率只记录，不参与任何计算；身体档案每条记哪项填哪项，没量的项为 null。",
            "计划是执行蓝本，训练中的临时改动不回写计划；计划、周安排、个人档案、饮食目标都只有当前值，没有历史。",
            "饮食目标是主人手动设定的当前值，不是推算值，也不代表过去某天的目标。",
            "餐次条目的 per100 是记录时的快照（只有热量和三大营养素），改删食物不影响历史；foods 里的完整营养是当前值，"
                    + "不是记录时的值；食物已删除时 foods 里没有对应条目。",
            "预置食物里名字带 * 的，营养数据由公开资料补全，可信度低一档。",
            "history 的结果是这个时间范围的完整数据，请整段覆盖本地副本，不要按 id 合并：记录被删后只会从结果里消失，"
                    + "没有删除标记。id 不保证长期稳定（编辑一次训练会重写它的动作和组次）。");

    private final ObjectMapper mapper;
    private final UserProfileMapper userProfileMapper;
    private final BodyRecordMapper bodyRecordMapper;
    private final WorkoutMapper workoutMapper;
    private final WorkoutBlockMapper blockMapper;
    private final WorkoutSetMapper setMapper;
    private final ExerciseMapper exerciseMapper;
    private final MealLogMapper mealLogMapper;
    private final MealPhotoMapper mealPhotoMapper;
    private final DietDailyMapper dietDailyMapper;
    private final FoodMapper foodMapper;
    private final FoodService foodService;
    private final SettingService settingService;
    private final PlanService planService;
    private final ScheduleService scheduleService;
    private final EnergyService energyService;

    public AgentDataService(ObjectMapper objectMapper,
                            UserProfileMapper userProfileMapper,
                            BodyRecordMapper bodyRecordMapper,
                            WorkoutMapper workoutMapper,
                            WorkoutBlockMapper blockMapper,
                            WorkoutSetMapper setMapper,
                            ExerciseMapper exerciseMapper,
                            MealLogMapper mealLogMapper,
                            MealPhotoMapper mealPhotoMapper,
                            DietDailyMapper dietDailyMapper,
                            FoodMapper foodMapper,
                            FoodService foodService,
                            SettingService settingService,
                            PlanService planService,
                            ScheduleService scheduleService,
                            EnergyService energyService) {
        // 全局配置是 non_null：这里要把 null 明确写出来——「爬楼组的重量是 null」和「字段不存在」对 agent 是两回事。
        // 每写一行都 flush 会让流式输出的系统调用翻很多倍，由批次末尾统一 flush。
        this.mapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS)
                .disable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);
        this.userProfileMapper = userProfileMapper;
        this.bodyRecordMapper = bodyRecordMapper;
        this.workoutMapper = workoutMapper;
        this.blockMapper = blockMapper;
        this.setMapper = setMapper;
        this.exerciseMapper = exerciseMapper;
        this.mealLogMapper = mealLogMapper;
        this.mealPhotoMapper = mealPhotoMapper;
        this.dietDailyMapper = dietDailyMapper;
        this.foodMapper = foodMapper;
        this.foodService = foodService;
        this.settingService = settingService;
        this.planService = planService;
        this.scheduleService = scheduleService;
        this.energyService = energyService;
    }

    // ── 当前状态 ──

    public void writeCurrent(Long userId, OutputStream out) throws IOException {
        LocalDate today = LocalDate.now();
        UserProfile p = userProfileMapper.selectById(userId);
        AgentCurrentVO.Profile profile = p == null ? null : new AgentCurrentVO.Profile(
                p.getSex(), p.getBirthDate(), energyService.ageOf(p.getBirthDate(), today),
                p.getHeightCm(), p.getActivityLevel());

        SettingVO s = settingService.get(userId);
        AgentCurrentVO.DietTargets targets = new AgentCurrentVO.DietTargets(
                s.getTargetKcal(), s.getTargetProtein(), s.getTargetCarb(), s.getTargetFat(), s.getTargetWater());

        List<AgentCurrentVO.Plan> plans = new ArrayList<>();
        Map<Long, String> planNames = new LinkedHashMap<>();
        for (PlanListItemVO item : planService.list(userId)) {
            PlanDetailVO d = planService.detail(userId, item.getId());
            planNames.put(d.getId(), d.getName());
            plans.add(new AgentCurrentVO.Plan(d.getId(), d.getName(), d.getExercises().stream()
                    .map(AgentDataService::toPlanExercise)
                    .toList()));
        }

        List<AgentCurrentVO.ScheduleDay> week = new ArrayList<>();
        for (ScheduleDayVO d : scheduleService.week(userId)) {
            String state = Boolean.TRUE.equals(d.getIsRest()) ? "rest" : d.getPlanId() != null ? "plan" : "unset";
            week.add(new AgentCurrentVO.ScheduleDay(d.getDay(), state, d.getPlanId(), planNames.get(d.getPlanId())));
        }

        AgentCurrentVO vo = new AgentCurrentVO(SCHEMA_VERSION, LocalDateTime.now(), TIMEZONE, profile,
                energyService.compute(userId, today), targets, plans, week, DEFINITIONS);
        mapper.writeValue(out, Result.success(vo));
    }

    private static AgentCurrentVO.PlanExercise toPlanExercise(PlanDetailVO.Item i) {
        List<AgentCurrentVO.PlanSet> sets = i.getSets() == null ? List.of() : i.getSets().stream()
                .map(s -> new AgentCurrentVO.PlanSet(s.getSetIndex(), s.getReps(), s.getWeight(), s.getFloors()))
                .toList();
        return new AgentCurrentVO.PlanExercise(i.getExerciseId(), i.getName(), i.getMuscle(), i.getMetric(),
                i.getCircuitNo(), sets);
    }

    // ── 历史 ──

    /**
     * 流式写出历史数据。参数错误在写出任何内容之前抛出，由全局异常处理器转成信封。
     *
     * <p>⚠️ 中途失败时状态码已发出，改不了；这时**必须让 JSON 保持残缺**，agent 才能靠解析失败发现这次没拉全。
     * 所以关掉 AUTO_CLOSE_JSON_CONTENT，也不用 try-with-resources——否则 close 会替我们补全括号，
     * 把半截数据变成一份合法的 JSON，静默丢数据。</p>
     */
    public void writeHistory(Long userId, String fromText, String toText, OutputStream out) throws IOException {
        LocalDate from = parseDate(fromText, "from");
        LocalDate to = parseDate(toText, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "from 不能晚于 to");
        }

        JsonGenerator g = mapper.createGenerator(out);
        g.disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);

        Map<Long, Exercise> exercises = visibleExercises(userId);
        Map<LocalDate, DayTotals> days = new TreeMap<>();
        Set<Long> usedExerciseIds = new HashSet<>();
        Set<Long> usedFoodIds = new HashSet<>();

        g.writeStartObject();
        g.writeNumberField("code", ResultCode.SUCCESS.getCode());
        g.writeStringField("message", ResultCode.SUCCESS.getMessage());
        g.writeNumberField("timestamp", System.currentTimeMillis());
        g.writeObjectFieldStart("data");
        g.writeNumberField("schemaVersion", SCHEMA_VERSION);
        g.writeObjectField("generatedAt", LocalDateTime.now());
        g.writeStringField("timezone", TIMEZONE);
        g.writeObjectField("from", from);
        g.writeObjectField("to", to);

        writeWorkouts(g, userId, from, to, exercises, days, usedExerciseIds);
        writeMeals(g, userId, from, to, days, usedFoodIds);
        writeBody(g, userId, from, to);
        writeWater(g, userId, from, to, days);
        writePhotos(g, userId, from, to);
        writeDays(g, userId, days);
        writeExercises(g, userId, exercises, usedExerciseIds);
        writeFoods(g, userId, usedFoodIds);

        g.writeEndObject();
        g.writeEndObject();
        g.close();
    }

    private void writeWorkouts(JsonGenerator g, Long userId, LocalDate from, LocalDate to,
                               Map<Long, Exercise> exercises, Map<LocalDate, DayTotals> days,
                               Set<Long> usedExerciseIds) throws IOException {
        g.writeArrayFieldStart("workouts");
        LocalDateTime lastAt = null;
        Long lastId = null;
        while (true) {
            LocalDateTime afterAt = lastAt;
            Long afterId = lastId;
            LambdaQueryWrapper<Workout> q = Wrappers.<Workout>lambdaQuery()
                    .eq(Workout::getUserId, userId)
                    .ge(from != null, Workout::getStartedAt, from == null ? null : from.atStartOfDay())
                    .lt(to != null, Workout::getStartedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                    .and(afterAt != null, w -> w.gt(Workout::getStartedAt, afterAt)
                            .or(x -> x.eq(Workout::getStartedAt, afterAt).gt(Workout::getId, afterId)))
                    .orderByAsc(Workout::getStartedAt)
                    .orderByAsc(Workout::getId)
                    .last("LIMIT " + WORKOUT_BATCH);
            List<Workout> page = workoutMapper.selectList(q);
            if (page.isEmpty()) {
                break;
            }
            Map<Long, List<AgentHistoryRows.Block>> blocks = loadBlocks(page, exercises, usedExerciseIds);
            for (Workout w : page) {
                LocalDate date = w.getStartedAt().toLocalDate();
                DayTotals day = days.computeIfAbsent(date, d -> new DayTotals());
                day.workoutCount++;
                if (w.getCalories() != null) {
                    day.trainingKcal += w.getCalories();
                }
                g.writeObject(new AgentHistoryRows.Workout(w.getId(), w.getTitle(), w.getPlanId(), w.getStartedAt(),
                        date, w.getDurationSec(), w.getCalories(), w.getTotalVolume(), w.getNote(),
                        blocks.getOrDefault(w.getId(), List.of())));
            }
            g.flush();
            if (page.size() < WORKOUT_BATCH) {
                break;
            }
            Workout last = page.get(page.size() - 1);
            lastAt = last.getStartedAt();
            lastId = last.getId();
        }
        g.writeEndArray();
    }

    /** 一批训练的动作与组次，一次查齐（训练详情页那种逐块查法，在这里会变成上万次查询）。 */
    private Map<Long, List<AgentHistoryRows.Block>> loadBlocks(List<Workout> workouts, Map<Long, Exercise> exercises,
                                                               Set<Long> usedExerciseIds) {
        List<WorkoutBlock> blocks = blockMapper.selectList(Wrappers.<WorkoutBlock>lambdaQuery()
                .in(WorkoutBlock::getWorkoutId, workouts.stream().map(Workout::getId).toList())
                .orderByAsc(WorkoutBlock::getSortOrder)
                .orderByAsc(WorkoutBlock::getId));
        if (blocks.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<WorkoutSet>> setsByBlock = setMapper.selectList(Wrappers.<WorkoutSet>lambdaQuery()
                        .in(WorkoutSet::getBlockId, blocks.stream().map(WorkoutBlock::getId).toList())
                        .orderByAsc(WorkoutSet::getSetIndex))
                .stream()
                .collect(Collectors.groupingBy(WorkoutSet::getBlockId));

        Map<Long, List<AgentHistoryRows.Block>> out = new LinkedHashMap<>();
        for (WorkoutBlock b : blocks) {
            if (b.getExerciseId() != null) {
                usedExerciseIds.add(b.getExerciseId());
            }
            Exercise e = exercises.get(b.getExerciseId());
            String muscle = e == null ? null : e.getMuscle();
            String metric = e == null || e.getMetric() == null ? METRIC_DEFAULT : e.getMetric();
            List<AgentHistoryRows.Set> sets = setsByBlock.getOrDefault(b.getId(), List.of()).stream()
                    .map(s -> toSet(s, metric))
                    .toList();
            out.computeIfAbsent(b.getWorkoutId(), k -> new ArrayList<>()).add(new AgentHistoryRows.Block(
                    b.getExerciseId(), b.getExerciseName(), muscle, metric, MUSCLE_CARDIO.equals(muscle), sets));
        }
        return out;
    }

    /** 爬楼组的重量和次数在库里是占位值 0／1（ADR 0025），对外一律给 null，免得被当成真实成绩。 */
    private static AgentHistoryRows.Set toSet(WorkoutSet s, String metric) {
        boolean floors = METRIC_FLOORS.equals(metric) || s.getFloors() != null;
        return new AgentHistoryRows.Set(s.getSetIndex(), floors ? null : s.getWeight(), floors ? null : s.getReps(),
                s.getFloors(), Boolean.TRUE.equals(s.getPr()), s.getDurationSec());
    }

    private void writeMeals(JsonGenerator g, Long userId, LocalDate from, LocalDate to,
                            Map<LocalDate, DayTotals> days, Set<Long> usedFoodIds) throws IOException {
        g.writeArrayFieldStart("meals");
        LocalDate lastDate = null;
        Long lastId = null;
        while (true) {
            LocalDate afterDate = lastDate;
            Long afterId = lastId;
            List<MealLog> page = mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                    .eq(MealLog::getUserId, userId)
                    .ge(from != null, MealLog::getLogDate, from)
                    .le(to != null, MealLog::getLogDate, to)
                    .and(afterDate != null, w -> w.gt(MealLog::getLogDate, afterDate)
                            .or(x -> x.eq(MealLog::getLogDate, afterDate).gt(MealLog::getId, afterId)))
                    .orderByAsc(MealLog::getLogDate)
                    .orderByAsc(MealLog::getId)
                    .last("LIMIT " + ROW_BATCH));
            if (page.isEmpty()) {
                break;
            }
            for (MealLog m : page) {
                if (m.getFoodId() != null) {
                    usedFoodIds.add(m.getFoodId());
                }
                AgentHistoryRows.Nutrients actual = new AgentHistoryRows.Nutrients(
                        DietService.actual(m.getKcalPer100(), m.getGrams()),
                        DietService.actual(m.getProteinPer100(), m.getGrams()),
                        DietService.actual(m.getCarbPer100(), m.getGrams()),
                        DietService.actual(m.getFatPer100(), m.getGrams()));
                days.computeIfAbsent(m.getLogDate(), d -> new DayTotals()).addMeal(m.getMeal(), actual);
                g.writeObject(new AgentHistoryRows.Meal(m.getId(), m.getLogDate(), m.getMeal(), m.getFoodId(),
                        m.getFoodName(), m.getGrams(),
                        new AgentHistoryRows.Nutrients(m.getKcalPer100(), m.getProteinPer100(),
                                m.getCarbPer100(), m.getFatPer100()),
                        actual));
            }
            g.flush();
            if (page.size() < ROW_BATCH) {
                break;
            }
            MealLog last = page.get(page.size() - 1);
            lastDate = last.getLogDate();
            lastId = last.getId();
        }
        g.writeEndArray();
    }

    private void writeBody(JsonGenerator g, Long userId, LocalDate from, LocalDate to) throws IOException {
        g.writeArrayFieldStart("body");
        LocalDateTime lastAt = null;
        Long lastId = null;
        while (true) {
            LocalDateTime afterAt = lastAt;
            Long afterId = lastId;
            List<BodyRecord> page = bodyRecordMapper.selectList(Wrappers.<BodyRecord>lambdaQuery()
                    .eq(BodyRecord::getUserId, userId)
                    .ge(from != null, BodyRecord::getMeasuredAt, from == null ? null : from.atStartOfDay())
                    .lt(to != null, BodyRecord::getMeasuredAt, to == null ? null : to.plusDays(1).atStartOfDay())
                    .and(afterAt != null, w -> w.gt(BodyRecord::getMeasuredAt, afterAt)
                            .or(x -> x.eq(BodyRecord::getMeasuredAt, afterAt).gt(BodyRecord::getId, afterId)))
                    .orderByAsc(BodyRecord::getMeasuredAt)
                    .orderByAsc(BodyRecord::getId)
                    .last("LIMIT " + ROW_BATCH));
            if (page.isEmpty()) {
                break;
            }
            for (BodyRecord r : page) {
                g.writeObject(new AgentHistoryRows.Body(r.getId(), r.getMeasuredAt(), r.getWeight(), r.getBodyFat(),
                        r.getChest(), r.getWaist(), r.getHip(), r.getArm(), r.getNote()));
            }
            g.flush();
            if (page.size() < ROW_BATCH) {
                break;
            }
            BodyRecord last = page.get(page.size() - 1);
            lastAt = last.getMeasuredAt();
            lastId = last.getId();
        }
        g.writeEndArray();
    }

    private void writeWater(JsonGenerator g, Long userId, LocalDate from, LocalDate to,
                            Map<LocalDate, DayTotals> days) throws IOException {
        g.writeArrayFieldStart("water");
        LocalDate lastDate = null;
        while (true) {
            List<DietDaily> page = dietDailyMapper.selectList(Wrappers.<DietDaily>lambdaQuery()
                    .eq(DietDaily::getUserId, userId)
                    .ge(from != null, DietDaily::getLogDate, from)
                    .le(to != null, DietDaily::getLogDate, to)
                    .gt(lastDate != null, DietDaily::getLogDate, lastDate)
                    .orderByAsc(DietDaily::getLogDate)
                    .last("LIMIT " + ROW_BATCH));
            if (page.isEmpty()) {
                break;
            }
            for (DietDaily d : page) {
                int ml = d.getWaterMl() == null ? 0 : d.getWaterMl();
                // 饮水加过又减回 0 的日子不算「有记录」
                if (ml > 0) {
                    days.computeIfAbsent(d.getLogDate(), x -> new DayTotals()).waterMl = ml;
                }
                g.writeObject(new AgentHistoryRows.Water(d.getLogDate(), ml));
            }
            g.flush();
            if (page.size() < ROW_BATCH) {
                break;
            }
            lastDate = page.get(page.size() - 1).getLogDate();
        }
        g.writeEndArray();
    }

    private void writePhotos(JsonGenerator g, Long userId, LocalDate from, LocalDate to) throws IOException {
        g.writeArrayFieldStart("photos");
        LocalDate lastDate = null;
        Long lastId = null;
        while (true) {
            LocalDate afterDate = lastDate;
            Long afterId = lastId;
            // 只取元数据：image／thumb 两列是历史遗留的 BLOB，不能带出来
            List<MealPhoto> page = mealPhotoMapper.selectList(Wrappers.<MealPhoto>lambdaQuery()
                    .select(MealPhoto::getId, MealPhoto::getLogDate, MealPhoto::getMeal, MealPhoto::getMime,
                            MealPhoto::getSizeBytes, MealPhoto::getThumbBytes)
                    .eq(MealPhoto::getUserId, userId)
                    .ge(from != null, MealPhoto::getLogDate, from)
                    .le(to != null, MealPhoto::getLogDate, to)
                    .and(afterDate != null, w -> w.gt(MealPhoto::getLogDate, afterDate)
                            .or(x -> x.eq(MealPhoto::getLogDate, afterDate).gt(MealPhoto::getId, afterId)))
                    .orderByAsc(MealPhoto::getLogDate)
                    .orderByAsc(MealPhoto::getId)
                    .last("LIMIT " + ROW_BATCH));
            if (page.isEmpty()) {
                break;
            }
            for (MealPhoto p : page) {
                String url = "/api/agent/photos/" + p.getLogDate() + "/" + p.getMeal();
                g.writeObject(new AgentHistoryRows.Photo(p.getLogDate(), p.getMeal(), p.getMime(), p.getSizeBytes(),
                        p.getThumbBytes(), url, url + "/thumb"));
            }
            g.flush();
            if (page.size() < ROW_BATCH) {
                break;
            }
            MealPhoto last = page.get(page.size() - 1);
            lastDate = last.getLogDate();
            lastId = last.getId();
        }
        g.writeEndArray();
    }

    /**
     * 每日汇总：消耗走 {@link EnergyService} 的同一份实现，只是把输入一次取好——
     * 按天调 {@code compute(userId, date)} 会把多年数据变成上万次查询。
     */
    private void writeDays(JsonGenerator g, Long userId, Map<LocalDate, DayTotals> days) throws IOException {
        g.writeArrayFieldStart("days");
        if (!days.isEmpty()) {
            UserProfile profile = userProfileMapper.selectById(userId);
            List<BodyRecord> weights = bodyRecordMapper.selectList(Wrappers.<BodyRecord>lambdaQuery()
                    .select(BodyRecord::getId, BodyRecord::getMeasuredAt, BodyRecord::getWeight)
                    .eq(BodyRecord::getUserId, userId)
                    .isNotNull(BodyRecord::getWeight)
                    .orderByAsc(BodyRecord::getMeasuredAt)
                    .orderByAsc(BodyRecord::getId));
            int next = 0;
            BodyRecord asOf = null;
            for (Map.Entry<LocalDate, DayTotals> e : days.entrySet()) {
                LocalDate date = e.getKey();
                DayTotals t = e.getValue();
                // days 按日期升序，指针只进不退：停下时 asOf 就是这天结束前最后一条体重（ADR 0042）
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
                while (next < weights.size() && weights.get(next).getMeasuredAt().isBefore(dayEnd)) {
                    asOf = weights.get(next++);
                }
                EnergyVO energy = energyService.compute(profile, asOf, date, t.trainingKcal);
                Integer gap = Boolean.TRUE.equals(energy.getComplete())
                        ? energy.getTotalKcal() - t.kcal.setScale(0, RoundingMode.HALF_UP).intValue()
                        : null;
                g.writeObject(new AgentHistoryRows.Day(date,
                        new AgentHistoryRows.Nutrients(t.kcal, t.protein, t.carb, t.fat),
                        t.waterMl, t.mealEntryCount, t.mealsLogged(), t.workoutCount, energy, gap));
            }
        }
        g.writeEndArray();
    }

    private void writeExercises(JsonGenerator g, Long userId, Map<Long, Exercise> exercises,
                                Set<Long> usedExerciseIds) throws IOException {
        g.writeArrayFieldStart("exercises");
        List<Exercise> rows = exercises.values().stream()
                .filter(e -> Objects.equals(e.getUserId(), userId) || usedExerciseIds.contains(e.getId()))
                .sorted(Comparator.comparing(Exercise::getId))
                .toList();
        for (Exercise e : rows) {
            g.writeObject(new AgentHistoryRows.Exercise(e.getId(),
                    e.getUserId() == null ? SOURCE_PRESET : SOURCE_CUSTOM, e.getName(), e.getMuscle(),
                    e.getMetric() == null ? METRIC_DEFAULT : e.getMetric(), Boolean.TRUE.equals(e.getArchived())));
        }
        g.writeEndArray();
    }

    /** 自建的全部 ＋ 被引用的预置；约 1800 条的预置全集不给。 */
    private void writeFoods(JsonGenerator g, Long userId, Set<Long> usedFoodIds) throws IOException {
        g.writeArrayFieldStart("foods");
        Long lastId = null;
        while (true) {
            List<Food> page = foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                    .eq(Food::getUserId, userId)
                    .gt(lastId != null, Food::getId, lastId)
                    .orderByAsc(Food::getId)
                    .last("LIMIT " + FOOD_BATCH));
            if (page.isEmpty()) {
                break;
            }
            for (Food f : page) {
                usedFoodIds.remove(f.getId());
                g.writeObject(toFoodRow(f));
            }
            g.flush();
            if (page.size() < FOOD_BATCH) {
                break;
            }
            lastId = page.get(page.size() - 1).getId();
        }
        List<Long> presetIds = usedFoodIds.stream().sorted().toList();
        for (int i = 0; i < presetIds.size(); i += FOOD_BATCH) {
            List<Long> chunk = presetIds.subList(i, Math.min(i + FOOD_BATCH, presetIds.size()));
            List<Food> page = foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                    .in(Food::getId, chunk)
                    .isNull(Food::getUserId)
                    .orderByAsc(Food::getId));
            for (Food f : page) {
                g.writeObject(toFoodRow(f));
            }
            g.flush();
        }
        g.writeEndArray();
    }

    private Map<String, Object> toFoodRow(Food f) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", f.getId());
        row.put("source", f.getUserId() == null ? SOURCE_PRESET : SOURCE_CUSTOM);
        row.putAll(foodService.toJsonMap(f));
        return row;
    }

    /** 预置 ＋ 自己的（含已下架）：历史记录要靠它取肌群与记录口径。 */
    private Map<Long, Exercise> visibleExercises(Long userId) {
        return exerciseMapper.selectList(Wrappers.<Exercise>lambdaQuery()
                        .isNull(Exercise::getUserId)
                        .or()
                        .eq(Exercise::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
    }

    private static LocalDate parseDate(String text, String name) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, name + " 的格式应为 YYYY-MM-DD");
        }
    }

    /** 一天的累计，边写明细边攒。 */
    private static final class DayTotals {
        private BigDecimal kcal = BigDecimal.ZERO;
        private BigDecimal protein = BigDecimal.ZERO;
        private BigDecimal carb = BigDecimal.ZERO;
        private BigDecimal fat = BigDecimal.ZERO;
        private int mealEntryCount;
        private final Set<String> meals = new HashSet<>();
        private int waterMl;
        private int workoutCount;
        private int trainingKcal;

        private void addMeal(String meal, AgentHistoryRows.Nutrients actual) {
            kcal = kcal.add(actual.kcal());
            protein = protein.add(actual.proteinG());
            carb = carb.add(actual.carbG());
            fat = fat.add(actual.fatG());
            mealEntryCount++;
            meals.add(meal);
        }

        private List<String> mealsLogged() {
            return MEAL_ORDER.stream().filter(meals::contains).toList();
        }
    }
}
