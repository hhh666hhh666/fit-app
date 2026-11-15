#!/usr/bin/env python3
"""把 aliases-zh.tsv 里的别名合并进管理端导出的预置食物 JSON（ADR 0056）。

预置数据以线上数据库为准（ADR 0049），所以别名不走迁移脚本，而是：
  1. 管理端「预置食物 → 导出」，得到 foods.json（带 id）；
  2. python3 scripts/preset-data/merge-aliases.py foods.json -o aliases-import.json
  3. 管理端「导入」aliases-import.json：先预检（应当全是「更新」），再正式导入。

输出只含这次有新别名的那几条（导入按 id 整体覆盖，没列出的预置不受影响）。
已有的别名保留，新别名追加在后面；名字方括号里已有的叫法、和名字一样的都跳过；每条最多 10 个。

另有 --check 模式：不读导出文件，只拿初始种子（V13）核对表里的名字都存在，供改表后自检。
"""
import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TSV = HERE / "aliases-zh.tsv"
SEED = HERE.parent.parent / "backend/src/main/resources/db/migration/V13__seed_preset_foods.sql"
MAX_COUNT = 10
MAX_LEN = 16


def load_table():
    table = {}
    for n, line in enumerate(TSV.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            sys.exit(f"{TSV.name} 第 {n} 行格式不对（要「名字<TAB>别名」）：{line}")
        name, aliases = parts[0].strip(), [a.strip() for a in re.split(r"[、，,]", parts[1]) if a.strip()]
        for a in aliases:
            if len(a) > MAX_LEN:
                sys.exit(f"{TSV.name} 第 {n} 行：别名「{a}」超过 {MAX_LEN} 个字")
        if name in table:
            sys.exit(f"{TSV.name} 第 {n} 行：「{name}」重复出现")
        table[name] = aliases
    return table


def bracket_aliases(name):
    """名字方括号里的叫法：番茄［西红柿］、乌鳢［黑鱼、乌鱼、生鱼］。"""
    out = set()
    for inner in re.findall(r"［([^］]*)］", name):
        out.update(a.strip() for a in re.split(r"[、，,]", inner) if a.strip())
    return out


def merged(name, existing, new):
    skip = bracket_aliases(name) | {name}
    out = [a for a in (existing or []) if a]
    for a in new:
        if a not in skip and a not in out:
            out.append(a)
    return out[:MAX_COUNT]


def check():
    seed = set(re.findall(r"^\(NULL,'([^']+)'", SEED.read_text(encoding="utf-8"), re.M))
    table = load_table()
    missing = [n for n in table if n not in seed]
    print(f"别名表 {len(table)} 条，种子里找不到的 {len(missing)} 条")
    for n in missing:
        print("  ✗", n)
    return 1 if missing else 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("export", nargs="?", help="管理端导出的预置食物 JSON")
    ap.add_argument("-o", "--out", default="aliases-import.json", help="输出文件（默认 aliases-import.json）")
    ap.add_argument("--check", action="store_true", help="只拿 V13 种子核对名字")
    args = ap.parse_args()
    if args.check:
        sys.exit(check())
    if not args.export:
        ap.error("要给管理端导出的 JSON 文件，或者用 --check")

    foods = json.loads(Path(args.export).read_text(encoding="utf-8"))
    table = load_table()
    by_name = {}
    for f in foods:
        by_name.setdefault(f.get("name"), []).append(f)

    out, missing = [], []
    for name, aliases in table.items():
        rows = by_name.get(name)
        if not rows:
            missing.append(name)
            continue
        for f in rows:
            if "id" not in f:
                sys.exit("导出文件里没有 id——要用管理端的「导出」，不是 App 里的导出")
            new = merged(name, f.get("aliases"), aliases)
            if new != (f.get("aliases") or []):
                row = dict(f)
                row["aliases"] = new
                row.pop("archived", None)  # 导入不读它，留着也无害；去掉免得误会
                out.append(row)

    Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"写出 {len(out)} 条到 {args.out}；别名表 {len(table)} 条，库里找不到的 {len(missing)} 条")
    for n in missing:
        print("  ✗", n)


if __name__ == "__main__":
    main()
