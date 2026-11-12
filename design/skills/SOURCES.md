# design/skills · 第三方技能来源与许可

> 最后核实：2026-11-12。技能清单与「适配哪些动作」见 [docs/06-技能.md](../../docs/06-技能.md)。
> **只读入库**：除路径说明外不要修改第三方文件，保持与上游可 diff，便于更新。

## 1. nextlevelbuilder/ui-ux-pro-max-skill

- 上游：https://github.com/nextlevelbuilder/ui-ux-pro-max-skill
- 入库：`ui-ux-pro-max-skill/`（上游 `.claude/skills/` 的 6 个技能：`ui-ux-pro-max`、`design`、`design-system`、`brand`、`banner-design`、`slides`）
- commit：`6ae86f61f520d6ca229d2f40e070410638c908c5`（main，2026-11-12 浅克隆）
- 许可：MIT（`LICENSE` 已随目录保留）
- 版本：`skill.json` 2.15.0
- 未入库：`ui-styling`（shadcn/ui + React + 5.5MB canvas 字体包，与「不引入新组件库」冲突）
- 依赖：Python 3.x（无第三方包；Windows 用 `python`）

## 2. pbakaus/impeccable

- 上游：https://github.com/pbakaus/impeccable
- 入库：`impeccable/`（上游 `.claude/skills/impeccable/` 的构建产物：`SKILL.md` + `reference/` + `scripts/`；上游源文件 `skill/` 不含 SKILL.md，故取构建产物）
- commit：`b4bff1626c344067788c2679f048a20057c7cc5a`（main，2026-11-12 浅克隆）
- 许可：Apache-2.0（`LICENSE`）；`NOTICE.md` 记录 ios/android 参考蒸馏自 MIT 的 ehmo/platform-design-skills，两者均已保留
- 版本：技能 4.8.2 / engine `0.1.22`（`scripts/VERSION`）
- 依赖：引擎动词首次使用需联网下载 engine 二进制到 `~/.impeccable/bin/0.1.22/`（`curl.exe` + `certutil` 校验）；纯文件阅读可覆盖 24 个命令中约 15 个（详见 docs/06 §2.2）
- 注意：`scripts/` 体积主要来自 `data/font-index.json`（1.1MB）与 `live-browser.js`（578KB），顾问模式用不到，但保留以保证技能完整

## 3. vercel-labs/agent-skills

- 上游：https://github.com/vercel-labs/agent-skills
- 入库：`vercel-agent-skills/`（上游 `skills/` 全部 9 个技能；上游根目录的 `*.zip` 打包产物未入库）
- commit：`1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62`（main，2026-11-12 浅克隆）
- 许可：**上游未提供 LICENSE 文件**（只有 README.md）→ 仅作内部参考，不再分发/公开
- 依赖：`web-design-guidelines` / `writing-guidelines` 每次审查时联网拉取规则（raw.githubusercontent.com），离线不可用

## 4. 本地改造（与上游的唯一差异）

`vercel-agent-skills` 的两个审查技能做了**离线快照化**（其余第三方文件一律保持上游原样）：

| 文件 | 改动 | 原因 |
|---|---|---|
| `web-design-guidelines/SKILL.md` | `How It Works` / `Guidelines Source` / `Usage` 三处改为先读 `references/command.md` | 上游要求每次审查联网拉取；离线不可用 |
| `web-design-guidelines/references/command.md` + `SNAPSHOT.md` | 新增（2026-11-12 快照，104 条 / 17 节，SHA-256 `d246b026…`） | 规则可溯源、离线可用 |
| `writing-guidelines/SKILL.md` | 同上 | 上游源本机实测 3 次拉取失败 |
| `writing-guidelines/references/command.md` + `SNAPSHOT.md` | 新增（2026-11-12 快照，146 条 / 22 节，SHA-256 `fb638d78…`） | 同上 |

## 5. 更新步骤

1. 克隆：`git clone --depth 1 --single-branch --branch main <repo> .workbuddy/tmp/skills-recon/<repo>`；GitHub 需代理，加 `-c http.proxy=http://127.0.0.1:7890`
2. 对比：`git diff --no-index <旧目录> <新目录>`，确认只有预期变化（上游可能调整目录结构）
3. 替换对应目录（保持本文记录的「包含 / 排除」规则），更新本文的 commit 与日期
4. `bash scripts/doc-check.sh` + `git status` 检查；在 `docs/04-日志.md` 记一条
