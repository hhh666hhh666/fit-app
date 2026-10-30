package com.example.fitapp.module.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
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
import com.example.fitapp.module.nutrition.service.FoodService;
import com.example.fitapp.module.nutrition.service.SettingService;
import com.example.fitapp.module.plan.service.PlanService;
import com.example.fitapp.module.plan.service.ScheduleService;
import com.example.fitapp.module.profile.entity.UserProfile;
import com.example.fitapp.module.profile.mapper.UserProfileMapper;
import com.example.fitapp.module.profile.service.EnergyService;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.entity.WorkoutBlock;
import com.example.fitapp.module.workout.entity.WorkoutSet;
import com.example.fitapp.module.workout.mapper.WorkoutBlockMapper;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.mapper.WorkoutSetMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（mapper 全 mock）：专测 agent 拿到错数据却看不出来的那些情况——
 * 爬楼占位值当成绩、历史消耗用了后来的体重、半截数据还是合法 JSON、没记录的日子混进汇总。
 */
@ExtendWith(MockitoExtension.class)
class AgentDataServiceTest {

    private static final long USER = 7L;

    @Mock private UserProfileMapper userProfileMapper;
    @Mock private BodyRecordMapper bodyRecordMapper;
    @Mock private WorkoutMapper workoutMapper;
    @Mock private WorkoutBlockMapper blockMapper;
    @Mock private WorkoutSetMapper setMapper;
    @Mock private ExerciseMapper exerciseMapper;
    @Mock private MealLogMapper mealLogMapper;
    @Mock private MealPhotoMapper mealPhotoMapper;
    @Mock private DietDailyMapper dietDailyMapper;
    @Mock private FoodMapper foodMapper;
    @Mock private FoodService foodService;
    @Mock private SettingService settingService;
    @Mock private PlanService planService;
    @Mock private ScheduleService scheduleService;

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private AgentDataService service;

    @BeforeAll
    static void initTableInfo() {
        // lambda 条件构造器要靠这份元数据把方法引用翻成列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Workout.class, WorkoutBlock.class, WorkoutSet.class, MealLog.class,
                BodyRecord.class, DietDaily.class, MealPhoto.class, Exercise.class, Food.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        // 消耗算法用真的：这里测的就是「代入哪条体重」
        EnergyService energy = new EnergyService(userProfileMapper, bodyRecordMapper, workoutMapper);
        service = new AgentDataService(json, userProfileMapper, bodyRecordMapper, workoutMapper, blockMapper,
                setMapper, exerciseMapper, mealLogMapper, mealPhotoMapper, dietDailyMapper, foodMapper,
                foodService, settingService, planService, scheduleService, energy);
    }

