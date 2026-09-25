# 轻舟（qingzhou）

低代码集成调度中台：接口组件 → 工作流编排 → 试运行 → 定时调度 → 开放平台调用 → 运行结果。

面向 **单组织 On-Prem 企业版**：控制台登录、三角色权限、操作审计、License 席位与到期控制。

- 前端：`web/`（Vue 3 + Element Plus + Vite）
- 后端：`server/`（Spring Boot 3 / Java 21）
- 数据库脚本：`sql/`
- 交付：`docker-compose.yml` + [docs/INSTALL.md](docs/INSTALL.md)

## 快速开始

```bash
cp .env.example .env
docker compose up -d --build
# 浏览器打开 http://localhost:8080 ，创建管理员后即可使用
```

本地开发与环境变量说明见 [docs/INSTALL.md](docs/INSTALL.md)。升级脚本见 [docs/UPGRADE.md](docs/UPGRADE.md)。安全基线见 [docs/SECURITY.md](docs/SECURITY.md)。

## 主链路

1. **接口组件**：HTTP / curl / 数据库脚本向导接入  
2. **凭证管理**：企微、Bearer、Basic、数据库及高级鉴权；组件优先引用凭证  
3. **工作流设计器**：拖拽编排、试跑、发布  
4. **定时调度**：间隔 / 每天 / 每周 / 一次（Cron 进高级）  
5. **开放平台**：应用、授权、调用助手、HMAC 对外 API  
6. **运行结果**：全部 / 失败与超时；执行链路三段式归因  
7. **系统**：用户、审计、License、站点设置  

## 角色

| 角色 | 能力 |
|------|------|
| 管理员 | 用户 / 审计 / License / 系统设置 + 全部业务 |
| 开发者 | 组件、凭证、工作流、调度、开放应用 CRUD |
| 只读运维 | 工作台与运行观测只读 |

## 编译注意

Java 21 下 `server/pom.xml` 已为 `maven-compiler-plugin` 配置 Lombok `annotationProcessorPaths`。
