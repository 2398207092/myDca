# 规则快照（本地离线化改造）

- 来源：https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md
- 快照时间：2026-11-12
- 体积 / SHA-256：8,055 B / `d246b026f4f29b5823a9cc857f9edf3d2507002e055e32040cadeaf3b38e0234`
- 规模：**104 条规则 / 17 节**（Accessibility、Focus States、Forms、Animation、Typography、Content Handling、Images、Performance、Navigation & State、Touch & Interaction、Safe Areas & Layout、Dark Mode & Theming、Locale & i18n、Hydration Safety、Hover & Interactive States、Content & Copy、Anti-patterns）

SKILL.md 已改为「先读本快照，联网只用于刷新」——上游要求每次审查重新拉取，本机对 raw.githubusercontent.com 的访问不稳定，且项目要求「数字可溯源」，故快照化。

刷新方法：`Invoke-WebRequest -Uri <上面的 URL> -Proxy http://127.0.0.1:7890 -UseBasicParsing`，覆盖本目录 `command.md`，然后更新本页与 [SOURCES.md](../../../SOURCES.md)。
