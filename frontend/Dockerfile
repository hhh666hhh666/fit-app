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

# ⚠️ 产物路径是 dist/build/h5 —— uni-app 的 H5 产物比 vite 多一层 build/h5，
#    少抄这一层会 COPY 到不存在的目录（构建期不报错，运行时全站 404）。
RUN npm run build:h5

FROM nginx:alpine
COPY --from=build /app/dist/build/h5 /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
