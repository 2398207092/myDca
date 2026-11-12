# design/skills · 技能库（仓库内）

> 2026-11-12：入库 3 家第三方技能（共 16 个技能）+ 原有 `taste-skill`。
> **唯一清单与「适配哪些动作」： [docs/06-技能.md](../../docs/06-技能.md)**（本页只做目录索引）。
> 来源、commit、许可、更新方法：[SOURCES.md](SOURCES.md)。

## 目录

| 目录 | 来源 | 说明 |
|---|---|---|
| `taste-skill/` | Leonxlnx/taste-skill（MIT） | 2026-11-05 起就在仓库内 |
| `ui-ux-pro-max-skill/` | nextlevelbuilder/ui-ux-pro-max-skill（MIT） | 6 个技能：设计智能检索 / 设计系统 / 品牌 / 横幅 / 幻灯片 |
| `impeccable/` | pbakaus/impeccable（Apache-2.0） | 24 个命令：评审 / 审计 / 打磨 / 加固 / 动效 |
| `vercel-agent-skills/` | vercel-labs/agent-skills（上游无 LICENSE） | 9 个技能，本项目只推荐 `web-design-guidelines` / `writing-guidelines` |

## 使用约定

1. **技能是顾问，不是依赖**：不改 `package.json`、不加组件库、不引入新依赖。
2. **一次只用一家**：同一任务不叠加两个技能（评审用 impeccable，规范审计用 web-design-guidelines，不同时跑）。
3. **产物先给用户**：不把 `PRODUCT.md` / `DESIGN.md` / `.impeccable/` / `design-system/` 写进仓库；临时产物放 `.workbuddy/tmp/`。
4. **不引用旧设计产物**（AGENTS §3.5）；技能建议与 `design/00-基准.md` 冲突时，以项目规则为准。
5. 改 UI 必须给真实渲染截图（390px、0 console、0 横向溢出）。

## 仓库外（不重复入库）

- DSH 全局已装（`~/.dsh/skills/`）：`impeccable`（旧版，引擎 0.1.18）、`web-design-guidelines`、`frontend-design`、`huashu-design`、`transitions-dev`、`spt-project-interaction-design`、`hallmark`、`theme-factory`、`canvas-design`、`brand-guidelines`（后两个不用于 App UI）。
- 仓库外缓存：`D:\mySpace\backups\skills-cache\`（hallmark、impeccable 旧版、jakubkrehel/skills 等）。
