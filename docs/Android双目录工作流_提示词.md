# Android 项目双目录工作流 — 给 AI 的提示词

## 前置准备：配置 Trae 沙箱权限

在开始前，先确保 Trae 的沙箱自定义配置已添加工作目录的读写权限。

检查 `%USERPROFILE%\.trae-cn\sandbox.json` 文件是否存在，内容如下：

```json
{
  "filesystem": {
    "readWrite": [
      "C:\\Users\\23982\\Desktop\\AI agent\\ASWorkspace",
      "C:\\Users\\23982\\Desktop\\AI agent\\TraeWorkspace"
    ],
    "readOnly": []
  },
  "network": {
    "default": "allow",
    "allow": [],
    "deny": []
  }
}
```

> 如果没有这个文件，在 Trae 中打开 设置 → 对话流 → 沙箱自定义配置 → 点击"打开配置"，Trae 会自动创建该文件，然后填入以上内容保存即可。
>
> 这个配置让沙箱在 ASWorkspace 和 TraeWorkspace 目录下以你的用户身份创建文件，避免权限问题。

## 背景

我的开发环境是 Windows。Trae（AI IDE）的 AI Agent 在沙箱中执行命令和创建文件。早期版本中沙箱创建的文件可能与我的 Windows 用户权限冲突，导致 Android Studio 的 Gradle 编译失败。

**解决方案：Trae 和 AS 各自用独立的工作目录，通过 Git 同步代码，互不干扰。**

## 要求

请帮我为一个新的 Android 项目搭建以下双目录工作流：

### 目录结构

```
GitHub: <org>/<project-name>  ← 唯一代码仓库
         ├── (push/pull) ── C:\Users\23982\Desktop\AI agent\TraeWorkspace\<project-name>\   ← Trae 编辑区
         └── (push/pull) ── C:\Users\23982\Desktop\AI agent\ASWorkspace\<project-name>\     ← AS 编译区
```

### 工作流程

1. **AS → Git**：用户在 AS 中写代码、commit、push
2. **Git → Trae**：用户在 Trae 中 pull 代码，用 AI 辅助重构、修改
3. **Trae → Git**：AI 改完代码后，commit、push（自动执行或引导用户操作）
4. **Git → AS**：用户切回 AS，pull 代码后直接编译运行

### 初始化步骤

请按顺序执行：

1. 如果 GitHub 仓库不存在，先在 GitHub 创建空仓库
2. **先在 ASWorkspace 中用 Android Studio 创建项目脚手架**：
   - 打开 Android Studio → New Project，选择模板创建项目
   - 保存到 `C:\Users\23982\Desktop\AI agent\ASWorkspace\<project-name>`
   - AS 会自动生成 `gradle-wrapper.jar`、`build.gradle` 等脚手架文件
   - 确认 Gradle 编译通过
   - commit + push 到 GitHub
3. **用户手动操作（在独立的 CMD/PowerShell 窗口中，不要在 Trae 的终端里）**，在 `ASWorkspace` 目录下克隆项目脚手架：
   ```bash
   cd C:\Users\23982\Desktop\AI agent\ASWorkspace\
   git clone <repo-url> <project-name>
   ```
   > 从此以后，ASWorkspace 的所有 `git pull` / `checkout` / `merge` 等操作，**都必须在这个独立终端中执行**，不能使用 Trae 内置的终端面板。
4. **在 Trae 中**，让 AI 在 `TraeWorkspace` 目录下克隆项目：
   ```bash
   cd C:\Users\23982\Desktop\AI agent\TraeWorkspace\
   git clone <repo-url> <project-name>
   ```
5. 回到 ASWorkspace 的独立终端，pull 确认两边一致
6. 在 AS 和 Trae 中分别打开，验证均能正常工作

### 注意事项

- `.gitignore` 必须包含以下内容（在 AS 创建项目时自动生成，确认不要遗漏）：
  ```gitignore
  *.iml
  .gradle/
  /build/
  /local.properties
  /.idea/
  .DS_Store
  /captures
  .externalNativeBuild/
  .cxx/
  *.apk
  *.aab
  *.hprof
  ```
- `ASWorkspace` 目录一旦初始化完成，**不要用 Trae 打开或修改**，只做 `git pull`
- `TraeWorkspace` 目录**不要运行 Gradle 编译**，只做代码编辑和 Git 操作
- 两个目录互不干扰，通过 Git 作为唯一的代码同步桥梁
- Git 的 remote URL 建议使用 HTTPS + Token 或 SSH 密钥方式，确保无密码 push/pull
- **🔴 关键：ASWorkspace 的所有文件操作（clone、pull、checkout、merge）必须由用户手动在独立终端中执行，绝不能由 AI/Trae 代为操作，也不能用 Trae 的内置终端。** Trae 沙箱创建的任何文件都会污染权限，导致 AS 编译失败。如果发现 AS 编译报权限错误，检查 ASWorkspace 目录是否被 Trae 写入过。

### 冲突处理

如果出现代码冲突（例如 AS 和 Trae 分别修改了同一文件）：

1. 在 ASWorkspace 的独立终端中执行 `git pull`，Git 会提示冲突
2. **在 ASWorkspace 中，用 Android Studio 打开项目**，通过 AS 的 Merge 工具解决冲突（AS 有图形化冲突解决界面）
3. 解决后 commit + push
4. 切到 TraeWorkspace，在 Trae 终端中 `git pull` 同步
5. 继续开发

> 不要尝试在 TraeWorkspace 中解决冲突，因为 Trae 没有图形化 Merge 工具，且解决冲突涉及大量文件写入（沙箱权限污染风险）。**冲突必须在 ASWorkspace 侧解决。**
