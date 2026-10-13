# -*- coding: utf-8 -*-
"""生成 V13__seed_preset_foods.sql —— 系统预置食物数据（《中国食物成分表》 + GI 表补搜）。

输入（同目录）：
  food_composition_full.csv   《中国食物成分表》1677 条
  glycemic_index_of_foods.json 血糖生成指数表 259 条
  gi-mapping.json             GI → 成分表 挂载映射（人工核验，86 条）＋放弃 2 条
  搜索结果.csv / 搜索结果-补充.csv  外部补搜结果（194 条，两轮）
  喷瓜全量数据.json            喷瓜的完整数据（选填项明细）

输出：backend/src/main/resources/db/migration/V13__seed_preset_foods.sql
口径：—/空 → NULL；Tr → 0；16 条「缺项实际≈0」按 0 补齐；25 条成分表缺项条目与 171 条 GI 孤儿
     均为外部补全，名字加 * 后缀；预置 user_id=NULL；unit 一律 g（每 100g）。
"""
import csv
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / 'backend' / 'src' / 'main' / 'resources' / 'db' / 'migration' / 'V13__seed_preset_foods.sql'
MISSING = ('—', '')

# ── 清洗读数 ──


def read_csv(path):
    with open(path, encoding='utf-8-sig') as f:
        return list(csv.DictReader(f))


def parse(v):
    """CSV/搜补值 → float | None。'—'/空 → None；'Tr' → 0；剥离尾部 * 标注（如油类能量 '899*'）后解析。"""
    s = (v or '').strip()
    if s in MISSING:
        return None
    if s.lower() == 'tr':
        return 0.0
    s = re.sub(r'\*+$', '', s).strip()
    try:
        return float(s)
    except ValueError:
        return None  # 由调用方记警告


# ── 输入数据 ──

comp = read_csv(HERE / 'food_composition_full.csv')
gi_json = json.load(open(HERE / 'glycemic_index_of_foods.json', encoding='utf-8'))
mapping = json.load(open(HERE / 'gi-mapping.json', encoding='utf-8'))
search1 = read_csv(HERE / '搜索结果.csv')
search2 = read_csv(HERE / '搜索结果-补充.csv')
pumpkin_detail = json.load(open(HERE / '喷瓜全量数据.json', encoding='utf-8'))

warnings = []

# 搜补合并表：食物名 → {kcal, protein, fat, carb, sodium}（第二轮非空值优先）
SEARCH_COLS = {'能量kcal': 'kcal', '蛋白g': 'protein', '脂肪g': 'fat', '碳水g': 'carb', '钠mg': 'sodium'}
search_merged = {}


def merge_search(rows, overwrite):
    for r in rows:
        name = r['食物名'].strip()
        d = search_merged.setdefault(name, {})
        for src_col, field in SEARCH_COLS.items():
            v = (r.get(src_col) or '').strip()
            if v:
                parsed = parse(v)
                if parsed is not None:
                    if overwrite or field not in d:
                        d[field] = parsed
                else:
                    warnings.append(f'搜补值无法解析：{name} {src_col}={v!r}')


merge_search(search1, overwrite=False)
merge_search(search2, overwrite=True)

# 16 条「缺项实际≈0」补 0 补丁：名字 → {字段: 0}
ZERO_PATCH = {
    '白脱（食品工业）［牛油、黄油］': {'protein': 0}, '胡麻油': {'protein': 0},
    '混合油（菜+棕）': {'protein': 0}, '辣椒油': {'protein': 0}, '棕榈油': {'protein': 0},
    '冬果梨（罐头）': {'fat': 0}, '白金瓜': {'fat': 0}, '紫背天葵［红风菜、血皮菜］': {'fat': 0},
    '穿心莲': {'fat': 0}, '白瓜': {'fat': 0}, '笋瓜［生瓜］': {'fat': 0}, '罗勒［兰香］': {'fat': 0},
    '食用大黄（鲜）': {'fat': 0}, '荆芥': {'fat': 0}, '鸭蛋白': {'fat': 0}, '鹅蛋白': {'fat': 0},
}

