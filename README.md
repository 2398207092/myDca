# 种树 · 基金追踪器

> 个人自用的基金/ETF 记账 App：持仓、交易、分红、定投、现金、资产快照。
> **记账工具——算错钱比崩溃更严重。**

---

## 功能

- **持仓管理**：买入/卖出/送股/复投，三种成本算法，市值与盈亏自动重算
- **分红追踪**：自动抓取分红记录，日历展示登记日/除息日/派息日，支持自动到账与复投
- **定投计划**：日/周/双周/月，按交易日历自动执行
- **资产总览**：现金/BTC/美股/黄金/红利分类，快照与走势图
- **数据自检**：每日对账 6 类规则，监控日志可在「我的」页查看

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2 + Java 17 + JPA + MySQL 8（:8080） |
| 前端 | Vue 3.4 + Vite 5 + TypeScript + Tailwind 3（:5173） |
| 部署 | 前端 GitHub Actions 自动；后端手动 `scp` + `deploy.sh` |

## 快速启动

```bash
# 后端（需本机 MySQL 8，并设置 DB_URL / DB_USERNAME / DB_PASSWORD）
cd myPhonePro/fund-tracker-backend && mvn spring-boot:run

# 前端
cd myPhonePro/stitch_fund_dividend_tracker && npm install && npm run dev
# → http://localhost:5173
```

## 文档

| 文档 | 内容 |
|------|------|
| [docs/00-INDEX.md](docs/00-INDEX.md) | **文档总入口**：导航、维护规则、当前状态 |
| [docs/01-系统.md](docs/01-系统.md) | 架构、模块、数据表、核心链路、算法口径 |
| [docs/02-运维.md](docs/02-运维.md) | 部署、服务器、回滚、已知运维风险 |
| [docs/03-问题.md](docs/03-问题.md) | 当前未修复问题（含 6 条 P0） |
| [AGENTS.md](AGENTS.md) | 维护红线与工作约定（AI 与人类通用） |

## 状态

代码基线 `6e6ca7d`，可编译可运行。**存在 6 条 P0（认证绕过、分红/定投重复入账），修复前不要叠加新功能**，详见 [docs/03-问题.md](docs/03-问题.md)。
