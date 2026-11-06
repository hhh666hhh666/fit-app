package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.PhotoData;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
import com.example.fitapp.common.TransientRetry;
import com.example.fitapp.module.nutrition.dto.FoodAmountDTO;
import com.example.fitapp.module.nutrition.dto.MealBatchDTO;
import com.example.fitapp.module.nutrition.dto.MealLogDTO;
import com.example.fitapp.module.nutrition.dto.MealUpdateDTO;
import com.example.fitapp.module.nutrition.dto.WaterDTO;
import com.example.fitapp.module.nutrition.entity.DietDaily;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.mapper.DietDailyMapper;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.mapper.MealPhotoMapper;
import com.example.fitapp.module.account.mapper.AvatarMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.nutrition.vo.DailyNutritionRow;
import com.example.fitapp.module.nutrition.vo.DayOverviewVO;
import com.example.fitapp.module.nutrition.vo.MealItemVO;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import com.example.fitapp.module.nutrition.vo.StorageVO;
import com.example.fitapp.module.nutrition.vo.TrendVO;
import com.example.fitapp.module.profile.service.EnergyService;
import com.example.fitapp.module.profile.vo.EnergyVO;
import com.example.fitapp.module.preset.entity.PresetHidden;
import com.example.fitapp.module.preset.service.PresetHiddenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 饮食记录：当日总览 / 条目增改删 / 每餐照片（上传即替换）/ 饮水加减 / 趋势。
 * 营养一律按条目快照（per100 × grams ÷ 100）现算；目标与饮水步进来自设置。
 */
@Service
@RequiredArgsConstructor
public class DietService {

    private static final List<String> MEAL_ORDER = List.of("breakfast", "lunch", "dinner", "snack");
    /** 餐照入库的尝试次数：正常 1 次 ＋ 瞬时故障重试 2 次（退避节奏见 {@link TransientRetry}）。 */
    private static final int PHOTO_WRITE_ATTEMPTS = 3;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;
    /** 趋势单次查询的窗口上限（天）：防呆用，正常远用不到（约 10 年）。 */
    private static final int MAX_TREND_DAYS = 3660;

    private final MealLogMapper mealLogMapper;
    private final MealPhotoMapper mealPhotoMapper;
    private final DietDailyMapper dietDailyMapper;
    private final FoodMapper foodMapper;
    private final PresetHiddenService presetHiddenService;
    private final SettingService settingService;

    /** 每日总消耗（基础代谢×生活活动系数 ＋ 手填训练消耗）的唯一实现，见 ADR 0029。 */
    private final EnergyService energyService;

    /** 餐照的文件存储（ADR 0026）：图片实体在磁盘上，库里只留路径。 */
    private final PhotoStorage photoStorage;

    /** 上传图片的大小、类型与缩略图口径（ADR 0051），餐照、日记图片和头像共用。 */
    private final PhotoUploadPolicy uploadPolicy;

    /** 日记图片的占用要和餐照一起算（ADR 0044）；这里只读占用，不依赖日记的业务。 */
    private final DiaryPhotoMapper diaryPhotoMapper;
    private final AvatarMapper avatarMapper;
    private final TrashItemMapper trashItemMapper;
    private final MealPhotoTrash mealPhotoTrash;

    /** 餐照总占用上限（字节，原图＋缩略图）；达上限即拒绝上传（ADR 0013，值在 application.yml）。 */
    @Value("${fitapp.storage.photo-quota-bytes:21474836480}")
    private long photoQuotaBytes;

    // ── 当日总览 ──

    public DayOverviewVO day(Long userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        List<MealLog> logs = mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                .eq(MealLog::getUserId, userId)
                .eq(MealLog::getLogDate, date)
                .orderByAsc(MealLog::getMeal)
                .orderByAsc(MealLog::getId));

        DayOverviewVO vo = new DayOverviewVO();
        vo.setDate(date.toString());

        DayOverviewVO.Totals totals = new DayOverviewVO.Totals();
        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carb = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        for (MealLog l : logs) {
            kcal = kcal.add(actual(l.getKcalPer100(), l.getGrams()));
            protein = protein.add(actual(l.getProteinPer100(), l.getGrams()));
            carb = carb.add(actual(l.getCarbPer100(), l.getGrams()));
            fat = fat.add(actual(l.getFatPer100(), l.getGrams()));
        }
        totals.setKcal(kcal);
        totals.setProtein(protein);
        totals.setCarb(carb);
        totals.setFat(fat);
        vo.setTotals(totals);

