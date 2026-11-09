-- 识别流水再记发出去的请求与收到的响应全文（ADR 0053 后续）。可空，老记录不回填。
-- request_json：发给服务商的请求体，图片的 base64 换成「已省略：N 字节」占位（其余与实发一致；Key 在请求头里，不在这里）。
-- response_json：服务商返回的完整响应（失败时是 HTTP 状态 ＋ 报错正文）。
ALTER TABLE `food_recognition`
    ADD COLUMN `request_json`  TEXT       NULL COMMENT '发给服务商的请求体（图片 base64 已换成占位）' AFTER `raw_output`,
    ADD COLUMN `response_json` MEDIUMTEXT NULL COMMENT '服务商返回的完整响应；失败时为 HTTP 状态＋报错正文' AFTER `request_json`;
