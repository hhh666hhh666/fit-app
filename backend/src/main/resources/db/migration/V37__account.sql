-- 账号页（ADR 0050）：昵称 ＋ 令牌版本号。
--   nickname：显示名，可空、可重名，1～20 个字符（按字符算，emoji 也算一个）。
--   token_version：改密码时加一；登录令牌里带着签发时的版本，对不上就拒绝——这样改完密码
--   其他设备的旧令牌立刻失效。已有令牌不带版本，按 0 处理，所以上线时不会让任何人掉线。
ALTER TABLE `user`
    ADD COLUMN `nickname`      VARCHAR(20) NULL COMMENT '昵称（显示名，可空、可重名）' AFTER `username`,
    ADD COLUMN `token_version` INT         NOT NULL DEFAULT 0 COMMENT '令牌版本：改密码时加一，旧令牌随之失效' AFTER `password_hash`;