    private JsonNode history(String from, String to) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeHistory(USER, from, to, out);
        return json.readTree(out.toByteArray()).get("data");
    }

    private static Workout workout(long id, LocalDateTime at, Integer calories) {
        Workout w = new Workout();
        w.setId(id);
        w.setUserId(USER);
        w.setTitle("推");
        w.setStartedAt(at);
        w.setCalories(calories);
        return w;
    }

    private static WorkoutBlock block(long id, long workoutId, long exerciseId) {
        WorkoutBlock b = new WorkoutBlock();
        b.setId(id);
        b.setWorkoutId(workoutId);
        b.setExerciseId(exerciseId);
        b.setExerciseName("动作");
        return b;
    }

    private static WorkoutSet set(long blockId, String weight, int reps, Integer floors) {
        WorkoutSet s = new WorkoutSet();
        s.setBlockId(blockId);
        s.setSetIndex(1);
        s.setWeight(new BigDecimal(weight));
        s.setReps(reps);
        s.setFloors(floors);
        return s;
    }

    private static Exercise exercise(long id, Long userId, String muscle, String metric) {
        Exercise e = new Exercise();
        e.setId(id);
        e.setUserId(userId);
        e.setName("动作" + id);
        e.setMuscle(muscle);
        e.setMetric(metric);
        return e;
    }

    private static MealLog meal(long id, LocalDate date, String mealName, String grams, String kcal) {
        MealLog m = new MealLog();
        m.setId(id);
        m.setLogDate(date);
        m.setMeal(mealName);
        m.setFoodName("食物");
        m.setGrams(new BigDecimal(grams));
        m.setKcalPer100(new BigDecimal(kcal));
        m.setProteinPer100(BigDecimal.ZERO);
        m.setCarbPer100(BigDecimal.ZERO);
        m.setFatPer100(BigDecimal.ZERO);
        return m;
    }

    private static BodyRecord weighed(LocalDateTime at, String kg) {
        BodyRecord r = new BodyRecord();
        r.setId(at.getDayOfYear() + 0L);
        r.setMeasuredAt(at);
        r.setWeight(new BigDecimal(kg));
        return r;
    }

    private static UserProfile profile() {
        UserProfile p = new UserProfile();
        p.setSex("male");
        p.setHeightCm(new BigDecimal("175"));
        p.setBirthDate(LocalDate.of(1996, 1, 1));
        p.setActivityLevel("sedentary");
        return p;
    }

    @Test
    void floorsSets_hidePlaceholderWeightAndRepsAsExplicitNull() throws IOException {
        Workout w = workout(1, LocalDateTime.of(2026, 10, 1, 9, 0), null);
        when(workoutMapper.selectList(any())).thenReturn(List.of(w));
        when(blockMapper.selectList(any())).thenReturn(List.of(block(11, 1, 100), block(12, 1, 200)));
        when(setMapper.selectList(any())).thenReturn(List.of(set(11, "0", 1, 30), set(12, "60", 8, null)));
        when(exerciseMapper.selectList(any())).thenReturn(List.of(
                exercise(100, null, "cardio", "floors"), exercise(200, null, "chest", "weight_reps")));

        JsonNode blocks = history(null, null).get("workouts").get(0).get("blocks");

        JsonNode stairs = blocks.get(0).get("sets").get(0);
        assertTrue(stairs.has("weightKg"), "null 要明确写出来，不能省略字段");
        assertTrue(stairs.get("weightKg").isNull());
        assertTrue(stairs.get("reps").isNull());
        assertEquals(30, stairs.get("floors").asInt());
        assertTrue(blocks.get(0).get("cardio").asBoolean());

        JsonNode bench = blocks.get(1).get("sets").get(0);
        assertEquals(60, bench.get("weightKg").asInt());
        assertEquals(8, bench.get("reps").asInt());
        assertFalse(blocks.get(1).get("cardio").asBoolean());
    }

    @Test
    void days_useWeightAsOfThatDayNotLatest() throws IOException {
        LocalDate d1 = LocalDate.of(2026, 1, 10);
        LocalDate d2 = LocalDate.of(2026, 2, 10);
        LocalDate d3 = LocalDate.of(2026, 3, 10);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                meal(1, d1, "lunch", "100", "500"),
                meal(2, d2, "lunch", "100", "500"),
                meal(3, d3, "lunch", "100", "500")));
        when(userProfileMapper.selectById(USER)).thenReturn(profile());
        // 查询按 selectList 调用顺序：第一次是 body 明细段（空），第二次才是体重序列
        when(bodyRecordMapper.selectList(any())).thenReturn(List.of(), List.of(
                weighed(LocalDateTime.of(2026, 2, 10, 23, 59), "80"),
                weighed(LocalDateTime.of(2026, 3, 11, 0, 0), "70")));

        JsonNode days = history(null, null).get("days");

        // d1 早于第一条体重：不算，也不用后面的体重兜底
        assertFalse(days.get(0).get("energy").get("complete").asBoolean());
        assertEquals("weight", days.get(0).get("energy").get("missing").asText());
        assertTrue(days.get(0).get("gapKcal").isNull());

        // d2 当天 23:59 那条算；d3 用不上次日 0 点才量的 70，仍是 80
        assertEquals(80, days.get(1).get("energy").get("weightKg").asInt());
        assertEquals(80, days.get(2).get("energy").get("weightKg").asInt());
        // 缺口＝总消耗 − 摄入
        int total = days.get(1).get("energy").get("totalKcal").asInt();
        assertEquals(total - 500, days.get(1).get("gapKcal").asInt());
    }

    @Test
    void days_includeTrainingKcalInEnergyAndOnlyListDaysWithRecords() throws IOException {
        LocalDate workoutDay = LocalDate.of(2026, 4, 1);
        LocalDate zeroWaterDay = LocalDate.of(2026, 4, 2);
        LocalDate waterDay = LocalDate.of(2026, 4, 3);
        when(workoutMapper.selectList(any())).thenReturn(List.of(
                workout(1, workoutDay.atTime(23, 30), 300), workout(2, workoutDay.atTime(23, 40), 20)));
        DietDaily zero = new DietDaily();
        zero.setLogDate(zeroWaterDay);
        zero.setWaterMl(0);
        DietDaily some = new DietDaily();
        some.setLogDate(waterDay);
        some.setWaterMl(500);
        when(dietDailyMapper.selectList(any())).thenReturn(List.of(zero, some));

        JsonNode data = history(null, null);
        JsonNode days = data.get("days");

        assertEquals(2, days.size(), "饮水加过又减回 0 的日子不算有记录");
        assertEquals(workoutDay.toString(), days.get(0).get("date").asText());
        assertEquals(2, days.get(0).get("workoutCount").asInt());
        assertEquals(320, days.get(0).get("energy").get("trainingKcal").asInt());
        assertEquals(waterDay.toString(), days.get(1).get("date").asText());
        assertEquals(500, days.get(1).get("waterMl").asInt());
        // 明细里两条饮水都在：汇总的筛选不影响原始数据
        assertEquals(2, data.get("water").size());
    }

    @Test
    void meals_computeActualAndTrackLoggedMealsInFixedOrder() throws IOException {
        LocalDate d = LocalDate.of(2026, 5, 1);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                meal(1, d, "dinner", "200", "100"),
                meal(2, d, "breakfast", "50", "100")));

        JsonNode data = history(null, null);

        assertEquals(0, new BigDecimal("200").compareTo(data.get("meals").get(0).get("actual").get("kcal").decimalValue()));
        JsonNode day = data.get("days").get(0);
        assertEquals(0, new BigDecimal("250").compareTo(day.get("intake").get("kcal").decimalValue()));
        assertEquals(2, day.get("mealEntryCount").asInt());
        assertEquals("breakfast", day.get("mealsLogged").get(0).asText());
        assertEquals("dinner", day.get("mealsLogged").get(1).asText());
    }

    @Test
    void fullBatch_keepsPaging_shortBatchStops() throws IOException {
        List<MealLog> full = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            full.add(meal(i, LocalDate.of(2026, 6, 1), "lunch", "100", "1"));
        }
        when(mealLogMapper.selectList(any())).thenReturn(full, List.of(meal(501, LocalDate.of(2026, 6, 2), "lunch", "100", "1")));

        JsonNode meals = history(null, null).get("meals");

        assertEquals(501, meals.size());
        verify(mealLogMapper, times(2)).selectList(any());
    }

    @Test
    void foods_giveOwnAndReferencedOnce_notThePresetLibrary() throws IOException {
        MealLog ownFoodMeal = meal(1, LocalDate.of(2026, 7, 1), "lunch", "100", "1");
        ownFoodMeal.setFoodId(5L);
        MealLog presetFoodMeal = meal(2, LocalDate.of(2026, 7, 1), "lunch", "100", "1");
        presetFoodMeal.setFoodId(900L);
        when(mealLogMapper.selectList(any())).thenReturn(List.of(ownFoodMeal, presetFoodMeal));
        Food own = new Food();
        own.setId(5L);
        own.setUserId(USER);
        Food preset = new Food();
        preset.setId(900L);
        when(foodMapper.selectList(any())).thenReturn(List.of(own), List.of(preset));
        lenient().when(foodService.toJsonMap(any())).thenReturn(Map.of("name", "x"));

        JsonNode foods = history(null, null).get("foods");

        assertEquals(2, foods.size(), "自建又被引用的食物只出现一次");
        assertEquals("custom", foods.get(0).get("source").asText());
        assertEquals("preset", foods.get(1).get("source").asText());
    }

    @Test
    void failureMidStream_leavesInvalidJson() {
        when(workoutMapper.selectList(any())).thenReturn(List.of(workout(1, LocalDateTime.of(2026, 8, 1, 9, 0), null)));
        when(mealLogMapper.selectList(any())).thenThrow(new IllegalStateException("db down"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(IllegalStateException.class, () -> service.writeHistory(USER, null, null, out));

        // 半截数据绝不能被补全成合法 JSON：那样 agent 会把残缺结果当成全部历史
        String partial = out.toString(StandardCharsets.UTF_8);
        assertTrue(partial.contains("workouts"));
        assertThrows(JsonProcessingException.class, () -> json.readTree(partial));
    }

    @Test
    void invalidRange_failsBeforeWritingOrQuerying() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(BusinessException.class, () -> service.writeHistory(USER, "2026-02-01", "2026-01-01", out));
        assertThrows(BusinessException.class, () -> service.writeHistory(USER, "2026/02/01", null, out));

        assertEquals(0, out.size());
        verify(workoutMapper, never()).selectList(any());
    }
}
