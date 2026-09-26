# 安装指南（On-Prem）

## 前置条件

- Docker / Docker Compose（推荐），或 JDK 21 + Node 20 + MySQL 8 + Redis 7
- 开放端口：控制台 `8080`（Compose）或前端 `5173` + 后端 `18080`

## 一键启动（Compose）

```bash
cp .env.example .env
# 编辑 .env，至少修改 DB_PASSWORD / REDIS_PASSWORD / AES_KEY / JWT_SECRET / LICENSE_SIGN_KEY

docker compose up -d --build
```

- 控制台：http://localhost:8080  
- 后端直连：http://localhost:18080  
- 首次打开会进入「创建管理员」引导

数据库初始化脚本会按顺序挂载：`V1 → V2 → V4 → V5 → V6 → V11`（跳过仅升级用的 V3；V7–V10 仍需按升级指南补跑）。

## 本地开发启动

```bash
export DB_HOST=127.0.0.1 DB_PASSWORD=... REDIS_PASSWORD=... AES_KEY=... JWT_SECRET=...

# 先执行 sql/V1…V6（见 docs/UPGRADE.md）

cd server && mvn spring-boot:run
cd web && npm install && npm run dev
```

前端默认 http://127.0.0.1:5173，`/api` 代理到 18080。

## 首次配置清单

1. 创建管理员账号并登录  
2. 系统设置 → 填写开放平台对外地址（`OPENAPI_PUBLIC_BASE_URL`）  
3. 导入 License（可在系统设置生成一年期演示 License）  
4. 按需创建开发者 / 只读运维账号  
5. 走通：组件 → 工作流试跑发布 → 调度或开放调用 → 运行结果  

## 健康检查

- `GET /api/health`（免登录）  
- 控制台「系统设置」健康面板（需管理员）