# 25 条成分表缺项条目：外部补全 → 名字加 *，缺项用搜补值
REFILL_NAMES = ['蜂胶液', '蜂王浆', '麻子籽', '薤［皎头］', '艾蒿', '白花菜', '白花桔梗',
                '百里香（鲜）', '刺楸（鲜）', '豆腐柴（鲜）', '独行菜（鲜）', '独行菜（宽，鲜）',
                '轮叶党参', '牛至', '喷瓜', '婆罗门参（白，鲜）', '婆罗门参（黑，鲜）［鸦葱］',
                '食用黄麻', '酸模（鲜）', '香茅', '小旋花（鲜）［狗儿蔓］', '珍珠花菜',
                '紫花桔梗', '紫萼香茶菜', '苣荬菜（尖叶）［取荬菜，苦麻子］']

# 成分表 CSV 列 → food 字段
COMP_MAP = {
    'water': 'water', 'energyKCal': 'kcal', 'protein': 'protein', 'fat': 'fat', 'CHO': 'carb',
    'dietaryFiber': 'fiber', 'cholesterol': 'cholesterol', 'ash': 'ash',
    'vitaminA': 'va', 'thiamin': 'vb1', 'riboflavin': 'vb2', 'niacin': 'niacin',
    'vitaminC': 'vc', 'vitaminETotal': 've',
    'Ca': 'calcium', 'P': 'phosphorus', 'K': 'potassium', 'Na': 'sodium', 'Mg': 'magnesium',
    'Fe': 'iron', 'Zn': 'zinc', 'Se': 'selenium', 'Cu': 'copper', 'Mn': 'manganese',
}

# ── 汇总所有行 ──
# 每行：dict(field -> value | None)，外加 name/unit/category/gi
rows = []

# A. 成分表 1677 条
for r in comp:
    name = r['foodName'].strip()
    item = {'name': name, 'unit': 'g', 'category': (r['category'] or '').strip() or None, 'gi': None}
    for col, field in COMP_MAP.items():
        raw = r.get(col, '')
        v = parse(raw)
        # 能量特例：kcal 缺失（如 'un'）时由 kJ 换算（与 FoodService 导入的 kJ 换算同口径）
        if v is None and field == 'kcal':
            kj = parse(r.get('energyKJ', ''))
            if kj is not None:
                v = round(kj / 4.184, 2)
        if v is None and (raw or '').strip() not in MISSING and (raw or '').strip().lower() != 'tr':
            warnings.append(f'成分表值无法解析：{name} {col}={raw!r}')
        item[field] = v

    # 16 条补 0
    for field, val in ZERO_PATCH.get(name, {}).items():
        if item.get(field) is None:
            item[field] = float(val)
        else:
            warnings.append(f'补 0 未生效（已有值）：{name} {field}={item[field]}')

    # 25 条外部补全：加星 + 缺项用搜补值
    if name in REFILL_NAMES:
        item['name'] = name + '*'
        patch = search_merged.get(name, {})
        for field in ('kcal', 'protein', 'fat', 'carb', 'sodium'):
            if item.get(field) is None and field in patch:
                item[field] = patch[field]
        # 喷瓜：选填项明细（CSV 空时补）
        if name == '喷瓜':
            for field, val in pumpkin_detail['fields'].items():
                if field in ('kcal', 'protein', 'fat', 'carb', 'sodium'):
                    continue
                if item.get(field) is None:
                    item[field] = float(val)
    rows.append(item)

# B. GI 孤儿 171 条（259 - 挂载 86 - 放弃 2）
map_index = {m['index'] for m in mapping['mappings']}
drop_index = {d['index'] for d in mapping.get('dropped', [])}
orphan_count = 0
for g in gi_json:
    for it in g['list']:
        if it['index'] in map_index or it['index'] in drop_index:
            continue
        name = it['foodName'].strip()
        if name.startswith('*'):
            name = name.lstrip('*').strip()
        item = {'name': name + '*', 'unit': 'g', 'category': g['foodGroup'], 'gi': float(it['GI'])}
        patch = search_merged.get(name, {})
        for field in ('kcal', 'protein', 'fat', 'carb', 'sodium'):
            item[field] = patch.get(field)
        if any(item.get(f) is None for f in ('kcal', 'protein', 'fat', 'carb')):
            warnings.append(f'GI 孤儿四值不齐：{name} {item}')
        rows.append(item)
        orphan_count += 1

