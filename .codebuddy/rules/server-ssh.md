---
description: 服务器入口与部署凭据红线——本项目的服务器别名、档案出处（GitHub servers 仓库）与流水线耦合点；上服务器 / 动部署凭据前先读。
alwaysApply: true
---

# 服务器入口与红线

**触发**：任何要 SSH 上部署服务器（排障 / 部署 / 查状态）、或要动 `DEPLOY_SSH_KEY` 这类部署凭据的时刻。

## 怎么连 & 查机器事实

- 本项目部署在 Host 别名 **`aliyun`** 上（`ssh aliyun`）。
- **机器级事实以 GitHub 上的服务器档案仓库为单一真相源**（不要假设本机存在同级目录，换机器可能没有）：
  - 仓库：[hhh666hhh666/servers](https://github.com/hhh666hhh666/servers)（`aliyun/` 目录）
  - `aliyun/README.md`：公网 IP、机器规格、端口分配总表、域名解析（灰云 / 橙云）
  - `aliyun/ssh访问.md`：直连与 Cloudflare 隧道两种接入配置、端口分工、断连排障
  - `aliyun/cloudflare隧道.md`：隧道维护与回滚
- 查阅方式：直接读 GitHub 页面或用 `gh api repos/hhh666hhh666/servers/contents/aliyun/<文件名>` 读取，**不在本仓库复制机器级事实**。

## 与本项目的耦合点

- **SSH 22 端口与 `DEPLOY_SSH_KEY`**：发布流水线（`.github/workflows/release.yml`）仍走 22 端口部署，且与同机其他项目共用同一把密钥。**要关 22 端口或轮换密钥，必须先同步改本仓库流水线与 Secrets（`DEPLOY_SSH_KEY` / `DEPLOY_SSH_KNOWN_HOSTS`）**，见 `docs/todo/archive/2026-10-24-TODO-服务器SSH端口迁移.md`。
- **应用部署与回滚**：发布、`.env`、宿主反代、备份与回滚口径一律看 `deploy/README.md`，本文件不复述。

## 红线

- 私钥**内容**不进文档、不进提交、不进消息；具体私钥路径以当前机器 `~/.ssh/config` 的 `IdentityFile` 为准，**不把某台开发机的盘符或绝对路径写进仓库**。
