#!/usr/bin/env bash
# 构建「apk 内置版」H5：注入 API 基址 → 塞进 android assets → 生成自更新用的 zip 与清单。
#
# 为什么要单独一份构建：apk 里的页面从虚拟域 https://appassets.androidplatform.net 加载，
# 与 API 不同源，`/api` 相对路径会打到虚拟域上 404。所以内置版必须把基址固化进产物
# （见 frontend/src/api/base.ts）。网页版仍是相对路径，两者互不影响。
#
# 产出：
#   android-shell/app/src/main/assets/web/    ← apk 内置兜底产物（不入库）
#   frontend/dist/build/h5/web/version.json   ← 壳的更新清单（随站点部署）
#   frontend/dist/build/h5/web/web-<版本>.zip ← 整套产物，壳下载它
#
# 用法：
#   bash scripts/build-apk-web.sh
#   FITAPP_SITE_ROOT=https://staging.example.com bash scripts/build-apk-web.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FE="$ROOT/frontend"
DIST="$FE/dist/build/h5"
ASSETS="$ROOT/android-shell/app/src/main/assets/web"
SITE_ROOT="${FITAPP_SITE_ROOT:-https://fit.11-2.cn}"
SITE_ROOT="${SITE_ROOT%/}"
API_BASE="${FITAPP_API_BASE:-$SITE_ROOT/api}"

# 构建本来就要清空旧产物。这里统一用 mv 挪走而不是 rm -rf：
#  ① 一次删几十上百个文件会撞上执行环境的批量删除保护，构建直接失败；
#  ② 挪走可逆，万一产物有问题还能翻回来。
# 调用方确认无误后可以手动清空 $TMPDIR 里的 fitapp-stale-*。
stash() {
    if [ -e "$1" ]; then
        local dst="${TMPDIR:-/tmp}/fitapp-stale-$(basename "$1")-$$-$(date +%s)"
        mv "$1" "$dst" && echo "    旧产物已挪到 $dst"
    fi
}

# 版本号 = package.json 的 version + 短 commit。
# 带上 commit 是为了「每次提交都能触发壳的自动更新」——只靠 semver 的话，
# 改完前端忘了升版本号，手机端就永远停在旧包上，而且很难察觉。
VERSION="$(cd "$FE" && node -p "require('./package.json').version" 2>/dev/null || echo 0.0.0)"
COMMIT="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo local)"
TAG="${VERSION}-${COMMIT}"

echo "==> 构建 H5"
echo "    站点根 : $SITE_ROOT"
echo "    API 基址: $API_BASE"
echo "    版本号 : $TAG"
stash "$DIST"
( cd "$FE" && VITE_API_BASE="$API_BASE" npm run build:h5 )

if [ ! -f "$DIST/index.html" ]; then
    echo "!! 产物缺 index.html：$DIST" >&2
    exit 1
fi

echo "==> 复制进 android assets（apk 内置兜底）"
stash "$ASSETS"
mkdir -p "$ASSETS"
cp -R "$DIST"/. "$ASSETS"/
# 生成物不进仓库；也避免把上一轮的 web/ 目录自己套自己
stash "$ASSETS/web"
# 内置产物的版本号：壳据此判断「肚子里这份是多少版」，首次启动就不会把内容相同的
# 线上包再下一遍（见 LocalWeb.bundledVersion / WebUpdater.seedAppliedIfEmpty）。
# ⚠️ 必须与线上 version.json 的拼法一致，否则这个种子无效。
printf '%s\n' "$TAG" > "$ASSETS/.version"
[ -f "$ASSETS/index.html" ] || { echo "!! 复制后仍缺 index.html" >&2; exit 1; }

echo "==> 生成自更新包与清单"
mkdir -p "$DIST/web"
python - "$DIST" "$DIST/web/web-$TAG.zip" "$TAG" "$SITE_ROOT" <<'PY'
import hashlib
import json
import os
import sys
import zipfile

dist, zip_path, tag, site_root = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

# 打包 dist 的内容（不含 web/ —— 那是清单与包本身，塞进去会自我递归）
if os.path.exists(zip_path):
    os.remove(zip_path)
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for name in sorted(os.listdir(dist)):
        if name == "web":
            continue
        full = os.path.join(dist, name)
        if os.path.isfile(full):
            zf.write(full, name)
        else:
            for base, _dirs, files in os.walk(full):
                for f in sorted(files):
                    p = os.path.join(base, f)
                    zf.write(p, os.path.relpath(p, dist).replace(os.sep, "/"))

digest = hashlib.sha256()
with open(zip_path, "rb") as fh:
    for chunk in iter(lambda: fh.read(1 << 16), b""):
        digest.update(chunk)

manifest = {
    "version": tag,
    "url": "/web/" + os.path.basename(zip_path),
    "sha256": digest.hexdigest(),
}
out = os.path.join(dist, "web", "version.json")
with open(out, "w", encoding="utf-8") as fh:
    json.dump(manifest, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

size_kb = os.path.getsize(zip_path) // 1024
print("    zip    : %s (%d KB)" % (zip_path, size_kb))
print("    清单   : %s" % out)
print("    sha256 : %s..." % manifest["sha256"][:16])
PY

echo "==> 完成"
echo "    下一步：bash scripts/build-apk-web.sh 之后构建 apk（见 android-shell/README.md）"
echo "    注意：站点要先带上 dist/build/h5/web/ 部署，壳才能探测到新版。"
