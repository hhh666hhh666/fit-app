// 食物营养字段表：编辑页（表单）与详情页（只读展示）共用。
// 键名与后端 camelCase 字段一一对应；「主组」为 4+1 必填，「可选分组」选填。

export interface FoodField {
  key: string
  label: string
}

/** 主组（4+1 必填）。 */
export const MAIN_FIELDS: FoodField[] = [
  { key: 'kcal', label: '热量（大卡）' },
  { key: 'protein', label: '蛋白质（g）' },
  { key: 'fat', label: '脂肪（g）' },
  { key: 'carb', label: '碳水化合物（g）' },
  { key: 'sodium', label: '钠（mg）' }
]

/** 可选营养分组。 */
export const OPTIONAL_GROUPS: { title: string; fields: FoodField[] }[] = [
  {
    title: '脂类相关',
    fields: [
      { key: 'saturatedFat', label: '饱和脂肪酸 (g)' },
      { key: 'transFat', label: '反式脂肪酸 (g)' },
      { key: 'monoFat', label: '单不饱和脂肪酸 (g)' },
      { key: 'polyFat', label: '多不饱和脂肪酸 (g)' },
      { key: 'cholesterol', label: '胆固醇 (mg)' }
    ]
  },
  {
    title: '碳水相关',
    fields: [
      { key: 'sugar', label: '糖 (g)' },
      { key: 'fiber', label: '膳食纤维 (g)' },
      { key: 'solubleFiber', label: '可溶性膳食纤维 (g)' },
      { key: 'insolubleFiber', label: '不溶性膳食纤维 (g)' },
      { key: 'starch', label: '淀粉 (g)' },
      { key: 'lactose', label: '乳糖 (g)' }
    ]
  },
  {
    title: '维生素',
    fields: [
      { key: 'va', label: '维生素A (μg RE)' },
      { key: 'vd', label: '维生素D (μg)' },
      { key: 've', label: '维生素E (mg α-TE)' },
      { key: 'vb1', label: '维生素B1 (mg)' },
      { key: 'vb2', label: '维生素B2 (mg)' },
      { key: 'vb6', label: '维生素B6 (mg)' },
      { key: 'vb12', label: '维生素B12 (μg)' },
      { key: 'vc', label: '维生素C (mg)' },
      { key: 'niacin', label: '烟酸 (mg)' },
      { key: 'folate', label: '叶酸 (μg DFE)' },
      { key: 'pantothenic', label: '泛酸 (mg)' },
      { key: 'biotin', label: '生物素 (μg)' }
    ]
  },
  {
    title: '矿物质',
    fields: [
      { key: 'calcium', label: '钙 (mg)' },
      { key: 'phosphorus', label: '磷 (mg)' },
      { key: 'potassium', label: '钾 (mg)' },
      { key: 'magnesium', label: '镁 (mg)' },
      { key: 'iron', label: '铁 (mg)' },
      { key: 'zinc', label: '锌 (mg)' },
      { key: 'iodine', label: '碘 (μg)' },
      { key: 'selenium', label: '硒 (μg)' },
      { key: 'copper', label: '铜 (mg)' },
      { key: 'manganese', label: '锰 (mg)' },
      { key: 'fluoride', label: '氟 (mg)' },
      { key: 'chromium', label: '铬 (μg)' },
      { key: 'molybdenum', label: '钼 (μg)' }
    ]
  },
  {
    title: '理化成分',
    fields: [
      { key: 'water', label: '水分 (g)' },
      { key: 'ash', label: '灰分 (g)' },
      { key: 'alcohol', label: '乙醇 (g)' },
      { key: 'organicAcid', label: '有机酸 (g)' }
    ]
  }
]
