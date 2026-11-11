#!/usr/bin/env python3
"""把开源动作数据集转成管理端「导入预置动作」用的 JSON（ADR 0055）。

用法：python3 -I scripts/exercise-dataset/build.py
  读：.cache/exercises-dataset/data/exercises.json（先跑 fetch.sh）＋ 同目录的 names-zh.tsv
  写：out/exercises-import.json

只取文字（MIT）：中文名、英文名、肌群、器械、目标肌、辅助肌群、中文要领，外加动图文件的键名。
动图本身不进这个文件，由 sync-media.sh 单独放到上传目录。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATASET = HERE / ".cache" / "exercises-dataset"
OUT = HERE / "out" / "exercises-import.json"

# 数据集的部位 → 本项目的七个肌群（CONTEXT.md「肌群」）
MUSCLE = {
    "chest": "chest", "back": "back",
    "upper legs": "leg", "lower legs": "leg",
    "shoulders": "shoulder", "neck": "shoulder",
    "upper arms": "arm", "lower arms": "arm",
    "waist": "core", "cardio": "cardio",
}

EQUIPMENT = {
    "assisted": "辅助器械", "band": "弹力带", "barbell": "杠铃", "body weight": "自重",
    "bosu ball": "波速球", "cable": "绳索", "dumbbell": "哑铃", "elliptical machine": "椭圆机",
    "ez barbell": "曲杆", "hammer": "大锤", "kettlebell": "壶铃", "leverage machine": "固定器械",
    "medicine ball": "药球", "olympic barbell": "奥杆", "resistance band": "阻力带",
    "roller": "泡沫轴", "rope": "绳", "skierg machine": "滑雪机", "sled machine": "滑轨器械",
    "smith machine": "史密斯机", "stability ball": "瑞士球", "stationary bike": "健身车",
    "stepmill machine": "登山机", "tire": "轮胎", "trap bar": "六角杠",
    "upper body ergometer": "手摇车", "weighted": "负重", "wheel roller": "健腹轮",
}

MUSCLE_ZH = {
    # target
    "abductors": "髋外展肌", "abs": "腹肌", "adductors": "髋内收肌", "biceps": "肱二头肌",
    "calves": "小腿", "cardiovascular system": "心肺", "delts": "三角肌", "forearms": "前臂",
    "glutes": "臀肌", "hamstrings": "腘绳肌", "lats": "背阔肌", "levator scapulae": "肩胛提肌",
    "pectorals": "胸肌", "quads": "股四头肌", "serratus anterior": "前锯肌", "spine": "竖脊肌",
    "traps": "斜方肌", "triceps": "肱三头肌", "upper back": "上背",
    # secondary
    "abdominals": "腹肌", "ankle stabilizers": "踝稳定肌", "ankles": "踝", "back": "背部",
    "brachialis": "肱肌", "chest": "胸部", "core": "核心", "deltoids": "三角肌", "feet": "足部",
    "grip muscles": "握力", "groin": "腹股沟", "hands": "手", "hip flexors": "髋屈肌",
    "inner thighs": "大腿内侧", "latissimus dorsi": "背阔肌", "lower abs": "下腹",
    "lower back": "下背", "obliques": "腹斜肌", "quadriceps": "股四头肌",
    "rear deltoids": "三角肌后束", "rhomboids": "菱形肌", "rotator cuff": "肩袖", "shins": "胫骨前肌",
    "shoulders": "肩部", "soleus": "比目鱼肌", "sternocleidomastoid": "胸锁乳突肌",
    "trapezius": "斜方肌", "upper chest": "上胸", "wrist extensors": "腕伸肌",
    "wrist flexors": "腕屈肌", "wrists": "腕",
}

# 数据集每条都以「重复所需的重复次数」收尾，对读的人没有信息量
BOILERPLATE = re.compile(r"^重复.{0,12}次数[。.]?$")


def fail(msg: str) -> None:
    print(f"✗ {msg}", file=sys.stderr)
    sys.exit(1)


def main() -> None:
    src = DATASET / "data" / "exercises.json"
    if not src.exists():
        fail(f"找不到 {src}，先跑 fetch.sh")
    rows = json.loads(src.read_text(encoding="utf-8"))

    names = {}
    for line in (HERE / "names-zh.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        sid, zh = line.split("\t")
        names[sid] = zh.strip()

    out, seen = [], set()
    for r in rows:
        sid = r["id"]
        zh = names.get(sid)
        if not zh:
            fail(f"{sid} {r['name']} 没有中文名（names-zh.tsv）")
        if zh in seen:
            fail(f"中文名重复：{zh}")
        if len(zh) > 64:
            fail(f"中文名过长：{zh}")
        seen.add(zh)

        muscle = MUSCLE.get(r["category"]) or fail(f"{sid} 未知部位 {r['category']}")
        equipment = EQUIPMENT.get(r["equipment"]) or fail(f"{sid} 未知器械 {r['equipment']}")
        target = MUSCLE_ZH.get(r["target"]) or fail(f"{sid} 未知目标肌 {r['target']}")
        secondary = []
        for m in r.get("secondary_muscles") or []:
            z = MUSCLE_ZH.get(m) or fail(f"{sid} 未知辅助肌 {m}")
            if z != target and z not in secondary:
                secondary.append(z)
        steps = [s.strip() for s in (r.get("instruction_steps") or {}).get("zh") or [] if s.strip()]
        steps = [s for s in steps if not BOILERPLATE.match(s)]

        media = Path(r["gif_url"]).stem
        for p in (DATASET / "videos" / f"{media}.gif", DATASET / "images" / f"{media}.jpg"):
            if not p.exists():
                fail(f"{sid} 缺少动图文件 {p.name}")

        out.append({
            "sourceId": sid,
            "name": zh,
            # 数据集里少数英文名有编码错乱（45в° → 45°）
            "nameEn": r["name"].replace("в°", "°"),
            "muscle": muscle,
            "equipment": equipment,
            "target": target,
            "secondary": secondary,
            "steps": steps,
            "media": media,
        })

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"✓ {len(out)} 个动作 → {OUT.relative_to(HERE.parent.parent)}（{OUT.stat().st_size // 1024} KB）")


if __name__ == "__main__":
    main()
