# 开源动作数据集 → 预置动作

把 [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) 的动作收进预置动作库。为什么这么做、版权口径与合并规则见 [ADR 0055](../../docs/adr/0055-exercise-dataset-and-media.md)。

## 四步

```bash
bash scripts/exercise-dataset/fetch.sh                      # 1. 下载数据集到 .cache/（约 160 MB，不入库）
python3 -I scripts/exercise-dataset/build.py                # 2. 生成 out/exercises-import.json
bash scripts/exercise-dataset/sync-media.sh aliyun:/srv/fit-app/uploads   # 3. 动图同步到服务器（本地开发换成 backend/target/dev-uploads）
```

4. 管理端「预置动作 → 导入」选 `out/exercises-import.json`，看预检结果再确认导入。重复导入按编号更新，不会重复建。

## 文件

| 文件 | 说明 |
|------|------|
| `names-zh.tsv` | 数据集编号 → 中文名，AI 拟定。改名就改这里再跑第 2、4 步；和原有预置同名的会被认领（只补资料） |
| `build.py` | 部位→肌群、器械与肌肉的中英对照、要领清洗都在这里 |
| `fetch.sh` / `sync-media.sh` | 下载数据集；把动图与缩略图放进上传目录的 `exercise-media/` |
| `.cache/`、`out/` | 下载物与生成物，`.gitignore` 挡着。⚠️ 动图版权归 Gym visual，不要提交进仓库 |
