# 规则快照（本地离线化改造）

- 来源：https://raw.githubusercontent.com/vercel-labs/writing-guidelines/main/command.md
- 快照时间：2026-11-12
- 体积 / SHA-256：14,228 B / `fb638d7821bb4472e4492aedcfb51f2636c7d31d34ff9f01cca5bcdce9b1841f`
- 规模：**146 条规则 / 22 节**（Planning & content type、Voice & tone、Banned words、Concision、AI-generated tells、Tone by content type、Headings、Structure、Lists、Code、Placeholders、Data sizes & units、Money & pricing、Emphasis、Punctuation & typography、Source formatting、Links、Models in examples、AI workflow、Quality checklist、Review、Anti-patterns）

SKILL.md 已改为「先读本快照，联网只用于刷新」——上游要求每次审查重新拉取，而本机实测该源 3 次拉取均失败；快照化后才能离线使用。

刷新方法：`Invoke-WebRequest -Uri <上面的 URL> -Proxy http://127.0.0.1:7890 -UseBasicParsing`，覆盖本目录 `command.md`，然后更新本页与 [SOURCES.md](../../../SOURCES.md)。
