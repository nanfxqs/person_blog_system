# 个人博客系统 (Personal Blog System)

> 基于 Java + FastAPI + PostgreSQL + DeepSeek AI 的现代博客平台

## 📋 目录

- [项目概述](#项目概述)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [前端界面说明](#前端界面说明)
  - [AI 功能使用说明](#ai-功能使用说明)
- [API 文档](#api-文档)
- [项目结构详解](#项目结构详解)
- [配置说明](#配置说明)
- [常见问题](#常见问题)

---

## 项目概述

这是一个功能完整的个人博客系统，采用前后端分离架构：

- **后端**: Java Spring Boot (REST API)
- **前端**: Python FastAPI (服务器端渲染)
- **数据库**: PostgreSQL
- **AI 功能**: DeepSeek 大模型集成

### 核心功能

| 功能模块 | 说明 |
|---------|------|
| 👤 用户系统 | 注册、登录、JWT 认证、角色权限 (admin/user) |
| 📝 文章管理 | 创建、编辑、发布/草稿、Markdown 支持 |
| 💬 评论系统 | 登录用户可评论，站长可管理 |
| 🤖 AI 摘要 | 自动生成文章摘要 (管理员触发) |
| ❓ AI 问答 | 基于文章内容智能问答 |
| 🔒 安全防护 | CSRF 保护、XSS 防护、HttpOnly Cookie |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户浏览器                             │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (FastAPI)                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  端口: 8000                                          │   │
│  │  功能: HTML 页面渲染 + 静态文件服务                    │   │
│  │  代理: /api/* 请求转发到后端                          │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP (内部网络)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  端口: 8080                                          │   │
│  │  功能: REST API + 业务逻辑 + AI 集成                  │   │
│  │  认证: JWT Token (存储在 HttpOnly Cookie)            │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ JDBC
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Database (PostgreSQL)                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  端口: 5432 (内部)                                    │   │
│  │  迁移: Flyway 自动管理数据库结构                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 数据流向

1. **用户访问** → 浏览器访问 `http://localhost:8000` (前端)
2. **页面渲染** → FastAPI 返回 HTML 页面 (Jinja2 模板)
3. **API 请求** → 前端将 `/api/*` 请求代理到后端 (8080 端口)
4. **业务处理** → Spring Boot 处理请求，访问数据库
5. **AI 功能** → 后端调用 DeepSeek API 进行智能处理

---

## 快速开始

### 环境要求

- Docker & Docker Compose
- Git
- (可选) Java 17+ (本地开发)
- (可选) Python 3.11+ (本地开发)

### 1. 克隆项目

```bash
git clone git@github.com:nanfxqs/person_blog_system.git
cd person_blog_system
```

### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填写以下关键配置
nano .env
```

**必须配置的项：**

```bash
# 数据库配置
DB_HOST=db
DB_PORT=5432
DB_NAME=person_blog_db
DB_USER=blog_user
DB_PASSWORD=your_secure_password_here

# JWT 密钥 (至少 32 个字符，用于签名)
JWT_SECRET=your-super-secret-jwt-key-at-least-32-characters-long

# 管理员账号
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=admin123456

# DeepSeek AI API (可选，用于 AI 功能)
DEEPSEEK_API_KEY=sk-your-deepseek-api-key
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat
```

### 3. 启动服务

```bash
# 启动所有服务 (后台运行)
docker compose up -d

# 查看日志
docker compose logs -f

# 或者只查看后端日志
docker compose logs -f backend
```

### 4. 访问应用

- **前端界面**: http://localhost:8000
- **后端 API**: http://localhost:8080/api

### 5. 首次使用

1. 访问 http://localhost:8000
2. 使用管理员账号登录：
   - 邮箱: `admin@example.com` (或你在 .env 中配置的)
   - 密码: `admin123456` (或你在 .env 中配置的)
3. 进入后台管理页面创建文章

---

## 前端界面说明

### 可用页面

| 页面 | 路径 | 说明 |
|-----|------|------|
| 首页 | `/` | 显示已发布的文章列表 |
| 文章详情 | `/posts/{id}` | 查看单篇文章、AI 问答、评论 |
| 登录 | `/login` | 用户登录 |
| 注册 | `/register` | 用户注册 |
| 后台首页 | `/admin` | 管理员仪表盘 |
| 文章管理 | `/admin/posts` | 创建/编辑/删除文章，AI 生成摘要 |
| 评论管理 | `/admin/comments` | 管理评论 |

### AI 功能使用说明

#### 🤖 AI 摘要生成 (管理员功能)

**使用位置**: 后台管理 → 编辑文章 → 摘要区域

1. 登录管理员账号
2. 进入后台 `/admin`
3. 点击已有文章进行编辑
4. 在"摘要"字段下方点击 **"生成 AI 摘要"** 按钮
5. AI 会自动分析文章内容，生成摘要并填入摘要框
6. 点击"Save"保存文章

**功能说明**:
- 仅对已有文章显示按钮（新建文章需先保存）
- 调用 DeepSeek AI 分析文章内容
- 生成中文摘要，保留关键信息
- 所有生成记录保存到数据库

#### ❓ AI 问答 (登录用户功能)

**使用位置**: 文章详情页 → AI 问答区域

1. 登录任意账号
2. 打开任意已发布的文章
3. 在评论区上方找到 **"AI 问答"** 区域
4. 在输入框中输入问题，例如：
   - "这篇文章主要讲了什么？"
   - "总结一下要点"
   - "文章中的技术是什么？"
5. 点击 **"提问"** 按钮
6. AI 会根据文章内容给出回答

**功能说明**:
- 仅登录用户可用
- AI 只基于当前文章内容回答
- 支持连续提问，每次独立回答
- 回答为中文，保持原文语言风格

### 界面截图说明

**文章编辑页 (管理员)**:
```
┌─────────────────────────────────────┐
│ Title: [文章标题输入框]              │
├─────────────────────────────────────┤
│ Summary: [摘要输入框]                │
│         [生成 AI 摘要] ← 紫色按钮    │
│         AI 摘要已生成 ✓              │
├─────────────────────────────────────┤
│ Content: [Markdown 编辑器]           │
├─────────────────────────────────────┤
│ [Save] [Publish] [Delete]            │
└─────────────────────────────────────┘
```

**文章详情页 (登录用户)**:
```
┌─────────────────────────────────────┐
│ 文章标题                             │
│ 发布日期                             │
│ 文章内容...                          │
├─────────────────────────────────────┤
│ AI 问答                              │
│ 对这篇文章有疑问？向 AI 提问吧       │
│ [输入问题...              ] [提问]   │
│ AI: 这篇文章主要介绍了...            │
├─────────────────────────────────────┤
│ Comments                             │
│ ...                                  │
└─────────────────────────────────────┘
```

---

## API 文档

### 认证相关

```
POST   /api/auth/register          # 注册
POST   /api/auth/login             # 登录
POST   /api/auth/logout            # 登出
POST   /api/auth/refresh           # 刷新 Token
GET    /api/auth/me                # 获取当前用户信息
```

### 文章相关 (公开)

```
GET    /api/posts?page=0&size=10   # 获取文章列表 (仅已发布)
GET    /api/posts/{id}             # 获取文章详情
POST   /api/posts/{id}/qa          # AI 问答 (需登录)
```

### 评论相关

```
GET    /api/posts/{postId}/comments    # 获取文章评论
POST   /api/posts/{postId}/comments    # 发表评论 (需登录)
```

### 管理员接口 (需 admin 角色)

```
# 文章管理
GET    /api/admin/posts              # 获取所有文章 (包括草稿)
POST   /api/admin/posts              # 创建文章
PUT    /api/admin/posts/{id}         # 更新文章
DELETE /api/admin/posts/{id}         # 删除文章
POST   /api/admin/posts/{id}/publish  # 发布文章
POST   /api/admin/posts/{id}/summary:generate  # AI 生成摘要

# 评论管理
GET    /api/admin/comments           # 获取所有评论
DELETE /api/admin/comments/{id}      # 删除评论
```

### 健康检查

```
GET    /api/health                   # 服务健康状态
```

---

## 项目结构详解

```
person-blog-system/
│
├── docker-compose.yml              # Docker 编排配置
├── .env.example                    # 环境变量模板
├── .env                            # 实际环境变量 (不提交到 git)
├── .gitignore                      # Git 忽略规则
│
├── backend/                        # Java 后端
│   ├── Dockerfile                  # 后端容器构建
│   ├── pom.xml                     # Maven 依赖配置
│   └── src/
│       └── main/
│           ├── java/com/blog/
│           │   ├── BlogApplication.java          # 应用入口
│           │   ├──
│           │   ├── controller/                   # REST API 控制器
│           │   │   ├── AuthController.java       # 认证接口
│           │   │   ├── PostController.java       # 文章查询 (公开)
│           │   │   ├── AdminPostController.java  # 文章管理 (管理员)
│           │   │   ├── CommentController.java    # 评论接口
│           │   │   ├── AiSummaryController.java  # AI 摘要
│           │   │   ├── AiQaController.java       # AI 问答
│           │   │   └── HealthController.java     # 健康检查
│           │   │
│           │   ├── service/                      # 业务逻辑层
│           │   │   ├── AiSummaryService.java     # AI 摘要服务
│           │   │   └── AiQaService.java          # AI 问答服务
│           │   │
│           │   ├── entity/                       # 数据库实体
│           │   │   ├── User.java                 # 用户
│           │   │   ├── Post.java                 # 文章
│           │   │   ├── Comment.java              # 评论
│           │   │   ├── RefreshToken.java         # 刷新令牌
│           │   │   ├── AiSummaryLog.java         # AI 摘要日志
│           │   │   └── AiQaLog.java              # AI 问答日志
│           │   │
│           │   ├── repository/                   # 数据访问层
│           │   │   ├── UserRepository.java
│           │   │   ├── PostRepository.java
│           │   │   └── ... (其他 Repository)
│           │   │
│           │   ├── security/                     # 安全组件
│           │   │   ├── JwtUtil.java              # JWT 工具
│           │   │   ├── JwtAuthenticationFilter.java
│           │   │   ├── CsrfProtectionFilter.java
│           │   │   └── RefreshTokenService.java
│           │   │
│           │   ├── client/                       # 外部客户端
│           │   │   └── DeepSeekClient.java       # DeepSeek API 客户端
│           │   │
│           │   └── config/                       # 配置类
│           │       ├── SecurityConfig.java       # 安全配置
│           │       ├── DeepSeekConfig.java       # AI 配置
│           │       └── SeedAdminRunner.java      # 初始化管理员
│           │
│           └── resources/
│               ├── application.yml               # 应用配置
│               └── db/migration/                 # 数据库迁移脚本
│                   ├── V1__create_users_table.sql
│                   ├── V2__create_posts_table.sql
│                   ├── V3__create_comments_table.sql
│                   ├── V4__create_refresh_tokens_table.sql
│                   ├── V5__create_ai_summary_logs_table.sql
│                   └── V6__create_ai_qa_logs_table.sql
│
├── frontend/                       # Python 前端
│   ├── Dockerfile                  # 前端容器构建
│   ├── requirements.txt            # Python 依赖
│   ├── main.py                     # FastAPI 应用入口
│   │
│   ├── templates/                  # HTML 模板 (Jinja2)
│   │   ├── base.html               # 基础模板 (导航、布局)
│   │   ├── index.html              # 首页 (文章列表)
│   │   ├── post_detail.html        # 文章详情页
│   │   ├── login.html              # 登录页
│   │   ├── register.html           # 注册页
│   │   ├── error.html              # 错误页
│   │   └── admin/                  # 后台管理模板
│   │       ├── dashboard.html      # 仪表盘
│   │       ├── post_form.html      # 文章编辑表单
│   │       └── comments.html       # 评论管理
│   │
│   └── static/                     # 静态文件
│       └── css/
│           └── style.css           # 样式文件
│
├── scripts/                        # 工具脚本
│   └── e2e.sh                      # 端到端测试脚本
│
└── .sisyphus/                      # 开发文档和证据
    ├── drafts/                     # 设计文档
    ├── plans/                      # 实现计划
    └── evidence/                   # 测试证据
```

### 核心文件作用说明

#### 后端关键文件

| 文件 | 作用 |
|-----|------|
| `BlogApplication.java` | Spring Boot 应用入口，启动整个后端服务 |
| `AuthController.java` | 处理登录/注册/登出，返回 JWT Token |
| `PostController.java` | 公开的文章查询接口，支持分页和过滤 |
| `AdminPostController.java` | 管理员文章管理，包括发布/草稿切换 |
| `AiSummaryController.java` | 调用 DeepSeek 生成文章摘要 |
| `AiQaController.java` | 调用 DeepSeek 回答关于文章的问题 |
| `JwtAuthenticationFilter.java` | 拦截请求，验证 JWT Token 有效性 |
| `CsrfProtectionFilter.java` | 防止 CSRF 攻击的双重提交验证 |
| `DeepSeekClient.java` | HTTP 客户端，调用 DeepSeek API |

#### 前端关键文件

| 文件 | 作用 |
|-----|------|
| `main.py` | FastAPI 应用，定义路由和代理规则 |
| `base.html` | 基础模板，包含导航栏和公共样式 |
| `index.html` | 首页，显示文章列表 |
| `post_detail.html` | 文章详情，包含评论区 |
| `admin/post_form.html` | 文章编辑表单 (Markdown 编辑器) |

#### 配置关键文件

| 文件 | 作用 |
|-----|------|
| `docker-compose.yml` | 定义三个服务 (db, backend, frontend) 的编排 |
| `application.yml` | Spring Boot 配置，数据库连接、JWT、AI 等 |
| `.env` | 敏感信息配置 (数据库密码、API Key 等) |

---

## 配置说明

### 环境变量详解

#### 数据库配置

```bash
DB_HOST=db              # 数据库主机名 (Docker 网络内)
DB_PORT=5432            # PostgreSQL 端口
DB_NAME=person_blog_db  # 数据库名
DB_USER=blog_user       # 数据库用户名
DB_PASSWORD=***         # 数据库密码 (必须强密码)
```

#### JWT 配置

```bash
JWT_SECRET=your-secret  # JWT 签名密钥 (至少 32 字符)
JWT_ACCESS_EXPIRATION=900        # Access Token 有效期 (秒)
JWT_REFRESH_EXPIRATION=604800    # Refresh Token 有效期 (秒)
```

#### Cookie 配置

```bash
COOKIE_SECURE=false     # 生产环境设为 true (HTTPS)
COOKIE_SAME_SITE=Strict # 防止 CSRF
```

#### AI 配置

```bash
DEEPSEEK_API_KEY=sk-***         # DeepSeek API 密钥
DEEPSEEK_BASE_URL=https://...   # API 基础 URL
DEEPSEEK_MODEL=deepseek-chat    # 使用的模型
DEEPSEEK_TIMEOUT=30000          # 请求超时 (毫秒)
DEEPSEEK_MAX_CONTENT_LENGTH=5000 # 最大内容长度
```

---

## 常见问题

### Q: 如何重置数据库？

```bash
# 停止并删除所有容器和数据卷
docker compose down -v

# 重新启动 (会自动重新创建数据库)
docker compose up -d
```

### Q: 如何查看后端日志？

```bash
# 实时查看日志
docker compose logs -f backend

# 查看最近 100 行
docker compose logs --tail=100 backend
```

### Q: 如何修改端口？

编辑 `docker-compose.yml`：

```yaml
services:
  backend:
    ports:
      - "9090:8080"  # 改为 9090 端口
  
  frontend:
    ports:
      - "3000:8000"  # 改为 3000 端口
```

### Q: AI 功能无法使用？

检查以下几点：

1. **API Key 是否正确配置**：
   ```bash
   grep DEEPSEEK_API_KEY .env
   ```

2. **后端是否获取到环境变量**：
   ```bash
   docker compose exec backend env | grep DEEPSEEK
   ```

3. **重启后端服务**：
   ```bash
   docker compose restart backend
   ```

### Q: 如何添加前端 AI 按钮？

参考以下代码片段：

**在文章编辑页面添加摘要按钮** (`frontend/templates/admin/post_form.html`):

```html
<button type="button" onclick="generateSummary()">生成 AI 摘要</button>
<div id="ai-summary-result"></div>

<script>
async function generateSummary() {
  const postId = document.getElementById('post-id').value;
  const response = await fetch(`/api/admin/posts/${postId}/summary:generate`, {
    method: 'POST',
    credentials: 'include'
  });
  const data = await response.json();
  document.getElementById('ai-summary-result').textContent = data.summary;
}
</script>
```

---

## 开发指南

### 本地开发后端

```bash
cd backend

# 使用 Maven 运行
mvn spring-boot:run

# 或者构建后运行
mvn package -DskipTests
java -jar target/blog-0.0.1-SNAPSHOT.jar
```

### 本地开发前端

```bash
cd frontend

# 创建虚拟环境
python -m venv .venv
source .venv/bin/activate  # Linux/Mac
# 或 .venv\Scripts\activate  # Windows

# 安装依赖
pip install -r requirements.txt

# 运行开发服务器
uvicorn main:app --reload --port 8000
```

### 运行测试

```bash
# 运行端到端测试
./scripts/e2e.sh --no-build

# 跳过 AI 测试
./scripts/e2e.sh --no-build --skip-ai
```

---

## 技术栈版本

| 组件 | 版本 |
|-----|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| PostgreSQL | 15 |
| Python | 3.11 |
| FastAPI | 0.104+ |
| DeepSeek API | v1 |

---

## 许可证

MIT License

---

## 联系与支持

如有问题，请通过 GitHub Issues 反馈。

**项目地址**: https://github.com/nanfxqs/person_blog_system
