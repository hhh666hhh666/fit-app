package com.example.fitapp.module.nutrition.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.nutrition.entity.FoodRecognition;
import com.example.fitapp.module.nutrition.service.FoodRecognitionService;
import com.example.fitapp.module.nutrition.vo.FoodDraftVO;
import com.example.fitapp.module.nutrition.vo.MealDraftVO;
import com.example.fitapp.module.nutrition.vo.RecognitionUsageVO;
import com.example.fitapp.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 营养成分表照片识别（ADR 0018）：识别（multipart 照片进、草稿出不落库）＋ 用量汇总 ＋ 用量明细。
 * Key 只存在于服务端 env——前端把照片发给本服务，由本服务转发 DashScope。
 */
@RestController
@RequestMapping("/api/food-recognition")
@RequiredArgsConstructor
public class FoodRecognitionController {

    private final FoodRecognitionService service;

    /** 识别：返回草稿（不落库）；保存由 POST /api/foods 完成。 */
    @PostMapping
    public Result<FoodDraftVO> recognize(@RequestParam("file") MultipartFile file) {
        return Result.success(service.recognize(UserContext.requireUserId(), file));
    }

    /**
     * 餐次 AI 识别（ADR 0052）：饭菜照片估算 / 营养成分表换算 → 临时食物草稿（不落库）；
     * 用户核对后由 POST /api/diet/meals/custom 记成临时条目。
     */
    @PostMapping("/meal")
    public Result<MealDraftVO> recognizeMeal(@RequestParam("file") MultipartFile file) {
        return Result.success(service.recognizeMeal(UserContext.requireUserId(), file));
    }

    /** 用量汇总：累计 token ＋ 请求次数（「我的」页外卡两值；不显示费用）。 */
    @GetMapping("/usage")
    public Result<RecognitionUsageVO> usage() {
        return Result.success(service.usage(UserContext.requireUserId()));
    }

    /** 用量明细分页（新的在前；含输入/输出/缓存命中 token、模型、耗时、成败）。 */
    /** 一条流水的全部信息（详情页用，ADR 0053）：含模型原始输出、失败原因、服务商与思考参数。 */
    @GetMapping("/logs/{id}")
    public Result<FoodRecognition> logDetail(@PathVariable Long id) {
        return Result.success(service.logDetail(UserContext.requireUserId(), id));
    }

    @GetMapping("/logs")
    public Result<List<FoodRecognition>> logs(@RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Integer offset) {
        return Result.success(service.logs(UserContext.requireUserId(), limit, offset));
    }
}
