# 种树 · 基金追踪器

> 个人基金/ETF 投资追踪工具，自动抓取净值与分红数据，支持定投计划与现金记账。

---

## 功能特性

- **持仓管理** — 记录基金买入/卖出，自动计算市值、成本、盈亏、股息率
- **分红日历** — 自动抓取持仓基金的分红记录，日历视图展示登记日/除息日/派息日
- **定投自动化** — 创建定投计划，按日/周/月/季自动生成买入记录
- **分红预测** — 基于历史分红数据预测未来分红金额及时间
- **现金记账** — 自动关联交易与现金账户，支持手动调整
- **数据审计** — 每日自动对账，检测数据异常

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + MySQL 8 |
| 前端 | Vue 3 + Vite + Tailwind CSS + Ionic |
| 部署 | 阿里云服务器 + GitHub Actions CI/CD |

---

## 核心算法

详见 [docs/财务算法分析报告.md](docs/财务算法分析报告.md)

- 三种成本算法：分红摊薄 / 摊薄成本 / 加权平均
- 预测分红：基于近3年历史数据
- 复投回本年限：迭代模拟分红再投资
- 数据审计：6条规则每日定时检查

---

## 项目结构

```
myDca/
├── myPhonePro/                          # 后端 + 前端
│   ├── src/main/java/...                # Spring Boot 后端
│   └── stitch_fund_dividend_tracker/    # Vue 前端
├── docs/                                # 文档
│   ├── 财务算法分析报告.md
│   ├── 功能提案_现金计算器与自动记账.md
│   └── proposals/
├── .github/workflows/                   # CI/CD
│   ├── deploy.yml                        # 前端自动部署
│   └── db-backup.yml                     # 数据库备份
└── README.md
```

---

## 部署

前端通过 GitHub Actions 自动部署到阿里云服务器：

```bash
# 推送到 main 分支即触发自动部署
git push origin main
```

后端手动部署：

```bash
# 服务器上执行
./deploy.sh
```

---

## 开发

```bash
# 前端开发
cd myPhonePro/stitch_fund_dividend_tracker
npm install
npm run dev

# 后端开发
# 使用 IDEA 打开 myPhonePro 项目，运行 MyPhoneProApplication
```

---

## 最近更新

- ✅ 现金编辑计算器（支持 +/- 相对运算）
- ✅ 自动现金记账（交易自动关联现金账户）
- ✅ 复投回本年限计算
- ✅ 数据审计器（每日自动对账）
- ✅ 修复 6 个核心 Bug（股息率异常、市值计算、数据抓取等）

---

*种树最好的时间是十年前，其次是现在。*
