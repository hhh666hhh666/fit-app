# fit-app 外部 agent 取数接口

只读接口，用来拉取一个用户的训练、饮食、身体和计划数据做分析。接口不能修改任何数据。

## 鉴权

在 App 的「我的 → 外部 agent」生成令牌，令牌只显示一次。每个用户只有一把，重新生成或吊销后旧令牌立即失效。

每个请求带上请求头：

```
Authorization: Bearer fit_xxxxxxxx
```

令牌无效时返回 HTTP 401，响应体是 `{ code: 401, message, data: null, timestamp }`。

## 接口

### GET /api/agent/current

没有时间线的数据，一律是当前值：

- `profile`：性别、出生日期、年龄、身高、生活活动量；没填时为 null
- `energyToday`：今天的每日总消耗拆解
- `dietTargets`：饮食目标；没设的项为 null
- `plans`：训练计划，含动作、循环组号 `circuitNo`（null 表示不参与循环）和逐组目标
- `weekSchedule`：周一到周日的安排；`dayOfWeek` 0 是周一，`state` 为 `plan`、`rest` 或 `unset`
- `definitions`：**数据口径说明，分析前请先读**

### GET /api/agent/history

历史数据。可选参数 `from`、`to`，格式 `YYYY-MM-DD`，两端都包含；都不传就是全部历史，不限时间范围。

```
GET /api/agent/history?from=2026-01-01&to=2026-03-31
```

`data` 的内容：

- `workouts`：一次训练，含 `blocks`（动作）和 `sets`（组次）
- `meals`：餐次条目；`per100` 是记录时的营养快照，`actual` 是按克数算出的实际摄入
- `body`：身体档案，每条记哪项填哪项，没量的项为 null
- `water`：每日饮水
- `photos`：餐照元数据和下载路径
- `days`：每日汇总，只含当天至少有一条饮食、饮水或训练记录的日子
- `exercises`：被引用的动作和主人自建的动作
- `foods`：被引用的食物和主人自建的食物，含完整营养（每 100g 或 100ml，只有已填的项）

响应是流式写出的，多年数据也可以一次拉完，但耗时会随数据量增长，请把客户端的超时设得宽一些。

⚠️ **流式响应中途失败时，HTTP 状态码仍是 200，但 JSON 会残缺。把 JSON 解析失败当作本次拉取失败，然后重试。**

### GET /api/agent/photos/{date}/{meal}

餐照原图，路径直接取自 `photos[].url`。末尾加 `/thumb` 是缩略图（长边不超过 320px，取自 `photos[].thumbUrl`）。`meal` 为 `breakfast`、`lunch`、`dinner` 或 `snack`。没有图时返回 HTTP 404。

### GET /api/agent/docs

本文档。公开访问，不含任何用户数据。

## 响应结构

除图片和本文档外，响应都是：

```json
{ "code": 0, "message": "成功", "timestamp": 1790000000000, "data": { "schemaVersion": 1 } }
```

- `code` 为 0 表示成功。参数错误时 HTTP 状态码仍是 200，`code` 为 400；服务暂时不可用时 `code` 为 503，稍后重试即可。
- `schemaVersion` 在字段含义发生不兼容变化时才会增加。
- 值为空的字段会明确写成 null，不会省略。

## 重要口径

完整口径见 `current` 的 `definitions`。最容易分析错的几条：

- **整段覆盖，不要按 id 合并**：不支持增量拉取，记录被删除后只会从结果里消失，没有删除标记；编辑一次训练会重写它的动作和组次，id 不保证稳定。
- **没记不等于没吃**：`gapKcal` 是粗略参考，请结合 `mealEntryCount` 和 `mealsLogged` 判断这天记全了没有。`gapKcal` 为正是缺口，为负是盈余；算不出消耗时为 null，原因见 `energy.missing`（`profile` 或 `weight`）。
- **历史消耗是近似值**：体重取那天结束前最后一次测得的值，身高、性别、活动量只有当前值。
- **爬楼看层数**：`metric` 为 `floors` 的动作，`weightKg` 和 `reps` 恒为 null，只看 `floors`。
- **总容量不能横向比较**：只宜同动作或同计划纵向对比，有氧不计入。
- **训练消耗是手填的**：`calories` 为 null 表示没填，不代表没消耗。
- **食物营养是当前值**：`foods` 不是记录当时的值；食物被删除后 `foods` 里就没有它了，这时 `meals[].foodId` 可能指向不存在的条目，请以 `per100` 快照为准。
- **饮食目标只有当前值**：不代表过去某天的目标。
- **名字带 `*` 的预置食物**：营养数据由公开资料补全，可信度低一档。

## 食物营养字段

`foods` 里的数值都是每 100g（`unit` 为 `ml` 时每 100ml）的含量：

- 大卡：`kcal`
- 克：`protein`、`fat`、`carb`、`saturated_fat`、`sugar`、`trans_fat`、`mono_fat`、`poly_fat`、`fiber`、`soluble_fiber`、`insoluble_fiber`、`starch`、`lactose`、`water`、`ash`、`alcohol`、`organic_acid`
- 毫克：`sodium`、`cholesterol`、`vb1`、`vb2`、`vb6`、`vc`、`niacin`、`pantothenic`、`calcium`、`phosphorus`、`potassium`、`magnesium`、`iron`、`zinc`、`copper`、`manganese`、`fluoride`
- 毫克 α-TE：`ve`
- 微克：`vd`、`vb12`、`biotin`、`iodine`、`selenium`、`chromium`、`molybdenum`
- 微克 RE：`va`
- 微克 DFE：`folate`
- 无单位：`gi`（升糖指数）
- `portion`：常用份的名称和克数
