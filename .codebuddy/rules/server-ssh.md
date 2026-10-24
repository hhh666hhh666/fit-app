---
description: 本机 SSH 与服务器入口——密钥位置（本机事实）、连接别名与「什么能写进仓库」的红线；上服务器 / 动部署凭据前先读。
alwaysApply: true
---

# 本机 SSH 与服务器入口

**触发**：任何要 SSH 上部署服务器（排障 / 部署 / 查状态）、或要动 `DEPLOY_SSH_KEY` 这类部署凭据的时刻。这里记的是**这台机器的事实**——换机器、或密钥再搬家时，本文件与 `C:\Users\lzh\.ssh\config` 要一起改（2026-10-12 迁过一次：`~/.ssh/fit/` → `~/.ssh/agent/`）。

## 怎么连

```bash
ssh aliyun
```

别名在 `C:\Users\lzh\.ssh\config`：`HostName 8.137.172.6`、`Port 39222`、`User root`、`IdentityFile` 指向私钥。

- **端口**：2026-10-24 起本机走 **39222**；sshd 目前 22 与 39222 都在听，发布流水线仍连 22，**22 不能关**——要关先改流水线与 known_hosts（见 `docs/todo/archive/2026-10-24-TODO-服务器SSH端口迁移.md`）。
- **连不上时先分清是哪一头**：`kex_exchange_identification: Connection closed by remote host`（TCP 连上了、连 SSH 横幅都没收到就断）且 `github.com:22` 也一样，就是本机流量被透明代理接管、节点不放行 SSH——域名解析成 `198.18.x.x`（fake-ip）即是证据。在代理里把 `8.137.172.6`、`11-2.cn` 设直连；别反复重试。

- **私钥位置**：`C:\Users\lzh\.ssh\agent\workbuddy.pem`。需要显式写路径时用 `~/.ssh/agent/workbuddy.pem` 的写法（git bash 的 `~` 即 `C:\Users\lzh`），**不要写反斜杠形式**。
- 与 quizzy 共用同一把、同一台服务器；发布流水线 SSH 部署用的 `DEPLOY_SSH_KEY` 内容就是这把私钥——CI 侧怎么用看 `deploy/README.md` 与 `.github/workflows/release.yml`，本文件不复述。

## 红线

- pem 的**内容**不进文档、不进提交、不进消息；仓库里能出现的只有**路径**。
- 发布与回滚的流程口径看 `deploy/README.md`；本文件只管「位置与怎么连」。