        // 餐次固定四条（保持渲染顺序）
        Map<String, List<MealItemVO>> byMeal = logs.stream().collect(Collectors.groupingBy(
                MealLog::getMeal, LinkedHashMap::new,
                Collectors.mapping(this::toItem, Collectors.toList())));
        List<DayOverviewVO.MealGroup> groups = new ArrayList<>();
        for (String m : MEAL_ORDER) {
            DayOverviewVO.MealGroup g = new DayOverviewVO.MealGroup();
            g.setMeal(m);
            g.setItems(byMeal.getOrDefault(m, List.of()));
            groups.add(g);
        }
        vo.setMeals(groups);

        // 饮水 + 目标（设置行不存在时给默认）
        SettingVO setting = settingService.get(userId);
        DietDaily dd = dietDailyMapper.selectOne(Wrappers.<DietDaily>lambdaQuery()
                .eq(DietDaily::getUserId, userId)
                .eq(DietDaily::getLogDate, date));
        DayOverviewVO.Water water = new DayOverviewVO.Water();
        water.setMl(dd == null ? 0 : dd.getWaterMl());
        water.setTarget(setting.getTargetWater());
        vo.setWater(water);

        DayOverviewVO.Targets targets = new DayOverviewVO.Targets();
        targets.setKcal(setting.getTargetKcal());
        targets.setProtein(setting.getTargetProtein());
        targets.setCarb(setting.getTargetCarb());
        targets.setFat(setting.getTargetFat());
        targets.setWater(setting.getTargetWater());
        vo.setTargets(targets);

        vo.setPhotoMeals(mealPhotoMapper.selectMealsWithPhoto(userId, date));

