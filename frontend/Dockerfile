# 前端（uni-app H5）镜像：构建产物 → nginx 静态托管 + /api 反代。
#
# 用 slim（Debian/glibc）而非 alpine：esbuild 没有 x64-musl 的官方二进制，
# alpine 下 vite build 会报 "You installed esbuild for another platform"。
FROM node:22-slim AS build
WORKDIR /app

# NPM_REGISTRY 默认 npmmirror（本机/国内快）；CI 在境外 runner 上传 https://registry.npmjs.org。
# ⚠️ ARG 要声明在用到它的这个 stage 里、且排在用到它的 RUN 之前；
#    写到 FROM nginx:alpine 之后它就看不到了。
ARG NPM_REGISTRY=https://registry.npmmirror.com

# 依赖层：npm ci 严格按 package-lock.json 安装
COPY package.json package-lock.json ./
RUN npm ci --registry="$NPM_REGISTRY" --no-audit --no-fund

COPY . .

# 版本与提交信息在构建期注入（由 release.yml 从 git tag 算出）。
# ⚠️ 声明在 npm ci **之后**、build **之前**：ARG 一变它之后的层缓存全失效，
#    放到前面会让每次发版都白重装一遍依赖。
ARG APP_VERSION=dev
ARG APP_COMMIT=unknown
# apk 内置版的 API 基址来源（见下面第二、三次构建）。换环境时改这个 build-arg。
ARG SITE_ROOT=https://fit.11-2.cn

# zip 只用于打壳的自动更新包，装在 build stage 里、不进最终镜像。
RUN apt-get update \
 && apt-get install -y --no-install-recommends zip \
 && rm -rf /var/lib/apt/lists/*

# ⚠️ 产物路径是 dist/build/h5 —— uni-app 的 H5 产物比 vite 多一层 build/h5，
#    少抄这一层会 COPY 到不存在的目录（构建期不报错，运行时全站 404）。

# ── 第 1 次构建：**网页版**产物（相对 /api、与站点同源）——镜像真正托管的就是它 ──
RUN npm run build:h5 && mv dist/build/h5 /app/web-site

# ── 第 2 次构建：**apk 内置版**产物（API 基址绝对化）──
# 壳里的页面 origin 是虚拟域 https://appassets.androidplatform.net，用相对 /api 会打到
# 虚拟域上 404，所以这一份必须把基址固化进去（见 frontend/src/api/base.ts）。
# 它**不直接对外**，只用来打壳要下载的更新包；网页版仍是上一份。
RUN VITE_API_BASE="${SITE_ROOT}/api" npm run build:h5

# ── 第 3 次：打 zip ＋ 清单，放进网页版产物的 web/ 目录 ──
# 壳启动时探测 /web/version.json，版本不同就下载 zip、校验 sha256、下次启动生效（ADR 0034）。
# ⚠️ 版本号拼法必须与 scripts/build-apk-web.sh 一致（版本 ＋ 短 commit）：两处产物由不同的
#    路径生成（本机打 apk、CI 出发版包），拼法一漂，壳就会反复判定「有新版本」重复下载。
RUN set -eux; \
    TAG="${APP_VERSION}-${APP_COMMIT}"; \
    mkdir -p /app/web-site/web; \
    ( cd dist/build/h5 && zip -qr "/app/web-site/web/web-${TAG}.zip" . ); \
    SHA="$(sha256sum "/app/web-site/web/web-${TAG}.zip" | cut -d' ' -f1)"; \
    printf '{\n  "version": "%s",\n  "url": "/web/web-%s.zip",\n  "sha256": "%s"\n}\n' \
      "$TAG" "$TAG" "$SHA" > /app/web-site/web/version.json

FROM nginx:alpine
COPY --from=build /app/web-site /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