# C. GI 挂载 86 条：写到对应成分表行
by_code = {}
for item, r in zip(rows, comp):
    by_code[r['foodCode']] = item  # 成分表行以 foodCode 定位
gi_mounted = 0
for m in mapping['mappings']:
    target = by_code.get(m['code'])
    if target is None:
        warnings.append(f'挂载目标找不到：{m["gi"]} -> code {m["code"]}')
        continue
    if target['gi'] is not None:
        warnings.append(f'挂载目标已有 GI：{m["code"]} {target["name"]}')
    target['gi'] = float(m['value'])
    gi_mounted += 1

# ── SQL 生成 ──
COLS = ['user_id', 'name', 'unit', 'category', 'kcal', 'protein', 'fat', 'carb', 'sodium',
        'cholesterol', 'fiber', 'insoluble_fiber', 'gi', 'va', 've', 'vb1', 'vb2', 'niacin', 'vc',
        'calcium', 'phosphorus', 'potassium', 'magnesium', 'iron', 'zinc', 'selenium',
        'copper', 'manganese', 'water', 'ash']


def fmt_num(v):
    if v is None:
        return 'NULL'
    r = round(float(v), 2)
    if r == int(r):
        return str(int(r))
    return ('%.2f' % r).rstrip('0').rstrip('.')


def q(s):
    return "'" + str(s).replace('\\', '\\\\').replace("'", "''") + "'"


def value_tuple(item):
    vals = []
    for c in COLS:
        if c == 'user_id':
            vals.append('NULL')
        elif c == 'unit':
            vals.append("'g'")
        elif c == 'name':
            vals.append(q(item['name']))
        elif c == 'category':
            vals.append(q(item['category']) if item['category'] else 'NULL')
        else:
            vals.append(fmt_num(item.get(c)))
    return '(' + ','.join(vals) + ')'


header = '''-- 系统预置食物数据：%d 条 = 《中国食物成分表》1677 条 + GI 表孤儿补搜 171 条（含 25 条成分表缺项条目外部补全）。
-- 本文件由 scripts/preset-data/gen_seed_sql.py 生成——请勿手改；修正数据请改输入后重跑脚本并新增迁移。
-- 口径：'—'/空 → NULL；'Tr' → 0；16 条「缺项实际≈0」按 0 补齐；196 条外部补全的条目名字带 * 后缀。
-- 预置条目 user_id=NULL（只读，可「复制为自定义」）；unit 一律 g（每 100g）。
DELETE FROM `food` WHERE `user_id` IS NULL;
''' % len(rows)

batches = []
BATCH = 150
for i in range(0, len(rows), BATCH):
    chunk = rows[i:i + BATCH]
    lines = ['']
    lines.append('INSERT INTO `food` (`' + '`,`'.join(COLS) + '`) VALUES')
    lines.append(',\n'.join(value_tuple(it) for it in chunk) + ';')
    batches.append('\n'.join(lines))

OUT.write_text(header + '\n'.join(batches) + '\n', encoding='utf-8')

# ── 报告 ──
star = sum(1 for it in rows if it['name'].endswith('*'))
total_with_gi = sum(1 for it in rows if it.get('gi') is not None)
na_null = sum(1 for it in rows if it.get('sodium') is None)
print('总条数:', len(rows))
print('带 * 条目:', star, '（成分表补全 25 + GI 孤儿', orphan_count, '）')
print('挂载 GI 到成分表行:', gi_mounted, '| GI 非空总行数（含孤儿自带）:', total_with_gi)
print('钠为空（未测）:', na_null)
print('SQL 语句批数:', len(batches))
print('输出文件:', OUT, '| 大小: %.1f KB' % (OUT.stat().st_size / 1024))
print()
if warnings:
    print('!! 警告 %d 条：' % len(warnings))
    for w in warnings[:40]:
        print('  ', w)
else:
    print('警告：无')
