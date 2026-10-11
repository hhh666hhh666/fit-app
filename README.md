# fit-app

> 一句话：个人自用的健身房训练记录 App——把每次训练（力量＋有氧）记下来，看得到自己的进步。

## 它解决什么问题

个人自用的训练记录工具：预设与自定义动作库、自建训练计划、组次录入、训练日志、统计（PR／频次／趋势）与身体档案。数据存在自己的服务器；手机端是 Android App（uni-app 网页＋套壳）。

现状：需求打磨完成（范围见 [`docs/requirements/scope.md`](docs/requirements/scope.md)，决策见 [`docs/adr/`](docs/adr/)），开发未开始。

**不是什么**：不是公开产品（不做注册风控与运营）；不预设训练计划模板；暂不含饮食模块（后置第二阶段）；不做 iOS 与小程序（现阶段）。

## 跑起来（本机开发）

前置：JDK 21、Maven 3.9+、Node 22、Docker（只用来起数据库）。

```bash
# 1. 起数据库
docker compose -f docker-compose.dev.yml up -d

# 2. 起后端（默认 8080）
cd backend && mvn -B -ntp spring-boot:run

# 3. 起前端（默认 5173，/api 已代理到后端）
cd frontend && npm install && npm run dev
```

打开 `http://127.0.0.1:5173/`，首页上那行来自后端接口的返回值是「前后端通了」的肉眼判据。

## 目录

| 目录 | 是什么 |
|------|--------|
| `backend/` | Spring Boot + MyBatis-Plus + Flyway |
| `frontend/` | Vue 3 + Vite + TypeScript + shadcn-vue + Tailwind v4 |
| `docs/` | 文档体系（索引见 `docs/README.md`） |
| `scripts/` | 机械守卫脚本 |
| `deploy/` | 部署侧配置模板（可选章节生成） |

## 配置要点

- 后端配置的默认值是**本机开发兜底**，容器里由 compose 注入环境变量覆盖。
- JWT 密钥必须换成本机随机值，见 `docs/operations/configuration.md`。
- `.env` 不入库，模板是 `.env.example`。

## CI 与部署

- CI（`.github/workflows/ci.yml`）：类型检查与构建、后端单测、文档守卫。
- 部署：见 [`deploy/README.md`](deploy/README.md)（可选章节，未生成则无）。