        // 每日总消耗与热量缺口（ADR 0029）：消耗口径只有 EnergyService 一处实现，这里只做减法
        EnergyVO energy = energyService.compute(userId, date);
        vo.setEnergy(energy);
        if (energy != null && Boolean.TRUE.equals(energy.getComplete()) && energy.getTotalKcal() != null) {
            int intake = kcal.setScale(0, RoundingMode.HALF_UP).intValue();
            vo.setGap(energy.getTotalKcal() - intake);
        }
        return vo;
    }

    // ── 条目 ──

    public MealItemVO addMeal(Long userId, MealLogDTO dto) {
        Food food = requireOwnFood(userId, dto.getFoodId());
        return insertMeal(userId, parseDate(dto.getDate()), dto.getMeal(), food, dto.getGrams());
    }

    /**
     * 批量往某一餐加条目（食物组合「整份加入」用，ADR 0012）：一次事务按传入顺序落库，
     * 避免"加了一半"留下半份组合。营养快照与单条添加同一口径。
     */
    @Transactional
    public List<MealItemVO> addMealsBatch(Long userId, MealBatchDTO dto) {
        if (!MEAL_ORDER.contains(dto.getMeal())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "餐次不正确");
        }
        List<MealItemVO> out = new ArrayList<>();
        for (FoodAmountDTO it : dto.getItems()) {
            Food food = requireOwnFood(userId, it.getFoodId());
            out.add(insertMeal(userId, dto.getDate(), dto.getMeal(), food, it.getGrams()));
        }
        return out;
    }

    /** 落一条条目：每 100 数值按 food 现值快照，此后改库/删库不影响本条。 */
    private MealItemVO insertMeal(Long userId, LocalDate date, String meal, Food food, BigDecimal grams) {
        MealLog log = new MealLog();
        log.setUserId(userId);
        log.setLogDate(date);
        log.setMeal(meal);
        log.setFoodId(food.getId());
        log.setFoodName(food.getName());
        log.setGrams(grams);
        log.setKcalPer100(food.getKcal());
        log.setProteinPer100(food.getProtein());
        log.setCarbPer100(food.getCarb());
        log.setFatPer100(food.getFat());
        mealLogMapper.insert(log);
        return toItem(log);
    }

    public MealItemVO updateMeal(Long userId, Long id, MealUpdateDTO dto) {
        MealLog log = requireOwnMeal(userId, id);
        log.setGrams(dto.getGrams());
        mealLogMapper.updateById(log);
        return toItem(log);
    }

    public void deleteMeal(Long userId, Long id) {
        requireOwnMeal(userId, id);
        mealLogMapper.deleteById(id);
    }

    // ── 照片（一餐可以有多张，ADR 0045）──

    /** 上传一张餐照，返回它的 id。 */
    public Long savePhoto(Long userId, String dateStr, String meal, MultipartFile file) {
        if (!MEAL_ORDER.contains(meal)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "餐次不正确");
        }
        String mime = uploadPolicy.validate(file);
        long size = file.getSize();
        LocalDate date = parseDate(dateStr);

        // 缩略图与数据库无关，放在重试之外：它是 CPU 密集活，重试时重算一遍没有意义。
        byte[] thumb = PhotoUploadPolicy.makeThumb(file);
        // 路径也放在重试之外：幂等靠它——首次写入其实成功、只是响应阶段才断连时，
        // 重试会查到同路径的那一行，不会多出一张。
        String relKey = PhotoStorage.uniqueKey(userId, date, meal);

        // 「查旧行 → 容量闸 → 写库」整块交给重试：三步都打数据库，而共用 MySQL 容器被
        // OOM-killer 杀掉重启的那几秒，正是它们会失败的地方（原委见 TransientRetry 的注释）。
        Long[] id = new Long[1];
        TransientRetry.run("上传餐照", PHOTO_WRITE_ATTEMPTS, () -> {
            MealPhoto existing = mealPhotoMapper.selectOne(Wrappers.<MealPhoto>lambdaQuery()
                    .eq(MealPhoto::getPath, relKey));
            if (existing != null) {
                id[0] = existing.getId();
                return;
            }

            // 容量闸（ADR 0013）：达上限直接拒绝。
            // 它抛的 BusinessException 不属可重试族，会直接穿过 TransientRetry 冒到全局处理器。
            checkPhotoQuota(userId, size);

            // 落盘放在写库之前：文件写不成就别动数据库。反过来（先写库、文件失败）会留下
            // 「记录指向不存在的图」；而写库失败时重试会往同一路径再写一遍——同路径覆盖，
            // 天然幂等，也不会堆积孤儿。
            try {
                photoStorage.write(relKey, mime, file, thumb);
            } catch (IOException e) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "照片保存失败，请稍后重试");
            }

            MealPhoto p = new MealPhoto();
            p.setUserId(userId);
            p.setLogDate(date);
            p.setMeal(meal);
            p.setMime(mime);
            p.setSizeBytes((int) size);
            p.setPath(relKey);
            p.setThumbBytes(thumb == null ? null : thumb.length);
            mealPhotoMapper.insert(p);
            id[0] = p.getId();
        });
        return id[0];
    }

    /** 容量闸（ADR 0013）：餐照和日记图片共用一个上限（ADR 0044），超限直接拒绝。 */
    public void checkPhotoQuota(Long userId, long delta) {
        // 回收站里的文件还在磁盘上，要算进去（ADR 0047）
        long used = mealPhotoMapper.sumBytes(userId) + diaryPhotoMapper.sumBytes(userId)
                + avatarMapper.sumBytes(userId) + trashItemMapper.sumPhotoBytes(userId);
        if (used + delta > photoQuotaBytes) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "照片空间已满（上限 " + quotaText() + "），请先到相册或回收站清理");
        }
    }

    /** 把一张餐照移到回收站（ADR 0047）：文件留在原处，到期或彻底删除才删。不存在 → 404。 */
    @Transactional
    public void deletePhoto(Long userId, Long id) {
        MealPhoto p = getPhoto(userId, id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "照片不存在");
        }
        mealPhotoTrash.put(userId, p);
        mealPhotoMapper.deleteById(p.getId());
    }

    /** 存储占用（ADR 0013）：目前只统计图片一类（原图 ＋ 缩略图），含餐照和日记图片。 */
    public StorageVO storage(Long userId) {
        StorageVO vo = new StorageVO();
        long trashBytes = trashItemMapper.sumPhotoBytes(userId);
        // 头像也占照片空间，但不进相册，所以只算字节、不算张数（ADR 0051）
        vo.setPhotoBytes(mealPhotoMapper.sumBytes(userId) + diaryPhotoMapper.sumBytes(userId)
                + avatarMapper.sumBytes(userId) + trashBytes);
        vo.setTrashBytes(trashBytes);
        vo.setPhotoCount((int) (mealPhotoMapper.countByUser(userId) + diaryPhotoMapper.countByUser(userId)));
        vo.setPhotoQuotaBytes(photoQuotaBytes);
        return vo;
    }

    /** 相册列表：按日期倒序、同日按早→晚；只出元数据，图片数据由前端逐个取。 */
    public List<MealPhoto> album(Long userId, Integer offset, Integer limit) {
        int size = (limit == null || limit < 1) ? 60 : Math.min(limit, 200);
        int start = (offset == null || offset < 0) ? 0 : offset;
        return mealPhotoMapper.selectAlbum(userId, start, size);
    }

    /** 上限的展示文案（GB，最多一位小数）。 */
    private String quotaText() {
        return BigDecimal.valueOf(photoQuotaBytes)
                .divide(BigDecimal.valueOf(BYTES_PER_GB), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + " GB";
    }

    /** 读照片元数据；不存在或不是这个用户的返回 null（由 Controller 转 404）。 */
    public MealPhoto getPhoto(Long userId, Long id) {
        return mealPhotoMapper.selectOne(Wrappers.<MealPhoto>lambdaQuery()
                .eq(MealPhoto::getId, id)
                .eq(MealPhoto::getUserId, userId));
    }

    /** 某一餐的全部照片（只出元数据），按上传先后。 */
    public List<MealPhoto> mealPhotos(Long userId, String dateStr, String meal) {
        return mealPhotoMapper.selectList(Wrappers.<MealPhoto>lambdaQuery()
                .select(MealPhoto::getId, MealPhoto::getLogDate, MealPhoto::getMeal, MealPhoto::getSizeBytes)
                .eq(MealPhoto::getUserId, userId)
                .eq(MealPhoto::getLogDate, parseDate(dateStr))
                .eq(MealPhoto::getMeal, meal)
                .orderByAsc(MealPhoto::getId));
    }

    /**
     * 读取餐照二进制。{@code wantThumb=true} 时优先缩略图、没有就回退原图（老数据无缩略图）。
     * 返回 null 表示这张图确实取不到（文件丢了、或记录不在）——由 Controller 转 404。
     */
    public PhotoData readPhoto(Long userId, Long id, boolean wantThumb) {
        MealPhoto p = getPhoto(userId, id);
        if (p == null) {
            return null;
        }
        if (p.getPath() != null) {
            if (wantThumb) {
                byte[] t = photoStorage.readThumb(p.getPath());
                if (t != null) {
                    return PhotoData.of(t, "image/jpeg");
                }
            }
            Path img = photoStorage.imageFile(p.getPath(), p.getMime());
            return img == null ? null : PhotoData.of(img, p.getMime());
        }
        // 过渡期回退：搬迁尚未完成（例如容器还没挂好卷）时图片仍在库里——
        // 别让读取功能干等着搬迁，能读就先读出来。
        if (wantThumb && hasBytes(p.getThumb())) {
            return PhotoData.of(p.getThumb(), "image/jpeg");
        }
        return hasBytes(p.getImage()) ? PhotoData.of(p.getImage(), p.getMime()) : null;
    }

    private static boolean hasBytes(byte[] b) {
        return b != null && b.length > 0;
    }

    // ── 饮水 ──

    /** 加减一笔（结果不小于 0），返回该日最新累计。 */
    @Transactional
    public int addWater(Long userId, WaterDTO dto) {
        if (dto.getDelta() == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "增减量不能为 0");
        }
        LocalDate date = parseDate(dto.getDate());
        DietDaily dd = dietDailyMapper.selectOne(Wrappers.<DietDaily>lambdaQuery()
                .eq(DietDaily::getUserId, userId)
                .eq(DietDaily::getLogDate, date));
        if (dd == null) {
            dd = new DietDaily();
            dd.setUserId(userId);
            dd.setLogDate(date);
            dd.setWaterMl(Math.max(0, dto.getDelta()));
            dietDailyMapper.insert(dd);
        } else {
            dd.setWaterMl(Math.max(0, dd.getWaterMl() + dto.getDelta()));
            dietDailyMapper.updateById(dd);
        }
        return dd.getWaterMl();
    }

    // ── 趋势 ──

    /**
     * 趋势（ADR 0015）：给了 from/to 按起止日期取；只给 to 时自动从**最早一条记录**起
     * （前端一次拉全量、在本地缩放平移）；两者都没给则退回「最近 days 天」（默认 30，兼容旧调用）。
     */
    public TrendVO trend(Long userId, String fromStr, String toStr, Integer days) {
        LocalDate to = (toStr == null || toStr.isBlank()) ? LocalDate.now() : parseDate(toStr);
        LocalDate from;
        if (fromStr != null && !fromStr.isBlank()) {
            from = parseDate(fromStr);
        } else {
            // 没给起点就从最早一条记录起（数据窗口＝全部历史）；一条都没有时退回最近 days 天
            LocalDate earliest = mealLogMapper.selectEarliestDate(userId);
            if (earliest != null) {
                from = earliest;
            } else {
                int n = (days == null || days < 1) ? 30 : Math.min(days, MAX_TREND_DAYS);
                from = to.minusDays(n - 1L);
            }
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起始日期晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_TREND_DAYS) {
            from = to.minusDays(MAX_TREND_DAYS - 1L);
        }

        Map<LocalDate, DailyNutritionRow> byDate = mealLogMapper.selectDailyTotals(userId, from, to)
                .stream().collect(Collectors.toMap(DailyNutritionRow::getLogDate, r -> r));
        Map<LocalDate, Integer> waterByDate = dietDailyMapper.selectRange(userId, from, to).stream()
                .collect(Collectors.toMap(DietDaily::getLogDate, DietDaily::getWaterMl, (a, b) -> b));

        List<TrendVO.Point> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyNutritionRow r = byDate.get(d);
            TrendVO.Point p = new TrendVO.Point();
            p.setDate(d.toString());
            p.setKcal(r == null ? BigDecimal.ZERO : scale2(r.getKcal()));
            p.setProtein(r == null ? BigDecimal.ZERO : scale2(r.getProtein()));
            p.setCarb(r == null ? BigDecimal.ZERO : scale2(r.getCarb()));
            p.setFat(r == null ? BigDecimal.ZERO : scale2(r.getFat()));
            p.setWater(waterByDate.getOrDefault(d, 0));
            points.add(p);
        }
        TrendVO vo = new TrendVO();
        vo.setDays(points);
        return vo;
    }

    // ── 辅助 ──

    /** 实际摄入 = per100 × grams ÷ 100（四舍五入 2 位）。 */
    public static BigDecimal actual(BigDecimal per100, BigDecimal grams) {
        if (per100 == null || grams == null) {
            return BigDecimal.ZERO;
        }
        return per100.multiply(grams).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private MealItemVO toItem(MealLog l) {
        MealItemVO i = new MealItemVO();
        i.setId(l.getId());
        i.setMeal(l.getMeal());
        i.setFoodId(l.getFoodId());
        i.setFoodName(l.getFoodName());
        i.setGrams(l.getGrams());
        i.setKcal(actual(l.getKcalPer100(), l.getGrams()));
        i.setProtein(actual(l.getProteinPer100(), l.getGrams()));
        i.setCarb(actual(l.getCarbPer100(), l.getGrams()));
        i.setFat(actual(l.getFatPer100(), l.getGrams()));
        return i;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "日期格式不正确");
        }
    }

    private Food requireOwnFood(Long userId, Long foodId) {
        Food food = foodMapper.selectById(foodId);
        // 允许系统预置（user_id 为空）：预置食物可直接被记录（快照照写）
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        // 已下架的预置：只有用过、没隐藏它的人还能拿来记（ADR 0048）
        if (food.getUserId() == null && Boolean.TRUE.equals(food.getArchived())
                && (!foodMapper.usedBy(userId, foodId)
                    || presetHiddenService.isHidden(userId, PresetHidden.FOOD, foodId))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "这条食物已下架");
        }
        return food;
    }

    private MealLog requireOwnMeal(Long userId, Long id) {
        MealLog log = mealLogMapper.selectById(id);
        if (log == null || !userId.equals(log.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return log;
    }
}
