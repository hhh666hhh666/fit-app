-- 识别流水多记一些，给「识别明细」的详情页用（ADR 0053）。全部可空：老记录没有这些信息，不回填。
-- 照片仍然不留（ADR 0018）；raw_output 是模型返回的文字（失败时是服务商的报错正文），截到 16000 字。
ALTER TABLE `food_recognition`
    ADD COLUMN `kind`             VARCHAR(16)   NULL COMMENT '识别入口：label=食物库营养成分表入库，meal=记一餐 AI 识别' AFTER `user_id`,
    ADD COLUMN `endpoint`         VARCHAR(255)  NULL COMMENT '服务商地址（不含 Key）' AFTER `model`,
    ADD COLUMN `thinking_mode`    VARCHAR(16)   NULL COMMENT '当时的思考开关配置' AFTER `endpoint`,
    ADD COLUMN `reasoning_effort` VARCHAR(16)   NULL COMMENT '当时的思考等级配置（空＝没传）' AFTER `thinking_mode`,
    ADD COLUMN `reasoning_tokens` INT           NULL COMMENT '输出 token 里用于思考的部分（服务商给了才有）' AFTER `cached_tokens`,
    ADD COLUMN `image_bytes`      INT           NULL COMMENT '发给模型的图片字节数' AFTER `duration_ms`,
    ADD COLUMN `image_mime`       VARCHAR(32)   NULL COMMENT '图片格式' AFTER `image_bytes`,
    ADD COLUMN `finish_reason`    VARCHAR(32)   NULL COMMENT '模型结束原因（stop / length …）' AFTER `image_mime`,
    ADD COLUMN `upstream_id`      VARCHAR(128)  NULL COMMENT '服务商返回的请求 ID' AFTER `finish_reason`,
    ADD COLUMN `result_name`      VARCHAR(64)   NULL COMMENT '识别出的名称（成功时）' AFTER `success`,
    ADD COLUMN `error_message`    VARCHAR(500)  NULL COMMENT '失败原因（给用户看的那句）' AFTER `result_name`,
    ADD COLUMN `raw_output`       TEXT          NULL COMMENT '模型原始输出；失败时为服务商报错正文（截到 16000 字）' AFTER `error_message`;
