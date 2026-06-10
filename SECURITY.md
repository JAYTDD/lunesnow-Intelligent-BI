# 🔒 安全配置说明

## 敏感信息管理

本项目使用环境变量管理敏感信息，避免将密码、API Key 等硬编码到代码中。

## 快速开始

### 1. 创建本地配置文件

```bash
# 复制模板文件
cp lunesnow-IntelligentBI-backend/src/main/resources/application-local.yml.example \
   lunesnow-IntelligentBI-backend/src/main/resources/application-local.yml

# 或使用环境变量
cp lunesnow-IntelligentBI-backend/.env.example lunesnow-IntelligentBI-backend/.env
```

### 2. 配置环境变量

编辑 `application-local.yml` 或 `.env` 文件，填入实际值：

```yaml
# application-local.yml
spring:
  datasource:
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:your_actual_password}

deepseek:
  api-key: ${DEEPSEEK_API_KEY:your_actual_api_key}
```

或使用 `.env` 文件：

```bash
DB_USERNAME=root
DB_PASSWORD=your_actual_password
DEEPSEEK_API_KEY=sk-your-actual-api-key
```

### 3. 激活本地配置

```bash
# 方式1：设置环境变量
export SPRING_PROFILES_ACTIVE=local

# 方式2：启动时指定
java -jar app.jar --spring.profiles.active=local
```

## 已配置的敏感信息

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|----------|--------|------|
| 数据库用户名 | `DB_USERNAME` | root | MySQL 用户名 |
| 数据库密码 | `DB_PASSWORD` | 空 | MySQL 密码 |
| DeepSeek API Key | `DEEPSEEK_API_KEY` | 空 | AI 服务密钥 |
| COS Access Key | `COS_ACCESS_KEY` | 空 | 腾讯云对象存储 |
| COS Secret Key | `COS_SECRET_KEY` | 空 | 腾讯云对象存储 |

## 安全措施

### ✅ 已实施

1. **Git Hooks 检查**
   - `.git/hooks/pre-commit` 自动检查敏感信息
   - 阻止包含密码、API Key 的代码提交

2. **.gitignore 配置**
   - 排除 `application-local.yml`
   - 排除 `.env*` 文件（除 `.env.example`）
   - 排除 Docker 配置文件

3. **环境变量替代**
   - `application.yml` 使用 `${VAR:default}` 语法
   - 敏感值从环境变量读取，无硬编码

### ⚠️ 注意事项

1. **Git 历史清理**
   - 如果敏感信息已被提交到 Git 历史，需要清理
   - 使用 BFG Repo Cleaner 或 git filter-branch

2. **生产环境**
   - 使用 Docker Secrets 或 Kubernetes Secrets
   - 不要在生产环境使用 `.env` 文件

3. **代码审查**
   - 提交前检查是否包含敏感信息
   - 使用 `git diff --cached` 查看暂存内容

## 环境变量配置

### 本地开发

```bash
# Linux/Mac
export DB_PASSWORD=your_password
export DEEPSEEK_API_KEY=sk-your-key

# Windows PowerShell
$env:DB_PASSWORD="your_password"
$env:DEEPSEEK_API_KEY="sk-your-key"

# Windows CMD
set DB_PASSWORD=your_password
set DEEPSEEK_API_KEY=sk-your-key
```

### IDE 配置

**IntelliJ IDEA:**
1. Run -> Edit Configurations
2. Environment variables 添加变量

**VS Code:**
1. 创建 `.env` 文件（已排除在 .gitignore）
2. 使用 dotenv 插件加载

## 检查敏感信息

### 手动检查

```bash
# 检查暂存区
git diff --cached | grep -i password

# 检查整个仓库
git log --all -p | grep -i "password\|api.key\|secret"
```

### 使用 Git Hooks

提交时会自动检查，发现敏感信息会阻止提交。

## 清理 Git 历史

如果敏感信息已被提交，使用以下命令清理：

```bash
# 使用 BFG Repo Cleaner（推荐）
java -jar bfg.jar --delete-files application.yml repo.git

# 或使用 git filter-branch
git filter-branch --force --index-filter \
  'git rm --cached --ignore-unmatch lunesnow-IntelligentBI-backend/src/main/resources/application.yml' \
  --prune-empty --tag-name-filter cat -- --all
```

清理后需要强制推送：

```bash
git push origin main --force
```

## 联系方式

如有安全问题，请联系项目管理员。
