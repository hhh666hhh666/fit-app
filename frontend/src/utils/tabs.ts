// 四个主 tab 的唯一定义：顺序即底栏从左到右（中间圆钮不在表里，占第 2 列）。
//
// 以前同一件事散在四处——nav.ts 的 ORDER、FtTabBar 的 tabs、App.vue 的页面根类名、
// pages.json 的 tabBar——新增或调换一个 tab 要记得改全。现在除 pages.json 外都从这里取；
// pages.json 是框架要读的，改不掉，由 `scripts/check-tab-config.sh` 校验它和这张表一致。

export const TABS = [
  { key: 'home', label: '首页', icon: 'home', iconOn: 'home-fill', url: '/pages/home/index', body: '.home__body', col: 0 },
  { key: 'log', label: '日志', icon: 'list', iconOn: 'list-fill', url: '/pages/log/index', body: '.log__body', col: 1 },
  { key: 'stats', label: '统计', icon: 'chart', iconOn: 'chart-fill', url: '/pages/stats/index', body: '.stats__body', col: 3 },
  { key: 'profile', label: '我的', icon: 'user', iconOn: 'user-fill', url: '/pages/profile/index', body: '.prof__body', col: 4 }
] as const

export type Tab = (typeof TABS)[number]
export type TabKey = Tab['key']

/** 在表里的下标（不是底栏的列）；不认识的 key 给 -1 */
export function tabIndex(key: TabKey): number {
  return TABS.findIndex((t) => t.key === key)
}
